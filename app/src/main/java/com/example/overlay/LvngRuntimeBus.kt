package com.example.overlay

import com.example.model.CrosshairStyle
import com.example.model.KeymapNode
import com.example.model.MacroConfig
import com.example.model.PerformanceMode
import com.example.model.ResolutionScale
import com.example.model.SystemTelemetry
import com.example.model.defaultKeymapNodes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TouchPointerPoint(
    val id: Long,
    val xPx: Float,
    val yPx: Float,
    val dx: Float = 0f,
    val dy: Float = 0f
)

data class OverlayRuntimeState(
    val isServiceActive: Boolean = false,
    val isControlPanelExpanded: Boolean = false,
    val activeGameName: String = "LVNG ARENA",
    val activeGamePackage: String? = null,
    val activeMode: PerformanceMode = PerformanceMode.OVERDRIVE,
    val telemetry: SystemTelemetry = SystemTelemetry(),
    val touchSensitivity: Float = 1.65f,
    val crosshairStyle: CrosshairStyle = CrosshairStyle.OFF,
    val resolutionScale: ResolutionScale = ResolutionScale.NATIVE,
    val isOrientationLocked: Boolean = false,
    val isMonitoringInfoVisible: Boolean = false,
    val showCoordinates: Boolean = false,
    val showTouchDots: Boolean = false,
    val isKeymapEditMode: Boolean = false,
    val keymapNodes: List<KeymapNode> = defaultKeymapNodes(),
    val macroConfig: MacroConfig = MacroConfig(),
    val activeTriggerId: String? = null,
    val pulsingTargetId: String? = null,
    val macroTapCount: Int = 0,
    val activeTouchPointers: List<TouchPointerPoint> = emptyList(),
    val lastTouchPoint: TouchPointerPoint? = null,
    val touchTrailPoints: List<Pair<Float, Float>> = emptyList(),
    val boostTriggerNonce: Long = 0L,
    val isLaunchVideoPlaying: Boolean = false,
    val lastBoostFreedMb: Long = 0L,
    val lastBoostTrimmedCount: Int = 0
)

object LvngRuntimeBus {
    private val _state = MutableStateFlow(OverlayRuntimeState())
    val state: StateFlow<OverlayRuntimeState> = _state.asStateFlow()

    fun update(transform: (OverlayRuntimeState) -> OverlayRuntimeState) {
        _state.update(transform)
    }
}
