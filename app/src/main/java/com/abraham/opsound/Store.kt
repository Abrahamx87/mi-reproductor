package com.abraham.opsound

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

data class Playlist(val id: Long, val name: String, val songIds: List<Long>)

/** Cómo ordenar la lista de canciones. */
enum class SortMode(val label: String) {
    RECENT("Más recientes"),
    TITLE("Nombre (A-Z)"),
    ARTIST("Artista (A-Z)"),
    DURATION("Duración"),
    MOST_PLAYED("Más escuchadas"),
}

/** Título y artista que tú corregiste dentro de Opsound. */
data class SongEdit(val title: String, val artist: String)

/**
 * Lo que Opsound guarda en tu celular: favoritos, playlists
 * y la última canción que escuchabas (para seguir donde te quedaste).
 */
class OpsoundStore(context: Context) {
    private val prefs = context.getSharedPreferences("opsound", Context.MODE_PRIVATE)

    var favorites by mutableStateOf(loadFavorites())
        private set

    var playlists by mutableStateOf(loadPlaylists())
        private set

    var playCounts by mutableStateOf(loadPlayCounts())
        private set

    var sortMode by mutableStateOf(
        runCatching { SortMode.valueOf(prefs.getString("sort", SortMode.RECENT.name)!!) }.getOrDefault(SortMode.RECENT)
    )
        private set

    var edits by mutableStateOf(loadEdits())
        private set

    // ── Ajustes
    var autoLyrics by mutableStateOf(prefs.getBoolean("auto_lyrics", true))
        private set
    var animatedCovers by mutableStateOf(prefs.getBoolean("animated_covers", true))
        private set
    /** Animación de la portada: nombre de [com.abraham.opsound.ui.CoverAnimation]. */
    var coverAnimation by mutableStateOf(
        prefs.getString("cover_animation", null) ?: if (prefs.getBoolean("animated_covers", true)) "LIVING" else "NONE"
    )
        private set
    fun changeCoverAnimation(name: String) { coverAnimation = name; prefs.edit().putString("cover_animation", name).apply() }
    var aiKey by mutableStateOf(prefs.getString("ai_key", "").orEmpty())
        private set
    var deepgramKey by mutableStateOf(prefs.getString("deepgram_key", "").orEmpty())
        private set
    /** "deepgram" o "groq": quién genera las letras con IA. */
    var aiProvider by mutableStateOf(prefs.getString("ai_provider", "deepgram").orEmpty())
        private set

    fun changeDeepgramKey(key: String) { deepgramKey = key.trim(); prefs.edit().putString("deepgram_key", deepgramKey).apply() }
    fun changeAiProvider(p: String) { aiProvider = p; prefs.edit().putString("ai_provider", p).apply() }

    /** Clave del proveedor elegido. */
    val activeAiKey: String get() = if (aiProvider == "groq") aiKey else deepgramKey

    fun changeAutoLyrics(on: Boolean) { autoLyrics = on; prefs.edit().putBoolean("auto_lyrics", on).apply() }
    fun changeAnimatedCovers(on: Boolean) { animatedCovers = on; prefs.edit().putBoolean("animated_covers", on).apply() }
    fun changeAiKey(key: String) { aiKey = key.trim(); prefs.edit().putString("ai_key", aiKey).apply() }

    /** Canciones que ya se buscaron en internet sin éxito (para no buscarlas cada vez). */
    fun lyricsNotFound(songId: Long) = prefs.getStringSet("lyrics_not_found", emptySet())!!.contains(songId.toString())
    fun markLyricsNotFound(songId: Long) {
        val set = prefs.getStringSet("lyrics_not_found", emptySet())!!.toMutableSet().apply { add(songId.toString()) }
        prefs.edit().putStringSet("lyrics_not_found", set).apply()
    }

    // ── Orden y "más escuchadas"
    fun changeSort(mode: SortMode) {
        sortMode = mode
        prefs.edit().putString("sort", mode.name).apply()
    }

    fun countPlay(songId: Long) {
        playCounts = playCounts + (songId to (playCounts[songId] ?: 0) + 1)
        prefs.edit().putString("plays", playCounts.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
    }

    private fun loadPlayCounts(): Map<Long, Int> =
        prefs.getString("plays", "").orEmpty().split(',').mapNotNull { entry ->
            val (id, n) = entry.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            val key = id.toLongOrNull() ?: return@mapNotNull null
            key to (n.toIntOrNull() ?: return@mapNotNull null)
        }.toMap()

    fun sort(list: List<Song>): List<Song> = when (sortMode) {
        SortMode.RECENT -> list
        SortMode.TITLE -> list.sortedBy { it.title.lowercase() }
        SortMode.ARTIST -> list.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
        SortMode.DURATION -> list.sortedByDescending { it.durationMs }
        SortMode.MOST_PLAYED -> list.sortedByDescending { playCounts[it.id] ?: 0 }
    }

    // ── Editar información
    fun saveEdit(songId: Long, edit: SongEdit?) {
        edits = if (edit == null) edits - songId else edits + (songId to edit)
        val json = JSONObject()
        edits.forEach { (id, e) -> json.put(id.toString(), JSONObject().put("t", e.title).put("a", e.artist)) }
        prefs.edit().putString("edits", json.toString()).apply()
    }

    private fun loadEdits(): Map<Long, SongEdit> = runCatching {
        val json = JSONObject(prefs.getString("edits", "{}")!!)
        json.keys().asSequence().mapNotNull { key ->
            val o = json.getJSONObject(key)
            key.toLongOrNull()?.let { it to SongEdit(o.getString("t"), o.getString("a")) }
        }.toMap()
    }.getOrDefault(emptyMap())

    fun applyEdits(list: List<Song>): List<Song> = list.map { song ->
        edits[song.id]?.let { song.copy(title = it.title, artist = it.artist) } ?: song
    }

    // ── Favoritos
    fun isFavorite(songId: Long) = songId in favorites

    fun toggleFavorite(songId: Long) {
        favorites = if (songId in favorites) favorites - songId else favorites + songId
        prefs.edit().putString("favorites", favorites.joinToString(",")).apply()
    }

    private fun loadFavorites(): Set<Long> =
        prefs.getString("favorites", "").orEmpty()
            .split(',').mapNotNull { it.toLongOrNull() }.toSet()

    // ── Playlists
    fun createPlaylist(name: String, songIds: List<Long> = emptyList()): Playlist {
        val playlist = Playlist(System.currentTimeMillis(), name.trim(), songIds.distinct())
        playlists = playlists + playlist
        savePlaylists()
        return playlist
    }

    fun addToPlaylist(playlistId: Long, songIds: List<Long>) {
        playlists = playlists.map { p ->
            if (p.id == playlistId) p.copy(songIds = (p.songIds + songIds).distinct()) else p
        }
        savePlaylists()
    }

    fun removeFromPlaylist(playlistId: Long, songId: Long) {
        playlists = playlists.map {
            if (it.id == playlistId) it.copy(songIds = it.songIds - songId) else it
        }
        savePlaylists()
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        playlists = playlists.map { if (it.id == playlistId) it.copy(name = name.trim()) else it }
        savePlaylists()
    }

    fun deletePlaylist(playlistId: Long) {
        playlists = playlists.filterNot { it.id == playlistId }
        savePlaylists()
    }

    private fun savePlaylists() {
        val array = JSONArray()
        playlists.forEach { p ->
            array.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("songs", JSONArray(p.songIds))
            )
        }
        prefs.edit().putString("playlists", array.toString()).apply()
    }

    private fun loadPlaylists(): List<Playlist> = runCatching {
        val array = JSONArray(prefs.getString("playlists", "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val ids = o.getJSONArray("songs")
            Playlist(
                id = o.getLong("id"),
                name = o.getString("name"),
                songIds = (0 until ids.length()).map { ids.getLong(it) },
            )
        }
    }.getOrDefault(emptyList())

    // ── Seguir donde te quedaste
    fun saveQueue(songIds: List<Long>, source: String) {
        prefs.edit()
            .putString("queue", songIds.joinToString(","))
            .putString("queue_source", source)
            .apply()
    }

    fun saveProgress(currentSongId: Long, positionMs: Long) {
        prefs.edit()
            .putLong("last_song", currentSongId)
            .putLong("last_position", positionMs)
            .apply()
    }

    val savedQueue: List<Long>
        get() = prefs.getString("queue", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    val savedSource: String
        get() = prefs.getString("queue_source", null) ?: "Tu música"

    val savedSongId: Long
        get() = prefs.getLong("last_song", -1L)

    val savedPosition: Long
        get() = prefs.getLong("last_position", 0L)
}
