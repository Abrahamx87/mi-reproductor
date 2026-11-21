package com.abraham.opsound.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.abraham.opsound.CustomCovers
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.abraham.opsound.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Memoria de portadas ya cargadas, para que la lista no las vuelva a leer al hacer scroll. */
private object ArtCache {
    val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    val blurred = LruCache<String, ImageBitmap>(12)
}

/** Carga la portada que viene dentro del archivo de la canción. */
@Composable
fun rememberArtwork(song: Song?, sizePx: Int): ImageBitmap? {
    val context = LocalContext.current
    val coverVersion = CustomCovers.version
    val key = song?.let { "${it.id}:$sizePx:$coverVersion" }
    val art by produceState(initialValue = key?.let { ArtCache.cache.get(it) }, key) {
        if (song == null || key == null) {
            value = null
            return@produceState
        }
        ArtCache.cache.get(key)?.let { value = it; return@produceState }
        value = withContext(Dispatchers.IO) {
            runCatching {
                // Primero la portada que tú elegiste; si no hay, la que trae el archivo.
                val custom = CustomCovers.file(song.id)
                if (custom != null) {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(custom.path, opts)
                    var sample = 1
                    while (opts.outWidth / (sample * 2) >= sizePx) sample *= 2
                    BitmapFactory.decodeFile(custom.path, BitmapFactory.Options().apply { inSampleSize = sample })!!
                        .asImageBitmap()
                } else {
                    context.contentResolver
                        .loadThumbnail(song.uri, Size(sizePx, sizePx), null)
                        .asImageBitmap()
                }
            }.getOrNull()
        }?.also { ArtCache.cache.put(key, it) }
    }
    return art
}

/**
 * Fondo borroso calculado UNA sola vez por canción (no en cada cuadro):
 * se achica la portada y se desenfoca; al estirarla en pantalla queda suave.
 */
@Composable
fun rememberBlurredBackground(song: Song?, cover: ImageBitmap?): ImageBitmap? {
    // La clave incluye la portada exacta, para nunca mezclar el fondo de una portada vieja.
    val id = if (song != null && cover != null) "${song.id}:${System.identityHashCode(cover)}" else null
    val bg by produceState(initialValue = id?.let { ArtCache.blurred.get(it) }, id, cover) {
        if (id == null || cover == null) {
            value = null
            return@produceState
        }
        ArtCache.blurred.get(id)?.let { value = it; return@produceState }
        value = withContext(Dispatchers.Default) {
            runCatching { blurSmall(cover.asAndroidBitmap()).asImageBitmap() }.getOrNull()
        }?.also { ArtCache.blurred.put(id, it) }
    }
    return bg
}

private fun blurSmall(source: Bitmap): Bitmap {
    val soft = if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) else source
    val n = 64
    val small = Bitmap.createScaledBitmap(soft, n, n, true)
    val px = IntArray(n * n)
    small.getPixels(px, 0, n, 0, 0, n, n)
    repeat(3) {
        boxBlur(px, n, radius = 4, horizontal = true)
        boxBlur(px, n, radius = 4, horizontal = false)
    }
    val out = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
    out.setPixels(px, 0, n, 0, 0, n, n)
    return out
}

private fun boxBlur(px: IntArray, n: Int, radius: Int, horizontal: Boolean) {
    val tmp = IntArray(n)
    for (line in 0 until n) {
        for (i in 0 until n) {
            var r = 0; var g = 0; var b = 0; var count = 0
            for (k in -radius..radius) {
                val j = (i + k).coerceIn(0, n - 1)
                val c = if (horizontal) px[line * n + j] else px[j * n + line]
                r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
                count++
            }
            tmp[i] = (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
        }
        for (i in 0 until n) {
            if (horizontal) px[line * n + i] = tmp[i] else px[i * n + line] = tmp[i]
        }
    }
}
