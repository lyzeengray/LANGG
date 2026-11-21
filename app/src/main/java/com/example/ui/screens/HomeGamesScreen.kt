package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AppLanguage
import com.example.model.GameProfile
import com.example.model.InstalledAppItem
import com.example.model.LvngSpaceUiState
import com.example.ui.theme.LvngBorderSubtle
import com.example.ui.theme.LvngCarbon
import com.example.ui.theme.LvngCrimson
import com.example.ui.theme.LvngCrimsonDark
import com.example.ui.theme.LvngElevated
import com.example.ui.theme.LvngEmerald
import com.example.ui.theme.LvngTextPrimary
import com.example.ui.theme.LvngTextSecondary
import com.example.ui.theme.LvngVoid

@Composable
fun HomeGamesScreen(
    uiState: LvngSpaceUiState,
    onOpenAddGame: () -> Unit,
    onLaunchGame: (GameProfile) -> Unit,
    onOpenGameTuning: (GameProfile) -> Unit,
    onBoostRam: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isId = uiState.language == AppLanguage.ID
    val freeRamPct = (100 - uiState.telemetry.ramUsagePercent).coerceIn(10, 100)

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier.fillMaxSize()
    ) {
        // Top Engine Readiness Card (clean minimalist layout matching Screenshot 4)
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isId) "Performa & Kontrol" else "Engine & Overlay",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LvngTextSecondary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(LvngCrimsonDark.copy(alpha = 0.7f))
                                .border(1.dp, LvngCrimson.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .clickable(onClick = onBoostRam)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("home_boost_pill")
                        ) {
                            Text(
                                text = if (isId) "+ Boost RAM" else "+ Boost RAM",
                                style = MaterialTheme.typography.labelSmall,
                                color = LvngTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${uiState.telemetry.fps}",
                            style = MaterialTheme.typography.displayMedium,
                            color = LvngTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "FPS  •  ${uiState.activeMode.label}",
                            style = MaterialTheme.typography.titleMedium,
                            color = LvngTextSecondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    Text(
                        text = if (isId) "RAM Bebas $freeRamPct%" else "Free RAM $freeRamPct%",
                        style = MaterialTheme.typography.labelLarge,
                        color = LvngCrimson,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { freeRamPct / 100f },
                    color = LvngCrimson,
                    trackColor = Color(0xFF23272B),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape)
                )
            }
        }

        // Section Header: PERPUSTAKAAN GAME + "+ Tambah Game"
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isId) "PERPUSTAKAAN GAME" else "GAME LIBRARY",
                    style = MaterialTheme.typography.labelLarge,
                    color = LvngTextPrimary,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(LvngCarbon)
                        .border(1.dp, LvngBorderSubtle, RoundedCornerShape(20.dp))
                        .clickable(onClick = onOpenAddGame)
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                        .testTag("add_game_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Tambah Game",
                        tint = LvngTextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isId) "Tambah Game" else "Add Game",
                        style = MaterialTheme.typography.labelLarge,
                        color = LvngTextPrimary
                    )
                }
            }
        }

        // Empty State or Added Games List
        if (uiState.addedGames.isEmpty()) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(LvngCarbon)
                        .border(1.dp, LvngBorderSubtle, RoundedCornerShape(18.dp))
                        .clickable(onClick = onOpenAddGame)
                        .padding(horizontal = 24.dp, vertical = 30.dp)
                        .testTag("empty_game_card")
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
                            "Tap di sini buat nambahin game"
                        } else {
                            "Tap here to add a game"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = LvngTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(uiState.addedGames, key = { it.packageName }) { game ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(LvngCarbon)
                        .border(1.dp, LvngBorderSubtle, RoundedCornerShape(16.dp))
                        .padding(12.dp)
                        .testTag("game_row_${game.packageName}")
                ) {
                    if (game.iconBitmap != null) {
                        Image(
                            bitmap = game.iconBitmap,
                            contentDescription = game.appName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(LvngElevated)
                        ) {
                            Icon(Icons.Filled.SportsEsports, contentDescription = null, tint = LvngCrimson)
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = game.appName,
                            style = MaterialTheme.typography.titleMedium,
                            color = LvngTextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${game.performanceMode.label} • ${String.format("%.2f", game.touchSensitivity)}x",
                            style = MaterialTheme.typography.labelSmall,
                            color = LvngTextSecondary
                        )
                    }

                    IconButton(
                        onClick = { onOpenGameTuning(game) },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("tune_game_${game.packageName}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Atur ${game.appName}",
                            tint = LvngTextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Button(
                        onClick = { onLaunchGame(game) },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LvngCrimson),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("play_game_${game.packageName}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isId) "Main" else "Play",
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

@Composable
fun AddGameScreen(
    installedApps: List<InstalledAppItem>,
    addedPackages: Set<String>,
    language: AppLanguage,
    onAddGame: (String) -> Unit,
    onRemoveGame: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isId = language == AppLanguage.ID
    var query by remember { mutableStateOf("") }

    val filtered = remember(installedApps, query) {
        if (query.isBlank()) installedApps
        else installedApps.filter {
            it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LvngVoid)
            .statusBarsPadding()
    ) {
        // Header: < Tambah Game (Screenshot 8)
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
                    .testTag("add_game_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    tint = LvngTextPrimary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isId) "Tambah Game" else "Add Game",
                style = MaterialTheme.typography.titleLarge,
                color = LvngTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        // Search input matching Screenshot 8
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = {
                Text(
                    text = if (isId) "Cari game atau aplikasi..." else "Search apps & games...",
                    color = LvngTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null, tint = LvngTextSecondary)
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LvngCrimson,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = LvngCarbon,
                unfocusedContainerColor = LvngCarbon,
                focusedTextColor = LvngTextPrimary,
                unfocusedTextColor = LvngTextPrimary
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("add_game_search")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Clean list of installed phone apps with right-side Toggle Switch (Screenshot 8)
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filtered, key = { it.packageName }) { app ->
                val isAdded = addedPackages.contains(app.packageName)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            if (isAdded) onRemoveGame(app.packageName) else onAddGame(app.packageName)
                        }
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                        .testTag("installed_app_row_${app.packageName}")
                ) {
                    if (app.iconBitmap != null) {
                        Image(
                            bitmap = app.iconBitmap,
                            contentDescription = app.appName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(LvngCarbon)
                        ) {
                            Icon(Icons.Filled.SportsEsports, contentDescription = null, tint = LvngCrimson)
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        color = LvngTextPrimary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Switch(
                        checked = isAdded,
                        onCheckedChange = { checked ->
                            if (checked) onAddGame(app.packageName) else onRemoveGame(app.packageName)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = LvngCrimson,
                            uncheckedThumbColor = Color(0xFFB0B5BC),
                            uncheckedTrackColor = Color(0xFF34383D)
                        ),
                        modifier = Modifier.testTag("switch_app_${app.packageName}")
                    )
                }
            }
        }
    }
}
