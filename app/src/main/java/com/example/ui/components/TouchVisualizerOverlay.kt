package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overlay.LvngRuntimeBus
import com.example.overlay.OverlayRuntimeState
import com.example.overlay.TouchPointerPoint
import com.example.ui.theme.LvngCrimson
import com.example.ui.theme.LvngCrimsonBright
import com.example.ui.theme.LvngCyan
import com.example.ui.theme.LvngTextPrimary
import com.example.ui.theme.LvngTextSecondary
import kotlin.math.roundToInt

fun Modifier.observePassiveTouches(enabled: Boolean): Modifier {
    if (!enabled) return this
    return pointerInput(enabled) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressedChanges = event.changes.filter { it.pressed }
                val pointers = pressedChanges.map { ch ->
                    val delta = ch.position - ch.previousPosition
                    TouchPointerPoint(
                        id = ch.id.value,
                        xPx = ch.position.x,
                        yPx = ch.position.y,
                        dx = delta.x,
                        dy = delta.y
                    )
                }

                if (pointers.isNotEmpty()) {
                    val primary = pointers.first()
                    LvngRuntimeBus.update { current ->
                        val nextTrail = (current.touchTrailPoints + (primary.xPx to primary.yPx))
                            .takeLast(28)
                        current.copy(
                            activeTouchPointers = pointers,
                            lastTouchPoint = primary,
                            touchTrailPoints = nextTrail
                        )
                    }
                } else {
                    LvngRuntimeBus.update { current ->
                        if (current.activeTouchPointers.isEmpty() && current.touchTrailPoints.isEmpty()) {
                            current
                        } else {
                            current.copy(
                                activeTouchPointers = emptyList(),
                                touchTrailPoints = emptyList()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LvngTouchTelemetryHudOverlay(
    state: OverlayRuntimeState,
    modifier: Modifier = Modifier
) {
    val showCoords = state.showCoordinates
    val showDots = state.showTouchDots
    if (!showCoords && !showDots) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenW = constraints.maxWidth
        val screenH = constraints.maxHeight
        val density = LocalDensity.current

        val activePointers = state.activeTouchPointers
        val primaryPointer = activePointers.firstOrNull() ?: state.lastTouchPoint
        val pulsingTarget = state.pulsingTargetId?.let { id ->
            state.macroConfig.targets.find { it.id == id }?.let { tgt ->
                Offset(tgt.xFraction * screenW, tgt.yFraction * screenH)
            }
        }

        // Full-screen Canvas for crosshair coordinate lines, pointer trails, and glowing touch dots
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)

            if (showCoords) {
                // Draw movement trail
                val trail = state.touchTrailPoints
                if (trail.size >= 2) {
                    for (i in 1 until trail.size) {
                        val p0 = trail[i - 1]
                        val p1 = trail[i]
                        drawLine(
                            color = LvngCyan.copy(alpha = (i.toFloat() / trail.size) * 0.85f),
                            start = Offset(p0.first, p0.second),
                            end = Offset(p1.first, p1.second),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Draw full-screen X / Y crosshair lines for active touch points
                activePointers.forEach { pt ->
                    drawLine(
                        color = LvngCrimsonBright.copy(alpha = 0.82f),
                        start = Offset(0f, pt.yPx),
                        end = Offset(size.width, pt.yPx),
                        strokeWidth = 1.4.dp.toPx(),
                        pathEffect = dashEffect
                    )
                    drawLine(
                        color = LvngCrimsonBright.copy(alpha = 0.82f),
                        start = Offset(pt.xPx, 0f),
                        end = Offset(pt.xPx, size.height),
                        strokeWidth = 1.4.dp.toPx(),
                        pathEffect = dashEffect
                    )
                    drawCircle(
                        color = LvngCyan,
                        radius = 4.dp.toPx(),
                        center = Offset(pt.xPx, pt.yPx)
                    )
                }

                // Also show coordinates of active macro nodes if macro is running
                if (state.macroConfig.isMacroActive) {
                    state.macroConfig.targets.forEach { tgt ->
                        val tx = tgt.xFraction * size.width
                        val ty = tgt.yFraction * size.height
                        drawCircle(
                            color = LvngCyan.copy(alpha = 0.7f),
                            radius = 6.dp.toPx(),
                            center = Offset(tx, ty),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
            }

            if (showDots) {
                // Draw glowing touch dot for each active finger
                activePointers.forEach { pt ->
                    val center = Offset(pt.xPx, pt.yPx)
                    drawCircle(
                        color = LvngCrimson.copy(alpha = 0.28f),
                        radius = 22.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = LvngCrimsonBright,
                        radius = 15.dp.toPx(),
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.92f),
                        radius = 8.dp.toPx(),
                        center = center
                    )
                }

                // Also show pulsing dot at macro auto-click target position
                pulsingTarget?.let { pos ->
                    drawCircle(
                        color = LvngCrimson.copy(alpha = 0.35f),
                        radius = 24.dp.toPx(),
                        center = pos
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.95f),
                        radius = 7.dp.toPx(),
                        center = pos
                    )
                }
            }
        }

        // Top Coordinate Readout Bar when "Tampilkan Koordinat" is active
        if (showCoords) {
            val xVal = primaryPointer?.xPx?.roundToInt() ?: 0
            val yVal = primaryPointer?.yPx?.roundToInt() ?: 0
            val dxVal = primaryPointer?.dx?.roundToInt() ?: 0
            val dyVal = primaryPointer?.dy?.roundToInt() ?: 0
            val pointerCount = activePointers.size

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 6.dp, start = 12.dp, end = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xDD10080A))
                    .border(1.dp, LvngCrimson.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "P:$pointerCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = LvngCrimsonBright,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "X: $xVal",
                    style = MaterialTheme.typography.labelSmall,
                    color = LvngTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Y: $yVal",
                    style = MaterialTheme.typography.labelSmall,
                    color = LvngTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "dX: $dxVal",
                    style = MaterialTheme.typography.labelSmall,
                    color = LvngCyan
                )
                Text(
                    text = "dY: $dyVal",
                    style = MaterialTheme.typography.labelSmall,
                    color = LvngCyan
                )
                Text(
                    text = "${screenW}×${screenH}",
                    style = MaterialTheme.typography.labelSmall,
                    color = LvngTextSecondary,
                    fontSize = 9.sp
                )
            }

            // Floating coordinate label next to active finger
            activePointers.firstOrNull()?.let { pt ->
                val xOff = (pt.xPx + with(density) { 14.dp.toPx() }).roundToInt()
                    .coerceIn(8, (screenW - 180).coerceAtLeast(8))
                val yOff = (pt.yPx - with(density) { 36.dp.toPx() }).roundToInt()
                    .coerceIn(8, (screenH - 60).coerceAtLeast(8))
                Box(
                    modifier = Modifier
                        .offset { IntOffset(xOff, yOff) }
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xDC120709))
                        .border(1.dp, LvngCrimson.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${pt.xPx.roundToInt()}, ${pt.yPx.roundToInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Compact confirmation badge when only Touch Dots is enabled
        if (showDots && !showCoords) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 8.dp, end = 14.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xCC14080A))
                    .border(1.dp, LvngCrimson.copy(alpha = 0.65f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(LvngCrimsonBright)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Sentuhan Aktif",
                    style = MaterialTheme.typography.labelSmall,
                    color = LvngTextPrimary,
                    fontSize = 10.sp
                )
            }
        }
    }
}
