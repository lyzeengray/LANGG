package com.example.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

import android.util.Log

data class CompositorDiagnostics(
    val status: String = LvngShizukuInputService.COMPOSITOR_STOPPED,
    val detail: String = "Compositor belum dimulai",
    val updatedAtMs: Long = 0L
) {
    val isGrabbed: Boolean
        get() = status == LvngShizukuInputService.COMPOSITOR_GRABBED
}

data class ShizukuLiveStatus(
    val isInstalled: Boolean = false,
    val isRunning: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val isUserServiceBound: Boolean = false
) {
    val isReady: Boolean
        get() = isRunning && isPermissionGranted
}

/**
 * Manages live Shizuku binder detection, runtime permission prompts ("Allow LVNG SPACE"),
 * and privileged UserService binding for Macro auto-tap, in-game touch visibility, and device boost.
 */
object LvngShizukuManager {

    private const val DIAG_TAG = "LvngTouchCompositor"
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val SHIZUKU_PERMISSION_REQ_CODE = 10421
    private const val BIND_TIMEOUT_MS = 8_000L
    private const val BIND_COOLDOWN_MS = 2_500L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val bindLock = ReentrantLock()
    private val bindCondition = bindLock.newCondition()

    private val _status = MutableStateFlow(ShizukuLiveStatus())
    val status: StateFlow<ShizukuLiveStatus> = _status.asStateFlow()

    private val _compositorDiagnostics = MutableStateFlow(CompositorDiagnostics())
    val compositorDiagnostics: StateFlow<CompositorDiagnostics> = _compositorDiagnostics.asStateFlow()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var remoteInputBinder: IBinder? = null

    @Volatile
    var isCompositorActive: Boolean = false
        private set

    @Volatile
    private var isBindingInProgress = false

    @Volatile
    private var lastBindAttemptElapsedMs = 0L

    @Volatile
    private var listenersRegistered = false

    private val repeatSeqCounter = AtomicLong(0L)
    private val minValidRepeatSeq = AtomicLong(0L)
    private val repeatStopExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "LvngMacroStop").apply { isDaemon = true }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        mainHandler.post {
            runCatching {
                val snap = refreshStatus()
                if (snap.isReady) {
                    bindUserServiceIfNeeded()
                }
            }
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        bindLock.withLock {
            remoteInputBinder = null
            isCompositorActive = false
            isBindingInProgress = false
            bindCondition.signalAll()
        }
        updateCompositorDiagnostic(
            status = LvngShizukuInputService.COMPOSITOR_STOPPED,
            detail = "Shizuku binder terputus"
        )
        mainHandler.post {
            runCatching { refreshStatus() }
        }
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_REQ_CODE) {
                mainHandler.post {
                    runCatching {
                        val snapshot = refreshStatus()
                        if (grantResult == PackageManager.PERMISSION_GRANTED && snapshot.isReady) {
                            bindUserServiceIfNeeded()
                        }
                    }
                }
            }
        }

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bindLock.withLock {
                remoteInputBinder = service
                isBindingInProgress = false
                bindCondition.signalAll()
            }
            val alive = runCatching { service?.isBinderAlive == true }.getOrDefault(false)
            _status.value = _status.value.copy(isUserServiceBound = alive)
            if (alive && service != null) {
                warmupTouchBackendAsync(service)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bindLock.withLock {
                remoteInputBinder = null
                isCompositorActive = false
                isBindingInProgress = false
                bindCondition.signalAll()
            }
            updateCompositorDiagnostic(
                status = LvngShizukuInputService.COMPOSITOR_STOPPED,
                detail = "UserService terputus"
            )
            _status.value = _status.value.copy(isUserServiceBound = false)
        }
    }

    fun init(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        bindLock.withLock {
            if (!listenersRegistered) {
                listenersRegistered = true
                runCatching { Shizuku.addBinderReceivedListenerSticky(binderReceivedListener, mainHandler) }
                runCatching { Shizuku.addBinderDeadListener(binderDeadListener, mainHandler) }
                runCatching { Shizuku.addRequestPermissionResultListener(permissionResultListener, mainHandler) }
            }
        }
        val snapshot = refreshStatus(ctx)
        if (snapshot.isReady) {
            bindUserServiceIfNeeded()
        }
    }

    /**
     * Pure status inspection — never triggers bindUserService() recursively so it cannot
     * overflow the stack when permission becomes granted or during startup/lifecycle callbacks.
     */
    fun refreshStatus(context: Context? = appContext): ShizukuLiveStatus {
        val ctx = context?.applicationContext ?: appContext
        if (ctx != null) appContext = ctx

        val installed = ctx?.let { isPackageInstalled(it, SHIZUKU_PACKAGE) } ?: false
        val running = runCatching {
            Shizuku.pingBinder() && !Shizuku.isPreV11()
        }.getOrDefault(false)

        val granted = if (running) {
            runCatching {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
        } else {
            false
        }

        val bound: Boolean
        bindLock.withLock {
            if (!running || !granted) {
                remoteInputBinder = null
                isBindingInProgress = false
                bindCondition.signalAll()
            }
            bound = runCatching { remoteInputBinder?.isBinderAlive == true }.getOrDefault(false)
            if (!bound && remoteInputBinder != null) {
                remoteInputBinder = null
            }
        }

        val snapshot = ShizukuLiveStatus(
            isInstalled = installed,
            isRunning = running,
            isPermissionGranted = granted,
            isUserServiceBound = bound
        )
        _status.value = snapshot
        return snapshot
    }

    fun requestPermission(context: Context): Boolean {
        val current = refreshStatus(context)
        if (!current.isRunning) return false
        if (current.isPermissionGranted) {
            bindUserServiceIfNeeded()
            return true
        }
        return runCatching {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQ_CODE)
            true
        }.getOrDefault(false)
    }

    fun openShizukuApp(context: Context): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    fun isReady(context: Context? = appContext): Boolean = refreshStatus(context).isReady

    fun bindUserServiceIfNeeded() {
        val ctx = appContext ?: return
        val statusSnapshot = refreshStatus(ctx)
        if (!statusSnapshot.isReady) return

        val now = SystemClock.elapsedRealtime()
        bindLock.withLock {
            if (remoteInputBinder?.isBinderAlive == true) return
            if (isBindingInProgress && (now - lastBindAttemptElapsedMs) < BIND_TIMEOUT_MS) return
            if (!isBindingInProgress && lastBindAttemptElapsedMs != 0L && (now - lastBindAttemptElapsedMs) < BIND_COOLDOWN_MS) return
            isBindingInProgress = true
            lastBindAttemptElapsedMs = now
        }

        val bindBlock = Runnable {
            val currentReady = runCatching {
                Shizuku.pingBinder() && !Shizuku.isPreV11() &&
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
            if (!currentReady) {
                bindLock.withLock {
                    isBindingInProgress = false
                    bindCondition.signalAll()
                }
                return@Runnable
            }
            val args = Shizuku.UserServiceArgs(
                ComponentName(ctx.packageName, LvngShizukuInputService::class.java.name)
            )
                .daemon(false)
                .processNameSuffix("lvng_input")
                .debuggable(false)
                .version(12)

            runCatching {
                Shizuku.bindUserService(args, userServiceConnection)
            }.onFailure {
                bindLock.withLock {
                    isBindingInProgress = false
                    bindCondition.signalAll()
                }
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            bindBlock.run()
        } else {
            mainHandler.post(bindBlock)
        }
    }

    private fun getAliveBinderImmediate(): IBinder? {
        return bindLock.withLock {
            remoteInputBinder?.takeIf { runCatching { it.isBinderAlive }.getOrDefault(false) }
        }
    }

    private fun buildNotReadyReason(status: ShizukuLiveStatus): String {
        return when {
            !status.isInstalled -> "Aplikasi Shizuku belum terpasang"
            !status.isRunning -> "Layanan Shizuku belum berjalan (aktifkan lewat Shizuku dulu)"
            !status.isPermissionGranted -> "Izin Shizuku untuk LVNG SPACE belum diberikan"
            else -> "Shizuku UserService gagal terhubung (coba restart Shizuku)"
        }
    }

    private fun getOrWaitForBinder(timeoutMs: Long = 3_500L): IBinder? {
        getAliveBinderImmediate()?.let { return it }

        val snap = refreshStatus()
        if (!snap.isReady) {
            return null
        }

        bindUserServiceIfNeeded()

        if (Looper.myLooper() == Looper.getMainLooper()) {
            return getAliveBinderImmediate()
        }

        val deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.coerceAtLeast(100L))
        bindLock.withLock {
            while (true) {
                val alive = remoteInputBinder?.takeIf { runCatching { it.isBinderAlive }.getOrDefault(false) }
                if (alive != null) return alive

                val remainingNs = deadlineNs - System.nanoTime()
                if (remainingNs <= 0L) return null

                runCatching {
                    bindCondition.await(remainingNs.coerceAtMost(TimeUnit.MILLISECONDS.toNanos(300L)), TimeUnit.NANOSECONDS)
                }
                if (!isBindingInProgress && remoteInputBinder == null) {
                    // Re-check once after mainHandler had a chance to invoke bindUserService
                    val checkAgain = refreshStatus()
                    if (!checkAgain.isReady) return null
                }
            }
        }
    }

    private fun updateCompositorDiagnostic(status: String, detail: String) {
        val safeStatus = when (status) {
            LvngShizukuInputService.COMPOSITOR_STARTING,
            LvngShizukuInputService.COMPOSITOR_GRABBED,
            LvngShizukuInputService.COMPOSITOR_FAILED,
            LvngShizukuInputService.COMPOSITOR_STOPPED -> status
            else -> LvngShizukuInputService.COMPOSITOR_STOPPED
        }
        isCompositorActive = (safeStatus == LvngShizukuInputService.COMPOSITOR_GRABBED)
        val prev = _compositorDiagnostics.value
        if (prev.status != safeStatus || prev.detail != detail) {
            if (safeStatus == LvngShizukuInputService.COMPOSITOR_FAILED) {
                Log.e(DIAG_TAG, "[Manager] status=$safeStatus | $detail")
            } else {
                Log.i(DIAG_TAG, "[Manager] status=$safeStatus | $detail")
            }
        }
        _compositorDiagnostics.value = CompositorDiagnostics(
            status = safeStatus,
            detail = detail,
            updatedAtMs = SystemClock.elapsedRealtime()
        )
    }

    private fun readDiagnosticTail(reply: Parcel, fallbackGrabbed: Boolean) {
        if (reply.dataAvail() <= 0) {
            updateCompositorDiagnostic(
                status = if (fallbackGrabbed) {
                    LvngShizukuInputService.COMPOSITOR_GRABBED
                } else {
                    LvngShizukuInputService.COMPOSITOR_STOPPED
                },
                detail = if (fallbackGrabbed) "Grab aktif" else "Status compositor belum tersedia"
            )
            return
        }
        val status = reply.readString().orEmpty().ifBlank {
            if (fallbackGrabbed) LvngShizukuInputService.COMPOSITOR_GRABBED else LvngShizukuInputService.COMPOSITOR_STOPPED
        }
        val detail = if (reply.dataAvail() > 0) reply.readString().orEmpty() else ""
        val normalizedStatus = if (status == LvngShizukuInputService.COMPOSITOR_GRABBED && !fallbackGrabbed) {
            LvngShizukuInputService.COMPOSITOR_FAILED
        } else {
            status
        }
        updateCompositorDiagnostic(normalizedStatus, detail)
    }

    private fun warmupTouchBackendAsync(binder: IBinder) {
        Thread({
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(LvngShizukuInputService.DESCRIPTOR)
                binder.transact(LvngShizukuInputService.TRANSACTION_PING, data, reply, 0)
                reply.readException()
                val res = reply.readInt()
                val diagStatus = if (reply.dataAvail() > 0) reply.readString().orEmpty() else ""
                val diagDetail = if (reply.dataAvail() > 0) reply.readString().orEmpty() else ""
                if (res == 2 || diagStatus == LvngShizukuInputService.COMPOSITOR_GRABBED ||
                    diagStatus == LvngShizukuInputService.COMPOSITOR_FAILED
                ) {
                    val normalizedStatus = if (diagStatus == LvngShizukuInputService.COMPOSITOR_GRABBED && res != 2) {
                        LvngShizukuInputService.COMPOSITOR_FAILED
                    } else {
                        diagStatus.ifBlank { LvngShizukuInputService.COMPOSITOR_GRABBED }
                    }
                    updateCompositorDiagnostic(normalizedStatus, diagDetail)
                }
            } catch (_: Throwable) {
                // Non-fatal; warmup will run lazily on first tap if needed
            } finally {
                data.recycle()
                reply.recycle()
            }
        }, "LvngShizukuWarmup").apply {
            isDaemon = true
            start()
        }
    }

    fun setCompositorActiveAsync(enabled: Boolean, rotation: Int = 0) {
        if (enabled) {
            val cur = _compositorDiagnostics.value
            if (cur.status != LvngShizukuInputService.COMPOSITOR_GRABBED) {
                updateCompositorDiagnostic(
                    status = LvngShizukuInputService.COMPOSITOR_STARTING,
                    detail = "Menyiapkan Multi-Touch InputInjector..."
                )
            }
        }
        repeatStopExecutor.execute {
            val binder = if (enabled) {
                getOrWaitForBinder(timeoutMs = 4_000L)
            } else {
                getAliveBinderImmediate()
            }
            if (binder == null) {
                if (!enabled) {
                    updateCompositorDiagnostic(
                        status = LvngShizukuInputService.COMPOSITOR_STOPPED,
                        detail = "Compositor dihentikan"
                    )
                } else {
                    val snap = refreshStatus()
                    updateCompositorDiagnostic(
                        status = LvngShizukuInputService.COMPOSITOR_FAILED,
                        detail = buildNotReadyReason(snap)
                    )
                }
                return@execute
            }
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(LvngShizukuInputService.DESCRIPTOR)
                data.writeInt(if (enabled) 1 else 0)
                data.writeInt(rotation)
                val transacted = binder.transact(
                    LvngShizukuInputService.TRANSACTION_SET_COMPOSITOR_ACTIVE,
                    data,
                    reply,
                    0
                )
                if (transacted) {
                    reply.readException()
                    val grabbedNow = reply.readInt() == 1
                    readDiagnosticTail(reply, fallbackGrabbed = grabbedNow)
                } else {
                    updateCompositorDiagnostic(
                        status = LvngShizukuInputService.COMPOSITOR_FAILED,
                        detail = "Binder transact SET_COMPOSITOR_ACTIVE gagal"
                    )
                }
            } catch (t: Throwable) {
                updateCompositorDiagnostic(
                    status = if (enabled) LvngShizukuInputService.COMPOSITOR_FAILED else LvngShizukuInputService.COMPOSITOR_STOPPED,
                    detail = "SET_COMPOSITOR_ACTIVE error: ${t.javaClass.simpleName}: ${t.message}"
                )
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }

    fun refreshCompositorStateAsync() {
        repeatStopExecutor.execute {
            val binder = getAliveBinderImmediate() ?: run {
                updateCompositorDiagnostic(
                    status = LvngShizukuInputService.COMPOSITOR_STOPPED,
                    detail = "UserService belum terhubung"
                )
                return@execute
            }
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(LvngShizukuInputService.DESCRIPTOR)
                val transacted = binder.transact(
                    LvngShizukuInputService.TRANSACTION_GET_COMPOSITOR_DIAGNOSTIC,
                    data,
                    reply,
                    0
                )
                if (transacted) {
                    reply.readException()
                    val grabbedNow = reply.readInt() == 1
                    readDiagnosticTail(reply, fallbackGrabbed = grabbedNow)
                }
            } catch (_: Throwable) {
                // ignore
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }

    private fun handleBinderFailure() {
        bindLock.withLock {
            remoteInputBinder = null
            isBindingInProgress = false
            bindCondition.signalAll()
        }
        bindUserServiceIfNeeded()
    }

    suspend fun dispatchClickAt(
        xPx: Float,
        yPx: Float,
        durationMs: Long,
        screenWidth: Int = 0,
        screenHeight: Int = 0,
        rotation: Int = 0
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val binder = getOrWaitForBinder(timeoutMs = 3_500L)
            if (binder == null) {
                updateCompositorDiagnostic(
                    status = LvngShizukuInputService.COMPOSITOR_FAILED,
                    detail = buildNotReadyReason(refreshStatus())
                )
                return@withContext false
            }

            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(LvngShizukuInputService.DESCRIPTOR)
                data.writeFloat(xPx)
                data.writeFloat(yPx)
                data.writeLong(durationMs)
                data.writeInt(screenWidth)
                data.writeInt(screenHeight)
                data.writeInt(rotation)
                val transacted = binder.transact(
                    LvngShizukuInputService.TRANSACTION_TAP,
                    data,
                    reply,
                    0
                )
                if (!transacted) return@withContext false
                reply.readException()
                val ok = reply.readInt() == 1
                if (reply.dataAvail() >= 4) {
                    val grabbedNow = reply.readInt() == 1
                    readDiagnosticTail(reply, fallbackGrabbed = grabbedNow)
                }
                ok
            } catch (t: Throwable) {
                updateCompositorDiagnostic(
                    status = LvngShizukuInputService.COMPOSITOR_FAILED,
                    detail = "Binder dispatchClickAt gagal: ${t.javaClass.simpleName}: ${t.message}"
                )
                handleBinderFailure()
                false
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }

    fun startRepeatTap(
        xPx: Float,
        yPx: Float,
        intervalMs: Long,
        durationMs: Long,
        screenWidth: Int = 0,
        screenHeight: Int = 0,
        rotation: Int = 0
    ): Boolean {
        val seqId = repeatSeqCounter.incrementAndGet()
        if (seqId <= minValidRepeatSeq.get()) return false

        val binder = getOrWaitForBinder(timeoutMs = 3_500L)
        if (binder == null) {
            updateCompositorDiagnostic(
                status = LvngShizukuInputService.COMPOSITOR_FAILED,
                detail = buildNotReadyReason(refreshStatus())
            )
            return false
        }
        if (seqId <= minValidRepeatSeq.get()) return false

        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(LvngShizukuInputService.DESCRIPTOR)
            data.writeFloat(xPx)
            data.writeFloat(yPx)
            data.writeLong(intervalMs)
            data.writeLong(durationMs)
            data.writeInt(screenWidth)
            data.writeInt(screenHeight)
            data.writeInt(rotation)
            data.writeLong(seqId)
            val transacted = binder.transact(
                LvngShizukuInputService.TRANSACTION_START_REPEAT,
                data,
                reply,
                0
            )
            if (!transacted) return false
            reply.readException()
            val started = reply.readInt() == 1
            if (reply.dataAvail() >= 4) {
                val grabbedNow = reply.readInt() == 1
                readDiagnosticTail(reply, fallbackGrabbed = grabbedNow)
            }
            if (started && seqId <= minValidRepeatSeq.get()) {
                dispatchOrderedStop(binder, minValidRepeatSeq.get())
                return false
            }
            started
        } catch (t: Throwable) {
            updateCompositorDiagnostic(
                status = LvngShizukuInputService.COMPOSITOR_FAILED,
                detail = "Binder startRepeatTap gagal: ${t.javaClass.simpleName}: ${t.message}"
            )
            handleBinderFailure()
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun stopRepeatTap() {
        val stopSeq = repeatSeqCounter.incrementAndGet()
        while (true) {
            val prev = minValidRepeatSeq.get()
            if (stopSeq <= prev || minValidRepeatSeq.compareAndSet(prev, stopSeq)) break
        }

        val binder = getAliveBinderImmediate() ?: return

        dispatchOrderedStop(binder, stopSeq)
    }

    private fun dispatchOrderedStop(binder: IBinder, stopSeq: Long) {
        repeatStopExecutor.execute {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(LvngShizukuInputService.DESCRIPTOR)
                data.writeLong(stopSeq)
                val transacted = binder.transact(
                    LvngShizukuInputService.TRANSACTION_STOP_REPEAT,
                    data,
                    reply,
                    0
                )
                if (transacted) {
                    reply.readException()
                }
            } catch (_: Throwable) {
                handleBinderFailure()
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }

    suspend fun isRepeatTapActive(): Boolean {
        return withContext(Dispatchers.IO) {
            val binder = getAliveBinderImmediate() ?: return@withContext false

            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(LvngShizukuInputService.DESCRIPTOR)
                val transacted = binder.transact(
                    LvngShizukuInputService.TRANSACTION_IS_REPEAT_ACTIVE,
                    data,
                    reply,
                    0
                )
                if (!transacted) return@withContext false
                reply.readException()
                val active = reply.readInt() == 1
                if (reply.dataAvail() >= 4) {
                    val grabbedNow = reply.readInt() == 1
                    readDiagnosticTail(reply, fallbackGrabbed = grabbedNow)
                }
                active
            } catch (_: Throwable) {
                false
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }

    suspend fun setSystemSetting(namespace: String, key: String, value: Int): Boolean {
        return withContext(Dispatchers.IO) {
            val binder = getOrWaitForBinder(timeoutMs = 2_000L) ?: return@withContext false
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(LvngShizukuInputService.DESCRIPTOR)
                data.writeString(namespace)
                data.writeString(key)
                data.writeInt(value)
                val transacted = binder.transact(
                    LvngShizukuInputService.TRANSACTION_SET_SETTING,
                    data,
                    reply,
                    0
                )
                if (!transacted) return@withContext false
                reply.readException()
                reply.readInt() == 1
            } catch (_: Throwable) {
                handleBinderFailure()
                false
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }

    suspend fun performBoost(
        activeGamePackage: String?,
        callerPackage: String,
        candidatePackages: List<String>
    ): Int {
        return withContext(Dispatchers.IO) {
            val binder = getOrWaitForBinder(timeoutMs = 2_000L) ?: return@withContext 0
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(LvngShizukuInputService.DESCRIPTOR)
                data.writeString(activeGamePackage.orEmpty())
                data.writeString(callerPackage)
                data.writeStringArray(candidatePackages.toTypedArray())
                val transacted = binder.transact(
                    LvngShizukuInputService.TRANSACTION_BOOST,
                    data,
                    reply,
                    0
                )
                if (!transacted) return@withContext 0
                reply.readException()
                reply.readInt()
            } catch (_: Throwable) {
                handleBinderFailure()
                0
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
    }
}
