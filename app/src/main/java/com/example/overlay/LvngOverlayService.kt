package com.example.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.data.LvngPreferencesRepository
import com.example.data.SystemTelemetryMonitor
import com.example.model.CrosshairStyle
import com.example.model.MacroConfig
import com.example.model.MacroTapMode
import com.example.model.MacroTriggerNode
import com.example.model.ResolutionScale
import com.example.ui.components.LvngBoostPerformanceBanner
import com.example.ui.components.LvngCrosshairReticle
import com.example.ui.components.LvngEdgeDockTab
import com.example.ui.components.LvngFloatingLogoBubble
import com.example.ui.components.LvngGameControlPanel
import com.example.ui.components.LvngMonitoringPill
import com.example.ui.components.LvngTouchTelemetryHudOverlay
import com.example.ui.components.MacroKeymapEditorOverlay
import com.example.ui.components.MacroRuntimeTargetMarker
import com.example.ui.components.MacroRuntimeTriggerCircle
import com.example.ui.components.observePassiveTouches
import com.example.ui.theme.LvngSpaceTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class LvngOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        private const val CHANNEL_ID = "lvng_overlay_service_channel"
        private const val NOTIF_ID = 4299
        const val EXTRA_GAME_NAME = "extra_game_name"
        const val EXTRA_GAME_PKG = "extra_game_pkg"
        const val EXTRA_PLAY_POPOUT = "extra_play_popout"
        const val EXTRA_POPOUT_DELAY_MS = "extra_popout_delay_ms"

        fun start(
            context: Context,
            gameName: String? = null,
            packageName: String? = null,
            playPopOut: Boolean = false,
            popOutDelayMs: Long = 0L
        ) {
            val intent = Intent(context, LvngOverlayService::class.java).apply {
                putExtra(EXTRA_GAME_NAME, gameName)
                putExtra(EXTRA_GAME_PKG, packageName)
                putExtra(EXTRA_PLAY_POPOUT, playPopOut)
                putExtra(EXTRA_POPOUT_DELAY_MS, popOutDelayMs)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LvngOverlayService::class.java))
        }
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var telemetryMonitor: SystemTelemetryMonitor
    private lateinit var prefsRepo: LvngPreferencesRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var telemetryJob: Job? = null
    private var macroLoopJob: Job? = null
    private var popOutDelayJob: Job? = null

    private var bubbleView: ComposeView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var isDockedToEdge by mutableStateOf(true)
    private var isDockedRightEdge by mutableStateOf(true)

    private var panelView: ComposeView? = null
    private var isPanelVisibleAnimated by mutableStateOf(false)
    private var isClosingPanel = false
    private var macroEditorView: ComposeView? = null
    private var auxOverlayView: ComposeView? = null
    private var monitoringView: ComposeView? = null
    private var monitoringParams: WindowManager.LayoutParams? = null
    private var monitoringPosXFraction = 0.04f
    private var monitoringPosYFraction = 0.06f
    private var popOutView: ComposeView? = null
    private val runtimeTriggerViews = mutableListOf<View>()
    private var lastToastDiagKey: String? = null
    private var lastToastShownAtMs: Long = 0L

    private fun showCompositorStatusToast(status: String, detail: String, force: Boolean = false) {
        val now = android.os.SystemClock.elapsedRealtime()
        val key = "$status|$detail"
        if (!force && key == lastToastDiagKey && (now - lastToastShownAtMs) < 2_500L) return
        if (force && (now - lastToastShownAtMs) < 1_200L && key == lastToastDiagKey) return
        lastToastDiagKey = key
        lastToastShownAtMs = now
        val text = when (status) {
            com.example.shizuku.LvngShizukuInputService.COMPOSITOR_GRABBED ->
                "InputInjector AKTIF ($detail)"
            com.example.shizuku.LvngShizukuInputService.COMPOSITOR_FAILED ->
                "InputInjector GAGAL: $detail"
            com.example.shizuku.LvngShizukuInputService.COMPOSITOR_STARTING ->
                "InputInjector: STARTING ($detail)"
            else ->
                "InputInjector: STOPPED ($detail)"
        }
        Toast.makeText(this@LvngOverlayService, text, Toast.LENGTH_LONG).show()
    }

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        telemetryMonitor = SystemTelemetryMonitor(applicationContext)
        prefsRepo = LvngPreferencesRepository(applicationContext)
        com.example.shizuku.LvngShizukuManager.init(applicationContext)

        startForegroundNotification()
        LvngRuntimeBus.update { it.copy(isServiceActive = true) }

        if (Settings.canDrawOverlays(this)) {
            attachBubbleWindow()
            bubbleView?.visibility = View.GONE
            attachAuxOverlayWindow()
            syncRuntimeMacroTriggers(LvngRuntimeBus.state.value.macroConfig)
        }
        serviceScope.launch {
            LvngRuntimeBus.state
                .map { it.isMonitoringInfoVisible }
                .distinctUntilChanged()
                .collect { visible ->
                    if (visible && Settings.canDrawOverlays(this@LvngOverlayService)) {
                        attachMonitoringWindow()
                    } else {
                        detachMonitoringWindow()
                    }
                }
        }
        serviceScope.launch {
            val savedPrefs = runCatching { prefsRepo.preferencesFlow.first() }.getOrNull()
            if (savedPrefs != null) {
                val savedMacro = savedPrefs.macroConfig
                LvngRuntimeBus.update { current ->
                    val shouldLoadMacro = current.macroConfig.triggers.isEmpty() &&
                        current.macroConfig.targets.isEmpty() &&
                        (savedMacro.triggers.isNotEmpty() || savedMacro.targets.isNotEmpty())
                    current.copy(
                        showCoordinates = savedPrefs.showTouchCoordinates,
                        showTouchDots = savedPrefs.showTouchDots,
                        macroConfig = if (shouldLoadMacro) savedMacro else current.macroConfig
                    )
                }
                if (Settings.canDrawOverlays(this@LvngOverlayService)) {
                    syncRuntimeMacroTriggers(LvngRuntimeBus.state.value.macroConfig)
                }
            }
        }
        serviceScope.launch {
            com.example.shizuku.LvngShizukuManager.status.collect { shizuku ->
                if (shizuku.isUserServiceBound) {
                    val current = LvngRuntimeBus.state.value
                    com.example.shizuku.LvngShizukuManager.setSystemSetting(
                        "system",
                        "show_touches",
                        if (current.showTouchDots) 1 else 0
                    )
                    com.example.shizuku.LvngShizukuManager.setSystemSetting(
                        "system",
                        "pointer_location",
                        if (current.showCoordinates) 1 else 0
                    )
                    val macroReady = current.macroConfig.isMacroActive &&
                        current.macroConfig.triggers.isNotEmpty() &&
                        current.macroConfig.targets.isNotEmpty()
                    com.example.shizuku.LvngShizukuManager.setCompositorActiveAsync(
                        macroReady,
                        getDisplayRotation()
                    )
                }
            }
        }
        serviceScope.launch {
            var prevStatus: String? = null
            com.example.shizuku.LvngShizukuManager.compositorDiagnostics.collect { diag ->
                android.util.Log.i("LvngTouchInjector", "[Overlay] status=${diag.status} | ${diag.detail}")
                val macroEnabled = LvngRuntimeBus.state.value.macroConfig.let {
                    it.isMacroActive && it.triggers.isNotEmpty() && it.targets.isNotEmpty()
                }
                if (macroEnabled && diag.status == com.example.shizuku.LvngShizukuInputService.COMPOSITOR_FAILED &&
                    prevStatus != diag.status
                ) {
                    showCompositorStatusToast(diag.status, diag.detail)
                }
                prevStatus = diag.status
            }
        }
        startLiveTelemetryLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val gameName = intent?.getStringExtra(EXTRA_GAME_NAME)
        val gamePkg = intent?.getStringExtra(EXTRA_GAME_PKG)
        val playPopOut = intent?.getBooleanExtra(EXTRA_PLAY_POPOUT, false) == true
        val popOutDelayMs = intent?.getLongExtra(EXTRA_POPOUT_DELAY_MS, 0L)?.coerceAtLeast(0L) ?: 0L
        if (!gameName.isNullOrBlank()) {
            LvngRuntimeBus.update {
                it.copy(
                    activeGameName = gameName,
                    activeGamePackage = gamePkg
                )
            }
        }
        popOutDelayJob?.cancel()
        if (playPopOut && Settings.canDrawOverlays(this)) {
            bubbleView?.visibility = View.GONE
            popOutDelayJob = serviceScope.launch {
                if (popOutDelayMs > 0L) {
                    delay(popOutDelayMs)
                }
                // Wait briefly if launch video is still finishing so the bubble appears cleanly in-game
                var waitChecks = 0
                while (LvngRuntimeBus.state.value.isLaunchVideoPlaying && waitChecks < 45) {
                    delay(80L)
                    waitChecks++
                }
                playPopOutBubbleToLeftEdge()
            }
        } else {
            bubbleView?.visibility = View.VISIBLE
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_service_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.overlay_service_title))
            .setContentText(getString(R.string.overlay_service_desc))
            .setOngoing(true)
            .setContentIntent(pi)
            .build()

        startForeground(NOTIF_ID, notif)
    }

    private fun startLiveTelemetryLoop() {
        telemetryJob?.cancel()
        telemetryJob = serviceScope.launch {
            launch {
                telemetryMonitor.observeLiveFps().collect { fps ->
                    val snap = telemetryMonitor.readSnapshot(fps, LvngRuntimeBus.state.value.telemetry.pingMs)
                    LvngRuntimeBus.update { it.copy(telemetry = snap) }
                }
            }
            while (true) {
                delay(1800L)
                val currentFps = LvngRuntimeBus.state.value.telemetry.fps
                val snap = telemetryMonitor.readSnapshot(currentFps, LvngRuntimeBus.state.value.telemetry.pingMs)
                LvngRuntimeBus.update { it.copy(telemetry = snap) }
            }
        }
    }

    private fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun getRealScreenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            bounds.width().coerceAtLeast(1) to bounds.height().coerceAtLeast(1)
        } else {
            val realMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(realMetrics)
            realMetrics.widthPixels.coerceAtLeast(1) to realMetrics.heightPixels.coerceAtLeast(1)
        }
    }

    private fun getDisplayRotation(): Int {
        return runCatching {
            val dm = getSystemService(android.hardware.display.DisplayManager::class.java)
            dm?.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.rotation
                ?: @Suppress("DEPRECATION") windowManager.defaultDisplay.rotation
        }.getOrDefault(android.view.Surface.ROTATION_0)
    }

    private fun WindowManager.LayoutParams.applyEdgeToEdgeCutout(): WindowManager.LayoutParams {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return this
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val density = resources.displayMetrics.density
        val (realW, realH) = getRealScreenSize()

        bubbleView?.let { view ->
            bubbleParams?.let { bp ->
                val bubbleSizePx = (42 * density).roundToInt()
                val dockWidthPx = (14 * density).roundToInt()

                bp.x = when {
                    isDockedToEdge && isDockedRightEdge -> (realW - dockWidthPx).coerceAtLeast(0)
                    isDockedToEdge -> 0
                    else -> bp.x.coerceIn(0, (realW - bubbleSizePx).coerceAtLeast(0))
                }
                bp.y = bp.y.coerceIn(0, (realH - bubbleSizePx).coerceAtLeast(0))
                if (view.isAttachedToWindow) {
                    runCatching { windowManager.updateViewLayout(view, bp) }
                }
            }
        }

        monitoringView?.let { mView ->
            monitoringParams?.let { mp ->
                val viewW = mView.width.takeIf { it > 0 } ?: (240 * density).roundToInt()
                val viewH = mView.height.takeIf { it > 0 } ?: (36 * density).roundToInt()
                val maxX = (realW - viewW).coerceAtLeast(0)
                val maxY = (realH - viewH).coerceAtLeast(0)
                mp.x = (monitoringPosXFraction * realW).roundToInt().coerceIn(0, maxX)
                mp.y = (monitoringPosYFraction * realH).roundToInt().coerceIn(0, maxY)
                if (mView.isAttachedToWindow) {
                    runCatching { windowManager.updateViewLayout(mView, mp) }
                }
            }
        }

        val currentConfig = LvngRuntimeBus.state.value.macroConfig
        if (currentConfig.isMacroActive) {
            com.example.shizuku.LvngShizukuManager.setCompositorActiveAsync(true, getDisplayRotation())
        }
        if (currentConfig.isMacroActive &&
            runtimeTriggerViews.size == currentConfig.triggers.size &&
            LvngRuntimeBus.state.value.activeTriggerId == null
        ) {
            val triggerSizePx = (86 * density).roundToInt()
            runtimeTriggerViews.forEachIndexed { idx, tView ->
                val trigger = currentConfig.triggers.getOrNull(idx) ?: return@forEachIndexed
                val lp = tView.layoutParams as? WindowManager.LayoutParams ?: return@forEachIndexed
                lp.width = triggerSizePx
                lp.height = triggerSizePx
                lp.x = (trigger.xFraction * realW).roundToInt() - (triggerSizePx / 2)
                lp.y = (trigger.yFraction * realH).roundToInt() - (triggerSizePx / 2)
                if (tView.isAttachedToWindow) {
                    runCatching { windowManager.updateViewLayout(tView, lp) }
                }
            }
        }
    }

    private fun prepareComposeView(content: @Composable () -> Unit): ComposeView {
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LvngOverlayService)
            setViewTreeViewModelStoreOwner(this@LvngOverlayService)
            setViewTreeSavedStateRegistryOwner(this@LvngOverlayService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                LvngSpaceTheme {
                    content()
                }
            }
        }
    }

    private fun safeRemoveView(view: View?) {
        if (view != null && view.isAttachedToWindow) {
            runCatching { windowManager.removeView(view) }
        }
    }

    private fun attachBubbleWindow() {
        if (bubbleView != null) return
        val density = resources.displayMetrics.density
        val (screenWidth, screenHeight) = getRealScreenSize()
        val dockWidthPx = (14 * density).roundToInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SPLIT_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - dockWidthPx).coerceAtLeast(0)
            y = (screenHeight * 0.36f).roundToInt()
            applyEdgeToEdgeCutout()
        }
        bubbleParams = params

        val view = prepareComposeView {
            if (isDockedToEdge) {
                LvngEdgeDockTab(isRightEdge = isDockedRightEdge)
            } else {
                LvngFloatingLogoBubble()
            }
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            val (realW, realH) = getRealScreenSize()
            val bubbleSizePx = (42 * density).roundToInt()
            val currentDockWidthPx = (14 * density).roundToInt()
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    val pt = TouchPointerPoint(0L, event.rawX, event.rawY)
                    LvngRuntimeBus.update {
                        it.copy(
                            activeTouchPointers = listOf(pt),
                            lastTouchPoint = pt,
                            touchTrailPoints = listOf(pt.xPx to pt.yPx)
                        )
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).roundToInt()
                    val dy = (event.rawY - initialTouchY).roundToInt()
                    if (abs(dx) > 8 || abs(dy) > 8) {
                        isDragging = true
                        isDockedToEdge = false
                    }
                    params.x = (initialX + dx).coerceIn(0, (realW - bubbleSizePx).coerceAtLeast(0))
                    params.y = (initialY + dy).coerceIn(0, (realH - bubbleSizePx).coerceAtLeast(0))
                    if (view.isAttachedToWindow) {
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                    val prev = LvngRuntimeBus.state.value.lastTouchPoint
                    val pt = TouchPointerPoint(
                        0L,
                        event.rawX,
                        event.rawY,
                        dx = prev?.let { event.rawX - it.xPx } ?: 0f,
                        dy = prev?.let { event.rawY - it.yPx } ?: 0f
                    )
                    LvngRuntimeBus.update {
                        it.copy(
                            activeTouchPointers = listOf(pt),
                            lastTouchPoint = pt,
                            touchTrailPoints = (it.touchTrailPoints + (pt.xPx to pt.yPx)).takeLast(28)
                        )
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    LvngRuntimeBus.update {
                        it.copy(activeTouchPointers = emptyList(), touchTrailPoints = emptyList())
                    }
                    if (!isDragging) {
                        openControlPanel()
                    } else {
                        val edgeThresholdPx = (44 * density).roundToInt()
                        when {
                            params.x <= edgeThresholdPx -> {
                                isDockedToEdge = true
                                isDockedRightEdge = false
                                params.x = 0
                            }
                            params.x >= realW - bubbleSizePx - edgeThresholdPx -> {
                                isDockedToEdge = true
                                isDockedRightEdge = true
                                params.x = (realW - currentDockWidthPx).coerceAtLeast(0)
                            }
                            else -> {
                                isDockedToEdge = false
                            }
                        }
                        if (view.isAttachedToWindow) {
                            runCatching { windowManager.updateViewLayout(view, params) }
                        }
                    }
                    true
                }

                else -> false
            }
        }

        bubbleView = view
        runCatching { windowManager.addView(view, params) }
    }

    private fun attachAuxOverlayWindow() {
        if (auxOverlayView != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).applyEdgeToEdgeCutout().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val im = getSystemService(android.hardware.input.InputManager::class.java)
                val maxOpacity = im?.maximumObscuringOpacityForTouch ?: 0.8f
                alpha = minOf(0.78f, maxOpacity)
            }
        }

        val view = prepareComposeView {
            val state by LvngRuntimeBus.state.collectAsState()
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val widthPx = constraints.maxWidth.toFloat()
                val heightPx = constraints.maxHeight.toFloat()

                if (state.crosshairStyle != CrosshairStyle.OFF) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        LvngCrosshairReticle(style = state.crosshairStyle)
                    }
                }

                // Render Trigger rings + Target markers in non-touchable aux overlay so
                // touch-catchers can shrink away during hold without hiding visual feedback.
                if (state.macroConfig.isMacroActive && !state.isKeymapEditMode) {
                    val composeDensity = androidx.compose.ui.platform.LocalDensity.current
                    val triggerRadiusPx = with(composeDensity) { 43.dp.toPx() }.roundToInt()
                    state.macroConfig.triggers.forEach { trigger ->
                        val tx = (trigger.xFraction * widthPx).roundToInt()
                        val ty = (trigger.yFraction * heightPx).roundToInt()
                        MacroRuntimeTriggerCircle(
                            triggerName = trigger.name,
                            isPressed = state.activeTriggerId == trigger.id,
                            modifier = Modifier.offset { IntOffset(tx - triggerRadiusPx, ty - triggerRadiusPx) }
                        )
                    }

                    val targetRadiusPx = with(composeDensity) { 24.dp.toPx() }.roundToInt()
                    state.macroConfig.targets.forEach { target ->
                        val cx = (target.xFraction * widthPx).roundToInt()
                        val cy = (target.yFraction * heightPx).roundToInt()
                        MacroRuntimeTargetMarker(
                            targetName = target.name,
                            isPulsing = state.pulsingTargetId == target.id,
                            modifier = Modifier.offset { IntOffset(cx - targetRadiusPx, cy - targetRadiusPx) }
                        )
                    }
                }

                // Live Coordinate HUD + Touch Dots overlay
                LvngTouchTelemetryHudOverlay(state = state)

                // Boost Performance pill banner at bottom center
                LvngBoostPerformanceBanner(
                    triggerNonce = state.boostTriggerNonce,
                    freedMb = state.lastBoostFreedMb,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp)
                )
            }
        }

        auxOverlayView = view
        runCatching { windowManager.addView(view, params) }
    }

    private fun attachMonitoringWindow() {
        if (monitoringView != null) return
        val density = resources.displayMetrics.density
        val (realW, realH) = getRealScreenSize()
        val estWidthPx = (240 * density).roundToInt()
        val estHeightPx = (36 * density).roundToInt()
        val maxX = (realW - estWidthPx).coerceAtLeast(0)
        val maxY = (realH - estHeightPx).coerceAtLeast(0)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SPLIT_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (monitoringPosXFraction * realW).roundToInt().coerceIn(0, maxX)
            y = (monitoringPosYFraction * realH).roundToInt().coerceIn(0, maxY)
            applyEdgeToEdgeCutout()
        }
        monitoringParams = params

        val view = prepareComposeView {
            val state by LvngRuntimeBus.state.collectAsState()
            LvngMonitoringPill(state = state)
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { v, event ->
            val (curW, curH) = getRealScreenSize()
            val viewW = v.width.takeIf { it > 0 } ?: estWidthPx
            val viewH = v.height.takeIf { it > 0 } ?: estHeightPx
            val boundMaxX = (curW - viewW).coerceAtLeast(0)
            val boundMaxY = (curH - viewH).coerceAtLeast(0)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).roundToInt()
                    val dy = (event.rawY - initialTouchY).roundToInt()
                    params.x = (initialX + dx).coerceIn(0, boundMaxX)
                    params.y = (initialY + dy).coerceIn(0, boundMaxY)
                    monitoringPosXFraction = if (curW > 0) params.x.toFloat() / curW.toFloat() else 0.04f
                    monitoringPosYFraction = if (curH > 0) params.y.toFloat() / curH.toFloat() else 0.06f
                    if (v.isAttachedToWindow) {
                        runCatching { windowManager.updateViewLayout(v, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }

        monitoringView = view
        runCatching { windowManager.addView(view, params) }
    }

    private fun detachMonitoringWindow() {
        safeRemoveView(monitoringView)
        monitoringView = null
        monitoringParams = null
    }

    private fun playPopOutBubbleToLeftEdge() {
        safeRemoveView(popOutView)
        popOutView = null
        bubbleView?.visibility = View.GONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).applyEdgeToEdgeCutout()

        lateinit var animView: ComposeView
        animView = prepareComposeView {
            com.example.ui.components.LvngBubblePopOutOverlay(
                visible = true,
                onAnimationComplete = {
                    val density = resources.displayMetrics.density
                    val (_, realH) = getRealScreenSize()
                    bubbleParams?.let { bp ->
                        bp.x = (4 * density).roundToInt()
                        bp.y = (realH * 0.36f).roundToInt()
                        isDockedToEdge = false
                        isDockedRightEdge = false
                        if (bubbleView?.isAttachedToWindow == true) {
                            runCatching { windowManager.updateViewLayout(bubbleView, bp) }
                        }
                    }
                    val targetBubble = bubbleView
                    targetBubble?.visibility = View.VISIBLE
                    if (targetBubble != null && targetBubble.isAttachedToWindow) {
                        targetBubble.post {
                            safeRemoveView(animView)
                            if (popOutView === animView) popOutView = null
                        }
                    } else {
                        safeRemoveView(animView)
                        if (popOutView === animView) popOutView = null
                    }
                }
            )
        }

        popOutView = animView
        runCatching { windowManager.addView(animView, params) }
    }

    private fun openControlPanel() {
        if (panelView != null) return
        isClosingPanel = false
        bubbleView?.visibility = View.GONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            applyEdgeToEdgeCutout()
        }

        isPanelVisibleAnimated = false
        val view = prepareComposeView {
            val state by LvngRuntimeBus.state.collectAsState()
            LaunchedEffect(Unit) {
                isPanelVisibleAnimated = true
            }
            AnimatedVisibility(
                visible = isPanelVisibleAnimated,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200))
            ) {
                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier
                        .fillMaxSize()
                        .observePassiveTouches(enabled = state.showCoordinates || state.showTouchDots)
                        .background(Color.Black.copy(alpha = 0.25f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { closeControlPanelAnimated() }
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .animateEnterExit(
                                enter = slideInVertically(
                                    initialOffsetY = { -it },
                                    animationSpec = tween(durationMillis = 300)
                                ),
                                exit = slideOutVertically(
                                    targetOffsetY = { -it },
                                    animationSpec = tween(durationMillis = 220)
                                )
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            )
                    ) {
                        LvngGameControlPanel(
                            state = state,
                            onMinimize = { closeControlPanelAnimated() },
                            onClose = { closeControlPanelAnimated() },
                            onOpenMacroEditor = {
                                closeControlPanelAnimated {
                                    openMacroEditor()
                                }
                            },
                            onToggleMonitoringInfo = {
                                val next = !LvngRuntimeBus.state.value.isMonitoringInfoVisible
                                LvngRuntimeBus.update { it.copy(isMonitoringInfoVisible = next) }
                                serviceScope.launch { prefsRepo.setMonitoringInfoVisible(next) }
                            },
                            onCycleCrosshair = {
                                val entries = CrosshairStyle.entries
                                val current = LvngRuntimeBus.state.value.crosshairStyle
                                val next = entries[(entries.indexOf(current) + 1) % entries.size]
                                LvngRuntimeBus.update { it.copy(crosshairStyle = next) }
                                serviceScope.launch { prefsRepo.setCrosshairStyle(next) }
                            },
                            onSelectPerformanceMode = { mode ->
                                LvngRuntimeBus.update { it.copy(activeMode = mode) }
                                serviceScope.launch { prefsRepo.setActiveMode(mode) }
                            },
                            onBoostRam = {
                                serviceScope.launch {
                                    val freed = telemetryMonitor.boostMemoryAndTrim(LvngRuntimeBus.state.value.activeGamePackage)
                                    val msg = if (freed > 0L) "RAM dibersihin (+${freed} MB)" else "Device udah dioptimalin"
                                    Toast.makeText(this@LvngOverlayService, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onBoostPerformance = {
                                LvngRuntimeBus.update { it.copy(boostTriggerNonce = it.boostTriggerNonce + 1L) }
                                serviceScope.launch {
                                    telemetryMonitor.boostMemoryAndTrim(LvngRuntimeBus.state.value.activeGamePackage)
                                }
                            },
                            onSelectResolutionScale = { scale ->
                                LvngRuntimeBus.update { it.copy(resolutionScale = scale) }
                                serviceScope.launch { prefsRepo.setResolutionScale(scale) }
                            },
                            onToggleOrientationLock = {
                                val next = !LvngRuntimeBus.state.value.isOrientationLocked
                                if (telemetryMonitor.setOrientationLocked(next)) {
                                    LvngRuntimeBus.update { it.copy(isOrientationLocked = next) }
                                } else {
                                    LvngRuntimeBus.update { it.copy(isOrientationLocked = next) }
                                }
                            },
                            onToggleCoordinates = {
                                val next = !LvngRuntimeBus.state.value.showCoordinates
                                LvngRuntimeBus.update { it.copy(showCoordinates = next) }
                                serviceScope.launch {
                                    prefsRepo.setShowTouchCoordinates(next)
                                    com.example.shizuku.LvngShizukuManager.setSystemSetting(
                                        "system",
                                        "pointer_location",
                                        if (next) 1 else 0
                                    )
                                }
                            },
                            onToggleTouchDots = {
                                val next = !LvngRuntimeBus.state.value.showTouchDots
                                LvngRuntimeBus.update { it.copy(showTouchDots = next) }
                                serviceScope.launch {
                                    prefsRepo.setShowTouchDots(next)
                                    com.example.shizuku.LvngShizukuManager.setSystemSetting(
                                        "system",
                                        "show_touches",
                                        if (next) 1 else 0
                                    )
                                }
                            },
                            onReturnToApp = {
                                closeControlPanelAnimated {
                                    val intent = Intent(this@LvngOverlayService, MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    }
                                    startActivity(intent)
                                }
                            },
                            onShutdownOverlay = {
                                stopSelf()
                            }
                        )
                    }
                }
            }
        }

        panelView = view
        LvngRuntimeBus.update { it.copy(isControlPanelExpanded = true) }
        runCatching { windowManager.addView(view, params) }
    }

    private fun closeControlPanelAnimated(afterClose: (() -> Unit)? = null) {
        if (isClosingPanel) return
        isClosingPanel = true
        serviceScope.launch {
            isPanelVisibleAnimated = false
            delay(230L)
            safeRemoveView(panelView)
            panelView = null
            isClosingPanel = false
            bubbleView?.visibility = View.VISIBLE
            LvngRuntimeBus.update { it.copy(isControlPanelExpanded = false) }
            afterClose?.invoke()
        }
    }

    private fun openMacroEditor() {
        if (macroEditorView != null) return
        clearRuntimeMacroTriggers()
        bubbleView?.visibility = View.GONE
        LvngRuntimeBus.update { it.copy(isKeymapEditMode = true) }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).applyEdgeToEdgeCutout()

        val view = prepareComposeView {
            val state by LvngRuntimeBus.state.collectAsState()
            MacroKeymapEditorOverlay(
                macroConfig = state.macroConfig,
                onUpdateConfig = { updated ->
                    LvngRuntimeBus.update { it.copy(macroConfig = updated) }
                },
                onSave = { saved ->
                    val finalConfig = saved.copy(
                        isMacroActive = saved.triggers.isNotEmpty() && saved.targets.isNotEmpty()
                    )
                    LvngRuntimeBus.update {
                        it.copy(
                            macroConfig = finalConfig,
                            isKeymapEditMode = false
                        )
                    }
                    serviceScope.launch {
                        prefsRepo.saveMacroConfig(finalConfig)
                    }
                    closeMacroEditor()
                    syncRuntimeMacroTriggers(finalConfig)
                    if (finalConfig.isMacroActive && !com.example.shizuku.LvngShizukuManager.isReady(this@LvngOverlayService)) {
                        Toast.makeText(
                            this@LvngOverlayService,
                            "Macro disimpan! Aktifin & izinin Shizuku dulu biar auto-click jalan",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                onCancel = {
                    LvngRuntimeBus.update { it.copy(isKeymapEditMode = false) }
                    closeMacroEditor()
                    syncRuntimeMacroTriggers(LvngRuntimeBus.state.value.macroConfig)
                }
            )
        }

        macroEditorView = view
        runCatching { windowManager.addView(view, params) }
    }

    private fun closeMacroEditor() {
        safeRemoveView(macroEditorView)
        macroEditorView = null
        bubbleView?.visibility = View.VISIBLE
    }

    private fun clearRuntimeMacroTriggers(releaseCompositor: Boolean = true) {
        macroLoopJob?.cancel()
        macroLoopJob = null
        com.example.shizuku.LvngShizukuManager.stopRepeatTap()
        if (releaseCompositor) {
            com.example.shizuku.LvngShizukuManager.setCompositorActiveAsync(false, 0)
        }
        runtimeTriggerViews.forEach { safeRemoveView(it) }
        runtimeTriggerViews.clear()
    }

    private fun syncRuntimeMacroTriggers(config: MacroConfig) {
        val shouldActivate = config.isMacroActive && config.triggers.isNotEmpty() && config.targets.isNotEmpty()
        clearRuntimeMacroTriggers(releaseCompositor = !shouldActivate)
        if (!shouldActivate) return

        com.example.shizuku.LvngShizukuManager.setCompositorActiveAsync(true, getDisplayRotation())

        val (realW, realH) = getRealScreenSize()
        val density = resources.displayMetrics.density
        val sizePx = (86 * density).roundToInt()
        val overlapPadPx = (16 * density).roundToInt()

        config.triggers.forEach { trigger ->
            val params = WindowManager.LayoutParams(
                sizePx,
                sizePx,
                overlayWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_SPLIT_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (trigger.xFraction * realW).roundToInt() - (sizePx / 2)
                y = (trigger.yFraction * realH).roundToInt() - (sizePx / 2)
                applyEdgeToEdgeCutout()
            }

            val triggerView = View(this).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }

            fun targetOverlapsTrigger(): Boolean {
                val (curW, curH) = getRealScreenSize()
                val primaryTarget = resolveTargetForTrigger(trigger) ?: return false
                val targetX = (primaryTarget.xFraction * curW).roundToInt()
                val targetY = (primaryTarget.yFraction * curH).roundToInt()
                val trigLeft = (trigger.xFraction * curW).roundToInt() - (sizePx / 2) - overlapPadPx
                val trigTop = (trigger.yFraction * curH).roundToInt() - (sizePx / 2) - overlapPadPx
                return targetX in trigLeft..(trigLeft + sizePx + overlapPadPx * 2) &&
                    targetY in trigTop..(trigTop + sizePx + overlapPadPx * 2)
            }

            fun shrinkCatcherOutOfWay() {
                val (curW, curH) = getRealScreenSize()
                val primaryTarget = resolveTargetForTrigger(trigger)
                val targetX = primaryTarget?.let { (it.xFraction * curW).roundToInt() } ?: -1
                val targetY = primaryTarget?.let { (it.yFraction * curH).roundToInt() } ?: -1
                val nearTopLeft = targetX in 0..8 && targetY in 0..8
                params.width = 1
                params.height = 1
                params.x = if (nearTopLeft) (curW - 1).coerceAtLeast(0) else 0
                params.y = if (nearTopLeft) (curH - 1).coerceAtLeast(0) else 0
                if (triggerView.isAttachedToWindow) {
                    runCatching { windowManager.updateViewLayout(triggerView, params) }
                }
            }

            fun restoreCatcherBounds() {
                val (curW, curH) = getRealScreenSize()
                params.width = sizePx
                params.height = sizePx
                params.x = (trigger.xFraction * curW).roundToInt() - (sizePx / 2)
                params.y = (trigger.yFraction * curH).roundToInt() - (sizePx / 2)
                if (triggerView.isAttachedToWindow) {
                    runCatching { windowManager.updateViewLayout(triggerView, params) }
                }
            }

            var lastMoveHudMs = 0L
            var activePointerId = MotionEvent.INVALID_POINTER_ID
            var isShrunk = false
            triggerView.setOnTouchListener { _, event ->
                // Ignore synthetic virtual macro pointerId (29) so injected macro taps never self-trigger.
                if (event.actionMasked == MotionEvent.ACTION_DOWN &&
                    event.getPointerId(0) == com.example.shizuku.LvngShizukuInputService.MACRO_POINTER_ID
                ) {
                    return@setOnTouchListener false
                }
                if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN &&
                    event.getPointerId(event.actionIndex) == com.example.shizuku.LvngShizukuInputService.MACRO_POINTER_ID
                ) {
                    return@setOnTouchListener false
                }
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (activePointerId != MotionEvent.INVALID_POINTER_ID) {
                            return@setOnTouchListener true
                        }
                        activePointerId = event.getPointerId(0)
                        // Only move triggerView out of the way when Target actually overlaps Trigger bounds,
                        // avoiding unnecessary WindowManager relayouts during non-overlapping gameplay touches.
                        isShrunk = if (targetOverlapsTrigger()) {
                            shrinkCatcherOutOfWay()
                            true
                        } else {
                            false
                        }
                        startMacroSpamForTrigger(trigger, delayFirstTapMs = if (isShrunk) 18L else 0L)
                        val cur = LvngRuntimeBus.state.value
                        if (cur.showCoordinates || cur.showTouchDots) {
                            val pt = TouchPointerPoint(0L, event.rawX, event.rawY)
                            LvngRuntimeBus.update {
                                it.copy(
                                    activeTouchPointers = listOf(pt),
                                    lastTouchPoint = pt
                                )
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val now = android.os.SystemClock.uptimeMillis()
                        if (now - lastMoveHudMs >= 24L) {
                            val cur = LvngRuntimeBus.state.value
                            if (cur.showCoordinates || cur.showTouchDots) {
                                val pIdx = if (activePointerId != MotionEvent.INVALID_POINTER_ID) {
                                    event.findPointerIndex(activePointerId)
                                } else {
                                    0
                                }
                                if (pIdx >= 0) {
                                    lastMoveHudMs = now
                                    val pt = TouchPointerPoint(0L, event.rawX, event.rawY)
                                    LvngRuntimeBus.update {
                                        it.copy(
                                            activeTouchPointers = listOf(pt),
                                            lastTouchPoint = pt
                                        )
                                    }
                                }
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        val liftedId = event.getPointerId(event.actionIndex)
                        if (liftedId == activePointerId) {
                            activePointerId = MotionEvent.INVALID_POINTER_ID
                            val isReleaseTap = resolveTargetForTrigger(trigger)?.tapMode == MacroTapMode.RELEASE_TAP
                            val wasShrunk = isShrunk
                            isShrunk = false
                            stopMacroSpamForTrigger(
                                trigger,
                                onPostDispatch = if (isReleaseTap && wasShrunk) ::restoreCatcherBounds else null
                            )
                            if (!isReleaseTap && wasShrunk) {
                                restoreCatcherBounds()
                            }
                            if (LvngRuntimeBus.state.value.activeTouchPointers.isNotEmpty()) {
                                LvngRuntimeBus.update {
                                    it.copy(activeTouchPointers = emptyList())
                                }
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        activePointerId = MotionEvent.INVALID_POINTER_ID
                        val isReleaseTap = resolveTargetForTrigger(trigger)?.tapMode == MacroTapMode.RELEASE_TAP
                        val wasShrunk = isShrunk
                        isShrunk = false
                        stopMacroSpamForTrigger(
                            trigger,
                            onPostDispatch = if (isReleaseTap && wasShrunk) ::restoreCatcherBounds else null
                        )
                        if (!isReleaseTap && wasShrunk) {
                            restoreCatcherBounds()
                        }
                        if (LvngRuntimeBus.state.value.activeTouchPointers.isNotEmpty()) {
                            LvngRuntimeBus.update {
                                it.copy(activeTouchPointers = emptyList())
                            }
                        }
                        true
                    }
                    else -> false
                }
            }

            runtimeTriggerViews.add(triggerView)
            runCatching { windowManager.addView(triggerView, params) }
        }
    }

    private fun resolveTargetForTrigger(trigger: MacroTriggerNode): com.example.model.MacroTargetNode? {
        val config = LvngRuntimeBus.state.value.macroConfig
        if (config.targets.isEmpty()) return null
        val triggerIndex = config.triggers.indexOfFirst { it.id == trigger.id }.coerceAtLeast(0)
        return config.targets.getOrNull(triggerIndex) ?: config.targets.first()
    }

    private fun startMacroSpamForTrigger(trigger: MacroTriggerNode, delayFirstTapMs: Long = 0L) {
        macroLoopJob?.cancel()
        com.example.shizuku.LvngShizukuManager.stopRepeatTap()
        LvngRuntimeBus.update { it.copy(activeTriggerId = trigger.id) }

        val (realW, realH) = getRealScreenSize()
        val rotation = getDisplayRotation()
        val primaryTarget = resolveTargetForTrigger(trigger) ?: return
        val targetX = primaryTarget.xFraction * realW
        val targetY = primaryTarget.yFraction * realH

        macroLoopJob = serviceScope.launch {
            if (delayFirstTapMs > 0L) {
                delay(delayFirstTapMs)
            }
            when (primaryTarget.tapMode) {
                MacroTapMode.TAP -> {
                    fireSingleTargetClick(primaryTarget.id, targetX, targetY, primaryTarget.durationMs.toLong(), realW, realH, rotation)
                }
                MacroTapMode.DOUBLE_TAP -> {
                    fireSingleTargetClick(primaryTarget.id, targetX, targetY, primaryTarget.durationMs.toLong(), realW, realH, rotation)
                    delay(primaryTarget.intervalMs.toLong().coerceAtLeast(10L))
                    fireSingleTargetClick(primaryTarget.id, targetX, targetY, primaryTarget.durationMs.toLong(), realW, realH, rotation)
                }
                MacroTapMode.REPEAT_TAP -> {
                    val intervalMs = primaryTarget.intervalMs.toLong().coerceIn(10L, 1000L)
                    val durationMs = primaryTarget.durationMs.toLong().coerceIn(10L, 1000L)
                    val startedNative = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        com.example.shizuku.LvngShizukuManager.startRepeatTap(
                            xPx = targetX,
                            yPx = targetY,
                            intervalMs = intervalMs,
                            durationMs = durationMs,
                            screenWidth = realW,
                            screenHeight = realH,
                            rotation = rotation
                        )
                    }
                    if (!isActive || LvngRuntimeBus.state.value.activeTriggerId != trigger.id) {
                        if (startedNative) {
                            com.example.shizuku.LvngShizukuManager.stopRepeatTap()
                        }
                        return@launch
                    }
                    if (startedNative) {
                        com.example.shizuku.LvngShizukuManager.refreshCompositorStateAsync()
                        // Keep target indicator statically lit while held and gently tick counter to avoid overlay relayout stutter
                        LvngRuntimeBus.update { it.copy(pulsingTargetId = primaryTarget.id) }
                        while (isActive) {
                            delay(180L)
                            if (!com.example.shizuku.LvngShizukuManager.isRepeatTapActive()) {
                                LvngRuntimeBus.update {
                                    if (it.activeTriggerId == trigger.id) {
                                        it.copy(activeTriggerId = null, pulsingTargetId = null)
                                    } else it
                                }
                                break
                            }
                            LvngRuntimeBus.update { it.copy(macroTapCount = it.macroTapCount + 1) }
                        }
                    } else {
                        // Do not run standalone UHID fallback when compositor EVIOCGRAB failed; surface exact reason
                        val diag = com.example.shizuku.LvngShizukuManager.compositorDiagnostics.value
                        showCompositorStatusToast(diag.status, diag.detail, force = true)
                        LvngRuntimeBus.update {
                            if (it.activeTriggerId == trigger.id) {
                                it.copy(activeTriggerId = null, pulsingTargetId = null)
                            } else {
                                it
                            }
                        }
                    }
                }
                MacroTapMode.RELEASE_TAP -> {
                    // Dispatched on ACTION_UP
                }
            }
        }
    }

    private fun stopMacroSpamForTrigger(
        trigger: MacroTriggerNode,
        onPostDispatch: (() -> Unit)? = null
    ) {
        val wasActive = LvngRuntimeBus.state.value.activeTriggerId == trigger.id
        macroLoopJob?.cancel()
        macroLoopJob = null
        com.example.shizuku.LvngShizukuManager.stopRepeatTap()
        LvngRuntimeBus.update { it.copy(activeTriggerId = null, pulsingTargetId = null) }

        val primaryTarget = resolveTargetForTrigger(trigger)
        if (wasActive && primaryTarget?.tapMode == MacroTapMode.RELEASE_TAP) {
            val (realW, realH) = getRealScreenSize()
            val rotation = getDisplayRotation()
            serviceScope.launch {
                try {
                    fireSingleTargetClick(
                        primaryTarget.id,
                        primaryTarget.xFraction * realW,
                        primaryTarget.yFraction * realH,
                        primaryTarget.durationMs.toLong(),
                        realW,
                        realH,
                        rotation
                    )
                } finally {
                    onPostDispatch?.invoke()
                }
            }
        } else {
            onPostDispatch?.invoke()
        }
    }

    private suspend fun fireSingleTargetClick(
        targetId: String,
        xPx: Float,
        yPx: Float,
        durationMs: Long,
        screenW: Int = 0,
        screenH: Int = 0,
        rotation: Int = 0
    ) {
        val dispatched = com.example.shizuku.LvngShizukuManager.dispatchClickAt(
            xPx,
            yPx,
            durationMs,
            screenW,
            screenH,
            rotation
        )
        if (!dispatched) {
            val diag = com.example.shizuku.LvngShizukuManager.compositorDiagnostics.value
            showCompositorStatusToast(diag.status, diag.detail, force = true)
            return
        }
        LvngRuntimeBus.update {
            it.copy(
                pulsingTargetId = targetId,
                macroTapCount = it.macroTapCount + 1,
                lastTouchPoint = TouchPointerPoint(id = -1L, xPx = xPx, yPx = yPx)
            )
        }
        serviceScope.launch {
            delay(55L)
            LvngRuntimeBus.update {
                if (it.pulsingTargetId == targetId) it.copy(pulsingTargetId = null) else it
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        telemetryJob?.cancel()
        macroLoopJob?.cancel()
        popOutDelayJob?.cancel()
        com.example.shizuku.LvngShizukuManager.stopRepeatTap()
        CoroutineScope(Dispatchers.IO).launch {
            com.example.shizuku.LvngShizukuManager.setSystemSetting("system", "show_touches", 0)
            com.example.shizuku.LvngShizukuManager.setSystemSetting("system", "pointer_location", 0)
        }
        serviceScope.cancel()
        clearRuntimeMacroTriggers()
        detachMonitoringWindow()
        safeRemoveView(macroEditorView)
        safeRemoveView(panelView)
        safeRemoveView(bubbleView)
        safeRemoveView(auxOverlayView)
        safeRemoveView(popOutView)
        macroEditorView = null
        panelView = null
        bubbleView = null
        auxOverlayView = null
        popOutView = null
        LvngRuntimeBus.update {
            it.copy(
                isServiceActive = false,
                isControlPanelExpanded = false,
                activeTriggerId = null,
                pulsingTargetId = null
            )
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
