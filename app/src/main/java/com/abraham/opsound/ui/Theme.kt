package com.abraham.opsound.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Azul de Opsound, cuando no hay portada. */
val AccentDefault = Color(0xFF4FC3F7)

val LocalAccent = compositionLocalOf { AccentDefault }

/** Color de acento actual: cambia según la portada de la canción que suena. */
val Accent: Color
    @Composable @ReadOnlyComposable
    get() = LocalAccent.current

/** Saca un color bonito de la portada y lo ajusta para que se lea bien sobre fondo oscuro. */
@Composable
fun rememberDynamicAccent(cover: ImageBitmap?): State<Color> {
    val target by produceState(AccentDefault, cover) {
        value = if (cover == null) AccentDefault else withContext(Dispatchers.Default) {
            runCatching {
                val bmp = cover.asAndroidBitmap().let {
                    if (it.config == android.graphics.Bitmap.Config.HARDWARE) it.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else it
                }
                val palette = Palette.from(bmp).maximumColorCount(16).generate()
                val swatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch
                    ?: palette.mutedSwatch ?: palette.dominantSwatch
                swatch?.let { readable(it.rgb) } ?: AccentDefault
            }.getOrDefault(AccentDefault)
        }
    }
    return animateColorAsState(target, tween(600), label = "acento")
}

private fun readable(rgb: Int): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(rgb, hsl)
    // Portadas grises: usamos el azul de siempre.
    if (hsl[1] < 0.18f) return AccentDefault
    hsl[1] = hsl[1].coerceIn(0.45f, 0.9f)
    hsl[2] = hsl[2].coerceIn(0.62f, 0.75f)
    return Color(ColorUtils.HSLToColor(hsl))
}

@Suppress("unused")
private fun Color.argb() = toArgb()
