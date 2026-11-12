package com.skydream.androiddesk.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.skydream.androiddesk.app.DeskController
import com.skydream.androiddesk.app.DeskUiState
import com.skydream.androiddesk.app.data.AppEntry
import com.skydream.androiddesk.app.data.AppIconArt
import com.skydream.androiddesk.core.icon.IconShape
import com.skydream.androiddesk.core.icon.IconStyle
import com.skydream.androiddesk.core.icon.IconTreatment

@Composable
fun AppIconStyleSection(entry: AppEntry, state: DeskUiState, controller: DeskController) {
    val theme = state.iconTheme
    val override = theme.overrideFor(entry.key)
    val sample = StyleSample(entry, rememberIconArt(entry))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("图标样式", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (!override.isEmpty) {
                TextButton(onClick = { controller.setIconOverride(entry.key, null) }) {
                    Text("跟随全局")
                }
            }
        }
        IconShapePicker(theme.styleFor(entry.key), sample, override.shape) { selection ->
            val latest = controller.state.value.iconTheme
            val current = latest.overrideFor(entry.key)
            val shape = selection.takeUnless { it == current.shape || it == latest.global.shape }
            controller.setIconOverride(entry.key, current.copy(shape = shape))
        }
        IconTreatmentPicker(theme.styleFor(entry.key), sample, override.treatment, appSpecific = true) { selection ->
            val latest = controller.state.value.iconTheme
            val current = latest.overrideFor(entry.key)
            val treatment = selection.takeUnless { it == current.treatment || it == latest.global.treatment }
            controller.setIconOverride(entry.key, current.copy(treatment = treatment))
        }
    }
}

internal data class StyleSample(val entry: AppEntry, val art: AppIconArt?)

@Composable
internal fun IconShapePicker(
    style: IconStyle,
    sample: StyleSample? = null,
    override: IconShape? = null,
    onSelect: (IconShape) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("外形", style = MaterialTheme.typography.titleMedium)
        if (sample != null) {
            AppStyleOptions(IconShape.entries, style.shape, override, IconShape::label, onSelect) { shape ->
                SampleIcon(sample, style.copy(shape = shape), 44)
            }
        } else {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IconShape.entries.chunked(3).forEach { shapes ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        shapes.forEach { shape ->
                            val selected = shape == style.shape
                            val tile = RoundedCornerShape(16.dp)
                            val scheme = MaterialTheme.colorScheme
                            Box(
                                Modifier.weight(1f).heightIn(min = 84.dp).clip(tile)
                                    .background(scheme.surfaceVariant)
                                    .then(if (selected) Modifier.border(2.dp, scheme.primary, tile) else Modifier)
                                    .selectable(selected, role = Role.RadioButton, onClick = { onSelect(shape) })
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Box(Modifier.size(40.dp).background(scheme.primary, shape.toShape()))
                                    Text(shape.label, style = MaterialTheme.typography.labelMedium)
                                }
                                if (selected) SelectionCheck(Modifier.align(Alignment.TopEnd))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun IconTreatmentPicker(
    style: IconStyle,
    sample: StyleSample?,
    override: IconTreatment? = null,
    appSpecific: Boolean = false,
    onSelect: (IconTreatment) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("风格", style = MaterialTheme.typography.titleMedium)
        if (appSpecific) {
            AppStyleOptions(IconTreatment.entries, style.treatment, override, IconTreatment::label, onSelect) { treatment ->
                SampleIcon(sample, style.copy(treatment = treatment), 44)
            }
        } else {
            val scheme = MaterialTheme.colorScheme
            val selectedSurface = scheme.primary.copy(alpha = 0.12f).compositeOver(scheme.surface)
            Column(Modifier.clip(RoundedCornerShape(16.dp)).selectableGroup()) {
                IconTreatment.entries.forEach { treatment ->
                    val selected = treatment == style.treatment
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 72.dp)
                            .background(if (selected) selectedSurface else scheme.surfaceVariant)
                            .selectable(selected, role = Role.RadioButton, onClick = { onSelect(treatment) })
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SampleIcon(sample, style.copy(treatment = treatment), 40)
                        Column(Modifier.weight(1f)) {
                            Text(treatment.label, style = MaterialTheme.typography.bodyLarge)
                            Text(treatment.hint, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                        if (selected) SelectionCheck()
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> AppStyleOptions(
    options: List<T>,
    selected: T,
    override: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    sample: @Composable (T) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        options.forEach { option ->
            val isSelected = selected == option
            val tile = RoundedCornerShape(12.dp)
            Column(
                Modifier.widthIn(min = 52.dp).clip(tile)
                    .selectable(isSelected, role = Role.RadioButton, onClick = { onSelect(option) }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(52.dp)
                        .then(if (isSelected) Modifier.border(2.dp, scheme.primary, tile) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    sample(option)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    label(option),
                    color = if (option == override) scheme.primary else scheme.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun SampleIcon(sample: StyleSample?, style: IconStyle, size: Int) {
    val scheme = MaterialTheme.colorScheme
    if (sample == null) {
        Box(
            Modifier.size(size.dp).background(scheme.primaryContainer, style.shape.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size((size / 3).dp).background(scheme.primary, CircleShape))
        }
    } else {
        StyledIcon(sample.art, sample.entry.label, sample.entry.key, style, size.dp)
    }
}

@Composable
private fun SelectionCheck(modifier: Modifier = Modifier) {
    Icon(Icons.Filled.Check, null, modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
}
