package com.skydream.androiddesk.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Colours for things drawn straight on the wallpaper, where the Material scheme does not apply. */
object DeskColors {
    /** Dock, banners and pills: see-through so the wallpaper still reads. */
    val chrome = Color(0x66101318)
    val chromeStrong = Color(0xCC14171D)
    val chromeBorder = Color(0x33FFFFFF)
    val onWallpaper = Color.White
    val onWallpaperDim = Color(0xCCFFFFFF)
    val accent = Color(0xFF7FB0FF)
    val danger = Color(0xFFE5484D)
    val previewBadge = Color(0xFFF2A33A)
    val ok = Color(0xFF34C38F)
    val drawerScrim = Color(0xE60E1116)
}

/** Follows the system's light/dark setting and, on Android 12+, its wallpaper colours. */
@Composable
fun DeskTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
