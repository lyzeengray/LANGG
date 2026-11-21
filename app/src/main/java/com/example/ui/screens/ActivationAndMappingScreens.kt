package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.ActivationMethod
import com.example.model.AppLanguage
import com.example.model.GameProfile
import com.example.ui.theme.LvngBorderSubtle
import com.example.ui.theme.LvngCarbon
import com.example.ui.theme.LvngCrimson
import com.example.ui.theme.LvngEmerald
import com.example.ui.theme.LvngTextPrimary
import com.example.ui.theme.LvngTextSecondary
import com.example.ui.theme.LvngVoid
import kotlinx.coroutines.launch

@Composable
fun ActivationScreen(
    currentMethod: ActivationMethod,
    isShizukuInstalled: Boolean,
    isShizukuRunning: Boolean,
    isShizukuPermitted: Boolean,
    language: AppLanguage,
    onRequestShizukuPermission: () -> Unit,
    onSelectActivation: (ActivationMethod) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isId = language == AppLanguage.ID
    var showInstructionsDialog by remember { mutableStateOf(false) }
    var showShizukuMissingDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LvngVoid)
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("activation_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    tint = LvngTextPrimary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isId) "Aktivasi" else "Activation",
                style = MaterialTheme.typography.titleLarge,
                color = LvngTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Card 1: Mulai via Shizuku
            item {
                val shizukuStatusMsg = when {
                    isShizukuRunning && isShizukuPermitted ->
                        if (isId) "Shizuku aktif & LVNG SPACE udah diizinin" else "Shizuku active & allowed"
                    isShizukuRunning ->
                        if (isId) "Shizuku nyala, tinggal pencet tombol buat izinin" else "Shizuku running, tap to allow LVNG SPACE"
                    isShizukuInstalled ->
                        if (isId) "Shizuku belum aktif, nyalain dulu di app Shizuku" else "Shizuku installed but not running"
                    else ->
                        if (isId) "Shizuku belum kepasang di HP ini" else "Shizuku not installed"
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(LvngCarbon)
                        .border(1.dp, LvngBorderSubtle, RoundedCornerShape(18.dp))
                        .padding(18.dp)
                ) {
                    Text(
                        text = if (isId) "Via Shizuku" else "Via Shizuku",
                        style = MaterialTheme.typography.titleLarge,
                        color = LvngTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = shizukuStatusMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isShizukuRunning && isShizukuPermitted) LvngEmerald else LvngTextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                when {
                                    isShizukuRunning && !isShizukuPermitted -> {
                                        onRequestShizukuPermission()
                                    }
                                    isShizukuRunning && isShizukuPermitted -> {
                                        onSelectActivation(ActivationMethod.SHIZUKU)
                                    }
                                    isShizukuInstalled -> {
                                        val launch = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                        if (launch != null) context.startActivity(launch)
                                    }
                                    else -> {
                                        showShizukuMissingDialog = true
                                    }
                                }
                            },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isShizukuRunning && isShizukuPermitted) LvngEmerald else LvngCrimson
                            ),
                            modifier = Modifier
                                .height(46.dp)
                                .testTag("activation_shizuku_start")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    isShizukuRunning && isShizukuPermitted -> if (isId) "Aktif" else "Active"
                                    isShizukuRunning -> if (isId) "Izinkan Shizuku" else "Allow Shizuku"
                                    isShizukuInstalled -> if (isId) "Buka Shizuku" else "Open Shizuku"
                                    else -> "Start"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Card 2: Mulai via Wireless Debugging
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(LvngCarbon)
                        .border(1.dp, LvngBorderSubtle, RoundedCornerShape(18.dp))
                        .padding(18.dp)
                ) {
                    Text(
                        text = if (isId) "Wireless Debugging" else "Wireless Debugging",
                        style = MaterialTheme.typography.titleLarge,
                        color = LvngTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isId) {
                            "Buat Android 11+, nyalain Wireless Debugging di opsi developer tanpa perlu PC"
                        } else {
                            "For Android 11+, turn on Wireless Debugging in Developer Options without a PC"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = LvngTextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { showInstructionsDialog = true },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LvngCrimson),
                            modifier = Modifier
                                .height(46.dp)
                                .testTag("activation_wireless_instructions")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isId) "Cara Pasang" else "Instructions",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                                }.onFailure {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                }
                            },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LvngCrimson),
                            modifier = Modifier
                                .height(46.dp)
                                .testTag("activation_wireless_pairing")
                        ) {
                            Icon(Icons.Filled.Link, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Opsi Developer",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showInstructionsDialog) {
        AlertDialog(
            onDismissRequest = { showInstructionsDialog = false },
            containerColor = LvngCarbon,
            title = {
                Text(
                    text = if (isId) "Cara Aktifin Shizuku" else "How to Start Shizuku",
                    color = LvngTextPrimary
                )
            },
            text = {
                Text(
                    text = if (isId) {
                        "1. Nyalain Opsi Developer > Wireless Debugging.\n2. Buka Shizuku lalu pairing & start.\n3. Balik ke LVNG SPACE lalu kasih izin."
                    } else {
                        "1. Enable Developer Options > Wireless Debugging.\n2. Open Shizuku to pair & start.\n3. Return here and tap Allow."
                    },
                    color = LvngTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { showInstructionsDialog = false }) {
                    Text("OK", color = LvngCrimson)
                }
            }
        )
    }

    if (showShizukuMissingDialog) {
        AlertDialog(
            onDismissRequest = { showShizukuMissingDialog = false },
            containerColor = LvngCarbon,
            title = {
                Text("Shizuku", color = LvngTextPrimary)
            },
            text = {
                Text(
                    text = if (isId) {
                        "Shizuku belum kepasang di HP ini, install Shizuku dulu ya."
                    } else {
                        "Shizuku is not installed on this phone yet."
                    },
                    color = LvngTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { showShizukuMissingDialog = false }) {
                    Text("OK", color = LvngCrimson)
                }
            }
        )
    }
}

@Composable
fun KeyMappingManagerScreen(
    addedGames: List<GameProfile>,
    language: AppLanguage,
    onExportJson: suspend () -> String,
    onImportJson: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isId = language == AppLanguage.ID

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = onExportJson()
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray())
                    }
                }
            }
        }
    }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()?.let { text ->
                onImportJson(text)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LvngVoid)
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("mapping_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    tint = LvngTextPrimary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isId) "Atur Keymap" else "Manage Mapping",
                style = MaterialTheme.typography.titleLarge,
                color = LvngTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Text(
                text = if (isId) {
                    "Backup atau share settingan keymap"
                } else {
                    "Back up or share keymap profiles"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = LvngCrimson
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (addedGames.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(LvngCarbon)
                        .border(1.dp, LvngBorderSubtle, RoundedCornerShape(18.dp))
                        .padding(horizontal = 24.dp, vertical = 28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SportsEsports,
                        contentDescription = null,
                        tint = LvngCrimson,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isId) "Belum ada game" else "No games yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = LvngTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isId) {
                            "Tambah game dulu biar bisa atur keymap"
                        } else {
                            "Add a game first to edit its keymap"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = LvngTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { createDocLauncher.launch("lvng_space_mappings.json") },
                        colors = ButtonDefaults.buttonColors(containerColor = LvngCrimson),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export JSON", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = { openDocLauncher.launch(arrayOf("application/json", "*/*")) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, tint = LvngTextPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import JSON", style = MaterialTheme.typography.labelLarge, color = LvngTextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(addedGames, key = { it.packageName }) { game ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(LvngCarbon)
                                .border(1.dp, LvngBorderSubtle, RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        ) {
                            Column {
                                Text(
                                    text = game.appName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = LvngTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${game.keymapNodes.size} tombol keymap tersimpan",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LvngTextSecondary
                                )
                            }
                            Text(
                                text = game.performanceMode.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = LvngCrimson,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
