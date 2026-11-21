package com.example

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.GoogleAuthHelper
import com.example.data.AccountRepository
import com.example.data.LvngPreferencesRepository
import com.example.data.StoredPreferencesState
import com.example.model.AppLanguage
import com.example.model.CrosshairStyle
import com.example.model.GameProfile
import com.example.model.MacroTapMode
import com.example.model.PermissionStep
import com.example.overlay.LvngOverlayService
import com.example.overlay.LvngRuntimeBus
import com.example.shizuku.LvngShizukuManager
import com.example.ui.components.CleanGameTuningSheet
import com.example.ui.components.ExtraTouchSettingsSheet
import com.example.ui.components.GameLaunchSlideSheet
import com.example.ui.components.LvngBoostPerformanceBanner
import com.example.ui.components.LvngBottomPillBar
import com.example.ui.components.LvngCrosshairReticle
import com.example.ui.components.LvngGameControlPanel
import com.example.ui.components.LvngHomeHeader
import com.example.ui.components.LvngMainTab
import com.example.ui.components.LvngTouchTelemetryHudOverlay
import com.example.ui.components.MacroKeymapEditorOverlay
import com.example.ui.components.MacroRuntimeTargetMarker
import com.example.ui.components.MacroRuntimeTriggerCircle
import com.example.ui.components.ShizukuNotReadyDialog
import com.example.ui.components.observePassiveTouches
import com.example.ui.screens.ActivationScreen
import com.example.ui.screens.AddGameScreen
import com.example.ui.screens.HomeGamesScreen
import com.example.ui.screens.KeyMappingManagerScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.OwnerPanelScreen
import com.example.ui.screens.PermissionOnboardingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TouchScreen
import com.example.ui.theme.LvngCarbon
import com.example.ui.theme.LvngCrimson
import com.example.ui.theme.LvngObsidian
import com.example.ui.theme.LvngSpaceTheme
import com.example.ui.theme.LvngTextPrimary
import com.example.ui.theme.LvngTextSecondary
import com.example.ui.theme.LvngVoid
import com.example.viewmodel.LvngAuthViewModel
import com.example.viewmodel.LvngSpaceViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class SubRoute {
    NONE,
    ADD_GAME,
    ACTIVATION,
    KEY_MAPPING_MANAGER
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LvngSpaceTheme {
                LvngAppNavigation()
            }
        }
    }
}

@Composable
fun LvngAppNavigation() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()

    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val prefsRepo = remember(appContext) { LvngPreferencesRepository(appContext) }
    val storedPrefs by prefsRepo.preferencesFlow.collectAsStateWithLifecycle(initialValue = StoredPreferencesState())

    val authViewModel: LvngAuthViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                LvngAuthViewModel(
                    accountRepository = AccountRepository(db),
                    prefsRepository = LvngPreferencesRepository(app)
                )
            }
        }
    )

    val formState by authViewModel.formState.collectAsStateWithLifecycle()
    val ownerAccountsState by authViewModel.ownerAccountsState.collectAsStateWithLifecycle()

    // Silent Google auto-sign-in attempt on startup if Firebase session is not yet active
    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            val credentialManager = CredentialManager.create(context)
            GoogleAuthHelper.attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val firebaseUser = currentUser
    val sessionAccountId = storedPrefs.loggedInAccountId

    // Verify any previously saved session against Firestore (detect deleted/expired/re-bound accounts)
    LaunchedEffect(firebaseUser?.uid, sessionAccountId) {
        if (firebaseUser != null && !sessionAccountId.isNullOrBlank()) {
            authViewModel.verifySavedSession(sessionAccountId) {
                LvngOverlayService.stop(appContext)
            }
        }
    }

    when {
        // Owner Management Panel after entering code 100214
        firebaseUser != null && formState.ownerVerifiedUid == firebaseUser.uid -> {
            key(firebaseUser.uid) {
                OwnerPanelScreen(
                    formState = formState,
                    accountsState = ownerAccountsState,
                    onCreateAccount = { user, pass, duration, customDays, onCreated ->
                        authViewModel.createAccount(user, pass, duration, customDays, onCreated)
                    },
                    onDeleteAccount = { accountId, username ->
                        authViewModel.deleteAccount(accountId, username)
                    },
                    onBack = { authViewModel.closeOwnerPanel() }
                )
            }
        }

        // Gate entire app behind Login until both Firebase Auth and LVNG Account session are valid
        firebaseUser == null || sessionAccountId.isNullOrBlank() -> {
            LoginScreen(
                firebaseUser = firebaseUser,
                formState = formState,
                onGoogleAuthSuccess = { authViewModel.clearLoginError() },
                onLoginSubmit = { user, pass ->
                    authViewModel.loginWithUsernamePassword(user, pass) {}
                },
                onVerifyOwnerCode = { code ->
                    authViewModel.verifyOwnerCode(
                        code = code,
                        currentFirebaseUid = Firebase.auth.currentUser?.uid
                    ) {}
                },
                onClearError = {
                    authViewModel.clearLoginError()
                    authViewModel.clearOwnerErrors()
                }
            )
        }

        // Authenticated Main LVNG SPACE App keyed by uid + accountId
        else -> {
            key("${firebaseUser.uid}:$sessionAccountId") {
                val lvngViewModel: LvngSpaceViewModel = viewModel(
                    key = "${firebaseUser.uid}:$sessionAccountId"
                )
                LvngSpaceRoot(
                    viewModel = lvngViewModel,
                    sessionKey = "${firebaseUser.uid}:$sessionAccountId",
                    ownerAuthLoading = formState.ownerAuthLoading,
                    ownerAuthError = formState.ownerAuthError,
                    onVerifyOwnerPin = { pin ->
                        authViewModel.verifyOwnerCode(
                            code = pin,
                            currentFirebaseUid = Firebase.auth.currentUser?.uid
                        ) {}
                    },
                    onClearOwnerError = { authViewModel.clearOwnerErrors() }
                )
            }
        }
    }
}

@Composable
fun LvngSpaceRoot(
    viewModel: LvngSpaceViewModel,
    sessionKey: String,
    ownerAuthLoading: Boolean = false,
    ownerAuthError: String? = null,
    onVerifyOwnerPin: (String) -> Unit = {},
    onClearOwnerError: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val busState by LvngRuntimeBus.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf(LvngMainTab.GAMES) }
    var activeSubRoute by remember { mutableStateOf(SubRoute.NONE) }
    var showExtraSettingsSheet by remember { mutableStateOf(false) }
    var showInAppMacroEditor by remember { mutableStateOf(false) }
    var tuningGame by remember { mutableStateOf<GameProfile?>(null) }
    var pendingLaunchGame by remember { mutableStateOf<GameProfile?>(null) }
    var showShizukuGateDialog by remember { mutableStateOf(false) }
    var forceShowPermissions by remember { mutableStateOf(false) }
    var inAppMacroJob by remember { mutableStateOf<Job?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
                viewModel.refreshInstalledApps()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (!msg.isNullOrBlank()) {
            if (msg == "SHIZUKU BELUM AKTIF") {
                showShizukuGateDialog = true
            } else {
                snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            }
            viewModel.clearStatusMessage()
        }
    }

    val nextPermissionStep = uiState.permissions.nextRequiredStep
    val showPermissionScreen = (forceShowPermissions || !uiState.onboardingDismissed) &&
        nextPermissionStep != PermissionStep.COMPLETE

    if (showPermissionScreen) {
        PermissionOnboardingScreen(
            step = nextPermissionStep,
            language = uiState.language,
            onRefreshPermissions = { viewModel.refreshPermissions() },
            onSkip = {
                forceShowPermissions = false
                viewModel.dismissOnboarding()
            }
        )
        return
    }

    if (activeSubRoute != SubRoute.NONE) {
        BackHandler { activeSubRoute = SubRoute.NONE }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LvngVoid)
            .observePassiveTouches(enabled = uiState.showTouchCoordinates || uiState.showTouchDots)
    ) {
        when (activeSubRoute) {
            SubRoute.ADD_GAME -> {
                AddGameScreen(
                    installedApps = uiState.installedApps,
                    addedPackages = uiState.addedGames.map { it.packageName }.toSet(),
                    language = uiState.language,
                    onAddGame = viewModel::addGameToSpace,
                    onRemoveGame = viewModel::removeGameFromSpace,
                    onBack = { activeSubRoute = SubRoute.NONE }
                )
            }

            SubRoute.ACTIVATION -> {
                ActivationScreen(
                    currentMethod = uiState.activationMethod,
                    isShizukuInstalled = uiState.permissions.isShizukuInstalled,
                    isShizukuRunning = uiState.permissions.isShizukuRunning,
                    isShizukuPermitted = uiState.permissions.isShizukuPermissionGranted,
                    language = uiState.language,
                    onRequestShizukuPermission = { viewModel.requestShizukuPermission() },
                    onSelectActivation = viewModel::setActivationMethod,
                    onBack = { activeSubRoute = SubRoute.NONE }
                )
            }

            SubRoute.KEY_MAPPING_MANAGER -> {
                KeyMappingManagerScreen(
                    addedGames = uiState.addedGames,
                    language = uiState.language,
                    onExportJson = viewModel::exportMappings,
                    onImportJson = viewModel::importMappings,
                    onBack = { activeSubRoute = SubRoute.NONE }
                )
            }

            SubRoute.NONE -> {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useRail = maxWidth >= 640.dp

                    Scaffold(
                        containerColor = LvngVoid,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        snackbarHost = {
                            SnackbarHost(hostState = snackbarHostState) { data ->
                                Snackbar(
                                    snackbarData = data,
                                    containerColor = LvngCarbon,
                                    contentColor = LvngTextPrimary
                                )
                            }
                        },
                        topBar = {
                            LvngHomeHeader(
                                language = uiState.language,
                                onToggleLanguage = viewModel::toggleLanguage
                            )
                        },
                        bottomBar = {
                            if (!useRail) {
                                LvngBottomPillBar(
                                    currentTab = currentTab,
                                    language = uiState.language,
                                    onSelectTab = { currentTab = it }
                                )
                            }
                        }
                    ) { innerPadding ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            if (useRail) {
                                NavigationRail(
                                    containerColor = LvngObsidian,
                                    contentColor = LvngTextPrimary,
                                    modifier = Modifier.fillMaxHeight()
                                ) {
                                    LvngMainTab.entries.forEach { tab ->
                                        val selected = tab == currentTab
                                        NavigationRailItem(
                                            selected = selected,
                                            onClick = { currentTab = tab },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = tab.labelId
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = if (uiState.language == AppLanguage.ID) tab.labelId else tab.labelEn,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            },
                                            colors = NavigationRailItemDefaults.colors(
                                                selectedIconColor = LvngCrimson,
                                                selectedTextColor = LvngCrimson,
                                                indicatorColor = LvngCrimson.copy(alpha = 0.16f),
                                                unselectedIconColor = LvngTextSecondary,
                                                unselectedTextColor = LvngTextSecondary
                                            )
                                        )
                                    }
                                }
                            }

                            Box(
                                contentAlignment = Alignment.TopCenter,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 760.dp)
                                        .fillMaxHeight()
                                ) {
                                    when (currentTab) {
                                        LvngMainTab.GAMES -> {
                                            HomeGamesScreen(
                                                uiState = uiState,
                                                onOpenAddGame = {
                                                    viewModel.refreshInstalledApps()
                                                    activeSubRoute = SubRoute.ADD_GAME
                                                },
                                                onLaunchGame = { game ->
                                                    pendingLaunchGame = game
                                                },
                                                onOpenGameTuning = { tuningGame = it },
                                                onBoostRam = viewModel::boostMemoryNow
                                            )
                                        }

                                        LvngMainTab.TOUCH -> {
                                            TouchScreen(
                                                uiState = uiState,
                                                onOpenActivation = { activeSubRoute = SubRoute.ACTIVATION },
                                                onOpenExtraSettings = { showExtraSettingsSheet = true },
                                                onUpdateTouchConfig = viewModel::updateTouchConfig,
                                                onApplyCurve = viewModel::applyTouchCurve
                                            )
                                        }

                                        LvngMainTab.SETTINGS -> {
                                            SettingsScreen(
                                                uiState = uiState,
                                                onOpenActivation = { activeSubRoute = SubRoute.ACTIVATION },
                                                onOpenKeyMappingManager = { activeSubRoute = SubRoute.KEY_MAPPING_MANAGER },
                                                onOpenExtraSettings = { showExtraSettingsSheet = true },
                                                onToggleLanguage = viewModel::toggleLanguage,
                                                onOpenPermissions = {
                                                    forceShowPermissions = true
                                                },
                                                onRequestShizuku = {
                                                    val st = LvngShizukuManager.refreshStatus(context)
                                                    if (st.isRunning && !st.isPermissionGranted) {
                                                        viewModel.requestShizukuPermission()
                                                    } else {
                                                        activeSubRoute = SubRoute.ACTIVATION
                                                    }
                                                },
                                                onOpenControlPreview = {
                                                    viewModel.setInAppControlPreviewOpen(true)
                                                },
                                                onBoostRam = viewModel::boostMemoryNow,
                                                onLogout = { viewModel.logoutAccount() },
                                                ownerAuthLoading = ownerAuthLoading,
                                                ownerAuthError = ownerAuthError,
                                                onVerifyOwnerPin = onVerifyOwnerPin,
                                                onClearOwnerError = onClearOwnerError
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // In-app crosshair & active Macro Trigger/Target preview when overlay service isn't active
        if (!uiState.isOverlayServiceRunning) {
            if (uiState.activeCrosshair != CrosshairStyle.OFF) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    LvngCrosshairReticle(style = uiState.activeCrosshair)
                }
            }

            if (busState.macroConfig.isMacroActive && !showInAppMacroEditor) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                    val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                    val screenW = constraints.maxWidth.coerceAtLeast(1)
                    val screenH = constraints.maxHeight.coerceAtLeast(1)
                    val rotation = remember(context, screenW, screenH) {
                        runCatching {
                            val dm = context.getSystemService(android.hardware.display.DisplayManager::class.java)
                            dm?.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.rotation
                                ?: android.view.Surface.ROTATION_0
                        }.getOrDefault(android.view.Surface.ROTATION_0)
                    }
                    val density = LocalDensity.current
                    val targetRadiusPx = with(density) { 24.dp.toPx() }.roundToInt()
                    val triggerRadiusPx = with(density) { 43.dp.toPx() }.roundToInt()

                    busState.macroConfig.targets.forEach { target ->
                        val cx = (target.xFraction * widthPx).roundToInt()
                        val cy = (target.yFraction * heightPx).roundToInt()
                        MacroRuntimeTargetMarker(
                            targetName = target.name,
                            isPulsing = busState.pulsingTargetId == target.id,
                            modifier = Modifier.offset { IntOffset(cx - targetRadiusPx, cy - targetRadiusPx) }
                        )
                    }

                    busState.macroConfig.triggers.forEachIndexed { idx, trigger ->
                        val cx = (trigger.xFraction * widthPx).roundToInt()
                        val cy = (trigger.yFraction * heightPx).roundToInt()
                        val linkedTarget = busState.macroConfig.targets.getOrNull(idx)
                            ?: busState.macroConfig.targets.firstOrNull()

                        MacroRuntimeTriggerCircle(
                            triggerName = trigger.name,
                            isPressed = busState.activeTriggerId == trigger.id,
                            modifier = Modifier
                                .offset { IntOffset(cx - triggerRadiusPx, cy - triggerRadiusPx) }
                                .pointerInput(trigger.id, linkedTarget, widthPx, heightPx, rotation) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        LvngRuntimeBus.update { it.copy(activeTriggerId = trigger.id) }
                                        inAppMacroJob?.cancel()
                                        if (linkedTarget != null) {
                                            val tx = linkedTarget.xFraction * widthPx
                                            val ty = linkedTarget.yFraction * heightPx
                                            inAppMacroJob = scope.launch {
                                                suspend fun fireOnce() {
                                                    val ok = LvngShizukuManager.dispatchClickAt(
                                                        tx,
                                                        ty,
                                                        linkedTarget.durationMs.toLong(),
                                                        screenW,
                                                        screenH,
                                                        rotation
                                                    )
                                                    if (!ok) {
                                                        val diag = LvngShizukuManager.compositorDiagnostics.value
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            "InputInjector ${diag.status}: ${diag.detail}",
                                                            android.widget.Toast.LENGTH_LONG
                                                        ).show()
                                                        return
                                                    }
                                                    LvngRuntimeBus.update {
                                                        it.copy(
                                                            pulsingTargetId = linkedTarget.id,
                                                            macroTapCount = it.macroTapCount + 1
                                                        )
                                                    }
                                                    launch {
                                                        delay(55L)
                                                        LvngRuntimeBus.update {
                                                            if (it.pulsingTargetId == linkedTarget.id) {
                                                                it.copy(pulsingTargetId = null)
                                                            } else it
                                                        }
                                                    }
                                                }
                                                when (linkedTarget.tapMode) {
                                                    MacroTapMode.TAP -> fireOnce()
                                                    MacroTapMode.DOUBLE_TAP -> {
                                                        fireOnce()
                                                        delay(linkedTarget.intervalMs.toLong().coerceAtLeast(10L))
                                                        fireOnce()
                                                    }
                                                    MacroTapMode.REPEAT_TAP -> {
                                                        val intervalMs = linkedTarget.intervalMs.toLong().coerceIn(10L, 1000L)
                                                        val durationMs = linkedTarget.durationMs.toLong().coerceIn(10L, 1000L)
                                                        val startedNative = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                            LvngShizukuManager.startRepeatTap(
                                                                xPx = tx,
                                                                yPx = ty,
                                                                intervalMs = intervalMs,
                                                                durationMs = durationMs,
                                                                screenWidth = screenW,
                                                                screenHeight = screenH,
                                                                rotation = rotation
                                                            )
                                                        }
                                                        if (!isActive || LvngRuntimeBus.state.value.activeTriggerId != trigger.id) {
                                                            if (startedNative) {
                                                                LvngShizukuManager.stopRepeatTap()
                                                            }
                                                            return@launch
                                                        }
                                                        if (startedNative) {
                                                            LvngRuntimeBus.update {
                                                                it.copy(
                                                                    pulsingTargetId = linkedTarget.id,
                                                                    macroTapCount = it.macroTapCount + 1
                                                                )
                                                            }
                                                            while (isActive) {
                                                                delay(180L)
                                                                if (!LvngShizukuManager.isRepeatTapActive()) {
                                                                    LvngRuntimeBus.update {
                                                                        if (it.activeTriggerId == trigger.id) {
                                                                            it.copy(activeTriggerId = null, pulsingTargetId = null)
                                                                        } else it
                                                                    }
                                                                    break
                                                                }
                                                                LvngRuntimeBus.update {
                                                                    it.copy(macroTapCount = it.macroTapCount + 1)
                                                                }
                                                            }
                                                        } else {
                                                            val diag = LvngShizukuManager.compositorDiagnostics.value
                                                            android.widget.Toast.makeText(
                                                                context,
                                                                "InputInjector ${diag.status}: ${diag.detail}",
                                                                android.widget.Toast.LENGTH_LONG
                                                            ).show()
                                                            LvngRuntimeBus.update {
                                                                if (it.activeTriggerId == trigger.id) {
                                                                    it.copy(activeTriggerId = null, pulsingTargetId = null)
                                                                } else it
                                                            }
                                                        }
                                                    }
                                                    MacroTapMode.RELEASE_TAP -> Unit
                                                }
                                            }
                                        }
                                        waitForUpOrCancellation()
                                        inAppMacroJob?.cancel()
                                        inAppMacroJob = null
                                        LvngShizukuManager.stopRepeatTap()
                                        LvngRuntimeBus.update {
                                            it.copy(activeTriggerId = null, pulsingTargetId = null)
                                        }
                                        if (linkedTarget?.tapMode == MacroTapMode.RELEASE_TAP) {
                                            val tx = linkedTarget.xFraction * widthPx
                                            val ty = linkedTarget.yFraction * heightPx
                                            scope.launch {
                                                val ok = LvngShizukuManager.dispatchClickAt(
                                                    tx,
                                                    ty,
                                                    linkedTarget.durationMs.toLong(),
                                                    screenW,
                                                    screenH,
                                                    rotation
                                                )
                                                if (!ok) {
                                                    val diag = LvngShizukuManager.compositorDiagnostics.value
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "InputInjector ${diag.status}: ${diag.detail}",
                                                        android.widget.Toast.LENGTH_LONG
                                                    ).show()
                                                    return@launch
                                                }
                                                LvngRuntimeBus.update {
                                                    it.copy(
                                                        pulsingTargetId = linkedTarget.id,
                                                        macroTapCount = it.macroTapCount + 1
                                                    )
                                                }
                                                delay(55L)
                                                LvngRuntimeBus.update { it.copy(pulsingTargetId = null) }
                                            }
                                        }
                                    }
                                }
                        )
                    }
                }
            }
        }

        // In-App LVNG -X Game Control Center Modal with smooth top-to-bottom slide animation
        if (uiState.isInAppControlPreviewOpen) {
            BackHandler { viewModel.setInAppControlPreviewOpen(false) }
        }
        AnimatedVisibility(
            visible = uiState.isInAppControlPreviewOpen,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(220))
        ) {
            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.68f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { viewModel.setInAppControlPreviewOpen(false) }
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .animateEnterExit(
                            enter = slideInVertically(
                                initialOffsetY = { -it },
                                animationSpec = tween(durationMillis = 320)
                            ),
                            exit = slideOutVertically(
                                targetOffsetY = { -it },
                                animationSpec = tween(durationMillis = 240)
                            )
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                ) {
                    LvngGameControlPanel(
                        state = busState,
                        onMinimize = { viewModel.setInAppControlPreviewOpen(false) },
                        onClose = { viewModel.setInAppControlPreviewOpen(false) },
                        onOpenMacroEditor = {
                            viewModel.setInAppControlPreviewOpen(false)
                            LvngRuntimeBus.update { it.copy(isKeymapEditMode = true) }
                            showInAppMacroEditor = true
                        },
                        onToggleMonitoringInfo = {
                            viewModel.setMonitoringInfoVisible(!uiState.isMonitoringInfoVisible)
                        },
                        onCycleCrosshair = {
                            val entries = CrosshairStyle.entries
                            val next = entries[(entries.indexOf(uiState.activeCrosshair) + 1) % entries.size]
                            viewModel.setCrosshairStyle(next)
                        },
                        onSelectPerformanceMode = viewModel::selectPerformanceMode,
                        onBoostRam = viewModel::boostMemoryNow,
                        onBoostPerformance = viewModel::triggerBoostPerformance,
                        onSelectResolutionScale = viewModel::setResolutionScale,
                        onToggleOrientationLock = viewModel::toggleOrientationLock,
                        onToggleCoordinates = {
                            viewModel.setShowTouchCoordinates(!uiState.showTouchCoordinates)
                        },
                        onToggleTouchDots = {
                            viewModel.setShowTouchDots(!uiState.showTouchDots)
                        },
                        onReturnToApp = { viewModel.setInAppControlPreviewOpen(false) },
                        onShutdownOverlay = { viewModel.stopOverlayBubble() }
                    )
                }
            }
        }

        // In-App Macro Keymap Editor Overlay
        if (showInAppMacroEditor) {
            BackHandler {
                LvngRuntimeBus.update { it.copy(isKeymapEditMode = false) }
                showInAppMacroEditor = false
            }
            MacroKeymapEditorOverlay(
                macroConfig = busState.macroConfig,
                onUpdateConfig = { updated ->
                    LvngRuntimeBus.update { it.copy(macroConfig = updated) }
                },
                onSave = { saved ->
                    viewModel.saveMacroConfig(saved)
                    showInAppMacroEditor = false
                },
                onCancel = {
                    LvngRuntimeBus.update { it.copy(isKeymapEditMode = false) }
                    showInAppMacroEditor = false
                }
            )
        }

        if (showExtraSettingsSheet) {
            ExtraTouchSettingsSheet(
                showCoordinates = uiState.showTouchCoordinates,
                showTouchDots = uiState.showTouchDots,
                language = uiState.language,
                onToggleCoordinates = viewModel::setShowTouchCoordinates,
                onToggleTouchDots = viewModel::setShowTouchDots,
                onDismiss = { showExtraSettingsSheet = false }
            )
        }

        tuningGame?.let { target ->
            CleanGameTuningSheet(
                game = target,
                onSave = { mode, sens, autoDnd, crosshair ->
                    viewModel.saveGameProfile(target.packageName, mode, sens, autoDnd, crosshair)
                },
                onRemove = { viewModel.removeGameFromSpace(target.packageName) },
                onDismiss = { tuningGame = null }
            )
        }

        // Topmost live Coordinate & Touch Dots HUD + Boost Performance Pill when overlay service isn't active
        if (!uiState.isOverlayServiceRunning) {
            LvngTouchTelemetryHudOverlay(state = busState)
            LvngBoostPerformanceBanner(
                triggerNonce = busState.boostTriggerNonce,
                freedMb = busState.lastBoostFreedMb,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 26.dp)
            )
        }

        // Half-screen bottom slide-to-open sheet when user taps Main on a selected game
        GameLaunchSlideSheet(
            game = pendingLaunchGame,
            language = uiState.language,
            onDismiss = { pendingLaunchGame = null },
            onSlideLaunchConfirmed = { selectedGame ->
                val shizukuStatus = LvngShizukuManager.refreshStatus(context)
                viewModel.refreshPermissions()
                if (!shizukuStatus.isReady) {
                    pendingLaunchGame = null
                    showShizukuGateDialog = true
                } else {
                    pendingLaunchGame = null
                    viewModel.launchGameWithOverlay(selectedGame, context)
                }
            }
        )

        if (showShizukuGateDialog) {
            ShizukuNotReadyDialog(
                isInstalled = uiState.permissions.isShizukuInstalled,
                isRunning = uiState.permissions.isShizukuRunning,
                isPermissionGranted = uiState.permissions.isShizukuPermissionGranted,
                onRequestPermission = {
                    viewModel.requestShizukuPermission()
                },
                onOpenShizuku = {
                    val opened = viewModel.openShizukuApp()
                    if (!opened) {
                        activeSubRoute = SubRoute.ACTIVATION
                        showShizukuGateDialog = false
                    }
                },
                onRecheck = {
                    viewModel.refreshPermissions()
                    if (LvngShizukuManager.isReady(context)) {
                        showShizukuGateDialog = false
                    }
                },
                onDismiss = { showShizukuGateDialog = false }
            )
        }
    }
}
