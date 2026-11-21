package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LvngPreferencesRepository
import com.example.data.StoredGameTuning
import com.example.data.StoredPreferencesState
import com.example.data.SystemTelemetryMonitor
import com.example.model.ActivationMethod
import com.example.model.AppLanguage
import com.example.model.CrosshairStyle
import com.example.model.GameProfile
import com.example.model.InstalledAppItem
import com.example.model.LvngSpaceUiState
import com.example.model.MacroConfig
import com.example.model.PerformanceMode
import com.example.model.PermissionStatus
import com.example.model.ResolutionScale
import com.example.model.TouchConfig
import com.example.model.TouchCurve
import com.example.overlay.LvngOverlayService
import com.example.overlay.LvngRuntimeBus
import com.example.shizuku.LvngShizukuManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LvngSpaceViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val prefsRepo = LvngPreferencesRepository(appContext)
    private val telemetryMonitor = SystemTelemetryMonitor(appContext)

    private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    private val _latestFps = MutableStateFlow(60)
    private val _pingMs = MutableStateFlow<Int?>(null)
    private val _permissions = MutableStateFlow(telemetryMonitor.readPermissionStatus())
    private val _onboardingDismissed = MutableStateFlow(false)
    private val _inAppControlPreviewOpen = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private var initialMacroSynced = false

    private val _uiState = MutableStateFlow(LvngSpaceUiState())
    val uiState: StateFlow<LvngSpaceUiState> = _uiState.asStateFlow()

    init {
        LvngShizukuManager.init(appContext)
        viewModelScope.launch {
            prefsRepo.getOrCreateDeviceId()
        }
        viewModelScope.launch {
            LvngShizukuManager.status.collect { shizukuStatus ->
                _permissions.value = telemetryMonitor.readPermissionStatus(shizukuStatus)
            }
        }
        startTelemetryLoops()
        refreshInstalledApps()
        refreshPermissions()
        observeCombinedState()
    }

    private fun startTelemetryLoops() {
        viewModelScope.launch {
            telemetryMonitor.observeLiveFps().collect { fps ->
                _latestFps.value = fps
            }
        }
        viewModelScope.launch {
            while (true) {
                _pingMs.value = telemetryMonitor.measureTcpLatency()
                delay(5000L)
            }
        }
    }

    fun refreshPermissions() {
        _permissions.value = telemetryMonitor.readPermissionStatus()
    }

    fun requestShizukuPermission(): Boolean {
        val started = LvngShizukuManager.requestPermission(appContext)
        refreshPermissions()
        return started
    }

    fun openShizukuApp(): Boolean = LvngShizukuManager.openShizukuApp(appContext)

    fun dismissOnboarding() {
        _onboardingDismissed.value = true
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            _installedApps.value = telemetryMonitor.loadInstalledLauncherApps()
        }
    }

    private fun observeCombinedState() {
        viewModelScope.launch {
            combine(
                prefsRepo.preferencesFlow,
                _installedApps,
                _latestFps,
                _pingMs,
                _permissions
            ) { prefs, apps, fps, ping, perms ->
                CoreDataSnapshot(prefs, apps, fps, ping, perms)
            }.combine(
                combine(
                    _onboardingDismissed,
                    _inAppControlPreviewOpen,
                    _statusMessage,
                    LvngRuntimeBus.state
                ) { dismissed, previewOpen, msg, bus ->
                    UiFlagsSnapshot(dismissed, previewOpen, msg, bus)
                }
            ) { core, flags ->
                val appMap = core.apps.associateBy { it.packageName }
                val gameProfiles = core.prefs.addedPackages.mapNotNull { pkg ->
                    val app = appMap[pkg] ?: return@mapNotNull null
                    val tuning = core.prefs.gameTunings[pkg] ?: StoredGameTuning()
                    GameProfile(
                        packageName = app.packageName,
                        appName = app.appName,
                        versionName = app.versionName,
                        isDetectedGame = app.isDetectedGame,
                        iconBitmap = app.iconBitmap,
                        performanceMode = tuning.performanceMode,
                        touchSensitivity = tuning.touchSensitivity,
                        autoDndOnLaunch = tuning.autoDndOnLaunch,
                        crosshairStyle = tuning.crosshairStyle,
                        keymapNodes = tuning.keymapNodes,
                        launchCount = tuning.launchCount
                    )
                }.sortedBy { it.appName.lowercase() }

                val telemetry = telemetryMonitor.readSnapshot(core.fps, core.ping)

                // Keep runtime bus synced with persistent settings & live telemetry
                val shouldSyncInitialMacro = !initialMacroSynced
                initialMacroSynced = true
                LvngRuntimeBus.update { bus ->
                    bus.copy(
                        activeMode = core.prefs.activeMode,
                        telemetry = telemetry,
                        touchSensitivity = core.prefs.touchConfig.sensitivityMultiplier,
                        crosshairStyle = core.prefs.activeCrosshair,
                        resolutionScale = core.prefs.resolutionScale,
                        isOrientationLocked = telemetryMonitor.isOrientationLocked(),
                        isMonitoringInfoVisible = core.prefs.isMonitoringInfoVisible,
                        showCoordinates = core.prefs.showTouchCoordinates,
                        showTouchDots = core.prefs.showTouchDots,
                        macroConfig = if (shouldSyncInitialMacro &&
                            bus.macroConfig.triggers.isEmpty() &&
                            bus.macroConfig.targets.isEmpty()
                        ) core.prefs.macroConfig else bus.macroConfig
                    )
                }

                LvngSpaceUiState(
                    language = core.prefs.language,
                    activeMode = core.prefs.activeMode,
                    touchConfig = core.prefs.touchConfig,
                    addedGames = gameProfiles,
                    selectedGamePackage = core.prefs.selectedGamePackage,
                    installedApps = core.apps,
                    telemetry = telemetry,
                    permissions = core.perms,
                    onboardingDismissed = flags.dismissed,
                    activationMethod = core.prefs.activationMethod,
                    showTouchCoordinates = core.prefs.showTouchCoordinates,
                    showTouchDots = core.prefs.showTouchDots,
                    isOrientationLocked = telemetryMonitor.isOrientationLocked(),
                    isMonitoringInfoVisible = core.prefs.isMonitoringInfoVisible,
                    isKeymapEditMode = flags.bus.isKeymapEditMode,
                    resolutionScale = core.prefs.resolutionScale,
                    activeCrosshair = core.prefs.activeCrosshair,
                    isOverlayServiceRunning = flags.bus.isServiceActive,
                    isInAppControlPreviewOpen = flags.previewOpen,
                    statusMessage = flags.msg,
                    loggedInAccountId = core.prefs.loggedInAccountId,
                    loggedInUsername = core.prefs.loggedInUsername,
                    loggedInExpiresAtMillis = core.prefs.loggedInExpiresAtMillis,
                    localDeviceId = core.prefs.localDeviceId
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun toggleLanguage() {
        viewModelScope.launch {
            val next = if (_uiState.value.language == AppLanguage.ID) AppLanguage.EN else AppLanguage.ID
            prefsRepo.setLanguage(next)
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            prefsRepo.setLanguage(language)
        }
    }

    fun selectPerformanceMode(mode: PerformanceMode) {
        viewModelScope.launch {
            prefsRepo.setActiveMode(mode)
            _statusMessage.value = "Mode ${mode.label} aktif (${mode.touchPollHz}Hz)"
        }
    }

    fun addGameToSpace(packageName: String) {
        viewModelScope.launch {
            prefsRepo.addGamePackage(packageName)
        }
    }

    fun removeGameFromSpace(packageName: String) {
        viewModelScope.launch {
            prefsRepo.removeGamePackage(packageName)
        }
    }

    fun saveGameProfile(
        packageName: String,
        mode: PerformanceMode,
        sensitivity: Float,
        autoDnd: Boolean,
        crosshair: CrosshairStyle
    ) {
        viewModelScope.launch {
            val existing = _uiState.value.addedGames.find { it.packageName == packageName }
            prefsRepo.saveGameTuning(
                packageName,
                StoredGameTuning(
                    performanceMode = mode,
                    touchSensitivity = sensitivity,
                    autoDndOnLaunch = autoDnd,
                    crosshairStyle = crosshair,
                    keymapNodes = existing?.keymapNodes ?: com.example.model.defaultKeymapNodes(),
                    launchCount = existing?.launchCount ?: 0
                )
            )
            _statusMessage.value = "Profil game disimpan"
        }
    }

    fun updateTouchConfig(transform: (TouchConfig) -> TouchConfig) {
        viewModelScope.launch {
            val updated = transform(_uiState.value.touchConfig)
            prefsRepo.updateTouchConfig(updated)
        }
    }

    fun applyTouchCurve(curve: TouchCurve) {
        updateTouchConfig { current ->
            when (curve) {
                TouchCurve.LINEAR -> current.copy(touchCurve = curve, sensitivityMultiplier = 1.25f, swipeAcceleration = 0.15f, microAimSmoothing = 0.10f)
                TouchCurve.PRO_FLICK -> current.copy(touchCurve = curve, sensitivityMultiplier = 1.85f, swipeAcceleration = 0.72f, microAimSmoothing = 0.25f)
                TouchCurve.SNIPER_PRECISION -> current.copy(touchCurve = curve, sensitivityMultiplier = 1.40f, swipeAcceleration = 0.30f, microAimSmoothing = 0.65f)
                TouchCurve.MOBA_RAPID -> current.copy(touchCurve = curve, sensitivityMultiplier = 2.05f, swipeAcceleration = 0.85f, microAimSmoothing = 0.15f)
            }
        }
    }

    fun setActivationMethod(method: ActivationMethod) {
        viewModelScope.launch {
            prefsRepo.setActivationMethod(method)
            _statusMessage.value = method.label
        }
    }

    fun setShowTouchCoordinates(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setShowTouchCoordinates(enabled)
            LvngShizukuManager.setSystemSetting("system", "pointer_location", if (enabled) 1 else 0)
        }
    }

    fun setShowTouchDots(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setShowTouchDots(enabled)
            LvngShizukuManager.setSystemSetting("system", "show_touches", if (enabled) 1 else 0)
        }
    }

    fun setMonitoringInfoVisible(visible: Boolean) {
        viewModelScope.launch {
            prefsRepo.setMonitoringInfoVisible(visible)
        }
    }

    fun setResolutionScale(scale: ResolutionScale) {
        viewModelScope.launch {
            prefsRepo.setResolutionScale(scale)
        }
    }

    fun setCrosshairStyle(style: CrosshairStyle) {
        viewModelScope.launch {
            prefsRepo.setCrosshairStyle(style)
        }
    }

    fun saveMacroConfig(config: MacroConfig) {
        viewModelScope.launch {
            val finalConfig = config.copy(
                isMacroActive = config.triggers.isNotEmpty() && config.targets.isNotEmpty()
            )
            LvngRuntimeBus.update {
                it.copy(
                    macroConfig = finalConfig,
                    isKeymapEditMode = false
                )
            }
            prefsRepo.saveMacroConfig(finalConfig)
            _statusMessage.value = when {
                !finalConfig.isMacroActive -> "Macro dimatiin (tambahin Trigger & Target dulu)"
                !LvngShizukuManager.isReady(appContext) ->
                    "Macro udah kesimpen! Aktifin & izinin Shizuku biar auto-click jalan"
                else -> "Macro aktif & siap dipake"
            }
        }
    }

    fun toggleOrientationLock() {
        val next = !_uiState.value.isOrientationLocked
        val success = telemetryMonitor.setOrientationLocked(next)
        if (!success) {
            _statusMessage.value = "Kasih izin ubah setelan dulu buat kunci layar"
        } else {
            LvngRuntimeBus.update { it.copy(isOrientationLocked = next) }
        }
    }

    fun boostMemoryNow() {
        viewModelScope.launch {
            val activeGamePkg = LvngRuntimeBus.state.value.activeGamePackage
            val freed = telemetryMonitor.boostMemoryAndTrim(activeGamePkg)
            _statusMessage.value = if (freed > 0) "RAM dibersihin (+${freed} MB)" else "Device udah dioptimalin"
        }
    }

    fun triggerBoostPerformance() {
        LvngRuntimeBus.update { it.copy(boostTriggerNonce = it.boostTriggerNonce + 1L) }
        viewModelScope.launch {
            val activeGamePkg = LvngRuntimeBus.state.value.activeGamePackage
            telemetryMonitor.boostMemoryAndTrim(activeGamePkg)
        }
    }

    fun launchGameWithOverlay(game: GameProfile, launchContext: android.content.Context? = null) {
        viewModelScope.launch {
            if (!LvngShizukuManager.isReady(appContext)) {
                refreshPermissions()
                _statusMessage.value = "SHIZUKU BELUM AKTIF"
                return@launch
            }
            LvngShizukuManager.bindUserServiceIfNeeded()

            LvngRuntimeBus.update {
                it.copy(
                    activeGameName = game.appName,
                    activeGamePackage = game.packageName,
                    activeMode = game.performanceMode,
                    touchSensitivity = game.touchSensitivity,
                    keymapNodes = game.keymapNodes
                )
            }

            if (appContext.packageManager.getLaunchIntentForPackage(game.packageName) == null) {
                _statusMessage.value = "Gagal buka ${game.appName}"
                return@launch
            }

            if (_uiState.value.permissions.hasOverlayPermission) {
                LvngOverlayService.start(
                    context = appContext,
                    gameName = game.appName,
                    packageName = game.packageName,
                    playPopOut = true,
                    popOutDelayMs = 10_000L
                )
            } else {
                _statusMessage.value = "Aktifin izin overlay biar logo panel muncul di game"
            }

            // Launch landscape intro video immediately before opening the target game
            com.example.GameLaunchVideoActivity.launch(
                context = launchContext ?: appContext,
                packageName = game.packageName,
                gameName = game.appName
            )

            // Run device boost and persist tuning asynchronously during video playback
            launch {
                prefsRepo.setActiveMode(game.performanceMode)
                if (game.crosshairStyle != CrosshairStyle.OFF) {
                    prefsRepo.setCrosshairStyle(game.crosshairStyle)
                }
                prefsRepo.incrementLaunchCount(game.packageName)
                telemetryMonitor.boostMemoryAndTrim(game.packageName)
            }
        }
    }

    fun startOverlayBubbleNow() {
        if (_uiState.value.permissions.hasOverlayPermission) {
            LvngOverlayService.start(appContext, "LVNG SPACE", appContext.packageName, playPopOut = false)
            _statusMessage.value = "Logo panel aktif"
        } else {
            _inAppControlPreviewOpen.value = true
        }
    }

    fun stopOverlayBubble() {
        LvngOverlayService.stop(appContext)
        _inAppControlPreviewOpen.value = false
    }

    fun logoutAccount() {
        viewModelScope.launch {
            LvngOverlayService.stop(appContext)
            _inAppControlPreviewOpen.value = false
            prefsRepo.clearLoginSession()
        }
    }

    fun setInAppControlPreviewOpen(open: Boolean) {
        _inAppControlPreviewOpen.value = open
    }

    suspend fun exportMappings(): String = prefsRepo.exportMappingsJson()

    fun importMappings(json: String) {
        viewModelScope.launch {
            val ok = prefsRepo.importMappingsJson(json)
            _statusMessage.value = if (ok) "Mapping berhasil diimpor" else "Format mapping tidak valid"
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    private data class CoreDataSnapshot(
        val prefs: StoredPreferencesState,
        val apps: List<InstalledAppItem>,
        val fps: Int,
        val ping: Int?,
        val perms: PermissionStatus
    )

    private data class UiFlagsSnapshot(
        val dismissed: Boolean,
        val previewOpen: Boolean,
        val msg: String?,
        val bus: com.example.overlay.OverlayRuntimeState
    )
}
