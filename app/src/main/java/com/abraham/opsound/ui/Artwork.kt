package com.abraham.opsound.ui

import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.abraham.opsound.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Carga la portada que viene dentro del archivo de la canción. */
@Composable
fun rememberArtwork(song: Song?, sizePx: Int): ImageBitmap? {
    val context = LocalContext.current
    val art by produceState<ImageBitmap?>(initialValue = null, song?.id, sizePx) {
        value = if (song == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver
                        .loadThumbnail(song.uri, Size(sizePx, sizePx), null)
                        .asImageBitmap()
                }.getOrNull()
            }
        }
    }
    return art
}
