package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AppLanguage
import com.example.model.GameProfile
import com.example.ui.theme.LvngAmber
import com.example.ui.theme.LvngBorderSubtle
import com.example.ui.theme.LvngCrimson
import com.example.ui.theme.LvngElevated
import com.example.ui.theme.LvngTextPrimary
import com.example.ui.theme.LvngTextSecondary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun GameLaunchSlideSheet(
    game: GameProfile?,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onSlideLaunchConfirmed: (GameProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val isId = language == AppLanguage.ID
    var activeGame by remember { mutableStateOf<GameProfile?>(null) }
    if (game != null) {
        activeGame = game
    }

    if (game != null) {
        BackHandler(onBack = onDismiss)
    }

    AnimatedVisibility(
        visible = game != null,
        enter = fadeIn(tween(240)),
        exit = fadeOut(tween(200)),
        modifier = modifier.fillMaxSize()
    ) {
        val currentGame = activeGame ?: return@AnimatedVisibility
        var dragProgress by remember(currentGame.packageName) { mutableFloatStateOf(0f) }
        var isDragging by remember(currentGame.packageName) { mutableStateOf(false) }
        var triggered by remember(currentGame.packageName) { mutableStateOf(false) }

        val animatedProgress by animateFloatAsState(
            targetValue = dragProgress,
            animationSpec = if (isDragging) tween(0) else spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "launch_slide_progress"
        )

        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .animateEnterExit(
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                        ),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(durationMillis = 220)
                        )
                    )
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .fillMaxHeight(0.50f)
                    .heightIn(min = 340.dp, max = 440.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color(0xFF0E1012))
                    .border(
                        width = 1.dp,
                        color = LvngBorderSubtle,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .navigationBarsPadding()
            ) {
                // Top Game Banner Card with darkened artwork backdrop & handle bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(114.dp)
                        .background(Color(0xFF191C20))
                ) {
                    if (currentGame.iconBitmap != null) {
                        Image(
                            bitmap = currentGame.iconBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alpha = 0.24f,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x9908090B),
                                        Color(0xE60E1012)
                                    )
                                )
                            )
                    )

                    // Top drag handle pill
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                            .width(40.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.38f))
                    )

                    // Game Info Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        if (currentGame.iconBitmap != null) {
                            Image(
                                bitmap = currentGame.iconBitmap,
                                contentDescription = currentGame.appName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                            )
                        } else {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(LvngElevated)
                            ) {
                                Icon(Icons.Filled.SportsEsports, contentDescription = null, tint = LvngCrimson)
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentGame.appName,
                                style = MaterialTheme.typography.titleMedium,
                                color = LvngTextPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentGame.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = LvngTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xCC1E2126))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.History,
                                        contentDescription = null,
                                        tint = LvngTextSecondary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${currentGame.launchCount}x",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LvngTextPrimary
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xCC1E2126))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.RocketLaunch,
                                        contentDescription = null,
                                        tint = LvngTextSecondary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = currentGame.performanceMode.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LvngTextPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${currentGame.performanceMode.touchPollHz}Hz",
                                style = MaterialTheme.typography.labelLarge,
                                color = LvngAmber,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${String.format("%.1f", currentGame.touchSensitivity)}x",
                                style = MaterialTheme.typography.labelSmall,
                                color = LvngTextSecondary
                            )
                        }
                    }
                }

                // Middle "Game Terpilih" + Bold Red LVNG Logo
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isId) "Game Terpilih" else "Selected Game",
                        style = MaterialTheme.typography.titleMedium,
                        color = LvngTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.lvng_space_logo),
                            contentDescription = "LVNG SPACE Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(92.dp)
                                .clip(RoundedCornerShape(22.dp))
                        )
                    }
                }

                // Bottom Slide-to-Open Track ("Geser untuk Membuka")
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 18.dp)
                        .height(58.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF17191C))
                        .border(1.dp, Color(0xFF282B30), RoundedCornerShape(16.dp))
                        .testTag("launch_slide_track")
                ) {
                    val density = LocalDensity.current
                    val thumbWidth = 80.dp
                    val trackWidthPx = constraints.maxWidth.toFloat()
                    val thumbWidthPx = with(density) { thumbWidth.toPx() }
                    val maxDragPx = (trackWidthPx - thumbWidthPx - with(density) { 8.dp.toPx() }).coerceAtLeast(1f)
                    val offsetXPx = (animatedProgress * maxDragPx).roundToInt()

                    Text(
                        text = if (isId) "Geser untuk Membuka" else "Slide to Launch",
                        style = MaterialTheme.typography.titleSmall,
                        color = LvngTextSecondary,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(start = 52.dp)
                            .alpha((1f - animatedProgress * 1.4f).coerceIn(0f, 1f))
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 4.dp)
                            .offset { IntOffset(offsetXPx, 0) }
                            .width(thumbWidth)
                            .height(50.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(LvngCrimson)
                            .pointerInput(currentGame.packageName, maxDragPx) {
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        isDragging = true
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        if (!triggered) {
                                            val next = ((dragProgress * maxDragPx + dragAmount) / maxDragPx)
                                                .coerceIn(0f, 1f)
                                            dragProgress = next
                                            if (next >= 0.80f) {
                                                triggered = true
                                                dragProgress = 1f
                                                isDragging = false
                                                onSlideLaunchConfirmed(currentGame)
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        isDragging = false
                                        if (!triggered) {
                                            dragProgress = 0f
                                        }
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                        if (!triggered) {
                                            dragProgress = 0f
                                        }
                                    }
                                )
                            }
                            .testTag("launch_slide_thumb")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardDoubleArrowRight,
                            contentDescription = "Geser ke kanan untuk membuka game",
                            tint = Color(0xFF1A0407),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Animated circular LVNG bubble transition:
 * Pops out large at screen center -> smoothly glides to the left edge while shrinking to compact bubble size.
 */
@Composable
fun LvngBubblePopOutOverlay(
    visible: Boolean,
    onAnimationComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        val baseSizeDp = 96.dp
        val baseSizePx = with(density) { baseSizeDp.toPx() }
        val startCenterX = widthPx / 2f
        val startCenterY = heightPx / 2f
        val targetCenterX = with(density) { 25.dp.toPx() }
        val targetCenterY = heightPx * 0.36f + with(density) { 21.dp.toPx() }

        val alphaProgress = remember { Animatable(0f) }
        val popProgress = remember { Animatable(0f) }
        val moveProgress = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            alphaProgress.snapTo(0f)
            popProgress.snapTo(0f)
            moveProgress.snapTo(0f)
            val fadeJob = launch {
                alphaProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                )
            }
            popProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            fadeJob.join()
            kotlinx.coroutines.delay(50L)
            moveProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 520,
                    easing = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
                )
            )
            onAnimationComplete()
        }

        val currentScale = if (moveProgress.value <= 0f) {
            0.35f + (1.16f - 0.35f) * popProgress.value
        } else {
            1.16f + (0.4375f - 1.16f) * moveProgress.value
        }

        val currentCenterX = startCenterX + (targetCenterX - startCenterX) * moveProgress.value
        val currentCenterY = startCenterY + (targetCenterY - startCenterY) * moveProgress.value

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset {
                    IntOffset(
                        (currentCenterX - baseSizePx / 2f).roundToInt(),
                        (currentCenterY - baseSizePx / 2f).roundToInt()
                    )
                }
                .size(baseSizeDp)
                .alpha(alphaProgress.value)
                .scale(currentScale)
                .shadow(18.dp, CircleShape, ambientColor = LvngCrimson, spotColor = LvngCrimson)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF550D12), Color(0xFF0A0607))
                    )
                )
                .border(2.5.dp, LvngCrimson, CircleShape)
                .padding(8.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.lvng_space_logo),
                contentDescription = "LVNG Bubble Pop Out",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }
    }
}

@Composable
fun ShizukuNotReadyDialog(
    isInstalled: Boolean,
    isRunning: Boolean,
    isPermissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRecheck: () -> Unit,
    onDismiss: () -> Unit
) {
    val titleText = when {
        !isRunning -> "SHIZUKU BELUM AKTIF"
        !isPermissionGranted -> "SHIZUKU BELUM DIIZINKAN"
        else -> "SHIZUKU SIAP"
    }
    val bodyText = when {
        !isInstalled -> "Shizuku belum keinstall di HP ini. Install & aktifin dulu, baru game bisa dibuka."
        !isRunning -> "Aktifin Shizuku dulu terus izinin LVNG SPACE di Shizuku. Game belum bisa dibuka kalau Shizuku mati."
        !isPermissionGranted -> "Shizuku udah nyala, tinggal pencet Izinkan di bawah biar LVNG SPACE dapet akses."
        else -> "Shizuku udah aktif & siap dipake."
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF131518),
        titleContentColor = LvngTextPrimary,
        textContentColor = LvngTextSecondary,
        title = {
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium,
                color = LvngCrimson,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = bodyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LvngTextSecondary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1D2024))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (isRunning) "Shizuku: Nyala" else "Shizuku: Mati",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isRunning) Color(0xFF34D399) else LvngCrimson
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1D2024))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (isPermissionGranted) "Izin: OK" else "Izin: Belum",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPermissionGranted) Color(0xFF34D399) else LvngAmber
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = {
                    if (isRunning && !isPermissionGranted) {
                        onRequestPermission()
                    } else {
                        onOpenShizuku()
                    }
                },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = LvngCrimson),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("shizuku_gate_action_button")
            ) {
                Text(
                    text = when {
                        isRunning && !isPermissionGranted -> "Izinkan Shizuku"
                        isInstalled -> "Buka Shizuku"
                        else -> "Cek Lagi"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.compose.material3.TextButton(
                    onClick = onRecheck,
                    modifier = Modifier.testTag("shizuku_gate_recheck_button")
                ) {
                    Text("Cek Lagi", color = LvngTextPrimary)
                }
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text("Tutup", color = LvngTextSecondary)
                }
            }
        }
    )
}
