package com.abraham.opsound

import java.text.Normalizer

/**
 * Sincroniza una letra que tú pegaste (texto correcto, sin tiempos) usando los tiempos
 * que la IA escuchó. Así la letra queda exacta y la IA solo pone cuándo se canta cada palabra.
 */
object LyricsAligner {

    class AlignException(message: String) : Exception(message)

    private fun norm(w: String): String =
        Normalizer.normalize(w.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9ñ]"), "")

    /** ¿Se parecen lo suficiente? (la IA a veces escribe "pa" en vez de "pa'", "q" en vez de "que", etc.) */
    private fun similar(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val longest = maxOf(a.length, b.length)
        if (longest <= 3) return false
        return 1f - distance(a, b).toFloat() / longest >= 0.7f
    }

    private fun distance(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            cur.copyInto(prev)
        }
        return prev[b.length]
    }

    /**
     * @param reference la letra correcta, una línea por renglón.
     * @param aiLrc lo que escuchó la IA, en LRC con tiempos por palabra.
     * @return la letra correcta en LRC con tiempos por palabra.
     */
    fun align(reference: String, aiLrc: String): String {
        // Palabras de la letra correcta, recordando a qué renglón pertenecen.
        val refLines = reference.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val ref = refLines.flatMapIndexed { li, line -> line.split(Regex("\\s+")).filter { it.isNotBlank() }.map { li to it } }
        val hyp = LyricsStore.parse(aiLrc).lines.flatMap { line ->
            line.words.ifEmpty { listOfNotNull(line.timeMs?.let { LyricWord(it, line.text) }) }
        }
        if (ref.isEmpty()) throw AlignException("No hay letra para sincronizar")
        if (hyp.size < 3) throw AlignException("La IA casi no reconoció voz en la canción")

        val r = ref.map { norm(it.second) }
        val h = hyp.map { norm(it.text) }
        val n = r.size
        val m = h.size
        // Alineación (como el "diff" de dos textos): busca la mayor cantidad de palabras que coinciden en orden.
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 1..n) for (j in 1..m) {
            dp[i][j] = maxOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1] + if (similar(r[i - 1], h[j - 1])) 1 else 0)
        }
        val times = LongArray(n) { -1L }
        var i = n
        var j = m
        while (i > 0 && j > 0) {
            when {
                similar(r[i - 1], h[j - 1]) && dp[i][j] == dp[i - 1][j - 1] + 1 -> { times[i - 1] = hyp[j - 1].timeMs; i--; j-- }
                dp[i - 1][j] >= dp[i][j - 1] -> i--
                else -> j--
            }
        }
        val matched = times.count { it >= 0 }
        if (matched < maxOf(3, n / 10)) throw AlignException("La IA reconoció muy poco de la canción para sincronizar la letra")

        // Palabras sin pareja: se reparten entre las que sí coincidieron.
        val perWord = 350L
        val firstIdx = times.indexOfFirst { it >= 0 }
        for (k in firstIdx - 1 downTo 0) times[k] = (times[k + 1] - perWord).coerceAtLeast(0)
        var last = firstIdx
        for (k in firstIdx + 1 until n) {
            if (times[k] >= 0) {
                val gap = k - last
                if (gap > 1) for (g in 1 until gap) times[last + g] = times[last] + (times[k] - times[last]) * g / gap
                last = k
            }
        }
        for (k in last + 1 until n) times[k] = times[k - 1] + perWord
        for (k in 1 until n) if (times[k] < times[k - 1]) times[k] = times[k - 1]

        // Armamos el LRC respetando tus renglones.
        val sb = StringBuilder()
        var k = 0
        refLines.forEachIndexed { li, _ ->
            val start = k
            while (k < n && ref[k].first == li) k++
            if (start == k) return@forEachIndexed
            sb.append('[').append(LyricsStore.stamp(times[start])).append(']')
            for (w in start until k) sb.append(" <").append(LyricsStore.stamp(times[w])).append('>').append(ref[w].second)
            sb.append('\n')
        }
        return sb.toString()
    }
}
