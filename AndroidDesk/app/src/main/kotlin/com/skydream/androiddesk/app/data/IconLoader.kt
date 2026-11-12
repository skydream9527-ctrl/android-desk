package com.skydream.androiddesk.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An app icon split into the pieces the icon styles need. All bitmaps are square and show
 * the same visible window, so a style can stack them without re-aligning.
 */
class AppIconArt(
    /** The complete icon, unmasked: background plus foreground for adaptive icons. */
    val full: ImageBitmap,
    /** Adaptive icons only: the logo layer on transparency. */
    val foreground: ImageBitmap?,
    /** Adaptive icons that ship a themed layer (Android 13+). */
    val monochrome: ImageBitmap?,
    val adaptive: Boolean,
    /** A plate colour that suits the logo: the icon's own background, or a pale tint of it. */
    val plate: Color,
    /** The icon's most characteristic colour. */
    val accent: Color,
)

class IconLoader(context: Context, private val catalog: AppCatalog) {
    private val sizePx = (context.resources.displayMetrics.density * 64f).toInt().coerceIn(96, 192)
    private val cache = LruCache<String, AppIconArt>(240)

    /** Bumped when icons may be stale, so composables holding an old bitmap load again. */
    var generation by mutableIntStateOf(0)
        private set

    fun peek(key: String): AppIconArt? = cache.get(key)

    suspend fun load(entry: AppEntry): AppIconArt? {
        peek(entry.key)?.let { return it }
        return withContext(Dispatchers.Default) {
            try {
                render(entry)?.also { cache.put(entry.key, it) }
            } catch (_: Exception) {
                // A broken icon resource in someone else's APK must not take the desktop down.
                null
            }
        }
    }

    fun invalidate(packageName: String) {
        cache.snapshot().keys
            .filter { it == packageName || it.startsWith("$packageName/") }
            .forEach { cache.remove(it) }
        generation++
    }

    private fun render(entry: AppEntry): AppIconArt? {
        val drawable = catalog.activityInfo(entry)?.getIcon(0) ?: return null
        if (drawable is AdaptiveIconDrawable) {
            val background = drawable.background
            val foreground = drawable.foreground
            val full = layers(background, foreground)
            val monochrome = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) drawable.monochrome else null
            val accent = characteristicColor(full)
            val base = if (background != null) averageColor(layers(background)) else Color.White
            return AppIconArt(
                full = full.asImageBitmap(),
                foreground = foreground?.let { layers(it).asImageBitmap() },
                monochrome = monochrome?.let { layers(it).asImageBitmap() },
                adaptive = true,
                plate = base,
                accent = accent,
            )
        }
        val full = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(Canvas(full))
        val accent = characteristicColor(full)
        return AppIconArt(
            full = full.asImageBitmap(),
            foreground = null,
            monochrome = null,
            adaptive = false,
            plate = lerp(Color.White, accent, 0.22f),
            accent = accent,
        )
    }

    /**
     * Draws adaptive-icon layers the way the system does: each layer is 108 units wide and
     * only the middle 72 are visible, so the layer overhangs the bitmap by a quarter on
     * every side.
     */
    private fun layers(vararg layers: Drawable?): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val overhang = sizePx / 4
        for (layer in layers) {
            layer ?: continue
            layer.setBounds(-overhang, -overhang, sizePx + overhang, sizePx + overhang)
            layer.draw(canvas)
        }
        return bitmap
    }

    private fun averageColor(bitmap: Bitmap): Color = weightedColor(bitmap) { _, _ -> 1f }

    /** Average weighted towards saturated pixels, so a logo on white still yields the logo's colour. */
    private fun characteristicColor(bitmap: Bitmap): Color =
        weightedColor(bitmap) { max, min -> 0.08f + if (max == 0f) 0f else (max - min) / max }

    private inline fun weightedColor(bitmap: Bitmap, weight: (max: Float, min: Float) -> Float): Color {
        val small = Bitmap.createScaledBitmap(bitmap, 16, 16, true)
        val pixels = IntArray(16 * 16)
        small.getPixels(pixels, 0, 16, 0, 0, 16, 16)
        var r = 0f
        var g = 0f
        var b = 0f
        var total = 0f
        for (p in pixels) {
            val a = (p ushr 24) / 255f
            if (a < 0.2f) continue
            val pr = ((p shr 16) and 0xFF) / 255f
            val pg = ((p shr 8) and 0xFF) / 255f
            val pb = (p and 0xFF) / 255f
            val w = a * weight(maxOf(pr, pg, pb), minOf(pr, pg, pb))
            r += pr * w
            g += pg * w
            b += pb * w
            total += w
        }
        return if (total <= 0f) Color(0xFF8A94A6) else Color(r / total, g / total, b / total)
    }
}
