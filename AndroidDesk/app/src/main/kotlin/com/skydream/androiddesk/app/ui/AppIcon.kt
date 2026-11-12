package com.skydream.androiddesk.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydream.androiddesk.app.data.AppEntry
import com.skydream.androiddesk.app.data.AppIconArt
import com.skydream.androiddesk.app.data.IconLoader
import com.skydream.androiddesk.core.icon.IconShape
import com.skydream.androiddesk.core.icon.IconStyle
import com.skydream.androiddesk.core.icon.IconTreatment
import kotlin.math.roundToInt

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
        if (art == null) {
            Plate(shape, Brush.linearGradient(listOf(Color(0x40FFFFFF), Color(0x26FFFFFF))))
            return@Box
        }
        when (style.treatment) {
            IconTreatment.Original -> when {
                art.adaptive -> Image(
                    bitmap = art.full,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().shadow(3.dp, shape).clip(shape),
                    contentScale = ContentScale.Crop,
                )
                else -> {
                    Plate(shape, Brush.linearGradient(listOf(Color.White, art.plate)), elevated = true)
                    Glyph(art, style.shape)
                }
            }

            IconTreatment.Glass -> {
                Plate(shape, Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x24FFFFFF))))
                Glyph(art, style.shape, contrastEdge = true)
                WallpaperEdge(shape, 1.dp, Color(0x99FFFFFF))
            }

            IconTreatment.Tinted -> {
                val base = lerp(Color(0xFF14171D), tint, 0.16f)
                Plate(shape, Brush.linearGradient(listOf(base, base)), elevated = true)
                when {
                    art.monochrome != null ->
                        Artwork(art.monochrome, shape, 1f, ColorFilter.tint(tint))
                    art.adaptive ->
                        Artwork(
                            art.full,
                            shape,
                            1f,
                            luminanceTint(base, tint, inverted = grayLevel(art.plate) > 0.55f),
                        )
                    else ->
                        Artwork(art.full, shape, legacyScale(style.shape), luminanceTint(lerp(base, tint, 0.46f), tint))
                }
            }

            IconTreatment.Gradient -> {
                val base = art.plate
                val colors = remember(art) {
                    listOf(lerp(base, Color.White, 0.34f), base, lerp(base, Color.Black, 0.26f))
                        .map { contrastStop(it, art.glyphLuminance) }
                }
                Plate(
                    shape,
                    Brush.linearGradient(colors),
                    elevated = true,
                )
                Glyph(art, style.shape)
            }

            IconTreatment.Outline -> {
                Plate(shape, Brush.linearGradient(listOf(Color(0x24000000), Color(0x24000000))))
                Glyph(art, style.shape, contrastEdge = true)
                WallpaperEdge(shape, 1.5.dp, Color(0xE6FFFFFF))
            }

            IconTreatment.Letter -> {
                val colors = remember(seed) {
                    val hue = Math.floorMod(seed.hashCode(), 360).toFloat()
                    listOf(Color.hsv(hue, 0.50f, 0.92f), Color.hsv((hue + 28f) % 360f, 0.66f, 0.70f))
                        .map { contrastStop(it, glyphLuminance = 1f) }
                }
                Plate(
                    shape,
                    Brush.linearGradient(colors),
                    elevated = true,
                )
                val fontSize = with(LocalDensity.current) { (size * 0.44f).toSp() }
                Text(
                    text = remember(label) { firstCharacter(label) },
                    color = Color.White,
                    fontSize = fontSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}

/** The hexagon's sloping sides need more clearance for square legacy artwork. */
private fun legacyScale(shape: IconShape): Float = if (shape == IconShape.Hexagon) 0.62f else 0.68f

@Composable
private fun Plate(shape: Shape, brush: Brush, elevated: Boolean = false) {
    val base = if (elevated) Modifier.fillMaxSize().shadow(3.dp, shape) else Modifier.fillMaxSize()
    Box(base.clip(shape).background(brush))
}

@Composable
private fun WallpaperEdge(shape: Shape, width: Dp, color: Color) {
    Box(
        Modifier.fillMaxSize()
            .border(0.75.dp, Color(0x90101925), shape)
            .padding(0.75.dp)
            .border(width, color, shape),
    )
}

/** Draws the actual logo and adds a small contrasting edge when the plate shows wallpaper. */
@Composable
private fun Glyph(art: AppIconArt, shape: IconShape, contrastEdge: Boolean = false) {
    val glyph = art.foreground
    val halo = when {
        !contrastEdge -> null
        art.glyphLuminance >= 0.24f -> Color(0x99091019)
        else -> Color(0x80FFFFFF)
    }
    Artwork(glyph ?: art.full, shape.toShape(), if (glyph != null) 1f else legacyScale(shape), halo = halo)
}

@Composable
private fun Artwork(
    image: ImageBitmap,
    shape: Shape,
    fraction: Float,
    filter: ColorFilter? = null,
    halo: Color? = null,
) {
    Box(
        Modifier.fillMaxSize().clip(shape).drawWithCache {
            val scale = minOf(size.width / image.width, size.height / image.height) * fraction
            val extent = IntSize((image.width * scale).roundToInt(), (image.height * scale).roundToInt())
            val origin = IntOffset(
                ((size.width - extent.width) / 2f).roundToInt(),
                ((size.height - extent.height) / 2f).roundToInt(),
            )
            val haloFilter = halo?.let { ColorFilter.tint(it) }
            val edge = 0.75.dp.roundToPx().coerceAtLeast(1)
            val shifts = if (haloFilter == null) emptyList() else
                listOf(IntOffset(-edge, 0), IntOffset(edge, 0), IntOffset(0, -edge), IntOffset(0, edge))
            onDrawBehind {
                if (extent.width > 0 && extent.height > 0) {
                    shifts.forEach { shift ->
                        drawImage(
                            image, dstOffset = origin + shift, dstSize = extent,
                            colorFilter = haloFilter, filterQuality = FilterQuality.Medium,
                        )
                    }
                    drawImage(image, dstOffset = origin, dstSize = extent, colorFilter = filter, filterQuality = FilterQuality.Medium)
                }
            }
        },
    )
}

private fun contrastStop(stop: Color, glyphLuminance: Float): Color {
    val toward = if (glyphLuminance >= 0.18f) Color.Black else Color.White
    var color = stop
    repeat(8) {
        val luminance = color.luminance()
        val contrast = (maxOf(luminance, glyphLuminance) + 0.05f) / (minOf(luminance, glyphLuminance) + 0.05f)
        if (contrast >= 3f) return color
        color = lerp(color, toward, 0.20f)
    }
    return color
}

private fun grayLevel(color: Color): Float = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue

/** Maps grayscale to a dark plate and a tinted glyph, retaining the original alpha. */
private fun luminanceTint(dark: Color, light: Color, inverted: Boolean = false): ColorFilter {
    val start = if (inverted) light else dark
    val end = if (inverted) dark else light
    val red = end.red - start.red
    val green = end.green - start.green
    val blue = end.blue - start.blue
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.299f * red, 0.587f * red, 0.114f * red, 0f, 255f * start.red,
                0.299f * green, 0.587f * green, 0.114f * green, 0f, 255f * start.green,
                0.299f * blue, 0.587f * blue, 0.114f * blue, 0f, 255f * start.blue,
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
    val labelSpace = if (showLabel) with(LocalDensity.current) { labelOnWallpaper.lineHeight.toDp() } + 6.dp else 0.dp
    val accessibleModifier = if (showLabel) modifier else modifier.semantics { contentDescription = label }
    BoxWithConstraints(accessibleModifier, contentAlignment = Alignment.Center) {
        val requestedSize = baseIconSize * (style.sizePercent / 100f)
        val iconSize = minOf(requestedSize, maxWidth, maxHeight - labelSpace).coerceAtLeast(0.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StyledIcon(rememberIconArt(entry), label, seed, style, iconSize)
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
}
