package com.abraham.opsound

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset

/** Una palabra con su momento exacto (cuando la letra viene sincronizada palabra por palabra). */
data class LyricWord(val timeMs: Long, val text: String)

/** Una línea de letra; timeMs = null si la letra no viene sincronizada. */
data class LyricLine(val timeMs: Long?, val text: String, val words: List<LyricWord> = emptyList())

data class Lyrics(val lines: List<LyricLine>) {
    val synced: Boolean get() = lines.any { it.timeMs != null }
}

object LyricsStore {
    private var dir: File? = null

    /** Cambia cuando agregas o quitas una letra, para refrescar la pantalla. */
    var version by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        if (dir == null) dir = File(context.filesDir, "lyrics").apply { mkdirs() }
    }

    private fun file(songId: Long) = dir?.let { File(it, "$songId.lrc") }

    fun hasSaved(songId: Long) = file(songId)?.exists() == true

    /** Busca la letra: primero la que tú guardaste, luego la que viene dentro del archivo. (Fuera del hilo principal.) */
    fun load(context: Context, song: Song): Lyrics? {
        init(context)
        val text = file(song.id)?.takeIf { it.exists() }?.readText()
            ?: runCatching { context.contentResolver.openInputStream(song.uri)?.use { readId3Lyrics(it) } }.getOrNull()
        return text?.takeIf { it.isNotBlank() }?.let { parse(it) }
    }

    fun saveText(context: Context, songId: Long, text: String) {
        init(context)
        file(songId)?.writeText(text.trim())
        version++
    }

    fun importFile(context: Context, songId: Long, uri: Uri) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("No se pudo leer")
        val text = if (bytes.size >= 2 && (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() || bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())) {
            String(bytes, Charsets.UTF_16)
        } else {
            String(bytes, Charsets.UTF_8).removePrefix("﻿")
        }
        saveText(context, songId, text)
    }

    fun remove(songId: Long) {
        file(songId)?.delete()
        version++
    }

    // ── Formato LRC: "[01:23.45] texto"
    private val tag = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val wordTag = Regex("""<(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?>([^<]*)""")

    private fun toMs(min: String, sec: String, frac: String): Long {
        val ms = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
        return min.toLong() * 60_000 + sec.toLong() * 1000 + ms
    }

    /** Convierte milisegundos a la etiqueta LRC "mm:ss.xx". */
    fun stamp(ms: Long): String {
        val t = ms.coerceAtLeast(0)
        return "%02d:%02d.%02d".format(t / 60_000, (t / 1000) % 60, (t % 1000) / 10)
    }

    fun parse(text: String): Lyrics {
        val lines = mutableListOf<LyricLine>()
        text.lineSequence().forEach { raw ->
            val matches = tag.findAll(raw).toList()
            if (matches.isEmpty()) {
                // Quitamos etiquetas de info como [ar:...] [ti:...]
                if (!raw.trim().matches(Regex("""\[[a-zA-Z]+:.*]"""))) lines += LyricLine(null, raw.trim())
            } else {
                val rawContent = raw.substring(matches.last().range.last + 1).trim()
                // LRC mejorado: "<00:12.30>palabra <00:12.80>otra" → tiempos por palabra.
                val words = wordTag.findAll(rawContent).map { m ->
                    LyricWord(toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3]), m.groupValues[4].trim())
                }.filter { it.text.isNotEmpty() }.toList()
                val content = rawContent.replace(Regex("""<\d{1,2}:\d{2}(?:[.:]\d{1,3})?>"""), "").replace(Regex("\\s+"), " ").trim()
                matches.forEach { m ->
                    val start = toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3])
                    lines += LyricLine(start, content, words)
                }
            }
        }
        val synced = lines.any { it.timeMs != null }
        val result = if (synced) lines.filter { it.timeMs != null }.sortedBy { it.timeMs } else lines
        return Lyrics(result.dropWhile { it.text.isBlank() }.dropLastWhile { it.text.isBlank() })
    }

    /** Lee la letra guardada dentro de un MP3 (etiqueta ID3, marco USLT). */
    private fun readId3Lyrics(input: InputStream): String? {
        val header = ByteArray(10)
        if (input.read(header) != 10) return null
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return null
        val version = header[3].toInt()
        val size = syncsafe(header, 6)
        if (size <= 0 || size > 20_000_000) return null
        val tag = ByteArray(size)
        var read = 0
        while (read < size) {
            val n = input.read(tag, read, size - read)
            if (n < 0) break
            read += n
        }
        var pos = 0
        while (pos + 10 <= read) {
            val id = String(tag, pos, 4, Charsets.ISO_8859_1)
            if (id[0] == '\u0000') break
            val frameSize = if (version >= 4) syncsafe(tag, pos + 4) else int32(tag, pos + 4)
            if (frameSize <= 0 || pos + 10 + frameSize > read) break
            if (id == "USLT") return decodeUslt(tag, pos + 10, frameSize)
            pos += 10 + frameSize
        }
        return null
    }

    private fun decodeUslt(b: ByteArray, start: Int, size: Int): String? {
        val encoding = b[start].toInt()
        var p = start + 4 // codificación + idioma (3)
        val end = start + size
        val charset: Charset = when (encoding) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        // Saltar la descripción (termina en 0 o en 00 00 para UTF-16).
        if (encoding == 1 || encoding == 2) {
            while (p + 1 < end && !(b[p] == 0.toByte() && b[p + 1] == 0.toByte())) p += 2
            p += 2
        } else {
            while (p < end && b[p] != 0.toByte()) p++
            p += 1
        }
        if (p >= end) return null
        return String(b, p, end - p, charset).trim('\u0000', ' ', '\n', '\r')
    }

    private fun syncsafe(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0x7F) shl 21) or ((b[o + 1].toInt() and 0x7F) shl 14) or
            ((b[o + 2].toInt() and 0x7F) shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun int32(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)
}
