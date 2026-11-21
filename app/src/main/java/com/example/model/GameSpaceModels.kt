package com.example.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.example.ui.theme.LvngAmber
import com.example.ui.theme.LvngCrimson
import com.example.ui.theme.LvngCyan
import com.example.ui.theme.LvngEmerald

enum class AppLanguage(val code: String, val displayName: String) {
    ID("ID", "Indonesia"),
    EN("EN", "English");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.find { it.code == code } ?: ID
    }
}

enum class PermissionStep {
    OVERLAY,
    WRITE_SETTINGS,
    NOTIFICATIONS,
    COMPLETE
}

enum class ActivationMethod(val id: String, val label: String) {
    NONE("NONE", "Belum Aktivasi"),
    SHIZUKU("SHIZUKU", "Shizuku Aktif"),
    WIRELESS_ADB("WIRELESS_ADB", "Wireless Debugging Aktif");

    companion object {
        fun fromId(id: String?): ActivationMethod =
            entries.find { it.id == id } ?: NONE
    }
}

enum class PerformanceMode(
    val id: String,
    val label: String,
    val subtitle: String,
    val touchPollHz: Int,
    val targetFpsLabel: String,
    val accentColor: Color
) {
    ECO("ECO", "ECO", "Hemat Baterai", 120, "60 FPS", LvngEmerald),
    BALANCED("BALANCED", "SEIMBANG", "Harian Stabil", 240, "90 FPS", LvngCyan),
    TURBO("TURBO", "TURBO", "Performa Tinggi", 360, "120 FPS", LvngAmber),
    OVERDRIVE("OVERDRIVE", "LVNG -X", "Overdrive Maksimal", 480, "MAX HZ", LvngCrimson);

    companion object {
        fun fromId(id: String?): PerformanceMode =
            entries.find { it.id == id } ?: OVERDRIVE
    }
}

enum class TouchCurve(
    val id: String,
    val title: String,
    val description: String
) {
    LINEAR("LINEAR", "Linear 1:1", "Input sentuhan mentah tanpa akselerasi"),
    PRO_FLICK("PRO_FLICK", "Pro Flick", "Respon cepat untuk FPS & aim"),
    SNIPER_PRECISION("SNIPER_PRECISION", "Presisi Mikro", "Redam getaran saat bidik jarak jauh"),
    MOBA_RAPID("MOBA_RAPID", "MOBA Rapid", "Arah skill instan & responsif");

    companion object {
        fun fromId(id: String?): TouchCurve =
            entries.find { it.id == id } ?: PRO_FLICK
    }
}

enum class CrosshairStyle(
    val id: String,
    val label: String
) {
    OFF("OFF", "Mati"),
    TACTICAL_CROSS("TACTICAL_CROSS", "Cross"),
    PRECISION_DOT("PRECISION_DOT", "Dot"),
    CRIMSON_RING("CRIMSON_RING", "Ring");

    companion object {
        fun fromId(id: String?): CrosshairStyle =
            entries.find { it.id == id } ?: OFF
    }
}

enum class ResolutionScale(val id: String, val label: String, val percent: Int) {
    NATIVE("NATIVE", "100% Native", 100),
    BALANCED("BALANCED", "85% Balanced", 85),
    PERFORMANCE("PERFORMANCE", "70% Turbo", 70);

    companion object {
        fun fromId(id: String?): ResolutionScale =
            entries.find { it.id == id } ?: NATIVE
    }
}

enum class MacroTapMode(val id: String, val label: String) {
    TAP("TAP", "Tap"),
    REPEAT_TAP("REPEAT_TAP", "Repeat Tap"),
    DOUBLE_TAP("DOUBLE_TAP", "Double Tap"),
    RELEASE_TAP("RELEASE_TAP", "Release Tap");

    companion object {
        fun fromId(id: String?): MacroTapMode =
            entries.find { it.id == id } ?: REPEAT_TAP
    }
}

data class MacroTriggerNode(
    val id: String,
    val name: String,
    val xFraction: Float,
    val yFraction: Float
)

data class MacroTargetNode(
    val id: String,
    val name: String,
    val xFraction: Float,
    val yFraction: Float,
    val tapMode: MacroTapMode = MacroTapMode.REPEAT_TAP,
    val intervalMs: Int = 70,
    val durationMs: Int = 70
)

data class MacroConfig(
    val triggers: List<MacroTriggerNode> = emptyList(),
    val targets: List<MacroTargetNode> = emptyList(),
    val isMacroActive: Boolean = false
)

data class KeymapNode(
    val id: String,
    val label: String,
    val xFraction: Float,
    val yFraction: Float
)

data class TouchConfig(
    val engineEnabled: Boolean = true,
    val samplingRateHz: Int = 480,
    val sensitivityMultiplier: Float = 1.65f,
    val swipeAcceleration: Float = 0.58f,
    val microAimSmoothing: Float = 0.25f,
    val edgeDeadzoneDp: Float = 8f,
    val touchCurve: TouchCurve = TouchCurve.PRO_FLICK
)

data class GameProfile(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val isDetectedGame: Boolean,
    val iconBitmap: ImageBitmap? = null,
    val performanceMode: PerformanceMode = PerformanceMode.OVERDRIVE,
    val touchSensitivity: Float = 1.75f,
    val autoDndOnLaunch: Boolean = true,
    val crosshairStyle: CrosshairStyle = CrosshairStyle.OFF,
    val keymapNodes: List<KeymapNode> = defaultKeymapNodes(),
    val launchCount: Int = 0
)

fun defaultKeymapNodes(): List<KeymapNode> = listOf(
    KeymapNode("aim", "AIM", 0.72f, 0.44f),
    KeymapNode("fire", "FIRE", 0.84f, 0.66f),
    KeymapNode("jump", "JMP", 0.88f, 0.45f),
    KeymapNode("crouch", "CRH", 0.78f, 0.80f)
)

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val isDetectedGame: Boolean,
    val iconBitmap: ImageBitmap?
)

data class SystemTelemetry(
    val fps: Int = 60,
    val displayRefreshRateHz: Int = 60,
    val ramUsedMb: Long = 0L,
    val ramTotalMb: Long = 1L,
    val ramUsagePercent: Int = 0,
    val cpuUsagePercent: Int = 28,
    val batteryPercent: Int = 100,
    val batteryTempCelsius: Float = 32.0f,
    val cpuCores: Int = 8,
    val deviceModel: String = "Android Device",
    val networkType: String = "Wi-Fi",
    val pingMs: Int? = null
)

data class PermissionStatus(
    val hasOverlayPermission: Boolean = false,
    val hasWriteSettingsPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val hasDndPolicyAccess: Boolean = false,
    val isShizukuInstalled: Boolean = false,
    val isShizukuRunning: Boolean = false,
    val isShizukuPermissionGranted: Boolean = false
) {
    val isShizukuReady: Boolean
        get() = isShizukuRunning && isShizukuPermissionGranted

    val nextRequiredStep: PermissionStep
        get() = when {
            !hasOverlayPermission -> PermissionStep.OVERLAY
            !hasWriteSettingsPermission -> PermissionStep.WRITE_SETTINGS
            !hasNotificationPermission -> PermissionStep.NOTIFICATIONS
            else -> PermissionStep.COMPLETE
        }
}

enum class AccountDurationOption(
    val id: String,
    val labelId: String,
    val labelEn: String,
    val days: Long
) {
    LIFETIME("LIFETIME", "Lifetime", "Lifetime", -1L),
    DAYS_1("DAYS_1", "1 Hari", "1 Day", 1L),
    DAYS_7("DAYS_7", "7 Hari", "7 Days", 7L),
    DAYS_30("DAYS_30", "30 Hari", "30 Days", 30L),
    CUSTOM("CUSTOM", "Custom", "Custom", 0L);

    companion object {
        fun fromId(id: String?): AccountDurationOption =
            entries.firstOrNull { it.id == id } ?: LIFETIME
    }
}

@androidx.annotation.Keep
@com.google.firebase.firestore.IgnoreExtraProperties
data class LicenseAccount(
    @com.google.firebase.firestore.DocumentId val id: String = "",
    val username: String = "",
    val passwordHash: String = "",
    val createdByUid: String = "",
    val durationType: String = AccountDurationOption.LIFETIME.id,
    val durationDays: Long = -1L,
    val expiresAtMillis: Long = -1L,
    val boundDeviceId: String = "",
    val boundDeviceName: String = "",
    val boundByUid: String = "",
    @com.google.firebase.firestore.ServerTimestamp val createdAt: com.google.firebase.Timestamp? = null,
    @com.google.firebase.firestore.ServerTimestamp val updatedAt: com.google.firebase.Timestamp? = null
) {
    @get:com.google.firebase.firestore.Exclude
    val isLifetimeAccount: Boolean
        get() = durationType == AccountDurationOption.LIFETIME.id || expiresAtMillis == -1L

    @get:com.google.firebase.firestore.Exclude
    val hasDeviceLock: Boolean
        get() = boundDeviceId.isNotBlank()

    fun isExpiredNow(nowMillis: Long = System.currentTimeMillis()): Boolean =
        !isLifetimeAccount && expiresAtMillis > 0L && nowMillis >= expiresAtMillis
}

data class LvngSpaceUiState(
    val language: AppLanguage = AppLanguage.ID,
    val activeMode: PerformanceMode = PerformanceMode.OVERDRIVE,
    val touchConfig: TouchConfig = TouchConfig(),
    val addedGames: List<GameProfile> = emptyList(),
    val selectedGamePackage: String? = null,
    val installedApps: List<InstalledAppItem> = emptyList(),
    val telemetry: SystemTelemetry = SystemTelemetry(),
    val permissions: PermissionStatus = PermissionStatus(),
    val onboardingDismissed: Boolean = false,
    val activationMethod: ActivationMethod = ActivationMethod.NONE,
    val showTouchCoordinates: Boolean = false,
    val showTouchDots: Boolean = false,
    val isOrientationLocked: Boolean = false,
    val isMonitoringInfoVisible: Boolean = false,
    val isKeymapEditMode: Boolean = false,
    val resolutionScale: ResolutionScale = ResolutionScale.NATIVE,
    val activeCrosshair: CrosshairStyle = CrosshairStyle.OFF,
    val isOverlayServiceRunning: Boolean = false,
    val isInAppControlPreviewOpen: Boolean = false,
    val statusMessage: String? = null,
    val loggedInAccountId: String? = null,
    val loggedInUsername: String? = null,
    val loggedInExpiresAtMillis: Long = 0L,
    val localDeviceId: String = ""
)
