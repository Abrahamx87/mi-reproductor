package com.abraham.opsound

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import java.io.File

/** Estilos de color de la tarjeta (como el selector de colores de Spotify). */
enum class CardStyle(val label: String) { COVER("Portada"), DARK("Oscuro"), LIGHT("Claro"), VIVID("Vivo") }

/**
 * Crea la imagen para compartir la letra, del tamaño de una historia de Instagram (1080 × 1920):
 * portada, nombre de la canción, la letra elegida y la marca de Opsound.
 */
object LyricsCard {
    const val MAX_CHARS = 150

    fun render(
        context: Context,
        cover: Bitmap?,
        lines: List<String>,
        title: String,
        artist: String,
        style: CardStyle,
    ): Bitmap {
        val w = 1080
        val h = 1920
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val soft = cover?.let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }

        // Colores sacados de la portada.
        val palette = soft?.let { runCatching { Palette.from(it).generate() }.getOrNull() }
        val vibrant = palette?.vibrantSwatch?.rgb ?: palette?.dominantSwatch?.rgb ?: 0xFF1E6FA8.toInt()
        val dark = palette?.darkMutedSwatch?.rgb ?: palette?.darkVibrantSwatch?.rgb ?: 0xFF0B0F14.toInt()

        val bgTop: Int
        val bgBottom: Int
        val cardColor: Int
        val textColor: Int
        when (style) {
            CardStyle.COVER -> { bgTop = vibrant; bgBottom = dark; cardColor = ColorUtils.setAlphaComponent(Color.BLACK, 110); textColor = Color.WHITE }
            CardStyle.DARK -> { bgTop = 0xFF1A2129.toInt(); bgBottom = 0xFF05080B.toInt(); cardColor = ColorUtils.setAlphaComponent(Color.WHITE, 18); textColor = Color.WHITE }
            CardStyle.LIGHT -> { bgTop = 0xFFF4F1EC.toInt(); bgBottom = 0xFFDCD6CC.toInt(); cardColor = Color.WHITE; textColor = 0xFF111111.toInt() }
            CardStyle.VIVID -> {
                val hsl = FloatArray(3).also { ColorUtils.colorToHSL(vibrant, it) }
                hsl[1] = hsl[1].coerceAtLeast(0.6f); hsl[2] = 0.42f
                val v = ColorUtils.HSLToColor(hsl)
                bgTop = v; bgBottom = ColorUtils.blendARGB(v, Color.BLACK, 0.35f); cardColor = ColorUtils.setAlphaComponent(Color.BLACK, 60); textColor = Color.WHITE
            }
        }

        // Fondo: degradado (y la portada muy desenfocada en el estilo "Portada").
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), bgTop, bgBottom, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
        if (style == CardStyle.COVER && soft != null) {
            // Portada muy difuminada que cubre TODA la historia (sin franjas ni cortes).
            val side = minOf(soft.width, soft.height)
            val square = Bitmap.createBitmap(soft, (soft.width - side) / 2, (soft.height - side) / 2, side, side)
            var blurred = Bitmap.createScaledBitmap(square, 20, 20, true)
            // Varias pasadas de agrandado suave: el difuminado queda liso, sin cuadritos.
            for (size in listOf(60, 180, 540)) blurred = Bitmap.createScaledBitmap(blurred, size, size, true)
            val cover = RectF((w - h) / 2f, 0f, (w + h) / 2f, h.toFloat()) // cuadro del alto de la historia, centrado
            c.drawBitmap(blurred, null, cover, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            // Velo oscuro suave para que la letra se lea bien.
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, h.toFloat(),
                    intArrayOf(ColorUtils.setAlphaComponent(Color.BLACK, 60), ColorUtils.setAlphaComponent(Color.BLACK, 90), ColorUtils.setAlphaComponent(Color.BLACK, 150)),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP,
                )
            })
        }

        val bold = ResourcesCompat.getFont(context, R.font.montserrat_bold) ?: Typeface.DEFAULT_BOLD
        val semi = ResourcesCompat.getFont(context, R.font.montserrat_semibold) ?: Typeface.DEFAULT_BOLD

        // Medidas de la tarjeta.
        val cardW = 900
        val pad = 64
        val coverSize = 150
        val textW = cardW - pad * 2

        val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor; typeface = bold; textSize = 66f }
        val lyricText = lines.joinToString("\n")
        val lyricLayout = StaticLayout.Builder.obtain(lyricText, 0, lyricText.length, lyricPaint, textW)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(10f, 1f)
            .build()

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor; typeface = bold; textSize = 40f }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(textColor, 180); typeface = semi; textSize = 34f }
        val headerTextW = (cardW - pad * 2 - coverSize - 32).toFloat()
        val titleText = TextUtils.ellipsize(title, titlePaint, headerTextW, TextUtils.TruncateAt.END).toString()
        val artistText = TextUtils.ellipsize(artist, artistPaint, headerTextW, TextUtils.TruncateAt.END).toString()

        val cardH = pad + coverSize + 56 + lyricLayout.height + 64 + 44 + pad
        val left = (w - cardW) / 2f
        val top = (h - cardH) / 2f
        val card = RectF(left, top, left + cardW, top + cardH)
        c.drawRoundRect(card, 56f, 56f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardColor })

        // Portada + título + artista.
        val coverRect = RectF(left + pad, top + pad, left + pad + coverSize, top + pad + coverSize)
        if (soft != null) {
            c.save()
            c.clipPath(Path().apply { addRoundRect(coverRect, 20f, 20f, Path.Direction.CW) })
            c.drawBitmap(soft, null, coverRect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            c.restore()
        } else {
            c.drawRoundRect(coverRect, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(textColor, 40) })
        }
        val textX = coverRect.right + 32
        c.drawText(titleText, textX, coverRect.top + 62, titlePaint)
        c.drawText(artistText, textX, coverRect.top + 116, artistPaint)

        // La letra.
        c.save()
        c.translate(left + pad, coverRect.bottom + 56)
        lyricLayout.draw(c)
        c.restore()

        // Marca de Opsound.
        val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(textColor, 170); typeface = bold; textSize = 34f }
        c.drawText("♪ Abraham CT Music", left + pad, card.bottom - pad, brand)
        return out
    }

    private fun saveToCache(context: Context, bitmap: Bitmap): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "letra_opsound.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    private fun uriFor(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /** Abre el menú de compartir de Android (WhatsApp, Instagram, etc.). */
    fun share(context: Context, bitmap: Bitmap, text: String) {
        val uri = uriFor(context, saveToCache(context, bitmap))
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartir letra").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Manda la imagen directo a Instagram (ahí eliges Historia). Regresa false si no está instalado. */
    fun shareToInstagram(context: Context, bitmap: Bitmap): Boolean {
        val uri = uriFor(context, saveToCache(context, bitmap))
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            setPackage("com.instagram.android")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(send) }.isSuccess
    }

    /** Guarda la imagen en tu galería, en la carpeta Imágenes/Opsound. */
    fun saveToGallery(context: Context, bitmap: Bitmap): Boolean = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Opsound_letra_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Opsound")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }.isSuccess
}
