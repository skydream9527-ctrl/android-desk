package com.skydream.androiddesk.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.skydream.androiddesk.app.DeskController
import com.skydream.androiddesk.app.DeskUiState
import com.skydream.androiddesk.app.data.AppEntry
import com.skydream.androiddesk.core.icon.IconStyle
import com.skydream.androiddesk.core.icon.IconTints
import com.skydream.androiddesk.core.icon.IconTreatment
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconStyleScreen(state: DeskUiState, controller: DeskController, onBack: () -> Unit) {
    val global = state.iconTheme.global
    val previewApps = remember(state.layout.items, state.apps) {
        state.layout.items
            .sortedWith(compareBy({ it.position.pageIndex }, { it.position.cellY }, { it.position.cellX }))
            .asSequence().mapNotNull { state.entryFor(it) }.distinctBy { it.key }.take(4).toList()
            .ifEmpty { state.apps.take(4) }
    }
    val sample = previewApps.firstOrNull()?.let { StyleSample(it, rememberIconArt(it)) }
    val overrideCount = state.iconTheme.overrides.size
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    fun changeStyle(update: IconStyle.() -> IconStyle) {
        controller.setGlobalIconStyle(controller.state.value.iconTheme.global.update())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("图标样式") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize().padding(horizontal = 16.dp)) {
                StylePreview(previewApps, state)
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp).verticalScroll(rememberScrollState())
                        .padding(top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    IconShapePicker(global) { shape -> changeStyle { copy(shape = shape) } }
                    IconTreatmentPicker(global, sample) { treatment -> changeStyle { copy(treatment = treatment) } }
                    if (global.treatment == IconTreatment.Tinted) {
                        TintPicker(global.tintArgb) { tint -> changeStyle { copy(tintArgb = tint) } }
                    }
                    SizePicker(global.sizePercent) { size -> changeStyle { copy(sizePercent = size) } }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .toggleable(global.showLabel, role = Role.Switch, onValueChange = { visible ->
                                changeStyle { copy(showLabel = visible) }
                            }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("显示应用名称", Modifier.weight(1f))
                        Switch(checked = global.showLabel, onCheckedChange = null)
                    }
                    if (overrideCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "有 ${overrideCount} 个应用使用单独样式",
                                Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(onClick = { confirmClear = true }) { Text("全部清除") }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear && overrideCount > 0) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除单独样式？") },
            text = { Text("${overrideCount} 个应用会改回跟随全局样式。") },
            confirmButton = {
                TextButton(onClick = {
                    controller.clearIconOverrides()
                    confirmClear = false
                }) { Text("全部清除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun StylePreview(apps: List<AppEntry>, state: DeskUiState) {
    val wallpaper = Brush.linearGradient(listOf(Color(0xFF243B55), Color(0xFF141E30)))
    Row(
        Modifier.fillMaxWidth().height(148.dp).clip(RoundedCornerShape(24.dp)).background(wallpaper)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(4) { index ->
            BoxWithConstraints(
                Modifier.weight(1f).height(132.dp),
                contentAlignment = Alignment.Center,
            ) {
                val entry = apps.getOrNull(index)
                if (entry != null) {
                    val baseSize = minOf(maxWidth * 0.62f, maxHeight * 0.56f, 64.dp).coerceAtLeast(36.dp)
                    IconCell(entry, entry.label, entry.key, state.styleFor(entry), baseSize, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private val tintLabels = listOf("蓝", "绿", "琥珀", "珊瑚", "紫", "近白")

@Composable
private fun TintPicker(selected: Long, onSelect: (Long) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Text("主题色", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            IconTints.presets.forEachIndexed { index, argb ->
                val active = selected == argb
                Box(
                    Modifier.weight(1f).height(56.dp)
                        .selectable(active, role = Role.RadioButton, onClick = { onSelect(argb) })
                        .semantics { contentDescription = tintLabels[index] },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(36.dp)
                            .border(if (active) 2.dp else 1.dp, if (active) scheme.primary else scheme.outline, CircleShape)
                            .padding(if (active) 4.dp else 1.dp)
                            .background(Color(argb), CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun SizePicker(size: Int, onChange: (Int) -> Unit) {
    val step = 5
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("大小", style = MaterialTheme.typography.titleMedium)
            Text("$size%")
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("小")
            Slider(
                value = size.toFloat(),
                onValueChange = { onChange((it / step).roundToInt() * step) },
                valueRange = IconStyle.MIN_SIZE.toFloat()..IconStyle.MAX_SIZE.toFloat(),
                steps = (IconStyle.MAX_SIZE - IconStyle.MIN_SIZE) / step - 1,
                modifier = Modifier.weight(1f).semantics { contentDescription = "大小" },
            )
            Text("大")
        }
    }
}
