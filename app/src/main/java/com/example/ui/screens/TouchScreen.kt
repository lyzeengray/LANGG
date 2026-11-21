package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ActivationMethod
import com.example.model.AppLanguage
import com.example.model.LvngSpaceUiState
import com.example.model.TouchConfig
import com.example.model.TouchCurve
import com.example.ui.theme.LvngBorderSubtle
import com.example.ui.theme.LvngCarbon
import com.example.ui.theme.LvngCrimson
import com.example.ui.theme.LvngEmerald
import com.example.ui.theme.LvngTextPrimary
import com.example.ui.theme.LvngTextSecondary
import kotlin.math.roundToInt

@Composable
fun TouchScreen(
    uiState: LvngSpaceUiState,
    onOpenActivation: () -> Unit,
    onOpenExtraSettings: () -> Unit,
    onUpdateTouchConfig: ((TouchConfig) -> TouchConfig) -> Unit,
    onApplyCurve: (TouchCurve) -> Unit,
    modifier: Modifier = Modifier
) {
    val isId = uiState.language == AppLanguage.ID
    val isActivated = uiState.permissions.isShizukuReady
    val touchConfig = uiState.touchConfig

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxSize()
    ) {
        // 1. Status Aktivasi Shizuku
        item {
            ActivationStatusBanner(
                isActivated = isActivated,
                activationLabel = when {
                    uiState.permissions.isShizukuReady -> if (isId) "Shizuku aktif & diizinin" else "Shizuku active"
                    uiState.permissions.isShizukuRunning -> if (isId) "Izinin LVNG SPACE di Shizuku" else "Allow in Shizuku"
                    else -> if (isId) "Shizuku belum aktif" else "Shizuku not active"
                },
                isId = isId,
                onClick = onOpenActivation,
                modifier = Modifier.testTag("touch_activation_banner")
            )
        }

        // 2. Clean Touch Engine Controls Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(LvngCarbon)
                    .border(1.dp, LvngBorderSubtle, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isId) "Mesin Sentuhan LVNG" else "LVNG Touch Engine",
                            style = MaterialTheme.typography.titleMedium,
                            color = LvngTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${touchConfig.samplingRateHz}Hz • ${touchConfig.touchCurve.title}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (touchConfig.engineEnabled) LvngEmerald else LvngTextSecondary
                        )
                    }
                    Switch(
                        checked = touchConfig.engineEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateTouchConfig { it.copy(engineEnabled = enabled) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = LvngCrimson
                        ),
                        modifier = Modifier.testTag("touch_engine_switch")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Sampling Rate Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(120, 240, 360, 480).forEach { hz ->
                        val active = touchConfig.samplingRateHz == hz
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) LvngCrimson.copy(alpha = 0.2f) else Color(0xFF0E1012))
                                .border(
                                    1.dp,
                                    if (active) LvngCrimson else LvngBorderSubtle,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onUpdateTouchConfig { it.copy(samplingRateHz = hz) } }
                                .testTag("touch_hz_$hz")
                        ) {
                            Text(
                                text = "${hz}Hz",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) LvngCrimson else LvngTextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isId) "Sensitivitas Sentuhan" else "Touch Sensitivity",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LvngTextPrimary
                    )
                    Text(
                        text = "${String.format("%.2f", touchConfig.sensitivityMultiplier)}x",
                        style = MaterialTheme.typography.labelMedium,
                        color = LvngCrimson,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = touchConfig.sensitivityMultiplier,
                    onValueChange = { v -> onUpdateTouchConfig { it.copy(sensitivityMultiplier = v) } },
                    valueRange = 1.0f..2.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = LvngCrimson,
                        activeTrackColor = LvngCrimson,
                        inactiveTrackColor = Color(0xFF0E1012)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isId) "Batas Anti-Sentuh Tepi" else "Edge Deadzone",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LvngTextPrimary
                    )
                    Text(
                        text = "${touchConfig.edgeDeadzoneDp.roundToInt()} dp",
                        style = MaterialTheme.typography.labelMedium,
                        color = LvngTextSecondary
                    )
                }
                Slider(
                    value = touchConfig.edgeDeadzoneDp,
                    onValueChange = { v -> onUpdateTouchConfig { it.copy(edgeDeadzoneDp = v) } },
                    valueRange = 0f..28f,
                    colors = SliderDefaults.colors(
                        thumbColor = LvngCrimson,
                        activeTrackColor = LvngCrimson,
                        inactiveTrackColor = Color(0xFF0E1012)
                    )
                )
            }
        }

        // 3. Kurva Sentuhan & Pengaturan Tambahan shortcut
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(LvngCarbon)
                    .border(1.dp, LvngBorderSubtle, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = if (isId) "Kurva Respon" else "Response Curve",
                    style = MaterialTheme.typography.labelLarge,
                    color = LvngTextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TouchCurve.entries.forEach { curve ->
                        val active = touchConfig.touchCurve == curve
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) LvngCrimson.copy(alpha = 0.2f) else Color(0xFF0E1012))
                                .border(
                                    1.dp,
                                    if (active) LvngCrimson else LvngBorderSubtle,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onApplyCurve(curve) }
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = curve.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) LvngCrimson else LvngTextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0E1012))
                        .clickable(onClick = onOpenExtraSettings)
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                        .testTag("touch_extra_settings_row")
                ) {
                    Column {
                        Text(
                            text = if (isId) "Setelan Tambahan" else "Additional Settings",
                            style = MaterialTheme.typography.titleMedium,
                            color = LvngTextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isId) "Koordinat & titik sentuhan" else "Touch coordinates & show touches",
                            style = MaterialTheme.typography.labelSmall,
                            color = LvngTextSecondary
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = LvngTextSecondary
                    )
                }
            }
        }

        // 4. Clean Live Touch Test Pad
        item {
            TouchDiagnosticPad(
                deadzoneDp = touchConfig.edgeDeadzoneDp,
                isId = isId
            )
        }
    }
}

@Composable
fun ActivationStatusBanner(
    isActivated: Boolean,
    activationLabel: String,
    isId: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LvngCarbon)
            .border(1.dp, LvngBorderSubtle, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isActivated) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (isActivated) LvngEmerald else LvngCrimson,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (isActivated) {
                        activationLabel
                    } else {
                        if (isId) "Perlu aktivasi" else "Activation required"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = LvngTextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = LvngTextSecondary
        )
    }
}

@Composable
private fun TouchDiagnosticPad(
    deadzoneDp: Float,
    isId: Boolean
) {
    val trailPoints = remember { mutableStateListOf<Offset>() }
    var coordsLabel by remember { mutableStateOf(if (isId) "Tes sentuh atau geser di sini" else "Touch or drag inside pad") }
    var deltaMs by remember { mutableLongStateOf(0L) }
    var lastTime by remember { mutableLongStateOf(0L) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(LvngCarbon)
            .border(1.dp, LvngBorderSubtle, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isId) "Uji Koordinat & Respon" else "Touch Coordinate Tester",
                style = MaterialTheme.typography.titleMedium,
                color = LvngTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (deltaMs > 0) "${deltaMs}ms" else "--ms",
                style = MaterialTheme.typography.labelMedium,
                color = LvngCrimson
            )
        }
        Text(
            text = coordsLabel,
            style = MaterialTheme.typography.labelSmall,
            color = LvngTextSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0A0B0D))
                .border(1.dp, LvngBorderSubtle, RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val active = event.changes.firstOrNull { it.pressed }
                            if (active != null) {
                                val now = System.nanoTime()
                                if (lastTime != 0L) {
                                    deltaMs = ((now - lastTime) / 1_000_000L).coerceAtLeast(1L)
                                }
                                lastTime = now
                                coordsLabel = "X: ${active.position.x.roundToInt()}   Y: ${active.position.y.roundToInt()}"
                                trailPoints.add(active.position)
                                if (trailPoints.size > 32) trailPoints.removeAt(0)
                            } else {
                                lastTime = 0L
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dz = deadzoneDp.dp.toPx()
                if (dz > 0f) {
                    drawRect(
                        color = LvngCrimson.copy(alpha = 0.14f),
                        topLeft = Offset.Zero,
                        size = androidx.compose.ui.geometry.Size(dz, size.height)
                    )
                    drawRect(
                        color = LvngCrimson.copy(alpha = 0.14f),
                        topLeft = Offset(size.width - dz, 0f),
                        size = androidx.compose.ui.geometry.Size(dz, size.height)
                    )
                }
                if (trailPoints.size >= 2) {
                    val path = Path().apply {
                        moveTo(trailPoints.first().x, trailPoints.first().y)
                        for (i in 1 until trailPoints.size) {
                            lineTo(trailPoints[i].x, trailPoints[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = LvngCrimson,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                trailPoints.lastOrNull()?.let { head ->
                    drawCircle(LvngCrimson.copy(alpha = 0.3f), radius = 16.dp.toPx(), center = head)
                    drawCircle(Color.White, radius = 4.dp.toPx(), center = head)
                }
            }
        }
    }
}
