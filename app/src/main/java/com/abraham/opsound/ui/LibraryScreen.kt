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
import androidx.compose.material.icons.rounded.AddToQueue
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.ui.graphics.vector.ImageVector
import com.abraham.opsound.SortMode
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
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

/** Acciones para varias canciones elegidas a la vez. */
enum class BulkAction { PLAY_NEXT, QUEUE, PLAYLIST, FAVORITE, DELETE }

@Composable
fun LibraryScreen(
    songs: List<Song>?,
    store: OpsoundStore,
    playback: PlaybackUi,
    position: () -> Long,
    onPlay: (list: List<Song>, index: Int, source: String) -> Unit,
    onOpenPlayer: () -> Unit,
    onPlayPause: () -> Unit,
    onSongOptions: (song: Song, playlistId: Long?) -> Unit,
    onNewPlaylist: () -> Unit,
    onPlaylistOptions: (playlistId: Long) -> Unit,
    onBulk: (action: BulkAction, songs: List<Song>) -> Unit,
    onOpenSettings: () -> Unit,
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
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val selecting = selected.isNotEmpty()

    BackHandler(enabled = selecting || searching || open != null) {
        when {
            selecting -> selected = emptySet()
            searching -> { searching = false; query = "" }
            else -> openKey = null
        }
    }

    val all = songs.orEmpty()
    val byId = remember(songs) { all.associateBy { it.id } }
    val q = query.normalizeForSearch()

    // La lista de canciones que se ve ahora (null si se ven artistas, álbumes, carpetas o playlists).
    val visible: List<Song>? = when {
        songs == null -> null
        searching -> if (q.isBlank()) emptyList()
            else all.filter { it.title.normalizeForSearch().contains(q) || it.artist.normalizeForSearch().contains(q) }
        open != null -> groupSongs(open, all, byId, store).let { if (open.tab == LibraryTab.PLAYLISTS) it else store.sort(it) }
        tab == LibraryTab.SONGS -> store.sort(all)
        tab == LibraryTab.FAVORITES -> store.sort(all.filter { store.isFavorite(it.id) })
        else -> null
    }
    val source = when {
        searching -> "Búsqueda"
        open != null -> open.title
        tab == LibraryTab.FAVORITES -> "Favoritos"
        else -> "Tu música"
    }
    val canSort = !searching && (open == null || open.tab != LibraryTab.PLAYLISTS)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
    ) {
        // ── Encabezado
        when {
            selecting -> SelectionBar(
                count = selected.size,
                onClose = { selected = emptySet() },
                onSelectAll = { selected = visible.orEmpty().map { it.id }.toSet() },
                onAction = { action ->
                    val chosen = visible.orEmpty().filter { it.id in selected }
                    onBulk(action, chosen)
                    selected = emptySet()
                },
            )
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
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Ajustes", tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }

        if (!searching && open == null && !selecting) {
            TabChips(selected = tab, onSelect = { tab = it; openKey = null })
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                songs == null -> CircularProgressIndicator(color = Accent, modifier = Modifier.align(Alignment.Center))

                visible != null -> when {
                    searching && q.isBlank() -> Hint("Escribe el nombre de una canción o artista.")
                    searching && visible.isEmpty() -> Hint("No hay canciones que coincidan con \"$query\".")
                    all.isEmpty() -> Hint("No encontré canciones en tu celular.\nGuarda música (MP3, M4A…) en la carpeta Música o Descargas.")
                    visible.isEmpty() && open?.tab == LibraryTab.PLAYLISTS ->
                        Hint("Esta playlist está vacía.\nEn cualquier canción toca ⋮ › Agregar a playlist.")
                    visible.isEmpty() && tab == LibraryTab.FAVORITES && open == null ->
                        Hint("Aún no tienes favoritos.\nToca el ❤ en el reproductor para guardar una canción aquí.")
                    visible.isEmpty() -> Hint("No hay canciones aquí.")
                    else -> {
                        val playlistId = if (open?.tab == LibraryTab.PLAYLISTS) open.key.toLongOrNull() else null
                        SongList(
                            list = visible,
                            header = if (searching) "${visible.size} resultados" else cantidad(visible.size),
                            playback = playback,
                            store = store,
                            selected = selected,
                            showSort = canSort,
                            onClick = { i ->
                                if (selecting) selected = selected.toggle(visible[i].id)
                                else onPlay(visible, i, source)
                            },
                            onLongClick = { i -> selected = selected.toggle(visible[i].id) },
                            onOptions = { onSongOptions(it, playlistId) },
                        )
                    }
                }

                all.isEmpty() -> Hint("No encontré canciones en tu celular.\nGuarda música (MP3, M4A…) en la carpeta Música o Descargas.")

                tab == LibraryTab.PLAYLISTS -> PlaylistList(store, byId, onNewPlaylist) { p ->
                    openKey = p.id.toString(); openTitle = p.name
                }

                else -> {
                    val groups = remember(songs, tab) { buildGroups(tab, all) }
                    GroupList(tab, groups) { g -> openKey = g.key; openTitle = g.title }
                }
            }
        }

        if (playback.song != null && !selecting) {
            MiniPlayer(playback, position, onOpenPlayer, onPlayPause)
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

private fun Set<Long>.toggle(id: Long) = if (id in this) this - id else this + id

@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit, onAction: (BulkAction) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Cancelar selección", tint = Color.White) }
            Text(
                if (count == 1) "1 seleccionada" else "$count seleccionadas",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSelectAll) { Text("Todas", color = Accent) }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BulkButton(Icons.Rounded.QueuePlayNext, "Siguiente") { onAction(BulkAction.PLAY_NEXT) }
            BulkButton(Icons.Rounded.AddToQueue, "A la fila") { onAction(BulkAction.QUEUE) }
            BulkButton(Icons.AutoMirrored.Rounded.PlaylistAdd, "Playlist") { onAction(BulkAction.PLAYLIST) }
            BulkButton(Icons.Rounded.FavoriteBorder, "Favoritos") { onAction(BulkAction.FAVORITE) }
            BulkButton(Icons.Rounded.Delete, "Eliminar", FavoriteColor) { onAction(BulkAction.DELETE) }
        }
    }
}

@Composable
private fun BulkButton(icon: ImageVector, label: String, color: Color = Color.White, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = color, fontSize = 12.sp)
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
    selected: Set<Long>,
    showSort: Boolean,
    onClick: (Int) -> Unit,
    onLongClick: (Int) -> Unit,
    onOptions: (Song) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(header, color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (showSort) SortButton(store)
            }
        }
        itemsIndexed(list, key = { _, song -> song.id }) { index, song ->
            SongRow(
                song = song,
                isCurrent = song.id == playback.song?.id,
                isFavorite = store.isFavorite(song.id),
                isSelected = song.id in selected,
                selecting = selected.isNotEmpty(),
                onClick = { onClick(index) },
                onLongClick = { onLongClick(index) },
                onOptions = { onOptions(song) },
            )
        }
    }
}

@Composable
private fun SortButton(store: OpsoundStore) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(store.sortMode.label, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label, color = if (mode == store.sortMode) Accent else Color.Unspecified) },
                    onClick = { store.changeSort(mode); open = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRow(
    song: Song,
    isCurrent: Boolean,
    isFavorite: Boolean,
    isSelected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOptions: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isSelected) Accent.copy(alpha = 0.16f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            SmallCover(song, 52.dp)
            if (isSelected) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.CheckCircle, contentDescription = "Seleccionada", tint = Accent, modifier = Modifier.size(28.dp)) }
            }
        }
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
        if (!selecting) {
            IconButton(onClick = onOptions) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Opciones", tint = Color.White.copy(alpha = 0.6f))
            }
        } else {
            Spacer(Modifier.width(48.dp))
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
private fun MiniPlayer(playback: PlaybackUi, position: () -> Long, onOpen: () -> Unit, onPlayPause: () -> Unit) {
    val song = playback.song ?: return
    val smooth = rememberSmoothPosition(position, playback.durationMs, playback.isPlaying)

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF141C23))
            .clickable(onClick = onOpen)
    ) {
        SmoothProgressLine(smooth, playback.durationMs, Modifier.fillMaxWidth(), color = Accent)
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
