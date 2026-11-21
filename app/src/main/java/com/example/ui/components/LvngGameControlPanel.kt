package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.ScreenLockRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.CrosshairStyle
import com.example.model.PerformanceMode
import com.example.model.ResolutionScale
import com.example.overlay.OverlayRuntimeState
import com.example.ui.theme.LvngCrimson
import com.example.ui.theme.LvngCrimsonBright
import com.example.ui.theme.LvngEmerald
import com.example.ui.theme.LvngTextPrimary
import com.example.ui.theme.LvngTextSecondary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class HudSubMenu {
    NONE,
    GENERAL_SETTINGS,
    RESOLUTION_SCALE
}

@Composable
fun LvngFloatingLogoBubble(
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(42.dp)
            .shadow(10.dp, CircleShape, ambientColor = LvngCrimson, spotColor = LvngCrimson)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF450B0E), Color(0xFF0A0607))
                )
            )
            .border(1.5.dp, LvngCrimson, CircleShape)
            .padding(4.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.lvng_space_logo),
            contentDescription = "LVNG SPACE Floating Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        )
    }
}

@Composable
fun LvngEdgeDockTab(
    isRightEdge: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = if (isRightEdge) {
        RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)
    } else {
        RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(14.dp)
            .height(68.dp)
            .shadow(8.dp, shape, ambientColor = LvngCrimson, spotColor = LvngCrimson)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xF02E080C),
                        Color(0xF5120608),
                        Color(0xF02E080C)
                    )
                )
            )
            .border(1.dp, LvngCrimson.copy(alpha = 0.9f), shape)
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(44.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            LvngCrimson.copy(alpha = 0.55f),
                            LvngCrimsonBright,
                            LvngCrimson.copy(alpha = 0.55f)
                        )
                    )
                )
        )
    }
}

@Composable
private fun rememberWibClockText(): String {
    var timeText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val wibZone = java.util.TimeZone.getTimeZone("Asia/Jakarta")
        val formatter = java.text.SimpleDateFormat("hh : mm a", java.util.Locale.US).apply {
            timeZone = wibZone
        }
        while (true) {
            val nowMs = System.currentTimeMillis()
            timeText = formatter.format(java.util.Date(nowMs)).uppercase(java.util.Locale.US)
            val delayToNextSecond = 1000L - (nowMs % 1000L)
            delay(delayToNextSecond.coerceIn(200L, 1000L))
        }
    }
    return timeText.ifEmpty {
        val wibZone = java.util.TimeZone.getTimeZone("Asia/Jakarta")
        java.text.SimpleDateFormat("hh : mm a", java.util.Locale.US).apply {
            timeZone = wibZone
        }.format(java.util.Date()).uppercase(java.util.Locale.US)
    }
}

@Composable
fun LvngGameControlPanel(
    state: OverlayRuntimeState,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    onOpenMacroEditor: () -> Unit,
    onToggleMonitoringInfo: () -> Unit,
    onCycleCrosshair: () -> Unit,
    onSelectPerformanceMode: (PerformanceMode) -> Unit,
    onBoostRam: () -> Unit,
    onBoostPerformance: () -> Unit,
    onSelectResolutionScale: (ResolutionScale) -> Unit,
    onToggleOrientationLock: () -> Unit,
    onToggleCoordinates: () -> Unit,
    onToggleTouchDots: () -> Unit,
    onReturnToApp: () -> Unit,
    onShutdownOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSubMenu by remember { mutableStateOf(HudSubMenu.NONE) }
    val wibClock = rememberWibClockText()

    BoxWithConstraints(
        modifier = modifier
            .widthIn(max = 540.dp)
            .fillMaxWidth()
            .heightIn(max = 236.dp)
    ) {
        val isCompact = maxWidth < 430.dp
        val clockRowHeight = if (isCompact) 16.dp else 18.dp

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Live WIB (UTC+7 / Asia/Jakarta) digital clock positioned cleanly above the top center notch
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(clockRowHeight)
                    .padding(bottom = 1.dp)
            ) {
                Text(
                    text = wibClock,
                    fontFamily = com.example.ui.theme.JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (isCompact) 11.sp else 12.5.sp,
                    letterSpacing = if (isCompact) 0.8.sp else 1.1.sp,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.85f),
                            offset = Offset(0f, 1.5f),
                            blurRadius = 4f
                        )
                    ),
                    modifier = Modifier.testTag("hud_wib_clock")
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 216.dp)
            ) {
                // Custom notched sci-fi polygon frame: black top fading down to 75% transparent (alpha = 0.25f)
                Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height
                    val c = if (isCompact) 11.dp.toPx() else 14.dp.toPx()
                    val topNotchStart = w * 0.29f
                    val topNotchEnd = w * 0.71f
                    val topSlope = if (isCompact) 9.dp.toPx() else 12.dp.toPx()
                    val topDip = if (isCompact) 8.dp.toPx() else 11.dp.toPx()

                    val botNotchStart = w * 0.27f
                    val botNotchEnd = w * 0.73f
                    val botSlope = if (isCompact) 8.dp.toPx() else 10.dp.toPx()
                    val botLift = if (isCompact) 4.dp.toPx() else 6.dp.toPx()

                    val framePath = Path().apply {
                        moveTo(c, 0f)
                        lineTo(topNotchStart - topSlope, 0f)
                        lineTo(topNotchStart, topDip)
                        lineTo(topNotchEnd, topDip)
                        lineTo(topNotchEnd + topSlope, 0f)
                        lineTo(w - c, 0f)
                        lineTo(w, c)
                        lineTo(w, h - c)
                        lineTo(w - c, h)
                        lineTo(botNotchEnd + botSlope, h)
                        lineTo(botNotchEnd, h - botLift)
                        lineTo(botNotchStart, h - botLift)
                        lineTo(botNotchStart - botSlope, h)
                        lineTo(c, h)
                        lineTo(0f, h - c)
                        lineTo(0f, c)
                        close()
                    }

                    // Outer crimson glow aura
                    drawPath(
                        path = framePath,
                        color = LvngCrimson.copy(alpha = 0.26f),
                        style = Stroke(width = 6.dp.toPx())
                    )

                    // Vertical black-to-75%-transparent fill (top ~98% opaque black -> bottom 25% opaque / 75% transparent)
                    drawPath(
                        path = framePath,
                        brush = Brush.verticalGradient(
                            0.00f to Color(0xFA030304),
                            0.32f to Color(0xD9030304),
                            0.68f to Color(0x80030304),
                            1.00f to Color(0x40030304)
                        )
                    )

                    // Subtle dark inner rim
                    drawPath(
                        path = framePath,
                        color = Color(0x885C0508),
                        style = Stroke(width = 4.dp.toPx())
                    )

                    // Crisp neon crimson outer border
                    drawPath(
                        path = framePath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                LvngCrimsonBright,
                                Color(0xFFD91418),
                                Color(0xFFE01B1F)
                            )
                        ),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Left & right metallic top shoulder highlight bars
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0x66FF7070), Color(0xEEFF4D4D), Color(0x66FF7070)),
                            startX = c,
                            endX = topNotchStart - topSlope
                        ),
                        start = Offset(c, 1.5.dp.toPx()),
                        end = Offset(topNotchStart - topSlope, 1.5.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0x66FF7070), Color(0xEEFF4D4D), Color(0x66FF7070)),
                            startX = topNotchEnd + topSlope,
                            endX = w - c
                        ),
                        start = Offset(topNotchEnd + topSlope, 1.5.dp.toPx()),
                        end = Offset(w - c, 1.5.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = if (isCompact) 9.dp else 14.dp,
                            vertical = if (isCompact) 5.dp else 7.dp
                        )
                ) {
                    // Top Header Row: LVNG -X GAME CONTROL + Minimize & Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.lvng_space_logo),
                                contentDescription = "LVNG -X",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(if (isCompact) 20.dp else 23.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, LvngCrimson, RoundedCornerShape(6.dp))
                            )
                            Spacer(modifier = Modifier.width(if (isCompact) 5.dp else 7.dp))
                            Column {
                                Text(
                                    text = "LVNG -X",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontSize = if (isCompact) 11.sp else 12.5.sp,
                                    lineHeight = if (isCompact) 12.sp else 14.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "GAME CONTROL",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = if (isCompact) 7.sp else 8.sp,
                                    lineHeight = if (isCompact) 8.sp else 9.sp,
                                    color = LvngCrimsonBright,
                                    letterSpacing = if (isCompact) 0.5.sp else 0.8.sp
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onMinimize,
                                modifier = Modifier
                                    .size(if (isCompact) 24.dp else 28.dp)
                                    .testTag("hud_minimize_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Remove,
                                    contentDescription = "Minimize",
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isCompact) 14.dp else 16.dp)
                                )
                            }
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier
                                    .size(if (isCompact) 24.dp else 28.dp)
                                    .testTag("hud_close_panel_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Tutup",
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isCompact) 14.dp else 16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isCompact) 2.dp else 4.dp))

                    // Horizontal 3-zone Control Deck: Left 6 circles | Center RAM-FPS-CPU | Right 5 circles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 3.dp else 6.dp)
                    ) {
                        LeftActionGrid(
                            state = state,
                            activeSubMenu = activeSubMenu,
                            isCompact = isCompact,
                            onOpenMacroEditor = onOpenMacroEditor,
                            onToggleGeneral = {
                                activeSubMenu = if (activeSubMenu == HudSubMenu.GENERAL_SETTINGS) HudSubMenu.NONE else HudSubMenu.GENERAL_SETTINGS
                            },
                            onToggleMonitoringInfo = onToggleMonitoringInfo,
                            onCycleCrosshair = onCycleCrosshair,
                            onBoostPerformance = onBoostPerformance,
                            onToggleResolution = {
                                activeSubMenu = if (activeSubMenu == HudSubMenu.RESOLUTION_SCALE) HudSubMenu.NONE else HudSubMenu.RESOLUTION_SCALE
                            },
                            modifier = Modifier.weight(1f)
                        )

                        CenterTelemetryArc(
                            ramPercent = state.telemetry.ramUsagePercent,
                            fps = state.telemetry.fps,
                            cpuPercent = state.telemetry.cpuUsagePercent,
                            isCompact = isCompact,
                            modifier = Modifier.width(if (isCompact) 98.dp else 150.dp)
                        )

                        RightSystemGrid(
                            state = state,
                            isCompact = isCompact,
                            onToggleOrientationLock = onToggleOrientationLock,
                            onToggleCoordinates = onToggleCoordinates,
                            onToggleTouchDots = onToggleTouchDots,
                            onReturnToApp = onReturnToApp,
                            onShutdownOverlay = onShutdownOverlay,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Expandable Inline Sub-Menu
                    AnimatedVisibility(visible = activeSubMenu != HudSubMenu.NONE) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xD90A090B))
                                .border(1.dp, LvngCrimson.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            when (activeSubMenu) {

                                HudSubMenu.RESOLUTION_SCALE -> {
                                    Text(
                                        text = "Skala Resolusi",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = LvngTextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ResolutionScale.entries.forEach { scale ->
                                            val selected = state.resolutionScale == scale
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(42.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (selected) LvngCrimson.copy(alpha = 0.25f) else Color(0xFF141216))
                                                    .border(
                                                        1.dp,
                                                        if (selected) LvngCrimson else Color(0xFF2A2428),
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable { onSelectResolutionScale(scale) }
                                            ) {
                                                Text(
                                                    text = scale.label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (selected) LvngTextPrimary else LvngTextSecondary
                                                )
                                            }
                                        }
                                    }
                                }

                                HudSubMenu.GENERAL_SETTINGS -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Mode Performa",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = LvngTextPrimary
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(LvngCrimson)
                                                .clickable(onClick = onBoostRam)
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "Bersihkan RAM",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        PerformanceMode.entries.forEach { mode ->
                                            val active = state.activeMode == mode
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(40.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (active) mode.accentColor.copy(alpha = 0.22f) else Color(0xFF141216))
                                                    .border(
                                                        1.dp,
                                                        if (active) mode.accentColor else Color(0xFF2A2428),
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable { onSelectPerformanceMode(mode) }
                                            ) {
                                                Text(
                                                    text = mode.label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (active) mode.accentColor else LvngTextSecondary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                HudSubMenu.NONE -> Unit
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isCompact) 2.dp else 4.dp))
                }
            }
        }
    }
}

@Composable
private fun CenterTelemetryArc(
    ramPercent: Int,
    fps: Int,
    cpuPercent: Int,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.height(if (isCompact) 70.dp else 84.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val arcDiameter = size.height * 0.88f
            val arcSize = Size(arcDiameter, arcDiameter)
            val centerX = size.width / 2f
            val topY = (size.height - arcDiameter) / 2f
            val arcOffset = if (isCompact) 16.dp.toPx() else 25.dp.toPx()
            val arcStroke = if (isCompact) 3.8.dp.toPx() else 5.dp.toPx()

            val segments = 10
            val ramLit = ((ramPercent / 100f) * segments).roundToInt().coerceIn(1, segments)
            val cpuLit = ((cpuPercent / 100f) * segments).roundToInt().coerceIn(1, segments)
            val litRed = Color(0xFFE52222)
            val unlitRed = Color(0xFF4D1A1C)

            // Left curved arc segments (RAM, rising bottom -> top)
            for (i in 0 until segments) {
                val startAngle = 224f - (i * 10.2f)
                val lit = i < ramLit
                drawArc(
                    color = if (lit) litRed else unlitRed,
                    startAngle = startAngle,
                    sweepAngle = 6.8f,
                    useCenter = false,
                    topLeft = Offset(centerX - arcDiameter / 2f - arcOffset, topY),
                    size = arcSize,
                    style = Stroke(width = arcStroke, cap = StrokeCap.Butt)
                )
            }

            // Right curved arc segments (CPU, rising bottom -> top)
            for (i in 0 until segments) {
                val startAngle = 44f - (i * 10.2f)
                val lit = i < cpuLit
                drawArc(
                    color = if (lit) litRed else unlitRed,
                    startAngle = startAngle,
                    sweepAngle = 6.8f,
                    useCenter = false,
                    topLeft = Offset(centerX - arcDiameter / 2f + arcOffset, topY),
                    size = arcSize,
                    style = Stroke(width = arcStroke, cap = StrokeCap.Butt)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = if (isCompact) 1.dp else 4.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$ramPercent%",
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = if (isCompact) 9.sp else 11.sp,
                    color = Color(0xFFE83A3A),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "RAM",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = if (isCompact) 6.5.sp else 7.5.sp,
                    color = Color(0xFFE2E6EC)
                )
            }

            Spacer(modifier = Modifier.width(if (isCompact) 3.dp else 6.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$fps",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isCompact) 16.sp else 21.sp
                )
                Text(
                    text = "FPS",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = if (isCompact) 6.5.sp else 7.5.sp,
                    color = Color(0xFFE2E6EC)
                )
            }

            Spacer(modifier = Modifier.width(if (isCompact) 3.dp else 6.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$cpuPercent%",
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = if (isCompact) 9.sp else 11.sp,
                    color = Color(0xFFE83A3A),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "CPU",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = if (isCompact) 6.5.sp else 7.5.sp,
                    color = Color(0xFFE2E6EC)
                )
            }
        }
    }
}

@Composable
private fun LeftActionGrid(
    state: OverlayRuntimeState,
    activeSubMenu: HudSubMenu,
    isCompact: Boolean,
    onOpenMacroEditor: () -> Unit,
    onToggleGeneral: () -> Unit,
    onToggleMonitoringInfo: () -> Unit,
    onCycleCrosshair: () -> Unit,
    onBoostPerformance: () -> Unit,
    onToggleResolution: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gap = if (isCompact) 1.5.dp else 3.dp
    Column(verticalArrangement = Arrangement.spacedBy(if (isCompact) 3.dp else 4.dp), modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            HudIconTile(
                icon = Icons.Filled.TouchApp,
                label = "Edit Keymap",
                active = state.isKeymapEditMode || state.macroConfig.isMacroActive,
                isCompact = isCompact,
                onClick = onOpenMacroEditor,
                modifier = Modifier
                    .weight(1f)
                    .testTag("hud_edit_keymap")
            )
            HudIconTile(
                icon = Icons.Filled.Settings,
                label = "Pengaturan Umum",
                active = activeSubMenu == HudSubMenu.GENERAL_SETTINGS,
                isCompact = isCompact,
                onClick = onToggleGeneral,
                modifier = Modifier.weight(1f)
            )
            HudIconTile(
                icon = Icons.Filled.Visibility,
                label = "Monitoring Info",
                active = state.isMonitoringInfoVisible,
                isCompact = isCompact,
                onClick = onToggleMonitoringInfo,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            HudIconTile(
                icon = Icons.Filled.CenterFocusStrong,
                label = "Crosshair Assistant",
                active = state.crosshairStyle != CrosshairStyle.OFF,
                isCompact = isCompact,
                onClick = onCycleCrosshair,
                modifier = Modifier.weight(1f)
            )
            HudIconTile(
                icon = Icons.Filled.RocketLaunch,
                label = "Boost Performance",
                active = false,
                isCompact = isCompact,
                onClick = onBoostPerformance,
                modifier = Modifier
                    .weight(1f)
                    .testTag("hud_boost_performance")
            )
            HudIconTile(
                icon = Icons.Filled.AspectRatio,
                label = "Skala Resolusi",
                active = activeSubMenu == HudSubMenu.RESOLUTION_SCALE || state.resolutionScale != ResolutionScale.NATIVE,
                isCompact = isCompact,
                onClick = onToggleResolution,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RightSystemGrid(
    state: OverlayRuntimeState,
    isCompact: Boolean,
    onToggleOrientationLock: () -> Unit,
    onToggleCoordinates: () -> Unit,
    onToggleTouchDots: () -> Unit,
    onReturnToApp: () -> Unit,
    onShutdownOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gap = if (isCompact) 1.5.dp else 3.dp
    Column(verticalArrangement = Arrangement.spacedBy(if (isCompact) 3.dp else 4.dp), modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            HudIconTile(
                icon = Icons.Filled.ScreenLockRotation,
                label = "Kunci Orientasi",
                active = state.isOrientationLocked,
                isCompact = isCompact,
                onClick = onToggleOrientationLock,
                modifier = Modifier.weight(1f)
            )
            HudIconTile(
                icon = Icons.Outlined.GpsFixed,
                label = "Tampilkan Koordinat",
                active = state.showCoordinates,
                isCompact = isCompact,
                onClick = onToggleCoordinates,
                modifier = Modifier.weight(1f)
            )
            HudIconTile(
                icon = Icons.Outlined.RadioButtonChecked,
                label = "Tampilkan Sentuhan",
                active = state.showTouchDots,
                isCompact = isCompact,
                onClick = onToggleTouchDots,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            Spacer(modifier = Modifier.weight(0.38f))
            HudIconTile(
                icon = Icons.AutoMirrored.Filled.Undo,
                label = "Kembali ke Aplikasi",
                active = false,
                isCompact = isCompact,
                onClick = onReturnToApp,
                modifier = Modifier.weight(1f)
            )
            HudIconTile(
                icon = Icons.Filled.PowerSettingsNew,
                label = "Matikan Aplikasi",
                active = false,
                isDanger = true,
                isCompact = isCompact,
                onClick = onShutdownOverlay,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.weight(0.38f))
        }
    }
}

@Composable
private fun HudIconTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    isDanger: Boolean = false,
    isCompact: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ringColor = when {
        active -> LvngCrimsonBright
        isDanger -> Color(0xFFD6DCE2)
        else -> Color(0xFFD6DCE2)
    }
    val circleFill = when {
        active -> Color(0x52B71C1C)
        else -> Color(0x660B0C0E)
    }
    val iconTint = when {
        active -> Color(0xFFFF5252)
        else -> Color.White
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 1.dp, horizontal = 1.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(if (isCompact) 22.dp else 27.dp)
                .clip(CircleShape)
                .background(circleFill)
                .border(if (active) 1.4.dp else 1.dp, ringColor, CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(if (isCompact) 11.5.dp else 13.5.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = if (isCompact) 6.8.sp else 8.3.sp,
            lineHeight = if (isCompact) 8.sp else 9.8.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun LvngMonitoringPill(
    state: OverlayRuntimeState,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xDC0D090B))
            .border(1.dp, LvngCrimson.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.DragIndicator,
            contentDescription = "Geser Monitoring Info",
            tint = LvngTextSecondary.copy(alpha = 0.75f),
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = "${state.telemetry.fps} FPS",
            style = MaterialTheme.typography.labelMedium,
            color = LvngEmerald,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "RAM ${state.telemetry.ramUsagePercent}%",
            style = MaterialTheme.typography.labelSmall,
            color = LvngTextPrimary
        )
        Text(
            text = "CPU ${state.telemetry.cpuUsagePercent}%",
            style = MaterialTheme.typography.labelSmall,
            color = LvngCrimsonBright
        )
        Text(
            text = "${String.format("%.1f", state.telemetry.batteryTempCelsius)}°C",
            style = MaterialTheme.typography.labelSmall,
            color = LvngTextSecondary
        )
    }
}

@Composable
fun LvngCrosshairReticle(
    style: CrosshairStyle,
    modifier: Modifier = Modifier
) {
    if (style == CrosshairStyle.OFF) return
    Canvas(modifier = modifier.size(48.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        when (style) {
            CrosshairStyle.PRECISION_DOT -> {
                drawCircle(LvngCrimson, radius = 4.dp.toPx(), center = Offset(cx, cy))
                drawCircle(Color.White, radius = 1.5.dp.toPx(), center = Offset(cx, cy))
            }
            CrosshairStyle.TACTICAL_CROSS -> {
                val gap = 5.dp.toPx()
                val arm = 12.dp.toPx()
                val stroke = 2.dp.toPx()
                drawLine(LvngCrimson, Offset(cx - gap - arm, cy), Offset(cx - gap, cy), stroke)
                drawLine(LvngCrimson, Offset(cx + gap, cy), Offset(cx + gap + arm, cy), stroke)
                drawLine(LvngCrimson, Offset(cx, cy - gap - arm), Offset(cx, cy - gap), stroke)
                drawLine(LvngCrimson, Offset(cx, cy + gap), Offset(cx, cy + gap + arm), stroke)
                drawCircle(Color.White, radius = 1.5.dp.toPx(), center = Offset(cx, cy))
            }
            CrosshairStyle.CRIMSON_RING -> {
                drawCircle(
                    color = LvngCrimson,
                    radius = 14.dp.toPx(),
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(LvngCrimson, radius = 2.5.dp.toPx(), center = Offset(cx, cy))
            }
            CrosshairStyle.OFF -> Unit
        }
    }
}

/**
 * Bottom-center animated Boost Performance pill matching the red metallic pill design:
 * Darker crimson left rocket pocket + dark rocket icon + metallic crimson body + bold italic BOOSTING -> BOOSTER ✔️
 * Fades out smoothly via opacity after completion.
 */
@Composable
fun LvngBoostPerformanceBanner(
    triggerNonce: Long,
    freedMb: Long = 0L,
    modifier: Modifier = Modifier
) {
    var seenNonce by remember { mutableLongStateOf(triggerNonce) }
    var isVisible by remember { mutableStateOf(false) }
    var isCompleted by remember { mutableStateOf(false) }
    val containerAlpha = remember { Animatable(0f) }
    val containerScale = remember { Animatable(0.86f) }

    LaunchedEffect(triggerNonce) {
        if (triggerNonce > seenNonce) {
            seenNonce = triggerNonce
            isVisible = true
            isCompleted = false
            containerAlpha.snapTo(0f)
            containerScale.snapTo(0.86f)
            containerAlpha.animateTo(1f, tween(durationMillis = 220, easing = FastOutSlowInEasing))
            containerScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            delay(1800L)
            isCompleted = true
            delay(1250L)
            containerAlpha.animateTo(0f, tween(durationMillis = 550, easing = FastOutSlowInEasing))
            isVisible = false
        }
    }

    if (!isVisible) return

    val infiniteTransition = rememberInfiniteTransition(label = "boost_rocket_motion")
    val rocketJitterX by infiniteTransition.animateFloat(
        initialValue = -1.8f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 120, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rocket_jitter_x"
    )
    val rocketJitterY by infiniteTransition.animateFloat(
        initialValue = -1.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 95, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rocket_jitter_y"
    )
    val sweepFraction by infiniteTransition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pill_sweep"
    )

    val launchLift = remember { Animatable(0f) }
    LaunchedEffect(isCompleted) {
        if (isCompleted) {
            launchLift.snapTo(0f)
            launchLift.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        } else {
            launchLift.snapTo(0f)
        }
    }

    val pillShape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .alpha(containerAlpha.value)
            .scale(containerScale.value)
            .width(238.dp)
            .height(48.dp)
            .shadow(14.dp, pillShape, ambientColor = LvngCrimson, spotColor = LvngCrimson)
            .clip(pillShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF9C0610),
                        Color(0xFFDB1422),
                        Color(0xFF8B050E)
                    )
                )
            )
            .border(1.8.dp, Color(0xFF140305), pillShape)
            .testTag("boost_performance_banner")
    ) {
        // Animated light sheen sweeping across the crimson pill while boosting
        if (!isCompleted) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val beamWidth = size.width * 0.28f
                val startX = sweepFraction * size.width - beamWidth / 2f
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        startX = startX,
                        endX = startX + beamWidth
                    )
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
        ) {
            // Darker crimson left pocket with animated dark rocket
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(52.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = 20.dp, bottomEnd = 20.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF6B030A),
                                Color(0xFF470206)
                            )
                        )
                    )
            ) {
                // Flame glow behind rocket when boosting
                if (!isCompleted) {
                    Box(
                        modifier = Modifier
                            .offset(x = (-5).dp, y = 5.dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0x99FF6B35))
                    )
                }

                Icon(
                    imageVector = Icons.Filled.RocketLaunch,
                    contentDescription = "Rocket Boost",
                    tint = Color(0xFF110305),
                    modifier = Modifier
                        .size(25.dp)
                        .offset(
                            x = if (isCompleted) (2.dp * launchLift.value) else rocketJitterX.dp,
                            y = if (isCompleted) (-3.dp * launchLift.value) else rocketJitterY.dp
                        )
                        .rotate(if (isCompleted) -8f else 0f)
                        .scale(if (isCompleted) 1.08f else 1f)
                )
            }

            // Center label: BOOSTING -> BOOSTER ✔️
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 10.dp)
            ) {
                AnimatedContent(
                    targetState = isCompleted,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220)))
                            .togetherWith(fadeOut(tween(150)))
                    },
                    label = "boost_status_text"
                ) { done ->
                    val doneLabel = if (freedMb > 0L) "BOOSTER +${freedMb}MB" else "BOOSTER ✔️"
                    Text(
                        text = if (done) doneLabel else "BOOSTING",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontStyle = FontStyle.Italic,
                        fontSize = 15.sp,
                        letterSpacing = 0.8.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
