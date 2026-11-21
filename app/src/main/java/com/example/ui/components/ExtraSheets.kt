package com.example.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AppLanguage
import com.example.model.CrosshairStyle
import com.example.model.GameProfile
import com.example.model.PerformanceMode
import com.example.ui.theme.LvngBorderSubtle
import com.example.ui.theme.LvngCarbon
import com.example.ui.theme.LvngCrimson
import com.example.ui.theme.LvngObsidian
import com.example.ui.theme.LvngTextPrimary
import com.example.ui.theme.LvngTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtraTouchSettingsSheet(
    showCoordinates: Boolean,
    showTouchDots: Boolean,
    language: AppLanguage,
    onToggleCoordinates: (Boolean) -> Unit,
    onToggleTouchDots: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isId = language == AppLanguage.ID

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LvngObsidian,
        contentColor = LvngTextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = if (isId) "Pengaturan Tambahan" else "Additional Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = LvngTextPrimary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(LvngCarbon)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = if (isId) "Tampilkan koordinat sentuhan" else "Show touch coordinates",
                    style = MaterialTheme.typography.titleMedium,
                    color = LvngTextPrimary
                )
                Switch(
                    checked = showCoordinates,
                    onCheckedChange = onToggleCoordinates,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = LvngCrimson
                    ),
                    modifier = Modifier.testTag("extra_show_coords_switch")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(LvngCarbon)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = if (isId) "Tampilkan sentuhan" else "Show touches",
                    style = MaterialTheme.typography.titleMedium,
                    color = LvngTextPrimary
                )
                Switch(
                    checked = showTouchDots,
                    onCheckedChange = onToggleTouchDots,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = LvngCrimson
                    ),
                    modifier = Modifier.testTag("extra_show_touches_switch")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isId) {
                    "Jalan pintas buat nyalain koordinat & sentuhan tanpa buka opsi developer"
                } else {
                    "Quick shortcut to show coordinates & touch dots without Developer Options"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = LvngCrimson,
                modifier = Modifier.clickable {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanGameTuningSheet(
    game: GameProfile,
    onSave: (PerformanceMode, Float, Boolean, CrosshairStyle) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedMode by remember(game) { mutableStateOf(game.performanceMode) }
    var sensitivity by remember(game) { mutableFloatStateOf(game.touchSensitivity) }
    var autoDnd by remember(game) { mutableStateOf(game.autoDndOnLaunch) }
    var crosshair by remember(game) { mutableStateOf(game.crosshairStyle) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LvngObsidian,
        contentColor = LvngTextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (game.iconBitmap != null) {
                    Image(
                        bitmap = game.iconBitmap,
                        contentDescription = game.appName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = game.appName,
                        style = MaterialTheme.typography.titleLarge,
                        color = LvngTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Profil Game LVNG -X",
                        style = MaterialTheme.typography.labelSmall,
                        color = LvngCrimson
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PerformanceMode.entries.forEach { mode ->
                    val active = selectedMode == mode
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) mode.accentColor.copy(alpha = 0.2f) else LvngCarbon)
                            .border(
                                1.dp,
                                if (active) mode.accentColor else LvngBorderSubtle,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedMode = mode }
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

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Sensitivitas Sentuhan", style = MaterialTheme.typography.bodyMedium, color = LvngTextPrimary)
                Text("${String.format("%.2f", sensitivity)}x", style = MaterialTheme.typography.labelMedium, color = LvngCrimson)
            }
            Slider(
                value = sensitivity,
                onValueChange = { sensitivity = it },
                valueRange = 1.0f..2.5f,
                colors = SliderDefaults.colors(
                    thumbColor = LvngCrimson,
                    activeTrackColor = LvngCrimson,
                    inactiveTrackColor = LvngCarbon
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CrosshairStyle.entries.forEach { style ->
                    val active = crosshair == style
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) LvngCrimson.copy(alpha = 0.2f) else LvngCarbon)
                            .border(
                                1.dp,
                                if (active) LvngCrimson else LvngBorderSubtle,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { crosshair = style }
                    ) {
                        Text(
                            text = style.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) LvngCrimson else LvngTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onRemove()
                        onDismiss()
                    },
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus", tint = LvngCrimson)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hapus", color = LvngCrimson)
                }

                Button(
                    onClick = {
                        onSave(selectedMode, sensitivity, autoDnd, crosshair)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LvngCrimson),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Simpan Profil", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
