package com.abraham.opsound.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abraham.opsound.OpsoundStore
import com.abraham.opsound.Song
import com.abraham.opsound.formatTime
import java.text.Normalizer

enum class LibraryTab(val label: String) {
    SONGS("Canciones"),
    FAVORITES("Favoritos"),
    PLAYLISTS("Playlists"),
    ARTISTS("Artistas"),
    ALBUMS("Álbumes"),
    FOLDERS("Carpetas"),
}

/** Una lista abierta: un artista, un álbum, una carpeta o una playlist. */
private data class OpenGroup(val tab: LibraryTab, val key: String, val title: String)

private data class Group(val key: String, val title: String, val subtitle: String, val songs: List<Song>)

@Composable
fun LibraryScreen(
    songs: List<Song>?,
    store: OpsoundStore,
    playback: PlaybackUi,
    onPlay: (list: List<Song>, index: Int, source: String) -> Unit,
    onOpenPlayer: () -> Unit,
    onPlayPause: () -> Unit,
    onSongOptions: (song: Song, playlistId: Long?) -> Unit,
    onNewPlaylist: () -> Unit,
    onPlaylistOptions: (playlistId: Long) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(LibraryTab.SONGS) }
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    var openTitle by rememberSaveable { mutableStateOf("") }
    // Las playlists toman su nombre actual (por si la renombras o la borras).
    val open = openKey?.let { key ->
        val title = if (tab == LibraryTab.PLAYLISTS) {
            store.playlists.firstOrNull { it.id.toString() == key }?.name
        } else openTitle
        title?.let { OpenGroup(tab, key, it) }
    }

    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    BackHandler(enabled = searching || open != null) {
        if (searching) {
            searching = false; query = ""
        } else {
            openKey = null
        }
    }

    val all = songs.orEmpty()
    val byId = remember(songs) { all.associateBy { it.id } }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
    ) {
        // ── Encabezado
        when {
            searching -> SearchBar(query, { query = it }, onClose = { searching = false; query = "" })
            open != null -> Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { openKey = null }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                }
                Text(
                    open.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (open.tab == LibraryTab.PLAYLISTS) {
                    IconButton(onClick = { onPlaylistOptions(open.key.toLong()) }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Opciones de la playlist", tint = Color.White)
                    }
                }
            }
            else -> Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, top = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Opsound", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { searching = true }) {
                    Icon(Icons.Rounded.Search, contentDescription = "Buscar", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }

        if (!searching && open == null) {
            TabChips(selected = tab, onSelect = { tab = it; openKey = null })
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                songs == null -> CircularProgressIndicator(color = Accent, modifier = Modifier.align(Alignment.Center))

                searching -> {
                    val q = query.normalizeForSearch()
                    val results = remember(songs, q) {
                        if (q.isBlank()) emptyList()
                        else all.filter { it.title.normalizeForSearch().contains(q) || it.artist.normalizeForSearch().contains(q) }
                    }
                    when {
                        q.isBlank() -> Hint("Escribe el nombre de una canción o artista.")
                        results.isEmpty() -> Hint("No hay canciones que coincidan con \"$query\".")
                        else -> SongList(results, "${results.size} resultados", playback, store,
                            onClick = { i -> onPlay(results, i, "Búsqueda") },
                            onOptions = { onSongOptions(it, null) })
                    }
                }

                open != null -> {
                    val list = groupSongs(open, all, byId, store)
                    val playlistId = if (open.tab == LibraryTab.PLAYLISTS) open.key.toLongOrNull() else null
                    if (list.isEmpty()) {
                        Hint(if (playlistId != null) "Esta playlist está vacía.\nEn cualquier canción toca ⋮ › Agregar a playlist." else "No hay canciones aquí.")
                    } else {
                        SongList(list, "${list.size} canciones", playback, store,
                            onClick = { i -> onPlay(list, i, open.title) },
                            onOptions = { onSongOptions(it, playlistId) })
                    }
                }

                all.isEmpty() -> Hint("No encontré canciones en tu celular.\nGuarda música (MP3, M4A…) en la carpeta Música o Descargas.")

                else -> when (tab) {
                    LibraryTab.SONGS -> SongList(all, "${all.size} canciones", playback, store,
                        onClick = { i -> onPlay(all, i, "Tu música") },
                        onOptions = { onSongOptions(it, null) })

                    LibraryTab.FAVORITES -> {
                        val favs = all.filter { store.isFavorite(it.id) }
                        if (favs.isEmpty()) Hint("Aún no tienes favoritos.\nToca el ❤ en el reproductor para guardar una canción aquí.")
                        else SongList(favs, "${favs.size} favoritos", playback, store,
                            onClick = { i -> onPlay(favs, i, "Favoritos") },
                            onOptions = { onSongOptions(it, null) })
                    }

                    LibraryTab.PLAYLISTS -> PlaylistList(store, byId, onNewPlaylist) { p ->
                        openKey = p.id.toString(); openTitle = p.name
                    }

                    else -> {
                        val groups = remember(songs, tab) { buildGroups(tab, all) }
                        GroupList(tab, groups) { g -> openKey = g.key; openTitle = g.title }
                    }
                }
            }
        }

        if (playback.song != null) {
            MiniPlayer(playback, onOpenPlayer, onPlayPause)
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

private fun groupSongs(open: OpenGroup, all: List<Song>, byId: Map<Long, Song>, store: OpsoundStore): List<Song> =
    when (open.tab) {
        LibraryTab.PLAYLISTS -> store.playlists.firstOrNull { it.id.toString() == open.key }
            ?.songIds?.mapNotNull { byId[it] }.orEmpty()
        LibraryTab.ARTISTS -> all.filter { it.artist == open.key }
        LibraryTab.ALBUMS -> all.filter { it.albumId.toString() == open.key }
        LibraryTab.FOLDERS -> all.filter { it.folder == open.key }
        else -> emptyList()
    }

private fun buildGroups(tab: LibraryTab, all: List<Song>): List<Group> = when (tab) {
    LibraryTab.ARTISTS -> all.groupBy { it.artist }
        .map { (artist, list) -> Group(artist, artist, cantidad(list.size), list) }
        .sortedBy { it.title.lowercase() }
    LibraryTab.ALBUMS -> all.groupBy { it.albumId }
        .map { (id, list) -> Group(id.toString(), list.first().album, "${list.first().artist} · ${cantidad(list.size)}", list) }
        .sortedBy { it.title.lowercase() }
    LibraryTab.FOLDERS -> all.groupBy { it.folder }
        .map { (folder, list) -> Group(folder, folder, cantidad(list.size), list) }
        .sortedBy { it.title.lowercase() }
    else -> emptyList()
}

private fun cantidad(n: Int) = if (n == 1) "1 canción" else "$n canciones"

@Composable
private fun TabChips(selected: LibraryTab, onSelect: (LibraryTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibraryTab.entries.forEach { t ->
            val isSel = t == selected
            Text(
                t.label,
                color = if (isSel) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSel) Accent else Color.White.copy(alpha = 0.10f))
                    .clickable { onSelect(t) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
    }
}

@Composable
private fun SongList(
    list: List<Song>,
    header: String,
    playback: PlaybackUi,
    store: OpsoundStore,
    onClick: (Int) -> Unit,
    onOptions: (Song) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
        item {
            Text(
                header,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 6.dp),
            )
        }
        itemsIndexed(list, key = { _, song -> song.id }) { index, song ->
            SongRow(
                song = song,
                isCurrent = song.id == playback.song?.id,
                isFavorite = store.isFavorite(song.id),
                onClick = { onClick(index) },
                onOptions = { onOptions(song) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRow(song: Song, isCurrent: Boolean, isFavorite: Boolean, onClick: () -> Unit, onOptions: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onOptions)
            .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallCover(song, 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (isCurrent) Accent else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isFavorite) {
                    Icon(Icons.Rounded.Favorite, contentDescription = "Favorito", tint = FavoriteColor, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    "${song.artist} · ${formatTime(song.durationMs)}",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onOptions) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Opciones", tint = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun PlaylistList(
    store: OpsoundStore,
    byId: Map<Long, Song>,
    onNew: () -> Unit,
    onOpen: (com.abraham.opsound.Playlist) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onNew).padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(Accent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Add, contentDescription = null, tint = Accent) }
                Spacer(Modifier.width(14.dp))
                Text("Nueva playlist", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        items(store.playlists, key = { it.id }) { p ->
            val first = p.songIds.firstNotNullOfOrNull { byId[it] }
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(p) }.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (first != null) SmallCover(first, 52.dp) else EmptyCover(52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(cantidad(p.songIds.count { it in byId }), color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun GroupList(tab: LibraryTab, groups: List<Group>, onOpen: (Group) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
        items(groups, key = { it.key }) { g ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(g) }.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (tab) {
                    LibraryTab.ALBUMS -> SmallCover(g.songs.first(), 52.dp)
                    LibraryTab.FOLDERS -> Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Folder, contentDescription = null, tint = Accent) }
                    else -> Box(
                        Modifier.size(52.dp).clip(CircleShape).background(Accent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(g.title.take(1).uppercase(), color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(g.subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun EmptyCover(size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.5f)) }
}

@Composable
private fun SmallCover(song: Song, size: Dp) {
    val art = rememberArtwork(song, 200)
    if (art == null) {
        EmptyCover(size)
    } else {
        Image(
            art,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)),
        )
    }
}

@Composable
private fun MiniPlayer(playback: PlaybackUi, onOpen: () -> Unit, onPlayPause: () -> Unit) {
    val song = playback.song ?: return
    val progress = if (playback.durationMs > 0) {
        (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f)
    } else 0f

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF141C23))
            .clickable(onClick = onOpen)
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth(progress).height(2.dp).background(Color.White))
        }
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallCover(song, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playback.isPlaying) "Pausar" else "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

/** Quita acentos y mayúsculas para que "cancion" encuentre "Canción". */
private fun String.normalizeForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .trim()

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focus.requestFocus() }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Buscar canción o artista", color = Color.White.copy(alpha = 0.45f), fontSize = 16.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = Montserrat),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Borrar", tint = Color.White.copy(alpha = 0.7f))
                }
            }
        }
        TextButton(onClick = onClose) { Text("Cancelar", color = Color.White) }
    }
}
