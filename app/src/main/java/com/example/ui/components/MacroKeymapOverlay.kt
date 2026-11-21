package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MacroConfig
import com.example.model.MacroTapMode
import com.example.model.MacroTargetNode
import com.example.model.MacroTriggerNode
import com.example.ui.theme.LvngCrimson
import com.example.ui.theme.LvngCrimsonBright
import com.example.ui.theme.LvngCrimsonSoft
import com.example.ui.theme.LvngTextPrimary
import com.example.ui.theme.LvngTextSecondary
import kotlin.math.hypot
import kotlin.math.roundToInt

private sealed interface SelectedMacroNode {
    data class Trigger(val id: String) : SelectedMacroNode
    data class Target(val id: String) : SelectedMacroNode
}

@Composable
fun MacroKeymapEditorOverlay(
    macroConfig: MacroConfig,
    onUpdateConfig: (MacroConfig) -> Unit,
    onSave: (MacroConfig) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedNode by remember {
        mutableStateOf<SelectedMacroNode?>(
            macroConfig.targets.firstOrNull()?.let { SelectedMacroNode.Target(it.id) }
                ?: macroConfig.triggers.firstOrNull()?.let { SelectedMacroNode.Trigger(it.id) }
        )
    }
    var editingTargetId by remember { mutableStateOf<String?>(null) }

    val latestConfig by rememberUpdatedState(macroConfig)
    val latestOnUpdate by rememberUpdatedState(onUpdateConfig)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x66000000))
    ) {
        val screenW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val screenH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        val selectedOffset: Offset? = when (val sel = selectedNode) {
            is SelectedMacroNode.Trigger -> {
                macroConfig.triggers.find { it.id == sel.id }?.let {
                    Offset(it.xFraction * screenW, it.yFraction * screenH)
                }
            }
            is SelectedMacroNode.Target -> {
                macroConfig.targets.find { it.id == sel.id }?.let {
                    Offset(it.xFraction * screenW, it.yFraction * screenH)
                }
            }
            null -> null
        }

        // Layer 1: Interactive Macro Canvas (Fixed-origin container coordinates for smooth 1:1 dragging!)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(screenW, screenH) {
                    val triggerHitRadiusPx = 56.dp.toPx()
                    val targetHitRadiusPx = 42.dp.toPx()

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val cfg = latestConfig

                        val hitTarget = cfg.targets
                            .map { tgt ->
                                val cx = tgt.xFraction * screenW
                                val cy = tgt.yFraction * screenH
                                tgt to hypot(down.position.x - cx, down.position.y - cy)
                            }
                            .filter { it.second <= targetHitRadiusPx }
                            .minByOrNull { it.second }
                            ?.first

                        val hitTrigger = if (hitTarget == null) {
                            cfg.triggers
                                .map { trig ->
                                    val cx = trig.xFraction * screenW
                                    val cy = trig.yFraction * screenH
                                    trig to hypot(down.position.x - cx, down.position.y - cy)
                                }
                                .filter { it.second <= triggerHitRadiusPx }
                                .minByOrNull { it.second }
                                ?.first
                        } else null

                        val activeSel: SelectedMacroNode? = when {
                            hitTarget != null -> SelectedMacroNode.Target(hitTarget.id)
                            hitTrigger != null -> SelectedMacroNode.Trigger(hitTrigger.id)
                            else -> null
                        }

                        if (activeSel != null) {
                            down.consume()
                            selectedNode = activeSel

                            val startCenter = when (activeSel) {
                                is SelectedMacroNode.Target -> Offset(
                                    hitTarget!!.xFraction * screenW,
                                    hitTarget.yFraction * screenH
                                )
                                is SelectedMacroNode.Trigger -> Offset(
                                    hitTrigger!!.xFraction * screenW,
                                    hitTrigger.yFraction * screenH
                                )
                            }
                            val grabOffset = startCenter - down.position

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                change.consume()
                                val newCenter = change.position + grabOffset
                                val newX = (newCenter.x / screenW).coerceIn(0.04f, 0.96f)
                                val newY = (newCenter.y / screenH).coerceIn(0.06f, 0.94f)
                                val currentCfg = latestConfig

                                when (activeSel) {
                                    is SelectedMacroNode.Trigger -> {
                                        latestOnUpdate(
                                            currentCfg.copy(
                                                triggers = currentCfg.triggers.map { t ->
                                                    if (t.id == activeSel.id) {
                                                        t.copy(xFraction = newX, yFraction = newY)
                                                    } else t
                                                }
                                            )
                                        )
                                    }
                                    is SelectedMacroNode.Target -> {
                                        latestOnUpdate(
                                            currentCfg.copy(
                                                targets = currentCfg.targets.map { tgt ->
                                                    if (tgt.id == activeSel.id) {
                                                        tgt.copy(xFraction = newX, yFraction = newY)
                                                    } else tgt
                                                }
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            // Dashed alignment crosshair lines passing through selected node
            Canvas(modifier = Modifier.fillMaxSize()) {
                selectedOffset?.let { pos ->
                    val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                    drawLine(
                        color = Color.White.copy(alpha = 0.6f),
                        start = Offset(0f, pos.y),
                        end = Offset(size.width, pos.y),
                        strokeWidth = 1.5f,
                        pathEffect = dash
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.6f),
                        start = Offset(pos.x, 0f),
                        end = Offset(pos.x, size.height),
                        strokeWidth = 1.5f,
                        pathEffect = dash
                    )
                }
            }

            // Trigger Nodes (Large transparent circle with crisp outline border & "Trigger 1" label)
            macroConfig.triggers.forEach { trigger ->
                val isSelected = selectedNode == SelectedMacroNode.Trigger(trigger.id)
                val centerX = (trigger.xFraction * screenW).roundToInt()
                val centerY = (trigger.yFraction * screenH).roundToInt()
                val radiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { 45.dp.toPx() }.roundToInt()

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset { IntOffset(centerX - radiusPx, centerY - radiusPx) }
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(Color(0x18000000))
                        .border(
                            width = if (isSelected) 2.2.dp else 1.5.dp,
                            color = if (isSelected) LvngCrimsonBright else Color.White.copy(alpha = 0.85f),
                            shape = CircleShape
                        )
                        .testTag("macro_trigger_${trigger.id}")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = trigger.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        if (isSelected) {
                            Text(
                                text = "$centerX,$centerY",
                                style = MaterialTheme.typography.labelSmall,
                                color = LvngCrimsonSoft,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Target Nodes (Smaller transparent circle with glowing crimson border & "Target 1" + coordinates)
            macroConfig.targets.forEach { target ->
                val isSelected = selectedNode == SelectedMacroNode.Target(target.id)
                val centerX = (target.xFraction * screenW).roundToInt()
                val centerY = (target.yFraction * screenH).roundToInt()
                val radiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { 27.dp.toPx() }.roundToInt()

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset { IntOffset(centerX - radiusPx, centerY - radiusPx) }
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) LvngCrimson.copy(alpha = 0.28f) else Color(0x22000000))
                        .border(
                            width = if (isSelected) 2.5.dp else 1.6.dp,
                            color = if (isSelected) LvngCrimsonBright else LvngCrimson.copy(alpha = 0.85f),
                            shape = CircleShape
                        )
                        .testTag("macro_target_${target.id}")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = target.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "$centerX,$centerY",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 8.sp
                        )
                    }
                }
            }
        }

        // Top Floating Macro Toolbar (Crimson LVNG theme)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp, start = 16.dp, end = 16.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xDB14080A))
                .border(1.dp, LvngCrimson.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // TRIGER button
                MacroToolbarIconButton(
                    icon = Icons.Filled.TouchApp,
                    label = "TRIGER",
                    tint = Color.White,
                    onClick = {
                        val nextIdx = macroConfig.triggers.size + 1
                        val newTrigger = MacroTriggerNode(
                            id = "trigger_$nextIdx",
                            name = "Trigger $nextIdx",
                            xFraction = 0.78f,
                            yFraction = 0.62f
                        )
                        onUpdateConfig(macroConfig.copy(triggers = macroConfig.triggers + newTrigger))
                        selectedNode = SelectedMacroNode.Trigger(newTrigger.id)
                    },
                    tag = "macro_add_trigger_button"
                )

                // TARGET button
                MacroToolbarIconButton(
                    icon = Icons.Filled.AdsClick,
                    label = "TARGET",
                    tint = LvngCrimsonBright,
                    onClick = {
                        val nextIdx = macroConfig.targets.size + 1
                        val newTarget = MacroTargetNode(
                            id = "target_$nextIdx",
                            name = "Target $nextIdx",
                            xFraction = 0.70f,
                            yFraction = 0.42f
                        )
                        onUpdateConfig(macroConfig.copy(targets = macroConfig.targets + newTarget))
                        selectedNode = SelectedMacroNode.Target(newTarget.id)
                    },
                    tag = "macro_add_target_button"
                )

                // CANCEL (X)
                MacroToolbarIconButton(
                    icon = Icons.Filled.Close,
                    label = null,
                    tint = Color.White,
                    onClick = onCancel,
                    tag = "macro_cancel_button"
                )

                // SAVE (✓)
                MacroToolbarIconButton(
                    icon = Icons.Filled.Check,
                    label = null,
                    tint = LvngCrimsonBright,
                    onClick = {
                        onSave(macroConfig.copy(isMacroActive = true))
                    },
                    tag = "macro_save_button"
                )
            }

            // Second row: Selected node bar + Delete + Settings + 4-way D-Pad
            selectedNode?.let { sel ->
                val selectedLabel = when (sel) {
                    is SelectedMacroNode.Trigger -> macroConfig.triggers.find { it.id == sel.id }?.name
                    is SelectedMacroNode.Target -> macroConfig.targets.find { it.id == sel.id }?.name
                }
                if (selectedLabel != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, LvngCrimson.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF3D1016))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = selectedLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Delete selected node
                        MacroSmallSquareButton(
                            icon = Icons.Filled.Delete,
                            onClick = {
                                when (sel) {
                                    is SelectedMacroNode.Trigger -> {
                                        val remaining = macroConfig.triggers.filterNot { it.id == sel.id }
                                        onUpdateConfig(macroConfig.copy(triggers = remaining))
                                        selectedNode = remaining.firstOrNull()?.let { SelectedMacroNode.Trigger(it.id) }
                                            ?: macroConfig.targets.firstOrNull()?.let { SelectedMacroNode.Target(it.id) }
                                    }
                                    is SelectedMacroNode.Target -> {
                                        val remaining = macroConfig.targets.filterNot { it.id == sel.id }
                                        onUpdateConfig(macroConfig.copy(targets = remaining))
                                        selectedNode = remaining.firstOrNull()?.let { SelectedMacroNode.Target(it.id) }
                                            ?: macroConfig.triggers.firstOrNull()?.let { SelectedMacroNode.Trigger(it.id) }
                                    }
                                }
                            },
                            tag = "macro_delete_node"
                        )

                        // Settings gear for Target node (opens Pengaturan Target 1 drawer matching Image 3!)
                        if (sel is SelectedMacroNode.Target) {
                            MacroSmallSquareButton(
                                icon = Icons.Filled.Settings,
                                onClick = { editingTargetId = sel.id },
                                tag = "macro_target_settings_button"
                            )
                        }

                        // Fine-position 4-way D-Pad
                        val nudgeStepX = 4f / screenW
                        val nudgeStepY = 4f / screenH
                        val nudgeNode: (Float, Float) -> Unit = { dx, dy ->
                            when (sel) {
                                is SelectedMacroNode.Trigger -> {
                                    onUpdateConfig(
                                        macroConfig.copy(
                                            triggers = macroConfig.triggers.map { t ->
                                                if (t.id == sel.id) {
                                                    t.copy(
                                                        xFraction = (t.xFraction + dx).coerceIn(0.05f, 0.95f),
                                                        yFraction = (t.yFraction + dy).coerceIn(0.08f, 0.92f)
                                                    )
                                                } else t
                                            }
                                        )
                                    )
                                }
                                is SelectedMacroNode.Target -> {
                                    onUpdateConfig(
                                        macroConfig.copy(
                                            targets = macroConfig.targets.map { tgt ->
                                                if (tgt.id == sel.id) {
                                                    tgt.copy(
                                                        xFraction = (tgt.xFraction + dx).coerceIn(0.05f, 0.95f),
                                                        yFraction = (tgt.yFraction + dy).coerceIn(0.08f, 0.92f)
                                                    )
                                                } else tgt
                                            }
                                        )
                                    )
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            DpadArrowButton(Icons.Filled.KeyboardArrowUp) { nudgeNode(0f, -nudgeStepY) }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                DpadArrowButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft) { nudgeNode(-nudgeStepX, 0f) }
                                DpadArrowButton(Icons.AutoMirrored.Filled.KeyboardArrowRight) { nudgeNode(nudgeStepX, 0f) }
                            }
                            DpadArrowButton(Icons.Filled.KeyboardArrowDown) { nudgeNode(0f, nudgeStepY) }
                        }
                    }
                }
            }
        }

        // Right-side "Pengaturan Target 1" Drawer matching Image 3!
        val editingTarget = editingTargetId?.let { id -> macroConfig.targets.find { it.id == id } }
        AnimatedVisibility(
            visible = editingTarget != null,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(200)),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            if (editingTarget != null) {
                TargetSettingsDrawer(
                    target = editingTarget,
                    onCancel = { editingTargetId = null },
                    onSave = { updatedTarget ->
                        onUpdateConfig(
                            macroConfig.copy(
                                targets = macroConfig.targets.map {
                                    if (it.id == updatedTarget.id) updatedTarget else it
                                }
                            )
                        )
                        editingTargetId = null
                    }
                )
            }
        }
    }
}

@Composable
private fun TargetSettingsDrawer(
    target: MacroTargetNode,
    onCancel: () -> Unit,
    onSave: (MacroTargetNode) -> Unit
) {
    var tapMode by remember(target.id) { mutableStateOf(target.tapMode) }
    var intervalMs by remember(target.id) { mutableIntStateOf(target.intervalMs) }
    var durationMs by remember(target.id) { mutableIntStateOf(target.durationMs) }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = 300.dp, max = 380.dp)
            .background(Color(0xF514080A))
            .border(1.dp, LvngCrimson.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .padding(20.dp)
    ) {
        Text(
            text = "Pengaturan ${target.name}",
            style = MaterialTheme.typography.titleLarge,
            color = LvngTextPrimary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Mode Tabs: Tap | Repeat Tap | Double Tap | Release Tap
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF210C10))
                .padding(4.dp)
        ) {
            MacroTapMode.entries.forEach { mode ->
                val active = tapMode == mode
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) LvngCrimson else Color.Transparent)
                        .clickable { tapMode = mode }
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) Color.White else LvngTextSecondary,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Interval Tap Slider + (- / +) steppers
        MacroIntervalSliderRow(
            label = "Interval Tap:",
            valueMs = intervalMs,
            onValueChange = { intervalMs = it.coerceIn(10, 1000) }
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Duration Tap Slider + (- / +) steppers
        MacroIntervalSliderRow(
            label = "Duration Tap:",
            valueMs = durationMs,
            onValueChange = { durationMs = it.coerceIn(10, 1000) }
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
        ) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1417)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text("BATAL", color = LvngTextPrimary, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    onSave(
                        target.copy(
                            tapMode = tapMode,
                            intervalMs = intervalMs,
                            durationMs = durationMs
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = LvngCrimson),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(44.dp)
                    .testTag("macro_target_save_button")
            ) {
                Text("SIMPAN", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MacroIntervalSliderRow(
    label: String,
    valueMs: Int,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = LvngTextPrimary
            )
            Text(
                text = "$valueMs ms",
                style = MaterialTheme.typography.titleMedium,
                color = LvngCrimsonSoft,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF350F14))
                    .clickable { onValueChange((valueMs - 5).coerceAtLeast(10)) }
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Kurangi", tint = LvngCrimsonBright, modifier = Modifier.size(18.dp))
            }

            Slider(
                value = valueMs.toFloat(),
                onValueChange = { onValueChange(it.roundToInt()) },
                valueRange = 10f..1000f,
                colors = SliderDefaults.colors(
                    thumbColor = LvngCrimsonBright,
                    activeTrackColor = LvngCrimson,
                    inactiveTrackColor = Color(0xFF31191D)
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF350F14))
                    .clickable { onValueChange((valueMs + 5).coerceAtMost(1000)) }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Tambah", tint = LvngCrimsonBright, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun MacroToolbarIconButton(
    icon: ImageVector,
    label: String?,
    tint: Color,
    onClick: () -> Unit,
    tag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF350F14))
            .border(1.dp, LvngCrimson.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(tag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label ?: "Action",
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MacroSmallSquareButton(
    icon: ImageVector,
    onClick: () -> Unit,
    tag: String
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF350F14))
            .border(1.dp, LvngCrimson.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun DpadArrowButton(
    icon: ImageVector,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF350F14))
            .clickable(onClick = onClick)
    ) {
        Icon(icon, contentDescription = null, tint = LvngCrimsonBright, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun MacroRuntimeTriggerCircle(
    triggerName: String,
    isPressed: Boolean,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        label = "triggerScale"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(86.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (isPressed) LvngCrimson.copy(alpha = 0.30f) else Color(0x14000000))
            .border(
                width = if (isPressed) 2.5.dp else 1.5.dp,
                color = if (isPressed) LvngCrimsonBright else Color.White.copy(alpha = 0.80f),
                shape = CircleShape
            )
    ) {
        Text(
            text = triggerName,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun MacroRuntimeTargetMarker(
    targetName: String,
    isPulsing: Boolean,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isPulsing) 1.24f else 1f,
        animationSpec = tween(70),
        label = "targetPulse"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (isPulsing) LvngCrimson.copy(alpha = 0.45f) else Color(0x18000000))
            .border(
                width = if (isPulsing) 2.5.dp else 1.4.dp,
                color = if (isPulsing) LvngCrimsonBright else LvngCrimson.copy(alpha = 0.82f),
                shape = CircleShape
            )
    ) {
        Text(
            text = targetName,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
