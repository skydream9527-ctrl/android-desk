package com.skydream.androiddesk.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydream.androiddesk.app.data.AppEntry
import com.skydream.androiddesk.app.data.AppIconArt
import com.skydream.androiddesk.app.data.IconLoader
import com.skydream.androiddesk.core.icon.IconStyle
import com.skydream.androiddesk.core.icon.IconTreatment

val LocalIconLoader = staticCompositionLocalOf<IconLoader?> { null }

/** The app's icon artwork, loaded off the main thread and cached; null while loading. */
@Composable
fun rememberIconArt(entry: AppEntry?): AppIconArt? {
    val loader = LocalIconLoader.current
    if (entry == null || loader == null) return null
    val generation = loader.generation
    var art by remember(entry.key, generation) { mutableStateOf(loader.peek(entry.key)) }
    LaunchedEffect(entry.key, generation) {
        if (art == null) art = loader.load(entry)
    }
    return art
}

/**
 * One app icon in the given style. The shape clips everything; the treatment decides what
 * goes inside it. [seed] keeps the Letter treatment's colour stable per app.
 */
@Composable
fun StyledIcon(
    art: AppIconArt?,
    label: String,
    seed: String,
    style: IconStyle,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = remember(style.shape) { style.shape.toShape() }
    val tint = Color(style.tintArgb)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        when (style.treatment) {
            IconTreatment.Original -> when {
                art == null -> Plate(shape, Brush.linearGradient(listOf(Color(0x40FFFFFF), Color(0x26FFFFFF))))
                art.adaptive -> Image(
                    bitmap = art.full,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().shadow(3.dp, shape).clip(shape),
                    contentScale = ContentScale.Crop,
                )
                else -> {
                    Plate(shape, Brush.linearGradient(listOf(Color.White, art.plate)), elevated = true)
                    Image(art.full, null, Modifier.fillMaxSize(LEGACY_SCALE))
                }
            }

            IconTreatment.Glass -> {
                Plate(shape, Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x24FFFFFF))))
                Box(Modifier.fillMaxSize().border(1.dp, Color(0x80FFFFFF), shape))
                Glyph(art, shape)
            }

            IconTreatment.Tinted -> {
                val base = lerp(Color(0xFF14171D), tint, 0.16f)
                Plate(shape, Brush.linearGradient(listOf(lerp(base, Color.White, 0.06f), base)), elevated = true)
                when {
                    art == null -> Unit
                    art.monochrome != null ->
                        Image(art.monochrome, null, Modifier.fillMaxSize().clip(shape), colorFilter = ColorFilter.tint(tint))
                    art.adaptive ->
                        Image(art.full, null, Modifier.fillMaxSize().clip(shape), colorFilter = luminanceTint(tint))
                    else ->
                        Image(art.full, null, Modifier.fillMaxSize(LEGACY_SCALE), colorFilter = luminanceTint(tint))
                }
            }

            IconTreatment.Gradient -> {
                val base = art?.plate ?: Color(0xFF8A94A6)
                Plate(
                    shape,
                    Brush.linearGradient(
                        listOf(lerp(base, Color.White, 0.34f), base, lerp(base, Color.Black, 0.26f)),
                    ),
                    elevated = true,
                )
                Glyph(art, shape)
            }

            IconTreatment.Outline -> {
                Plate(shape, Brush.linearGradient(listOf(Color(0x24000000), Color(0x24000000))))
                Box(Modifier.fillMaxSize().border(1.5.dp, Color(0xE6FFFFFF), shape))
                Glyph(art, shape)
            }

            IconTreatment.Letter -> {
                val hue = remember(seed) { ((seed.hashCode() % 360) + 360) % 360 }.toFloat()
                Plate(
                    shape,
                    Brush.linearGradient(listOf(Color.hsv(hue, 0.50f, 0.92f), Color.hsv((hue + 28f) % 360f, 0.66f, 0.70f))),
                    elevated = true,
                )
                val fontSize = with(LocalDensity.current) { (size * 0.44f).toSp() }
                Text(
                    text = remember(label) { firstCharacter(label) },
                    color = Color.White,
                    fontSize = fontSize,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** How much of the plate a non-adaptive (old-style) icon covers. */
private const val LEGACY_SCALE = 0.68f

@Composable
private fun Plate(shape: Shape, brush: Brush, elevated: Boolean = false) {
    val base = if (elevated) Modifier.fillMaxSize().shadow(3.dp, shape) else Modifier.fillMaxSize()
    Box(base.clip(shape).background(brush))
}

/** The app's own logo on top of a plate: the foreground layer when there is one. */
@Composable
private fun Glyph(art: AppIconArt?, shape: Shape) {
    if (art == null) return
    if (art.foreground != null) {
        Image(art.foreground, null, Modifier.fillMaxSize().clip(shape))
    } else {
        Image(art.full, null, Modifier.fillMaxSize(LEGACY_SCALE))
    }
}

/** Greyscale, then coloured with [tint]: keeps the logo's light and dark, drops its hues. */
private fun luminanceTint(tint: Color): ColorFilter {
    val k = 1.1f
    val lift = 28f
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.299f * tint.red * k, 0.587f * tint.red * k, 0.114f * tint.red * k, 0f, lift * tint.red,
                0.299f * tint.green * k, 0.587f * tint.green * k, 0.114f * tint.green * k, 0f, lift * tint.green,
                0.299f * tint.blue * k, 0.587f * tint.blue * k, 0.114f * tint.blue * k, 0f, lift * tint.blue,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

private fun firstCharacter(label: String): String {
    val trimmed = label.trim()
    if (trimmed.isEmpty()) return "?"
    return String(Character.toChars(trimmed.codePointAt(0))).uppercase()
}

private val labelOnWallpaper = TextStyle(
    fontSize = 12.sp,
    lineHeight = 14.sp,
    textAlign = TextAlign.Center,
    shadow = Shadow(color = Color(0x99000000), offset = Offset(0f, 1f), blurRadius = 4f),
)

/** Icon plus its name, as it sits on the desktop and in the drawer. */
@Composable
fun IconCell(
    entry: AppEntry?,
    label: String,
    seed: String,
    style: IconStyle,
    baseIconSize: Dp,
    modifier: Modifier = Modifier,
    showLabel: Boolean = style.showLabel,
    labelColor: Color = DeskColors.onWallpaper,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        StyledIcon(rememberIconArt(entry), label, seed, style, baseIconSize * (style.sizePercent / 100f))
        if (showLabel) {
            Text(
                text = label,
                color = labelColor,
                style = labelOnWallpaper,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
            )
        }
    }
}
