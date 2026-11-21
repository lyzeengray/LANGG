package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.model.AppLanguage
import com.example.model.LvngSpaceUiState
import com.example.ui.theme.LvngBorderSubtle
import com.example.ui.theme.LvngCarbon
import com.example.ui.theme.LvngCrimson
import com.example.ui.theme.LvngObsidian
import com.example.ui.theme.LvngTextPrimary
import com.example.ui.theme.LvngTextSecondary

@Composable
fun SettingsScreen(
    uiState: LvngSpaceUiState,
    onOpenActivation: () -> Unit,
    onOpenKeyMappingManager: () -> Unit,
    onOpenExtraSettings: () -> Unit,
    onToggleLanguage: () -> Unit,
    onOpenPermissions: () -> Unit,
    onRequestShizuku: () -> Unit,
    onOpenControlPreview: () -> Unit,
    onBoostRam: () -> Unit,
    onLogout: () -> Unit,
    ownerAuthLoading: Boolean = false,
    ownerAuthError: String? = null,
    onVerifyOwnerPin: (String) -> Unit = {},
    onClearOwnerError: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isId = uiState.language == AppLanguage.ID
    val shizukuStatusText = when {
        uiState.permissions.isShizukuReady -> if (isId) "Aktif" else "Active"
        uiState.permissions.isShizukuRunning -> if (isId) "Perlu Izin" else "Need Allow"
        else -> if (isId) "Mati" else "Off"
    }

    var showConfigurePinDialog by remember { mutableStateOf(false) }
    var configurePinInput by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        // 1. Status Aktivasi Shizuku
        item {
            ActivationStatusBanner(
                isActivated = uiState.permissions.isShizukuReady,
                activationLabel = when {
                    uiState.permissions.isShizukuReady -> if (isId) "Shizuku aktif & diizinin" else "Shizuku active"
                    uiState.permissions.isShizukuRunning -> if (isId) "Izinin LVNG SPACE di Shizuku" else "Allow in Shizuku"
                    else -> if (isId) "Shizuku belum aktif" else "Shizuku not active"
                },
                isId = isId,
                onClick = onOpenActivation,
                modifier = Modifier.testTag("settings_activation_banner")
            )
        }

        // 2. Setelan dasar Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(LvngCarbon)
                    .border(1.dp, LvngBorderSubtle, RoundedCornerShape(18.dp))
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = if (isId) "Setelan dasar" else "Basic settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = LvngTextSecondary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                )

                SettingsRowItem(
                    title = if (isId) "Atur Keymap" else "Manage Key Mapping",
                    onClick = onOpenKeyMappingManager,
                    tag = "settings_manage_mapping"
                )
                HorizontalDivider(color = LvngBorderSubtle, modifier = Modifier.padding(horizontal = 18.dp))

                SettingsRowItem(
                    title = if (isId) "Setelan Tambahan" else "Additional Settings",
                    onClick = onOpenExtraSettings,
                    tag = "settings_extra"
                )
                HorizontalDivider(color = LvngBorderSubtle, modifier = Modifier.padding(horizontal = 18.dp))

                SettingsRowItem(
                    title = if (isId) "Bahasa" else "Language",
                    trailingText = uiState.language.displayName,
                    onClick = onToggleLanguage,
                    tag = "settings_language"
                )
                HorizontalDivider(color = LvngBorderSubtle, modifier = Modifier.padding(horizontal = 18.dp))

                SettingsRowItem(
                    title = if (isId) "Izin Overlay" else "Overlay Permissions",
                    trailingText = if (uiState.permissions.hasOverlayPermission) {
                        if (isId) "Aman" else "Granted"
                    } else {
                        if (isId) "Cek" else "Check"
                    },
                    onClick = onOpenPermissions,
                    tag = "settings_permissions"
                )
            }
        }

        // 3. Kontrol & Perawatan LVNG SPACE
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(LvngCarbon)
                    .border(1.dp, LvngBorderSubtle, RoundedCornerShape(18.dp))
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "LVNG -X",
                    style = MaterialTheme.typography.titleMedium,
                    color = LvngTextSecondary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                )

                SettingsRowItem(
                    title = if (isId) "Izin Shizuku (Macro)" else "Shizuku Permission (Macro)",
                    trailingText = shizukuStatusText,
                    onClick = onRequestShizuku,
                    tag = "settings_shizuku"
                )
                HorizontalDivider(color = LvngBorderSubtle, modifier = Modifier.padding(horizontal = 18.dp))

                SettingsRowItem(
                    title = if (isId) "Cek Game Control" else "Preview Game Control",
                    onClick = onOpenControlPreview,
                    tag = "settings_preview_hud"
                )
                HorizontalDivider(color = LvngBorderSubtle, modifier = Modifier.padding(horizontal = 18.dp))

                SettingsRowItem(
                    title = if (isId) "Bersihin RAM" else "Clean RAM",
                    trailingText = "${uiState.telemetry.ramUsagePercent}%",
                    onClick = onBoostRam,
                    tag = "settings_clean_ram"
                )
            }
        }

        // 4. Akun, Keluar Akun & Configure
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(LvngCarbon)
                    .border(1.dp, LvngBorderSubtle, RoundedCornerShape(18.dp))
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = if (isId) "Akun" else "Account",
                    style = MaterialTheme.typography.titleMedium,
                    color = LvngTextSecondary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                )

                val expiryLabel = when {
                    uiState.loggedInExpiresAtMillis == -1L -> "Lifetime"
                    uiState.loggedInExpiresAtMillis > 0L -> {
                        val remainingDays = ((uiState.loggedInExpiresAtMillis - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0L)
                        if (isId) "Sisa ~${remainingDays + 1} Hari" else "~${remainingDays + 1} Days Left"
                    }
                    else -> if (isId) "Aktif" else "Active"
                }

                SettingsRowItem(
                    title = uiState.loggedInUsername ?: "LVNG User",
                    trailingText = expiryLabel,
                    onClick = {},
                    tag = "settings_active_account"
                )
                HorizontalDivider(color = LvngBorderSubtle, modifier = Modifier.padding(horizontal = 18.dp))

                SettingsRowItem(
                    title = if (isId) "Keluar Akun" else "Log Out",
                    onClick = onLogout,
                    tag = "settings_logout"
                )
                HorizontalDivider(color = LvngBorderSubtle, modifier = Modifier.padding(horizontal = 18.dp))

                SettingsRowItem(
                    title = "Configure",
                    onClick = {
                        onClearOwnerError()
                        configurePinInput = ""
                        showConfigurePinDialog = true
                    },
                    tag = "settings_configure"
                )
            }
        }
    }

    if (showConfigurePinDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!ownerAuthLoading) {
                    showConfigurePinDialog = false
                }
            },
            containerColor = LvngCarbon,
            titleContentColor = LvngTextPrimary,
            textContentColor = LvngTextSecondary,
            icon = {
                Icon(
                    imageVector = Icons.Filled.AdminPanelSettings,
                    contentDescription = null,
                    tint = LvngCrimson,
                    modifier = Modifier.size(30.dp)
                )
            },
            title = {
                Text(
                    text = "Configure",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = if (isId) "Masukin PIN owner buat masuk ke buat akun" else "Enter owner PIN to open account creator",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LvngTextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = configurePinInput,
                        onValueChange = { raw ->
                            val filtered = raw.filter { it.isDigit() }.take(8)
                            configurePinInput = filtered
                            onClearOwnerError()
                            if (filtered.length == 6 && !ownerAuthLoading) {
                                onVerifyOwnerPin(filtered)
                            }
                        },
                        label = { Text("PIN") },
                        leadingIcon = {
                            Icon(Icons.Filled.Key, contentDescription = null, tint = LvngCrimson)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LvngCrimson,
                            unfocusedBorderColor = LvngBorderSubtle,
                            focusedContainerColor = LvngObsidian,
                            unfocusedContainerColor = LvngObsidian,
                            focusedTextColor = LvngTextPrimary,
                            unfocusedTextColor = LvngTextPrimary,
                            focusedLabelColor = LvngCrimson,
                            unfocusedLabelColor = LvngTextSecondary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("configure_pin_input")
                    )

                    if (!ownerAuthError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = ownerAuthError,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF8A95)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onVerifyOwnerPin(configurePinInput) },
                    enabled = !ownerAuthLoading && configurePinInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = LvngCrimson),
                    modifier = Modifier.testTag("configure_pin_submit")
                ) {
                    if (ownerAuthLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Masuk",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfigurePinDialog = false },
                    enabled = !ownerAuthLoading
                ) {
                    Text(if (isId) "Batal" else "Cancel", color = LvngTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SettingsRowItem(
    title: String,
    trailingText: String? = null,
    onClick: () -> Unit,
    tag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp)
            .testTag(tag)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = LvngTextPrimary,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!trailingText.isNullOrBlank()) {
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.bodyMedium,
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
