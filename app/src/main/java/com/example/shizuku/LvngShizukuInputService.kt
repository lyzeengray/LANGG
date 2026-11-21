package com.example.shizuku

import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.Process as AndroidProcess
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.InputChannel
import android.view.InputDevice
import android.view.InputEvent
import android.view.InputEventReceiver
import android.view.InputMonitor
import android.view.MotionEvent
import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport

/**
 * Isolated helper for `InputMonitor` / `InputEventReceiver` so ART only loads/verifies
 * hidden `android.view.Input*` types AFTER `VMRuntime.setHiddenApiExemptions` has run.
 */
private class LvngInputMonitorHost private constructor(
    private val monitor: InputMonitor,
    private val thread: HandlerThread,
    private val receiver: InputEventReceiver,
    val sourceDescription: String
) {
    fun dispose() {
        val looper = runCatching { thread.looper }.getOrNull()
        if (looper != null && thread.isAlive && Looper.myLooper() != looper) {
            val latch = CountDownLatch(1)
            val posted = runCatching {
                Handler(looper).post {
                    runCatching { receiver.dispose() }
                    runCatching { monitor.dispose() }
                    runCatching { thread.quitSafely() }
                    latch.countDown()
                }
            }.getOrDefault(false)
            if (posted) {
                runCatching { latch.await(300L, TimeUnit.MILLISECONDS) }
                return
            }
        }
        runCatching { receiver.dispose() }
        runCatching { monitor.dispose() }
        runCatching { thread.quitSafely() }
    }

    companion object {
        private const val MONITOR_NAME = "lvng-macro"

        private fun unwrap(t: Throwable): Throwable {
            return if (t is InvocationTargetException) (t.targetException ?: t) else t
        }

        fun open(
            iInputManagerObj: Any?,
            onMotionEvent: (MotionEvent) -> Unit
        ): Pair<LvngInputMonitorHost?, String> {
            val attempts = mutableListOf<String>()
            var monitor: InputMonitor? = null
            var sourceName = ""

            // 1) Android 14–16 (API 34–36): InputManagerGlobal.getInstance().monitorGestureInput(name, displayId)
            if (monitor == null) {
                try {
                    val imgClass = Class.forName("android.hardware.input.InputManagerGlobal")
                    val inst = imgClass.getMethod("getInstance").invoke(null)
                    val m = imgClass.getMethod(
                        "monitorGestureInput",
                        String::class.java,
                        Int::class.javaPrimitiveType
                    ).apply { isAccessible = true }
                    val res = m.invoke(inst, MONITOR_NAME, Display.DEFAULT_DISPLAY) as? InputMonitor
                    if (res != null) {
                        monitor = res
                        sourceName = "InputManagerGlobal"
                    }
                } catch (t: Throwable) {
                    val u = unwrap(t)
                    attempts.add("InputManagerGlobal=${u.javaClass.simpleName}:${u.message}")
                }
            }

            // 2) Android 12–13 (API 31–33): InputManager.getInstance().monitorGestureInput(name, displayId)
            if (monitor == null) {
                try {
                    val imClass = Class.forName("android.hardware.input.InputManager")
                    val inst = imClass.getMethod("getInstance").invoke(null)
                    val m = imClass.getMethod(
                        "monitorGestureInput",
                        String::class.java,
                        Int::class.javaPrimitiveType
                    ).apply { isAccessible = true }
                    val res = m.invoke(inst, MONITOR_NAME, Display.DEFAULT_DISPLAY) as? InputMonitor
                    if (res != null) {
                        monitor = res
                        sourceName = "InputManager"
                    }
                } catch (t: Throwable) {
                    val u = unwrap(t)
                    attempts.add("InputManager=${u.javaClass.simpleName}:${u.message}")
                }
            }

            // 3) Direct Binder: IInputManager.monitorGestureInput (3-arg with token or 2-arg)
            if (monitor == null && iInputManagerObj != null) {
                val resPair = tryBinderMonitorGestureInput(iInputManagerObj, "IInputManager")
                if (resPair.first != null) {
                    monitor = resPair.first
                    sourceName = resPair.second
                } else {
                    attempts.add(resPair.second)
                }
            }

            // 4) Android 11–13: IWindowManager.monitorGestureInput (2-arg on API 30, 3-arg on API 31–33)
            if (monitor == null) {
                try {
                    val smClass = Class.forName("android.os.ServiceManager")
                    val getService = smClass.getMethod("getService", String::class.java)
                    val wmBinder = getService.invoke(null, "window") as? IBinder
                    if (wmBinder != null) {
                        val wmStub = Class.forName("android.view.IWindowManager\$Stub")
                        val wm = wmStub.getMethod("asInterface", IBinder::class.java).invoke(null, wmBinder)
                        if (wm != null) {
                            val resPair = tryBinderMonitorGestureInput(wm, "IWindowManager")
                            if (resPair.first != null) {
                                monitor = resPair.first
                                sourceName = resPair.second
                            } else {
                                attempts.add(resPair.second)
                            }
                        } else {
                            attempts.add("IWindowManager=null")
                        }
                    } else {
                        attempts.add("windowBinder=null")
                    }
                } catch (t: Throwable) {
                    val u = unwrap(t)
                    attempts.add("IWindowManager=${u.javaClass.simpleName}:${u.message}")
                }
            }

            val resolvedMonitor = monitor
                ?: return null to "monitorGestureInput gagal [${attempts.joinToString("; ")}]"

            val channel: InputChannel = try {
                resolvedMonitor.inputChannel
            } catch (t: Throwable) {
                runCatching { resolvedMonitor.dispose() }
                val u = unwrap(t)
                return null to "InputMonitor.getInputChannel gagal: ${u.javaClass.simpleName}: ${u.message}"
            }

            val thread = HandlerThread("LvngInputMonitor", AndroidProcess.THREAD_PRIORITY_URGENT_DISPLAY)
            thread.start()
            val looper: Looper = thread.looper

            val createdReceiver = arrayOfNulls<InputEventReceiver>(1)
            val initError = arrayOfNulls<Throwable>(1)
            val initLatch = CountDownLatch(1)

            Handler(looper).post {
                try {
                    createdReceiver[0] = object : InputEventReceiver(channel, looper) {
                        override fun onInputEvent(event: InputEvent) {
                            try {
                                if (event is MotionEvent) {
                                    onMotionEvent(event)
                                }
                            } finally {
                                finishInputEvent(event, false)
                            }
                        }
                    }
                } catch (t: Throwable) {
                    initError[0] = t
                } finally {
                    initLatch.countDown()
                }
            }

            runCatching { initLatch.await(1_000L, TimeUnit.MILLISECONDS) }
            val receiver = createdReceiver[0]
            if (receiver == null) {
                val err = initError[0]?.let { unwrap(it) }
                runCatching { resolvedMonitor.dispose() }
                runCatching { thread.quitSafely() }
                val reason = if (err != null) {
                    "InputEventReceiver gagal: ${err.javaClass.simpleName}: ${err.message}"
                } else {
                    "InputEventReceiver timeout"
                }
                return null to reason
            }

            return LvngInputMonitorHost(resolvedMonitor, thread, receiver, sourceName) to sourceName
        }

        private fun tryBinderMonitorGestureInput(service: Any, tag: String): Pair<InputMonitor?, String> {
            val methods = service.javaClass.methods.filter { it.name == "monitorGestureInput" }
            if (methods.isEmpty()) return null to "$tag=noMethod"
            val errors = mutableListOf<String>()
            for (m in methods.sortedByDescending { it.parameterTypes.size }) {
                try {
                    m.isAccessible = true
                    val params = m.parameterTypes
                    val res = when {
                        params.size == 3 &&
                            IBinder::class.java.isAssignableFrom(params[0]) &&
                            params[1] == String::class.java &&
                            params[2] == Int::class.javaPrimitiveType -> {
                            m.invoke(service, Binder(), MONITOR_NAME, Display.DEFAULT_DISPLAY)
                        }
                        params.size == 2 &&
                            params[0] == String::class.java &&
                            params[1] == Int::class.javaPrimitiveType -> {
                            m.invoke(service, MONITOR_NAME, Display.DEFAULT_DISPLAY)
                        }
                        else -> continue
                    }
                    if (res is InputMonitor) {
                        return res to "$tag(${params.size}arg)"
                    }
                } catch (t: Throwable) {
                    val u = unwrap(t)
                    errors.add("${m.parameterTypes.size}arg:${u.javaClass.simpleName}:${u.message}")
                }
            }
            return null to "$tag=[${errors.joinToString(",")}]"
        }
    }
}

/**
 * Privileged UserService executed inside Shizuku's process (uid 2000 shell).
 *
 * Cross-Android (Android 11–16 / API 30–36) Multi-Touch Macro Backend:
 * - Unlocks hidden APIs immediately upon service construction.
 * - Uses `IInputManager.injectInputEvent` (with `InputManagerGlobal` / `InputManager` fallback)
 *   paired with `LvngInputMonitorHost` (`InputMonitor` + `InputEventReceiver`) to merge virtual
 *   macro touches (`MACRO_POINTER_ID = 29`) seamlessly onto active physical touches using
 *   `ACTION_POINTER_DOWN` / `ACTION_POINTER_UP` with matching `deviceId`, `source`, `displayId`,
 *   and `downTime`.
 */
@Keep
class LvngShizukuInputService : Binder() {

    private data class ActivePointer(
        val id: Int,
        val x: Float,
        val y: Float,
        val pressure: Float,
        val size: Float,
        val touchMajor: Float,
        val touchMinor: Float,
        val toolType: Int
    )

    private object HiddenApiHelper {
        private val initDone = AtomicBoolean(false)

        @Volatile
        var unlockError: String? = null
            private set

        fun unlock(): String? {
            if (!initDone.compareAndSet(false, true)) return unlockError
            val err = runUnlock()
            unlockError = err
            if (err != null) {
                Log.w(DIAG_TAG, "unlockHiddenApis warning: $err")
            } else {
                Log.i(DIAG_TAG, "unlockHiddenApis OK (API ${Build.VERSION.SDK_INT})")
            }
            return err
        }

        private fun runUnlock(): String? {
            return try {
                val classArrayType = emptyArray<Class<*>>()::class.java
                val stringArrayType = emptyArray<String>()::class.java
                val forName = Class::class.java.getDeclaredMethod("forName", String::class.java)
                val getDeclaredMethod = Class::class.java.getDeclaredMethod(
                    "getDeclaredMethod",
                    String::class.java,
                    classArrayType
                )
                val vmRuntimeClass = forName.invoke(null, "dalvik.system.VMRuntime") as Class<*>
                val getRuntime = getDeclaredMethod.invoke(
                    vmRuntimeClass,
                    "getRuntime",
                    emptyArray<Class<*>>()
                ) as Method
                val setExemptions = getDeclaredMethod.invoke(
                    vmRuntimeClass,
                    "setHiddenApiExemptions",
                    arrayOf<Class<*>>(stringArrayType)
                ) as Method
                getRuntime.isAccessible = true
                setExemptions.isAccessible = true
                val runtime = getRuntime.invoke(null)
                setExemptions.invoke(runtime, arrayOf("L"))
                null
            } catch (t1: Throwable) {
                try {
                    val stringArrayType = emptyArray<String>()::class.java
                    val vmRuntimeClass = Class.forName("dalvik.system.VMRuntime")
                    val getRuntime = vmRuntimeClass.getDeclaredMethod("getRuntime").apply { isAccessible = true }
                    val setExemptions = vmRuntimeClass.getDeclaredMethod(
                        "setHiddenApiExemptions",
                        stringArrayType
                    ).apply { isAccessible = true }
                    setExemptions.invoke(getRuntime.invoke(null), arrayOf("L"))
                    null
                } catch (t2: Throwable) {
                    val u1 = unwrap(t1)
                    val u2 = unwrap(t2)
                    "meta=${u1.javaClass.simpleName}:${u1.message}; direct=${u2.javaClass.simpleName}:${u2.message}"
                }
            }
        }
    }

    private inner class FrameworkTouchInjector {
        private val lifecycleLock = Any()
        private val injectLock = Any()
        private val pointerLock = Any()

        private val ready = AtomicBoolean(false)

        @Volatile
        var status: String = COMPOSITOR_STOPPED
            private set

        @Volatile
        var detail: String = "InputInjector belum dimulai"
            private set

        @Volatile
        private var inputInjectorObj: Any? = null

        @Volatile
        private var iInputManagerObj: Any? = null

        @Volatile
        private var injectInputEventMethod: Method? = null

        @Volatile
        private var setDisplayIdMethod: Method? = null

        @Volatile
        private var getDisplayIdMethod: Method? = null

        @Volatile
        private var monitorHost: LvngInputMonitorHost? = null

        @Volatile
        private var monitorWarning: String? = null

        @Volatile
        private var resolvedTouchDeviceId: Int = 0

        @Volatile
        private var preferZeroDevIdForSolo: Boolean = false

        @Volatile
        private var lastInjectFailureDetail: String? = null

        // Guarded by pointerLock
        private val activePhysicalPointers = LinkedHashMap<Int, ActivePointer>()
        private var activeDownTime: Long = 0L
        private var activeDeviceId: Int = 0
        private var activeSource: Int = InputDevice.SOURCE_TOUCHSCREEN
        private var activeDisplayId: Int = Display.DEFAULT_DISPLAY

        // Guarded by injectLock
        private var isMacroPointerDown: Boolean = false
        private var macroGestureDownTime: Long = 0L
        private var macroWasMixed: Boolean = false
        private var lastMacroX: Float = 0f
        private var lastMacroY: Float = 0f
        private var lastMacroDeviceId: Int = 0
        private var lastMacroSource: Int = InputDevice.SOURCE_TOUCHSCREEN
        private var lastMacroDisplayId: Int = Display.DEFAULT_DISPLAY

        private fun updateState(newStatus: String, newDetail: String, isError: Boolean = false) {
            status = newStatus
            detail = newDetail
            if (isError) {
                Log.e(DIAG_TAG, "Injector status=$newStatus | $newDetail")
            } else {
                Log.i(DIAG_TAG, "Injector status=$newStatus | $newDetail")
            }
        }

        fun isReady(): Boolean = ready.get() && status == COMPOSITOR_GRABBED && inputInjectorObj != null

        fun start(): Boolean {
            synchronized(lifecycleLock) {
                if (isReady()) return true

                updateState(COMPOSITOR_STARTING, "Menyiapkan Android ${Build.VERSION.RELEASE} InputInjector...")
                HiddenApiHelper.unlock()

                if (!ensureBinderMethods()) {
                    ready.set(false)
                    return false
                }

                if (resolvedTouchDeviceId <= 0) {
                    resolvedTouchDeviceId = discoverPhysicalTouchDeviceId()
                }

                if (monitorHost == null) {
                    val (host, monitorDesc) = runCatching {
                        LvngInputMonitorHost.open(iInputManagerObj) { me ->
                            onMonitorMotionEvent(me)
                        }
                    }.getOrElse { t ->
                        val u = unwrap(t)
                        null to "LvngInputMonitorHost exception: ${u.javaClass.simpleName}: ${u.message}"
                    }

                    if (host != null) {
                        monitorHost = host
                        monitorWarning = null
                    } else {
                        monitorWarning = monitorDesc
                        Log.w(DIAG_TAG, "InputMonitor tidak tersedia, lanjut single-pointer fallback: $monitorDesc")
                    }
                }

                lastInjectFailureDetail = null
                ready.set(true)
                val monInfo = monitorHost?.sourceDescription ?: "no-monitor (${monitorWarning ?: "unknown"})"
                updateState(
                    COMPOSITOR_GRABBED,
                    "Aktif (API ${Build.VERSION.SDK_INT}, $monInfo, devId=$resolvedTouchDeviceId)"
                )
                return true
            }
        }

        fun stop() {
            synchronized(lifecycleLock) {
                releaseMacroIfActive()
                ready.set(false)
                val prevHost = monitorHost
                monitorHost = null
                prevHost?.dispose()
                synchronized(pointerLock) {
                    activePhysicalPointers.clear()
                    activeDownTime = 0L
                }
                if (status != COMPOSITOR_FAILED) {
                    updateState(COMPOSITOR_STOPPED, "InputInjector dihentikan")
                }
            }
        }

        fun markFailed(reason: String) {
            ready.set(false)
            updateState(COMPOSITOR_FAILED, reason, isError = true)
        }

        private fun ensureBinderMethods(): Boolean {
            if (inputInjectorObj != null && injectInputEventMethod != null) return true
            val errors = mutableListOf<String>()

            // Resolve displayId helpers
            setDisplayIdMethod = runCatching {
                MotionEvent::class.java.methods.firstOrNull {
                    it.name == "setDisplayId" && it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == Int::class.javaPrimitiveType
                }?.apply { isAccessible = true }
            }.getOrNull()

            getDisplayIdMethod = runCatching {
                InputEvent::class.java.methods.firstOrNull {
                    it.name == "getDisplayId" && it.parameterTypes.isEmpty()
                }?.apply { isAccessible = true }
            }.getOrNull()

            // 1) Try direct IInputManager Binder via ServiceManager
            try {
                val smClass = Class.forName("android.os.ServiceManager")
                val getService = smClass.getMethod("getService", String::class.java)
                val inputBinder = getService.invoke(null, "input") as? IBinder
                if (inputBinder != null) {
                    val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
                    val im = stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, inputBinder)
                    if (im != null) {
                        iInputManagerObj = im
                        val m = runCatching {
                            im.javaClass.getMethod(
                                "injectInputEvent",
                                InputEvent::class.java,
                                Int::class.javaPrimitiveType
                            ).apply { isAccessible = true }
                        }.getOrNull()
                        if (m != null) {
                            inputInjectorObj = im
                            injectInputEventMethod = m
                            return true
                        }
                        errors.add("IInputManager.injectInputEvent tidak ditemukan")
                    } else {
                        errors.add("IInputManager.Stub.asInterface=null")
                    }
                } else {
                    errors.add("ServiceManager.getService(input)=null")
                }
            } catch (t: Throwable) {
                val u = unwrap(t)
                errors.add("IInputManager=${u.javaClass.simpleName}:${u.message}")
            }

            // 2) Try InputManagerGlobal.getInstance() (Android 14–16)
            try {
                val imgClass = Class.forName("android.hardware.input.InputManagerGlobal")
                val inst = imgClass.getMethod("getInstance").invoke(null)
                val m = imgClass.methods.firstOrNull {
                    it.name == "injectInputEvent" &&
                        it.parameterTypes.size == 2 &&
                        InputEvent::class.java.isAssignableFrom(it.parameterTypes[0]) &&
                        it.parameterTypes[1] == Int::class.javaPrimitiveType
                }?.apply { isAccessible = true }
                if (inst != null && m != null) {
                    inputInjectorObj = inst
                    injectInputEventMethod = m
                    return true
                }
            } catch (t: Throwable) {
                val u = unwrap(t)
                errors.add("InputManagerGlobal=${u.javaClass.simpleName}:${u.message}")
            }

            // 3) Try InputManager.getInstance() (Android 11–13)
            try {
                val imClass = Class.forName("android.hardware.input.InputManager")
                val inst = imClass.getMethod("getInstance").invoke(null)
                val m = imClass.methods.firstOrNull {
                    it.name == "injectInputEvent" &&
                        it.parameterTypes.size == 2 &&
                        InputEvent::class.java.isAssignableFrom(it.parameterTypes[0]) &&
                        it.parameterTypes[1] == Int::class.javaPrimitiveType
                }?.apply { isAccessible = true }
                if (inst != null && m != null) {
                    inputInjectorObj = inst
                    injectInputEventMethod = m
                    return true
                }
            } catch (t: Throwable) {
                val u = unwrap(t)
                errors.add("InputManager=${u.javaClass.simpleName}:${u.message}")
            }

            updateState(
                COMPOSITOR_FAILED,
                "IInputManager tidak tersedia [${errors.joinToString("; ")}]",
                isError = true
            )
            return false
        }

        private fun onMonitorMotionEvent(me: MotionEvent) {
            if ((me.source and InputDevice.SOURCE_TOUCHSCREEN) != InputDevice.SOURCE_TOUCHSCREEN) return

            val pointerCount = me.pointerCount
            if (pointerCount <= 0) return

            if (pointerCount == 1 && me.getPointerId(0) == MACRO_POINTER_ID) return
            val actionMasked = me.actionMasked
            if ((actionMasked == MotionEvent.ACTION_POINTER_DOWN ||
                    actionMasked == MotionEvent.ACTION_POINTER_UP) &&
                me.getPointerId(me.actionIndex) == MACRO_POINTER_ID
            ) {
                return
            }

            val observedDisp = runCatching {
                (getDisplayIdMethod?.invoke(me) as? Int) ?: Display.DEFAULT_DISPLAY
            }.getOrDefault(Display.DEFAULT_DISPLAY)

            synchronized(pointerLock) {
                if (me.deviceId > 0) {
                    activeDeviceId = me.deviceId
                    resolvedTouchDeviceId = me.deviceId
                }
                if (me.source != 0) {
                    activeSource = me.source
                }
                if (observedDisp >= 0) {
                    activeDisplayId = observedDisp
                }

                when (actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        activePhysicalPointers.clear()
                        activeDownTime = me.downTime
                        recordPhysicalPointersLocked(me, pointerCount)
                    }

                    MotionEvent.ACTION_POINTER_DOWN -> {
                        if (activePhysicalPointers.isEmpty()) {
                            activeDownTime = me.downTime
                        }
                        recordPhysicalPointersLocked(me, pointerCount)
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (activePhysicalPointers.isEmpty()) {
                            activeDownTime = me.downTime
                        }
                        recordPhysicalPointersLocked(me, pointerCount)
                    }

                    MotionEvent.ACTION_POINTER_UP -> {
                        recordPhysicalPointersLocked(me, pointerCount)
                        val liftedId = me.getPointerId(me.actionIndex)
                        if (liftedId != MACRO_POINTER_ID) {
                            activePhysicalPointers.remove(liftedId)
                        }
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        activePhysicalPointers.clear()
                        activeDownTime = 0L
                    }
                }
            }
        }

        private fun recordPhysicalPointersLocked(me: MotionEvent, pointerCount: Int) {
            for (i in 0 until pointerCount) {
                val pid = me.getPointerId(i)
                if (pid < 0 || pid == MACRO_POINTER_ID) continue
                activePhysicalPointers[pid] = ActivePointer(
                    id = pid,
                    x = me.getX(i),
                    y = me.getY(i),
                    pressure = me.getPressure(i).coerceIn(0.05f, 1.0f),
                    size = me.getSize(i).coerceAtLeast(0.04f),
                    touchMajor = me.getTouchMajor(i).let { if (it > 0f) it else 12f },
                    touchMinor = me.getTouchMinor(i).let { if (it > 0f) it else 12f },
                    toolType = me.getToolType(i).let {
                        if (it == MotionEvent.TOOL_TYPE_UNKNOWN) MotionEvent.TOOL_TYPE_FINGER else it
                    }
                )
            }
        }

        private fun discoverPhysicalTouchDeviceId(): Int {
            return runCatching {
                val ids = InputDevice.getDeviceIds()
                var bestId = 0
                var bestArea = -1f
                for (id in ids) {
                    val dev = InputDevice.getDevice(id) ?: continue
                    if (!dev.isEnabled || dev.isVirtual) continue
                    val sources = dev.sources
                    if ((sources and InputDevice.SOURCE_TOUCHSCREEN) != InputDevice.SOURCE_TOUCHSCREEN) continue
                    if (dev.name.equals(LEGACY_VIRTUAL_NAME, ignoreCase = true)) continue
                    val rx = dev.getMotionRange(MotionEvent.AXIS_X)
                    val ry = dev.getMotionRange(MotionEvent.AXIS_Y)
                    val area = if (rx != null && ry != null) rx.max * ry.max else 0f
                    if (bestId == 0 || area > bestArea) {
                        bestId = id
                        bestArea = area
                    }
                }
                bestId
            }.getOrDefault(0)
        }

        fun tapOnce(
            xPx: Float,
            yPx: Float,
            holdMs: Long,
            genCheck: Long?
        ): Boolean {
            if (!isReady() && !start()) return false

            var usedDownTime = 0L
            var wasMixedAtDown = false
            var devId = 0
            var src = InputDevice.SOURCE_TOUCHSCREEN
            var dispId = Display.DEFAULT_DISPLAY
            var downSent = false

            try {
                synchronized(injectLock) {
                    val macroPtr = macroPointer(xPx, yPx)

                    // Phase 1: Try multi-touch merge (ACTION_POINTER_DOWN) when physical touches are active.
                    var snapPhys: List<ActivePointer> = emptyList()
                    var snapDownTime = 0L
                    var snapDevId = 0
                    var snapSrc = InputDevice.SOURCE_TOUCHSCREEN
                    var snapDispId = Display.DEFAULT_DISPLAY

                    for (attempt in 0 until 2) {
                        synchronized(pointerLock) {
                            snapPhys = activePhysicalPointers.values.toList()
                            snapDownTime = activeDownTime
                            snapDevId = if (activeDeviceId > 0) activeDeviceId else resolvedTouchDeviceId
                            snapSrc = activeSource
                            snapDispId = activeDisplayId
                        }

                        if (snapPhys.isEmpty() || snapDownTime <= 0L) break

                        val now = SystemClock.uptimeMillis()
                        usedDownTime = snapDownTime
                        wasMixedAtDown = true
                        devId = snapDevId
                        src = snapSrc
                        dispId = snapDispId
                        val combined = ArrayList<ActivePointer>(snapPhys.size + 1).apply {
                            addAll(snapPhys)
                            add(macroPtr)
                        }
                        val macroIdx = combined.size - 1
                        val action = MotionEvent.ACTION_POINTER_DOWN or
                            (macroIdx shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                        val ok = injectSyntheticEvent(
                            downTime = usedDownTime,
                            eventTime = now.coerceAtLeast(usedDownTime),
                            action = action,
                            pointers = combined,
                            deviceId = devId,
                            source = src,
                            displayId = dispId,
                            mode = INJECT_MODE_WAIT_FOR_RESULT
                        )
                        if (ok) {
                            downSent = true
                            break
                        }
                        if (!isReady()) break
                        if (attempt == 0) Thread.yield()
                    }

                    // Phase 2: Fresh single-pointer ACTION_DOWN (also acts as clean fallback if InputMonitor state was stale or OEM disallowed pointer-merge).
                    if (!downSent && isReady()) {
                        val preferredDevId = if (preferZeroDevIdForSolo) 0 else snapDevId
                        val candidateDevIds = if (preferredDevId != 0) intArrayOf(preferredDevId, 0) else intArrayOf(0)
                        for (candDevId in candidateDevIds) {
                            if (!isReady()) break
                            val now = SystemClock.uptimeMillis()
                            usedDownTime = now
                            wasMixedAtDown = false
                            devId = candDevId
                            src = snapSrc
                            dispId = snapDispId
                            val ok = injectSyntheticEvent(
                                downTime = usedDownTime,
                                eventTime = now,
                                action = MotionEvent.ACTION_DOWN,
                                pointers = listOf(macroPtr),
                                deviceId = devId,
                                source = src,
                                displayId = dispId,
                                mode = INJECT_MODE_WAIT_FOR_RESULT
                            )
                            if (ok) {
                                downSent = true
                                if (candDevId == 0 && preferredDevId != 0) {
                                    preferZeroDevIdForSolo = true
                                }
                                if (snapPhys.isNotEmpty()) {
                                    synchronized(pointerLock) {
                                        activePhysicalPointers.clear()
                                        activeDownTime = 0L
                                    }
                                }
                                break
                            }
                        }
                    }

                    if (!downSent) {
                        if (status != COMPOSITOR_FAILED) {
                            val reason = lastInjectFailureDetail
                                ?: "IInputManager.injectInputEvent ditolak sistem (aktifkan Debugging USB - Setelan keamanan di Opsi Developer)"
                            markFailed(reason)
                        }
                        return false
                    }

                    isMacroPointerDown = true
                    macroGestureDownTime = usedDownTime
                    macroWasMixed = wasMixedAtDown
                    lastMacroX = xPx
                    lastMacroY = yPx
                    lastMacroDeviceId = devId
                    lastMacroSource = src
                    lastMacroDisplayId = dispId
                }

                if (holdMs > 0L) {
                    sleepPreciseMs(holdMs, genCheck)
                }
            } finally {
                if (downSent) {
                    synchronized(injectLock) {
                        if (isMacroPointerDown) {
                            liftMacroPointerLocked(
                                usedDownTime = usedDownTime,
                                wasMixedAtDown = wasMixedAtDown,
                                xPx = xPx,
                                yPx = yPx,
                                devId = devId,
                                src = src,
                                dispId = dispId
                            )
                        }
                    }
                }
            }
            return true
        }

        fun releaseMacroIfActive() {
            synchronized(injectLock) {
                if (!isMacroPointerDown) return
                liftMacroPointerLocked(
                    usedDownTime = macroGestureDownTime,
                    wasMixedAtDown = macroWasMixed,
                    xPx = lastMacroX,
                    yPx = lastMacroY,
                    devId = lastMacroDeviceId,
                    src = lastMacroSource,
                    dispId = lastMacroDisplayId
                )
            }
        }

        private fun liftMacroPointerLocked(
            usedDownTime: Long,
            wasMixedAtDown: Boolean,
            xPx: Float,
            yPx: Float,
            devId: Int,
            src: Int,
            dispId: Int
        ) {
            isMacroPointerDown = false
            var currentPhys: List<ActivePointer>
            var currentDownTime: Long
            synchronized(pointerLock) {
                currentPhys = activePhysicalPointers.values.toList()
                currentDownTime = activeDownTime
            }
            val macroPtr = macroPointer(xPx, yPx)
            val nowUp = SystemClock.uptimeMillis().coerceAtLeast(usedDownTime)

            when {
                currentPhys.isNotEmpty() && currentDownTime == usedDownTime -> {
                    val combined = ArrayList<ActivePointer>(currentPhys.size + 1).apply {
                        addAll(currentPhys)
                        add(macroPtr)
                    }
                    val macroIdx = combined.size - 1
                    val action = MotionEvent.ACTION_POINTER_UP or
                        (macroIdx shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                    injectSyntheticEvent(
                        downTime = usedDownTime,
                        eventTime = nowUp,
                        action = action,
                        pointers = combined,
                        deviceId = devId,
                        source = src,
                        displayId = dispId,
                        mode = INJECT_MODE_ASYNC
                    )
                }

                currentPhys.isNotEmpty() && currentDownTime != usedDownTime -> {
                    // Physical gesture restarted under new downTime; InputDispatcher already ended old gesture.
                }

                else -> {
                    if (!wasMixedAtDown || currentDownTime == 0L) {
                        injectSyntheticEvent(
                            downTime = usedDownTime,
                            eventTime = nowUp,
                            action = MotionEvent.ACTION_UP,
                            pointers = listOf(macroPtr),
                            deviceId = devId,
                            source = src,
                            displayId = dispId,
                            mode = INJECT_MODE_ASYNC
                        )
                    }
                }
            }
        }

        private fun macroPointer(xPx: Float, yPx: Float): ActivePointer {
            return ActivePointer(
                id = MACRO_POINTER_ID,
                x = xPx.coerceAtLeast(0f),
                y = yPx.coerceAtLeast(0f),
                pressure = 1.0f,
                size = 0.08f,
                touchMajor = 12f,
                touchMinor = 12f,
                toolType = MotionEvent.TOOL_TYPE_FINGER
            )
        }

        private fun injectSyntheticEvent(
            downTime: Long,
            eventTime: Long,
            action: Int,
            pointers: List<ActivePointer>,
            deviceId: Int,
            source: Int,
            displayId: Int,
            mode: Int
        ): Boolean {
            val target = inputInjectorObj ?: return false
            val method = injectInputEventMethod ?: return false
            if (pointers.isEmpty()) return false

            val count = pointers.size
            val props = Array(count) { i ->
                val p = pointers[i]
                MotionEvent.PointerProperties().apply {
                    id = p.id
                    toolType = p.toolType
                }
            }
            val coords = Array(count) { i ->
                val p = pointers[i]
                MotionEvent.PointerCoords().apply {
                    x = p.x
                    y = p.y
                    pressure = p.pressure
                    size = p.size
                    touchMajor = p.touchMajor
                    touchMinor = p.touchMinor
                }
            }

            val ev = MotionEvent.obtain(
                downTime,
                eventTime,
                action,
                count,
                props,
                coords,
                0,
                0,
                1.0f,
                1.0f,
                deviceId,
                0,
                source,
                0
            ) ?: return false

            return try {
                if (displayId >= 0) {
                    setDisplayIdMethod?.invoke(ev, displayId)
                }
                val res = (method.invoke(target, ev, mode) as? Boolean) ?: false
                if (!res && mode == INJECT_MODE_WAIT_FOR_RESULT) {
                    lastInjectFailureDetail =
                        "injectInputEvent=false (action=0x${action.toString(16)}, count=$count, devId=$deviceId, disp=$displayId)"
                    Log.w(DIAG_TAG, lastInjectFailureDetail!!)
                }
                res
            } catch (t: Throwable) {
                val u = unwrap(t)
                val msg = "injectInputEvent ${u.javaClass.simpleName}: ${u.message}"
                lastInjectFailureDetail = msg
                Log.e(DIAG_TAG, msg, u)
                if (u is SecurityException) {
                    markFailed(
                        "Izin INJECT_EVENTS ditolak sistem (di MIUI/HyperOS aktifkan 'Debugging USB - Setelan keamanan'): ${u.message}"
                    )
                }
                false
            } finally {
                ev.recycle()
            }
        }
    }

    init {
        HiddenApiHelper.unlock()
    }

    private val shellLock = Any()
    private val repeatThreadLock = Any()

    private var shellProcess: Process? = null
    private var shellWriter: BufferedWriter? = null
    private var shellReader: BufferedReader? = null

    private val touchInjector by lazy { FrameworkTouchInjector() }

    @Volatile
    private var warmupStarted = false

    private val repeatGeneration = AtomicLong(0L)
    private val highestStoppedSeq = AtomicLong(0L)
    private var repeatThread: Thread? = null

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        return when (code) {
            TRANSACTION_TAP -> {
                data.enforceInterface(DESCRIPTOR)
                val x = data.readFloat()
                val y = data.readFloat()
                val durationMs = data.readLong()
                val screenW = if (data.dataAvail() >= 12) data.readInt() else 0
                val screenH = if (data.dataAvail() >= 8) data.readInt() else 0
                val rotation = if (data.dataAvail() >= 4) data.readInt() else 0
                val ok = performTap(x, y, durationMs, screenW, screenH, rotation)
                reply?.writeNoException()
                reply?.writeInt(if (ok) 1 else 0)
                reply?.writeInt(if (touchInjector.isReady()) 1 else 0)
                reply?.writeString(touchInjector.status)
                reply?.writeString(touchInjector.detail)
                true
            }

            TRANSACTION_PING -> {
                data.enforceInterface(DESCRIPTOR)
                triggerAsyncWarmup()
                reply?.writeNoException()
                reply?.writeInt(if (touchInjector.isReady()) 2 else 1)
                reply?.writeString(touchInjector.status)
                reply?.writeString(touchInjector.detail)
                true
            }

            TRANSACTION_DESTROY -> {
                data.enforceInterface(DESCRIPTOR)
                stopRepeatTap()
                touchInjector.stop()
                cleanupShell()
                reply?.writeNoException()
                true
            }

            TRANSACTION_START_REPEAT -> {
                data.enforceInterface(DESCRIPTOR)
                val x = data.readFloat()
                val y = data.readFloat()
                val intervalMs = data.readLong()
                val durationMs = data.readLong()
                val screenW = if (data.dataAvail() >= 12) data.readInt() else 0
                val screenH = if (data.dataAvail() >= 8) data.readInt() else 0
                val rotation = if (data.dataAvail() >= 4) data.readInt() else 0
                val seqId = if (data.dataAvail() >= 8) data.readLong() else 0L
                val ok = startRepeatTap(x, y, intervalMs, durationMs, screenW, screenH, rotation, seqId)
                reply?.writeNoException()
                reply?.writeInt(if (ok) 1 else 0)
                reply?.writeInt(if (touchInjector.isReady()) 1 else 0)
                reply?.writeString(touchInjector.status)
                reply?.writeString(touchInjector.detail)
                true
            }

            TRANSACTION_STOP_REPEAT -> {
                data.enforceInterface(DESCRIPTOR)
                val seqId = if (data.dataAvail() >= 8) data.readLong() else 0L
                stopRepeatTap(seqId)
                reply?.writeNoException()
                true
            }

            TRANSACTION_SET_SETTING -> {
                data.enforceInterface(DESCRIPTOR)
                val namespace = data.readString().orEmpty()
                val key = data.readString().orEmpty()
                val value = data.readInt()
                val ok = setSystemSetting(namespace, key, value)
                reply?.writeNoException()
                reply?.writeInt(if (ok) 1 else 0)
                true
            }

            TRANSACTION_BOOST -> {
                data.enforceInterface(DESCRIPTOR)
                val activeGamePkg = data.readString().orEmpty()
                val callerPkg = data.readString().orEmpty()
                val candidatePkgs = data.createStringArray() ?: emptyArray()
                val boostedCount = performRealDeviceBoost(activeGamePkg, callerPkg, candidatePkgs)
                reply?.writeNoException()
                reply?.writeInt(boostedCount)
                true
            }

            TRANSACTION_IS_REPEAT_ACTIVE -> {
                data.enforceInterface(DESCRIPTOR)
                val isRunning = synchronized(repeatThreadLock) {
                    repeatThread?.isAlive == true && touchInjector.isReady()
                }
                reply?.writeNoException()
                reply?.writeInt(if (isRunning) 1 else 0)
                reply?.writeInt(if (touchInjector.isReady()) 1 else 0)
                reply?.writeString(touchInjector.status)
                reply?.writeString(touchInjector.detail)
                true
            }

            TRANSACTION_SET_COMPOSITOR_ACTIVE -> {
                data.enforceInterface(DESCRIPTOR)
                val enabled = data.readInt() == 1
                if (data.dataAvail() >= 4) data.readInt()
                val activeNow = setInjectorActive(enabled)
                reply?.writeNoException()
                reply?.writeInt(if (activeNow) 1 else 0)
                reply?.writeString(touchInjector.status)
                reply?.writeString(touchInjector.detail)
                true
            }

            TRANSACTION_IS_COMPOSITOR_ACTIVE -> {
                data.enforceInterface(DESCRIPTOR)
                reply?.writeNoException()
                reply?.writeInt(if (touchInjector.isReady()) 1 else 0)
                reply?.writeString(touchInjector.status)
                reply?.writeString(touchInjector.detail)
                true
            }

            TRANSACTION_GET_COMPOSITOR_DIAGNOSTIC -> {
                data.enforceInterface(DESCRIPTOR)
                reply?.writeNoException()
                reply?.writeInt(if (touchInjector.isReady()) 1 else 0)
                reply?.writeString(touchInjector.status)
                reply?.writeString(touchInjector.detail)
                true
            }

            else -> super.onTransact(code, data, reply, flags)
        }
    }

    private fun triggerAsyncWarmup() {
        if (warmupStarted) return
        warmupStarted = true
        Thread({
            HiddenApiHelper.unlock()
            touchInjector.start()
        }, "LvngInputWarmup").apply {
            isDaemon = true
            start()
        }
    }

    private fun setInjectorActive(enabled: Boolean): Boolean {
        if (!enabled) {
            stopRepeatTap()
            touchInjector.stop()
            return false
        }
        return touchInjector.start()
    }

    private fun sleepPreciseMs(ms: Long, genCheck: Long? = null) {
        if (ms <= 0L) return
        val deadlineNs = System.nanoTime() + ms * 1_000_000L
        while (true) {
            if (genCheck != null && repeatGeneration.get() != genCheck) return
            val remainingNs = deadlineNs - System.nanoTime()
            if (remainingNs <= 0L) return
            LockSupport.parkNanos(remainingNs.coerceAtMost(5_000_000L))
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun performTap(
        x: Float,
        y: Float,
        durationMs: Long,
        screenW: Int,
        screenH: Int,
        rotation: Int
    ): Boolean {
        if (screenW <= 0 || screenH <= 0) return false
        val safeX = x.coerceIn(1f, screenW.toFloat())
        val safeY = y.coerceIn(1f, screenH.toFloat())
        val holdMs = durationMs.coerceIn(24L, 1000L).coerceAtMost(400L)

        if (!touchInjector.isReady() && !touchInjector.start()) {
            return false
        }
        return touchInjector.tapOnce(safeX, safeY, holdMs, null)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun startRepeatTap(
        x: Float,
        y: Float,
        intervalMs: Long,
        durationMs: Long,
        screenW: Int,
        screenH: Int,
        rotation: Int,
        seqId: Long = 0L
    ): Boolean {
        if (screenW <= 0 || screenH <= 0) return false
        val safeX = x.coerceIn(1f, screenW.toFloat())
        val safeY = y.coerceIn(1f, screenH.toFloat())
        val clampedInterval = intervalMs.coerceIn(10L, 1000L)
        val effectiveHoldMs = if (clampedInterval <= 120L) {
            durationMs.coerceIn(24L, 45L)
        } else {
            durationMs.coerceIn(24L, 120L)
        }
        val effectiveGapMs = clampedInterval.coerceAtLeast(28L)

        if (!touchInjector.isReady() && !touchInjector.start()) {
            return false
        }

        synchronized(repeatThreadLock) {
            if (seqId > 0L && seqId <= highestStoppedSeq.get()) {
                return false
            }

            val myGen = repeatGeneration.incrementAndGet()
            repeatThread?.let { prev ->
                LockSupport.unpark(prev)
                prev.interrupt()
                runCatching { prev.join(180L) }
            }

            val worker = Thread({
                try {
                    runCatching {
                        AndroidProcess.setThreadPriority(AndroidProcess.THREAD_PRIORITY_URGENT_DISPLAY)
                    }

                    while (repeatGeneration.get() == myGen && !Thread.currentThread().isInterrupted) {
                        if (!touchInjector.isReady()) {
                            Log.e(
                                DIAG_TAG,
                                "LvngMacroLoop dihentikan (${touchInjector.status}: ${touchInjector.detail})"
                            )
                            break
                        }
                        val tapped = touchInjector.tapOnce(safeX, safeY, effectiveHoldMs, myGen)
                        if (!tapped && !touchInjector.isReady()) {
                            break
                        }

                        if (repeatGeneration.get() != myGen) break
                        sleepPreciseMs(effectiveGapMs, myGen)
                    }
                } finally {
                    touchInjector.releaseMacroIfActive()
                    synchronized(repeatThreadLock) {
                        if (repeatThread === Thread.currentThread()) {
                            repeatThread = null
                        }
                    }
                }
            }, "LvngMacroLoop").apply {
                priority = Thread.MAX_PRIORITY
                isDaemon = true
            }
            repeatThread = worker
            worker.start()
        }
        return true
    }

    private fun stopRepeatTap(seqId: Long = 0L) {
        synchronized(repeatThreadLock) {
            if (seqId > 0L) {
                while (true) {
                    val prev = highestStoppedSeq.get()
                    if (seqId <= prev || highestStoppedSeq.compareAndSet(prev, seqId)) break
                }
            }
            repeatGeneration.incrementAndGet()
            repeatThread?.let { thread ->
                LockSupport.unpark(thread)
                thread.interrupt()
                runCatching { thread.join(180L) }
            }
            repeatThread = null
            touchInjector.releaseMacroIfActive()
        }
    }

    private fun setSystemSetting(namespace: String, key: String, value: Int): Boolean {
        val safeNs = when (namespace) {
            "system", "secure", "global" -> namespace
            else -> return false
        }
        val allowedKeys = setOf("show_touches", "pointer_location", "accelerometer_rotation")
        if (key !in allowedKeys) return false
        val safeVal = if (value != 0) 1 else 0
        val out = runShellBatch(listOf("settings put $safeNs $key $safeVal"))
        return out != null
    }

    private fun performRealDeviceBoost(
        activeGamePackage: String,
        callerPackage: String,
        candidatePackages: Array<String>
    ): Int {
        val protectedExact = HashSet<String>().apply {
            add("android")
            add("com.android.systemui")
            add("com.android.phone")
            add("com.android.bluetooth")
            add("com.android.settings")
            add("com.google.android.gms")
            add("com.google.android.gsf")
            add("moe.shizuku.privileged.api")
            if (callerPackage.isNotBlank()) add(callerPackage)
            if (activeGamePackage.isNotBlank()) add(activeGamePackage)
        }

        val envInfo = runShellBatch(
            listOf(
                "cmd package resolve-activity --brief -c android.intent.category.HOME 2>/dev/null | tail -n 1",
                "settings get secure default_input_method 2>/dev/null"
            )
        ).orEmpty()

        envInfo.forEach { line ->
            val pkg = line.trim().substringBefore('/').trim()
            if (pkg.contains('.') && !pkg.contains(' ')) {
                protectedExact.add(pkg)
            }
        }

        val sanitizedTargets = candidatePackages
            .map { it.trim() }
            .filter { pkg ->
                pkg.isNotBlank() &&
                    pkg !in protectedExact &&
                    pkg.matches(Regex("[a-zA-Z0-9_\\.]+"))
            }
            .distinct()
            .take(60)

        val commands = mutableListOf<String>()
        commands.add("am kill-all 2>/dev/null")
        sanitizedTargets.forEach { pkg ->
            commands.add("am send-trim-memory $pkg COMPLETE 2>/dev/null; am kill $pkg 2>/dev/null")
        }
        commands.add("pm trim-caches 1024M 2>/dev/null")
        commands.add("cmd power set-fixed-performance-mode-enabled true 2>/dev/null")
        commands.add("sync; echo 3 > /proc/sys/vm/drop_caches 2>/dev/null")

        runShellBatch(commands)
        return sanitizedTargets.size
    }

    private fun ensureShellLocked(): Boolean {
        val proc = shellProcess
        if (proc != null && proc.isAlive && shellWriter != null && shellReader != null) {
            return true
        }
        cleanupShellLocked()
        return runCatching {
            val started = ProcessBuilder("sh").redirectErrorStream(true).start()
            shellProcess = started
            shellWriter = BufferedWriter(OutputStreamWriter(started.outputStream))
            shellReader = BufferedReader(InputStreamReader(started.inputStream))
            true
        }.getOrElse {
            cleanupShellLocked()
            false
        }
    }

    private fun runShellBatch(commands: List<String>): List<String>? {
        val marker = "__LVNG_DONE_${System.nanoTime()}__"
        synchronized(shellLock) {
            if (!ensureShellLocked()) return null
            return runCatching {
                val writer = shellWriter!!
                val reader = shellReader!!
                commands.forEach { cmd ->
                    writer.write(cmd)
                    writer.newLine()
                }
                writer.write("echo $marker")
                writer.newLine()
                writer.flush()

                val lines = mutableListOf<String>()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line == marker) break
                    lines.add(line)
                }
                lines
            }.getOrElse {
                cleanupShellLocked()
                null
            }
        }
    }

    private fun cleanupShell() {
        synchronized(shellLock) {
            cleanupShellLocked()
        }
    }

    private fun cleanupShellLocked() {
        runCatching { shellWriter?.close() }
        runCatching { shellReader?.close() }
        runCatching { shellProcess?.destroy() }
        shellWriter = null
        shellReader = null
        shellProcess = null
    }

    companion object {
        const val DESCRIPTOR = "com.example.shizuku.ILvngInputService"
        const val TRANSACTION_TAP = IBinder.FIRST_CALL_TRANSACTION + 1
        const val TRANSACTION_PING = IBinder.FIRST_CALL_TRANSACTION + 2
        const val TRANSACTION_DESTROY = IBinder.FIRST_CALL_TRANSACTION + 3
        const val TRANSACTION_START_REPEAT = IBinder.FIRST_CALL_TRANSACTION + 4
        const val TRANSACTION_STOP_REPEAT = IBinder.FIRST_CALL_TRANSACTION + 5
        const val TRANSACTION_SET_SETTING = IBinder.FIRST_CALL_TRANSACTION + 6
        const val TRANSACTION_BOOST = IBinder.FIRST_CALL_TRANSACTION + 7
        const val TRANSACTION_IS_REPEAT_ACTIVE = IBinder.FIRST_CALL_TRANSACTION + 8
        const val TRANSACTION_SET_COMPOSITOR_ACTIVE = IBinder.FIRST_CALL_TRANSACTION + 9
        const val TRANSACTION_IS_COMPOSITOR_ACTIVE = IBinder.FIRST_CALL_TRANSACTION + 10
        const val TRANSACTION_GET_COMPOSITOR_DIAGNOSTIC = IBinder.FIRST_CALL_TRANSACTION + 11

        private const val DIAG_TAG = "LvngTouchInjector"
        const val COMPOSITOR_STOPPED = "STOPPED"
        const val COMPOSITOR_STARTING = "STARTING"
        const val COMPOSITOR_GRABBED = "GRABBED"
        const val COMPOSITOR_FAILED = "FAILED"

        const val MACRO_POINTER_ID = 15
        private const val LEGACY_VIRTUAL_NAME = "LVNG Virtual Touch"

        private const val INJECT_MODE_ASYNC = 0
        private const val INJECT_MODE_WAIT_FOR_RESULT = 1

        private fun unwrap(t: Throwable): Throwable {
            return if (t is InvocationTargetException) (t.targetException ?: t) else t
        }
    }
}
