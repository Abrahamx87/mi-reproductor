package com.abraham.opsound

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

data class Playlist(val id: Long, val name: String, val songIds: List<Long>)

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
    fun createPlaylist(name: String, firstSongId: Long? = null): Playlist {
        val playlist = Playlist(System.currentTimeMillis(), name.trim(), listOfNotNull(firstSongId))
        playlists = playlists + playlist
        savePlaylists()
        return playlist
    }

    fun addToPlaylist(playlistId: Long, songId: Long) {
        playlists = playlists.map {
            if (it.id == playlistId && songId !in it.songIds) it.copy(songIds = it.songIds + songId) else it
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
