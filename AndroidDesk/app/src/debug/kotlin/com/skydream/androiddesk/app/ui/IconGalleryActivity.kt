package com.skydream.androiddesk.app.ui

import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skydream.androiddesk.app.DeskGraph
import com.skydream.androiddesk.app.data.AppEntry
import com.skydream.androiddesk.app.data.AppIconArt
import com.skydream.androiddesk.app.data.IconLoader
import com.skydream.androiddesk.core.icon.IconShape
import com.skydream.androiddesk.core.icon.IconStyle
import com.skydream.androiddesk.core.icon.IconTreatment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Open with adb shell am start -n com.skydream.androiddesk/.app.ui.IconGalleryActivity. */
class IconGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val controller = DeskGraph.controller(this)
        setContent {
            val apps by controller.catalog.apps.collectAsStateWithLifecycle()
            var samples by remember { mutableStateOf<Map<GalleryKind, GallerySample>?>(null) }
            LaunchedEffect(apps, controller.icons.generation) {
                samples = apps?.let { entries ->
                    findSamples(entries, controller.icons) { entry ->
                        controller.catalog.activityInfo(entry)?.getIcon(0) is BitmapDrawable
                    }
                }
            }
            DeskTheme { IconGallery(samples) }
        }
    }
}

private enum class GalleryKind(val title: String) {
    Adaptive("自适应图标"),
    Legacy("旧式位图图标"),
    Themed("主题图标"),
}

private data class GallerySample(val entry: AppEntry, val art: AppIconArt)

private suspend fun findSamples(
    entries: List<AppEntry>,
    loader: IconLoader,
    isBitmap: (AppEntry) -> Boolean,
): Map<GalleryKind, GallerySample> = withContext(Dispatchers.Default) {
    buildMap {
        // Prefer recognizable system apps when they are installed. Classify from the
        // actual launcher artwork, since the same package can differ across Android images.
        val preferred = listOf("com.android.settings", "com.android.dialer", "com.android.camera2")
        for (entry in entries.sortedBy { preferred.indexOf(it.packageName).let { index -> if (index < 0) preferred.size else index } }) {
            val art = loader.load(entry) ?: continue
            val kind = when {
                art.monochrome != null -> GalleryKind.Themed
                art.adaptive -> GalleryKind.Adaptive
                GalleryKind.Legacy !in this && runCatching { isBitmap(entry) }.getOrDefault(false) -> GalleryKind.Legacy
                else -> continue
            }
            putIfAbsent(kind, GallerySample(entry, art))
            if (size == GalleryKind.entries.size) break
        }
    }
}

@Composable
private fun IconGallery(samples: Map<GalleryKind, GallerySample>?) {
    var kind by rememberSaveable { mutableStateOf(GalleryKind.Adaptive) }
    var light by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("图标画廊", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GalleryKind.entries.forEach { option ->
                    FilterChip(selected = kind == option, onClick = { kind = option }, label = { Text(option.title) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = !light, onClick = { light = false }, label = { Text("深色") })
                FilterChip(selected = light, onClick = { light = true }, label = { Text("浅色") })
            }
            val sample = samples?.get(kind)
            when {
                sample != null -> {
                    Text(sample.entry.label, style = MaterialTheme.typography.titleMedium)
                    Text(sample.entry.packageName, style = MaterialTheme.typography.bodySmall)
                    GalleryGrid(sample, light)
                }
                samples != null -> Text("没有符合条件的应用")
            }
        }
    }
}

@Composable
private fun GalleryGrid(sample: GallerySample, light: Boolean) {
    val wallpaper = if (light) listOf(Color(0xFFFFFBF0), Color(0xFFC9DDEA))
    else listOf(Color(0xFF243B55), Color(0xFF141E30))
    val labelColor = if (light) Color(0xFF1C2734) else Color.White
    val rowHeaderWidth = 44.dp
    BoxWithConstraints(
        Modifier.fillMaxWidth().background(Brush.linearGradient(wallpaper)).padding(4.dp),
    ) {
        val iconSize = ((maxWidth - rowHeaderWidth) / IconTreatment.entries.size - 6.dp).coerceIn(24.dp, 54.dp)
        Column {
            Row {
                Box(Modifier.width(rowHeaderWidth))
                IconTreatment.entries.forEach { treatment ->
                    Text(treatment.label, Modifier.weight(1f), color = labelColor, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            }
            IconShape.entries.forEach { shape ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(shape.label, Modifier.width(rowHeaderWidth), color = labelColor, fontSize = 11.sp)
                    IconTreatment.entries.forEach { treatment ->
                        Box(Modifier.weight(1f).height(iconSize + 10.dp), contentAlignment = Alignment.Center) {
                            StyledIcon(
                                sample.art,
                                sample.entry.label,
                                sample.entry.key,
                                IconStyle(shape = shape, treatment = treatment),
                                iconSize,
                            )
                        }
                    }
                }
            }
        }
    }
}
