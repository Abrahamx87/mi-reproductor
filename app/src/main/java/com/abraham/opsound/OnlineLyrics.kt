package com.abraham.opsound

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/**
 * Letras desde internet:
 *  - LRCLIB (lrclib.net): base gratis de letras sincronizadas de canciones conocidas.
 *  - IA (Whisper en Groq): escucha la canción y escribe la letra con tiempos por palabra.
 * Todo se llama fuera del hilo principal.
 */
object OnlineLyrics {
    private const val USER_AGENT = "Opsound (https://github.com/Abrahamx87/mi-reproductor)"

    /** Quita cosas como "(Official Video)", "[Audio]", "ft. X" para buscar mejor. */
    private fun clean(text: String): String =
        text.replace(Regex("""\((?:official|video|audio|lyric|letra|visualizer|en vivo|live)[^)]*\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[[^]]*]"""), "")
            .replace(Regex("""\s+(?:ft\.?|feat\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\.(mp3|m4a|wav|flac|ogg)$""", RegexOption.IGNORE_CASE), "")
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun httpGet(url: String): Pair<Int, String?> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            val code = conn.responseCode
            code to if (code == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            conn.disconnect()
        }
    }

    /** Busca la letra en LRCLIB. Regresa texto LRC (sincronizado) o letra normal; null si no existe. */
    fun searchLrclib(song: Song): String? {
        val title = clean(song.title)
        val artist = clean(song.artist).takeUnless { it == "Artista desconocido" }.orEmpty()
        val seconds = song.durationMs / 1000

        // 1) Búsqueda exacta con artista, título y duración.
        if (artist.isNotEmpty()) {
            val (code, body) = httpGet(
                "https://lrclib.net/api/get?artist_name=${enc(artist)}&track_name=${enc(title)}&duration=$seconds"
            )
            if (code == 200 && body != null) pick(JSONObject(body))?.let { return it }
        }

        // 2) Búsqueda flexible: elige la de duración más parecida, prefiriendo las sincronizadas.
        val query = if (artist.isNotEmpty()) "track_name=${enc(title)}&artist_name=${enc(artist)}" else "q=${enc(title)}"
        val (code, body) = httpGet("https://lrclib.net/api/search?$query")
        if (code != 200 || body == null) return null
        val results = JSONArray(body)
        val candidates = (0 until results.length()).map { results.getJSONObject(it) }
            .filter { seconds <= 0 || abs(it.optDouble("duration", 0.0) - seconds) <= 8 }
            .sortedWith(compareByDescending<JSONObject> { !it.isNull("syncedLyrics") }
                .thenBy { abs(it.optDouble("duration", 0.0) - seconds) })
        return candidates.firstNotNullOfOrNull { pick(it) }
    }

    private fun pick(o: JSONObject): String? {
        if (o.optBoolean("instrumental")) return null
        val synced = o.optString("syncedLyrics").takeIf { !o.isNull("syncedLyrics") && it.isNotBlank() }
        val plain = o.optString("plainLyrics").takeIf { !o.isNull("plainLyrics") && it.isNotBlank() }
        return synced ?: plain
    }

    class AiException(message: String) : Exception(message)

    /**
     * La IA escucha la canción y escribe la letra con el momento exacto de cada palabra.
     * Regresa texto LRC mejorado: "[00:12.30] <00:12.30>palabra <00:12.80>otra".
     */
    fun transcribeWithAi(context: Context, song: Song, apiKey: String): String {
        if (apiKey.isBlank()) throw AiException("Falta tu clave de IA. Ponla en Ajustes.")
        val bytes = context.contentResolver.openInputStream(song.uri)?.use { it.readBytes() }
            ?: throw AiException("No se pudo leer la canción")
        if (bytes.size > 25 * 1024 * 1024) throw AiException("La canción pesa más de 25 MB (límite gratis de la IA)")

        val boundary = "----Opsound${System.currentTimeMillis()}"
        val conn = (URL("https://api.groq.com/openai/v1/audio/transcriptions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 180_000
            setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        // Armamos el cuerpo primero para poder mandar su tamaño exacto.
        val body = java.io.ByteArrayOutputStream()
        DataOutputStream(body).use { out ->
            fun field(name: String, value: String) {
                out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n")
                out.write(value.toByteArray(Charsets.UTF_8))
                out.writeBytes("\r\n")
            }
            field("model", "whisper-large-v3")
            field("response_format", "verbose_json")
            field("timestamp_granularities[]", "word")
            field("timestamp_granularities[]", "segment")
            field("language", "es")
            field("temperature", "0")
            val type = context.contentResolver.getType(song.uri).orEmpty()
            val ext = when {
                "mp4" in type || "m4a" in type || "aac" in type -> "m4a"
                "ogg" in type || "opus" in type -> "ogg"
                "flac" in type -> "flac"
                "wav" in type -> "wav"
                else -> "mp3"
            }
            out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"cancion.$ext\"\r\nContent-Type: application/octet-stream\r\n\r\n")
            out.write(bytes)
            out.writeBytes("\r\n--$boundary--\r\n")
        }
        val payload = body.toByteArray()
        conn.setFixedLengthStreamingMode(payload.size)
        try {
            conn.outputStream.use { it.write(payload) }
            val code = conn.responseCode
            if (code != 200) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw AiException(
                    when (code) {
                        401 -> "La clave de IA no es válida. Revísala en Ajustes."
                        413 -> "La canción es muy pesada para la IA."
                        429 -> "Se alcanzó el límite gratis de la IA. Intenta en un rato."
                        else -> "La IA respondió con error $code. ${err.take(120)}"
                    }
                )
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            return buildLrc(json).ifBlank { throw AiException("La IA no encontró voz en esta canción") }
        } finally {
            conn.disconnect()
        }
    }

    /** Junta las palabras de la IA en líneas (por frase) con tiempos por palabra. */
    private fun buildLrc(json: JSONObject): String {
        val words = json.optJSONArray("words")
        val segments = json.optJSONArray("segments")
        val sb = StringBuilder()
        if (words != null && words.length() > 0 && segments != null) {
            var w = 0
            for (i in 0 until segments.length()) {
                val seg = segments.getJSONObject(i)
                val segEnd = seg.optDouble("end")
                val lineWords = mutableListOf<Pair<Double, String>>()
                while (w < words.length()) {
                    val word = words.getJSONObject(w)
                    val start = word.optDouble("start")
                    if (start > segEnd + 0.05 && lineWords.isNotEmpty()) break
                    lineWords += start to word.optString("word").trim()
                    w++
                }
                if (lineWords.isEmpty()) continue
                // Frases muy largas se parten en líneas de máximo ~8 palabras, como en Apple Music.
                lineWords.chunked(8).forEach { chunk ->
                    sb.append('[').append(LyricsStore.stamp((chunk.first().first * 1000).toLong())).append(']')
                    chunk.forEach { (t, text) -> sb.append(" <").append(LyricsStore.stamp((t * 1000).toLong())).append('>').append(text) }
                    sb.append('\n')
                }
            }
            return sb.toString()
        }
        // Sin palabras: usamos las frases.
        if (segments != null) {
            for (i in 0 until segments.length()) {
                val seg = segments.getJSONObject(i)
                sb.append('[').append(LyricsStore.stamp((seg.optDouble("start") * 1000).toLong())).append("] ")
                    .append(seg.optString("text").trim()).append('\n')
            }
        }
        return sb.toString()
    }
}
