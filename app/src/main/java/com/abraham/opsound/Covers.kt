package com.abraham.opsound

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * Portadas que tú eliges desde la galería. Se guardan dentro de Opsound
 * (no modifican el archivo de música original).
 */
object CustomCovers {
    private var dir: File? = null

    /** Cambia cada vez que pones o quitas una portada, para refrescar la pantalla. */
    var version by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        if (dir == null) dir = File(context.filesDir, "covers").apply { mkdirs() }
    }

    fun file(songId: Long): File? = dir?.let { File(it, "$songId.jpg") }?.takeIf { it.exists() }

    fun has(songId: Long) = file(songId) != null || videoFile(songId) != null

    /** Portada en video (se repite en bucle, sin sonido). */
    fun videoFile(songId: Long): File? = dir?.let { File(it, "$songId.mp4") }?.takeIf { it.exists() }

    /** Copia el video elegido y guarda un cuadro como imagen (para el fondo, colores y notificación). */
    fun setVideo(context: Context, songId: Long, video: Uri) {
        init(context)
        val target = File(dir, "$songId.mp4")
        context.contentResolver.openInputStream(video)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            ?: error("No se pudo leer el video")
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(target.path)
            val frame = retriever.getFrameAtTime(500_000) ?: retriever.frameAtTime
            if (frame != null) {
                val side = minOf(frame.width, frame.height)
                val square = Bitmap.createBitmap(frame, (frame.width - side) / 2, (frame.height - side) / 2, side, side)
                File(dir, "$songId.jpg").outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            }
        } finally {
            retriever.release()
        }
        version++
    }

    /** Copia la imagen elegida, recortada en cuadrado y a buen tamaño. (Llamar fuera del hilo principal.) */
    fun set(context: Context, songId: Long, image: Uri) {
        init(context)
        val source = ImageDecoder.createSource(context.contentResolver, image)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > 1600) {
                val scale = 1600f / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
        }
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val target = File(dir, "$songId.jpg")
        target.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        File(dir, "$songId.mp4").delete() // una imagen nueva reemplaza al video
        version++
    }

    /** Portadas que vienen incluidas en Opsound (carpeta assets/covers). */
    val presets = listOf("covers/preset_1.jpg", "covers/preset_2.jpg", "covers/preset_3.jpg")

    /** Usa una de las portadas incluidas. (Llamar fuera del hilo principal.) */
    fun setPreset(context: Context, songId: Long, asset: String) {
        init(context)
        val target = File(dir, "$songId.jpg")
        context.assets.open(asset).use { input -> target.outputStream().use { input.copyTo(it) } }
        File(dir, "$songId.mp4").delete()
        version++
    }

    fun remove(songId: Long) {
        file(songId)?.delete()
        videoFile(songId)?.delete()
        version++
    }
}
