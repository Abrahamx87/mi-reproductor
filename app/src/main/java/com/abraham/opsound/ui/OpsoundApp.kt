package com.abraham.opsound.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.abraham.opsound.OpsoundStore
import com.abraham.opsound.PlaybackService
import android.widget.Toast
import android.app.Activity
import android.app.RecoverableSecurityException
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.runtime.mutableIntStateOf
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import com.abraham.opsound.CustomCovers
import com.abraham.opsound.Lyrics
import com.abraham.opsound.LyricsStore
import com.abraham.opsound.OnlineLyrics
import com.abraham.opsound.SongEdit
import androidx.compose.runtime.produceState
import androidx.compose.runtime.CompositionLocalProvider
import com.abraham.opsound.Song
import com.abraham.opsound.loadSongs
import com.abraham.opsound.toMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

val AppBackground = Color(0xFF070B0F)
val FavoriteColor = Color(0xFFFF4D6D)

private fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun permissionsToAsk(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun hasAudioAccess(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED

@Composable
fun OpsoundApp() {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(hasAudioAccess(context)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasAccess = hasAudioAccess(context) }

    LaunchedEffect(Unit) {
        if (!hasAccess) launcher.launch(permissionsToAsk())
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        if (hasAccess) {
            PlayerApp()
        } else {
            PermissionScreen(
                onAsk = { launcher.launch(permissionsToAsk()) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                    )
                },
            )
        }
        UpdateDialog()
    }
}

@Composable
private fun PermissionScreen(onAsk: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = Accent, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(20.dp))
        Text("Bienvenido a Opsound", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "Para reproducir tu música necesito permiso para ver las canciones de tu celular.",
            color = Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAsk,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
        ) { Text("Permitir acceso a mi música") }
        TextButton(onClick = onOpenSettings) {
            Text("Abrir ajustes de la app", color = Color.White.copy(alpha = 0.7f))
        }
    }
}

/** Conecta la pantalla con el servicio que reproduce la música. */
@Composable
private fun rememberMediaController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { controller = runCatching { future.get() }.getOrNull() },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            controller = null
            MediaController.releaseFuture(future)
        }
    }
    return controller
}

enum class LoopMode { OFF, ALL, ONE }

data class PlaybackUi(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val loop: LoopMode = LoopMode.OFF,
    val hasNext: Boolean = false,
)

private fun readPlayback(player: Player, songsById: Map<String, Song>): PlaybackUi {
    val song = player.currentMediaItem?.mediaId?.let { songsById[it] }
    val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: (song?.durationMs ?: 0L)
    return PlaybackUi(
        song = song,
        isPlaying = player.playWhenReady && player.playbackState != Player.STATE_ENDED,
        durationMs = duration,
        shuffle = player.shuffleModeEnabled,
        loop = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> LoopMode.ONE
            Player.REPEAT_MODE_ALL -> LoopMode.ALL
            else -> LoopMode.OFF
        },
        hasNext = player.hasNextMediaItem(),
    )
}

@Composable
private fun PlayerApp() {
    val context = LocalContext.current
    val store = remember { OpsoundStore(context.applicationContext) }
    remember { CustomCovers.init(context.applicationContext); LyricsStore.init(context.applicationContext); 0 }
    var rawSongs by remember { mutableStateOf<List<Song>?>(null) }
    LaunchedEffect(Unit) {
        rawSongs = withContext(Dispatchers.IO) { runCatching { loadSongs(context) }.getOrDefault(emptyList()) }
    }
    // Aplica los títulos/artistas que corregiste.
    val songs = remember(rawSongs, store.edits) { rawSongs?.let { store.applyEdits(it) } }
    val songsById = remember(songs) { songs.orEmpty().associateBy { it.id.toString() } }

    val scope = rememberCoroutineScope()
    var playingFrom by rememberSaveable { mutableStateOf(store.savedSource) }

    val controller = rememberMediaController()
    var ui by remember { mutableStateOf(PlaybackUi()) }
    // La posición va aparte: así solo se redibuja la barra de tiempo, no toda la pantalla.
    var positionMs by remember { mutableLongStateOf(0L) }
    val position = remember { { positionMs } }
    LaunchedEffect(controller, songsById) {
        val player = controller ?: return@LaunchedEffect
        // Seguir donde te quedaste: recupera la última lista, canción y segundo.
        if (player.mediaItemCount == 0 && songsById.isNotEmpty()) {
            val queue = store.savedQueue.mapNotNull { songsById[it.toString()] }
            if (queue.isNotEmpty()) {
                val index = queue.indexOfFirst { it.id == store.savedSongId }.coerceAtLeast(0)
                val position = if (queue[index].id == store.savedSongId) store.savedPosition else 0L
                player.setMediaItems(queue.map { it.toMediaItem() }, index, position)
                player.prepare()
            }
        }
        var tick = 0
        var lastSongId = -1L
        var counted = false
        while (true) {
            val fresh = readPlayback(player, songsById)
            if (fresh != ui) ui = fresh
            positionMs = player.currentPosition.coerceAtLeast(0)
            // Cuenta una reproducción cuando escuchas al menos 10 segundos.
            val currentId = ui.song?.id ?: -1L
            if (currentId != lastSongId) { lastSongId = currentId; counted = false }
            if (!counted && currentId >= 0 && positionMs > 10_000) { store.countPlay(currentId); counted = true }
            tick++
            if (tick % 10 == 0) {
                ui.song?.let { store.saveProgress(it.id, positionMs) }
            }
            delay(200)
        }
    }

    // ── Fila de reproducción (se actualiza sola cuando cambia)
    var queue by remember { mutableStateOf<List<Song>>(emptyList()) }
    var queueIndex by remember { mutableIntStateOf(0) }
    var showQueue by remember { mutableStateOf(false) }
    DisposableEffect(controller, songsById) {
        val player = controller ?: return@DisposableEffect onDispose { }
        fun refresh() {
            queue = (0 until player.mediaItemCount).map { i ->
                val item = player.getMediaItemAt(i)
                songsById[item.mediaId] ?: Song(
                    id = item.mediaId.toLongOrNull() ?: -1L,
                    title = item.mediaMetadata.title?.toString() ?: "Canción",
                    artist = item.mediaMetadata.artist?.toString() ?: "",
                    album = "", albumId = -1L, durationMs = 0L, folder = "",
                )
            }
            queueIndex = player.currentMediaItemIndex
        }
        val listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) = refresh()
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refresh()
        }
        refresh()
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    fun saveQueueNow() {
        val player = controller ?: return
        val ids = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).mediaId.toLongOrNull() }
        store.saveQueue(ids, playingFrom)
    }

    fun enqueue(list: List<Song>, next: Boolean) {
        val player = controller ?: return
        if (list.isEmpty()) return
        val items = list.map { it.toMediaItem() }
        if (player.mediaItemCount == 0) {
            player.setMediaItems(items)
            player.prepare()
            player.play()
            playingFrom = "Tu fila"
        } else if (next) {
            player.addMediaItems(player.currentMediaItemIndex + 1, items)
        } else {
            player.addMediaItems(items)
        }
        saveQueueNow()
        val what = if (list.size == 1) "Canción" else "${list.size} canciones"
        Toast.makeText(
            context,
            if (next) "$what después de la actual" else "$what al final de la fila",
            Toast.LENGTH_SHORT,
        ).show()
    }
    fun enqueue(song: Song, next: Boolean) = enqueue(listOf(song), next)

    // ── Cambiar portada: eliges una imagen de tu galería
    var coverFor by remember { mutableStateOf<Song?>(null) }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val song = coverFor
        coverFor = null
        if (uri != null && song != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { CustomCovers.set(context, song.id, uri) }.isSuccess
                }
                Toast.makeText(context, if (ok) "Portada cambiada" else "No se pudo usar esa imagen", Toast.LENGTH_SHORT).show()
            }
        }
    }
    // Primero se abre la ventanita con las 2 opciones: predeterminadas o galería.
    var choosingCoverFor by remember { mutableStateOf<Song?>(null) }
    fun pickCover(song: Song) {
        choosingCoverFor = song
    }
    var videoFor by remember { mutableStateOf<Song?>(null) }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val song = videoFor
        videoFor = null
        if (uri != null && song != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { runCatching { CustomCovers.setVideo(context, song.id, uri) }.isSuccess }
                Toast.makeText(context, if (ok) "Portada en video lista 🎬" else "No se pudo usar ese video", Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun pickFromGallery(song: Song) {
        coverFor = song
        coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // ── Eliminar: Android pide confirmación y la manda a la papelera
    var deleting by remember { mutableStateOf<List<Song>>(emptyList()) }
    fun onDeleted(list: List<Song>) {
        val ids = list.map { it.id }.toSet()
        rawSongs = rawSongs?.filterNot { it.id in ids }
        controller?.let { player ->
            for (i in player.mediaItemCount - 1 downTo 0) {
                if (player.getMediaItemAt(i).mediaId.toLongOrNull()?.let { it in ids } == true) player.removeMediaItem(i)
            }
        }
        list.forEach { song ->
            if (store.isFavorite(song.id)) store.toggleFavorite(song.id)
            store.playlists.forEach { p -> if (song.id in p.songIds) store.removeFromPlaylist(p.id, song.id) }
            if (CustomCovers.has(song.id)) CustomCovers.remove(song.id)
        }
        saveQueueNow()
        val msg = if (list.size == 1) "\"${list.first().title}\" se movió a la papelera" else "${list.size} canciones se movieron a la papelera"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val list = deleting
        deleting = emptyList()
        if (result.resultCode == Activity.RESULT_OK && list.isNotEmpty()) onDeleted(list)
    }
    fun requestDelete(list: List<Song>) {
        if (list.isEmpty()) return
        val resolver = context.contentResolver
        val sender = if (Build.VERSION.SDK_INT >= 30) {
            MediaStore.createTrashRequest(resolver, list.map { it.uri }, true).intentSender
        } else {
            try {
                list.forEach { resolver.delete(it.uri, null, null) }
                onDeleted(list)
                return
            } catch (e: RecoverableSecurityException) {
                e.userAction.actionIntent.intentSender
            }
        }
        deleting = list
        deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
    }

    // ── Editar información
    var editing by remember { mutableStateOf<Song?>(null) }

    // ── Letras: la que viene en el archivo o la que tú agregas
    val currentSong = ui.song
    var lyrics by remember { mutableStateOf<Lyrics?>(null) }
    var lyricsStatus by remember { mutableStateOf<LyricsStatus>(LyricsStatus.Idle) }
    var showSettings by remember { mutableStateOf(false) }
    LaunchedEffect(currentSong?.id, LyricsStore.version) {
        val song = currentSong
        if (song == null) { lyrics = null; return@LaunchedEffect }
        val local = withContext(Dispatchers.IO) { runCatching { LyricsStore.load(context, song) }.getOrNull() }
        lyrics = local
        if (local != null) { lyricsStatus = LyricsStatus.Idle; return@LaunchedEffect }
        if (lyricsStatus == LyricsStatus.Generating) return@LaunchedEffect
        lyricsStatus = LyricsStatus.Idle
        // Sin letra guardada: la buscamos sola en internet (una vez por canción).
        if (store.autoLyrics && !store.lyricsNotFound(song.id)) {
            lyricsStatus = LyricsStatus.Searching
            val result = withContext(Dispatchers.IO) { runCatching { OnlineLyrics.searchLrclib(song) } }
            val found = result.getOrNull()
            when {
                found != null -> LyricsStore.saveText(context, song.id, found) // recarga sola
                result.isSuccess -> { store.markLyricsNotFound(song.id); lyricsStatus = LyricsStatus.Idle }
                else -> lyricsStatus = LyricsStatus.Idle // sin internet: se intenta otro día
            }
        }
    }

    fun searchLyrics() {
        val song = currentSong ?: return
        scope.launch {
            lyricsStatus = LyricsStatus.Searching
            val result = withContext(Dispatchers.IO) { runCatching { OnlineLyrics.searchLrclib(song) } }
            val found = result.getOrNull()
            if (found != null) {
                LyricsStore.saveText(context, song.id, found)
                Toast.makeText(context, "Letra encontrada 🎤", Toast.LENGTH_SHORT).show()
            } else {
                lyricsStatus = LyricsStatus.Failed(
                    if (result.isFailure) "No hay conexión a internet." else "No encontré esta letra en internet. Prueba con la IA ✨"
                )
            }
        }
    }

    fun generateLyrics() {
        val song = currentSong ?: return
        if (store.activeAiKey.isBlank()) {
            lyricsStatus = LyricsStatus.Failed("Primero pon tu clave gratis de IA en Ajustes.")
            showSettings = true
            return
        }
        scope.launch {
            lyricsStatus = LyricsStatus.Generating
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (store.aiProvider == "groq") OnlineLyrics.transcribeWithAi(context, song, store.aiKey)
                    else OnlineLyrics.transcribeWithDeepgram(context, song, store.deepgramKey)
                }
            }
            result.onSuccess { lrc ->
                LyricsStore.saveText(context, song.id, lrc)
                lyricsStatus = LyricsStatus.Idle
                Toast.makeText(context, "La IA generó la letra ✨", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                lyricsStatus = LyricsStatus.Failed(
                    (e as? OnlineLyrics.AiException)?.message ?: "No se pudo conectar con la IA. Revisa tu internet."
                )
            }
        }
    }
    var pastingLyricsFor by remember { mutableStateOf<Song?>(null) }
    var lyricsFileFor by remember { mutableStateOf<Song?>(null) }
    val lyricsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val song = lyricsFileFor
        lyricsFileFor = null
        if (uri != null && song != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { runCatching { LyricsStore.importFile(context, song.id, uri) }.isSuccess }
                Toast.makeText(context, if (ok) "Letra agregada" else "No se pudo leer ese archivo", Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun pickLyricsFile(song: Song) {
        lyricsFileFor = song
        lyricsPicker.launch(arrayOf("*/*"))
    }

    // ── Color de la app según la portada que suena
    val accent by rememberDynamicAccent(rememberArtwork(currentSong, 1024))

    // Ventanitas de opciones
    var optionsFor by remember { mutableStateOf<Pair<Song, Long?>?>(null) }
    var pickPlaylistFor by remember { mutableStateOf<List<Song>?>(null) }
    var newPlaylistFor by remember { mutableStateOf<List<Song>?>(null) }
    var creatingPlaylist by remember { mutableStateOf(false) }
    var playlistOptions by remember { mutableStateOf<Long?>(null) }
    var renamingPlaylist by remember { mutableStateOf<Long?>(null) }
    var deletingPlaylist by remember { mutableStateOf<Long?>(null) }

    var showPlayer by rememberSaveable { mutableStateOf(false) }

    fun playPause() {
        val player = controller ?: return
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
    }

    // Todo lo de abajo usa el color sacado de la portada.
    CompositionLocalProvider(LocalAccent provides accent) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val scope = rememberCoroutineScope()
        // 0 = reproductor arriba (visible); heightPx = escondido abajo.
        val offsetY = remember { Animatable(if (showPlayer) 0f else heightPx) }
        LaunchedEffect(showPlayer, heightPx) {
            offsetY.animateTo(
                if (showPlayer) 0f else heightPx,
                tween(durationMillis = 420, easing = FastOutSlowInEasing),
            )
        }

        LibraryScreen(
            songs = songs,
            playback = ui,
            store = store,
            position = position,
            onPlay = { list, index, source ->
                controller?.let { player ->
                    player.setMediaItems(list.map { it.toMediaItem() }, index, 0L)
                    player.prepare()
                    player.play()
                    playingFrom = source
                    store.saveQueue(list.map { it.id }, source)
                    showPlayer = true
                }
            },
            onSongOptions = { song, playlistId -> optionsFor = song to playlistId },
            onNewPlaylist = { creatingPlaylist = true },
            onPlaylistOptions = { playlistOptions = it },
            onOpenSettings = { showSettings = true },
            onBulk = { action, list ->
                when (action) {
                    BulkAction.PLAY_NEXT -> enqueue(list, next = true)
                    BulkAction.QUEUE -> enqueue(list, next = false)
                    BulkAction.PLAYLIST -> pickPlaylistFor = list
                    BulkAction.FAVORITE -> {
                        list.forEach { if (!store.isFavorite(it.id)) store.toggleFavorite(it.id) }
                        Toast.makeText(context, "Agregadas a favoritos", Toast.LENGTH_SHORT).show()
                    }
                    BulkAction.DELETE -> requestDelete(list)
                }
            },
            onOpenPlayer = { showPlayer = true },
            onPlayPause = ::playPause,
        )

        // Oscurece la lista mientras el reproductor sube o baja (transición suave).
        val playerVisible by remember(heightPx) { derivedStateOf { offsetY.value < heightPx - 0.5f } }
        if (playerVisible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = (1f - offsetY.value / heightPx).coerceIn(0f, 1f) * 0.7f }
                    .background(Color.Black)
            )
        }

        if (ui.song != null && playerVisible) {
            val player = controller
            BackHandler(enabled = showPlayer) { showPlayer = false }
            Box(
                Modifier
                    .fillMaxSize()
                    // Deslizar el dedo hacia abajo baja el reproductor siguiendo tu dedo.
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            scope.launch { offsetY.snapTo((offsetY.value + delta).coerceIn(0f, heightPx)) }
                        },
                        onDragStopped = { velocity ->
                            if (offsetY.value > heightPx * 0.22f || velocity > 1800f) {
                                if (showPlayer) {
                                    showPlayer = false
                                } else {
                                    offsetY.animateTo(heightPx, tween(300, easing = FastOutSlowInEasing))
                                }
                            } else {
                                offsetY.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                            }
                        },
                    )
                    // Se mueve con graphicsLayer (en la GPU), sin recalcular la pantalla en cada cuadro.
                    .graphicsLayer { translationY = offsetY.value }
            ) {
                NowPlayingScreen(
                    playback = ui,
                    playingFrom = playingFrom,
                    isFavorite = ui.song?.let { store.isFavorite(it.id) } ?: false,
                    onToggleFavorite = { ui.song?.let { store.toggleFavorite(it.id) } },
                    onAddToPlaylist = { pickPlaylistFor = ui.song?.let { listOf(it) } },
                    position = position,
                    onOpenQueue = { showQueue = true },
                    onChangeCover = { ui.song?.let { pickCover(it) } },
                    onEditInfo = { editing = ui.song },
                    lyrics = lyrics,
                    hasSavedLyrics = ui.song?.let { LyricsStore.version >= 0 && LyricsStore.hasSaved(it.id) } ?: false,
                    onLoadLyricsFile = { ui.song?.let { pickLyricsFile(it) } },
                    onPasteLyrics = { pastingLyricsFor = ui.song },
                    onRemoveLyrics = { ui.song?.let { LyricsStore.remove(it.id) } },
                    lyricsStatus = lyricsStatus,
                    hasAiKey = store.activeAiKey.isNotBlank(),
                    animatedCover = store.animatedCovers,
                    videoCover = ui.song?.let { s -> CustomCovers.version.let { CustomCovers.videoFile(s.id) } },
                    onSearchLyrics = ::searchLyrics,
                    onGenerateLyrics = ::generateLyrics,
                    onPlayPause = ::playPause,
                    onPrevious = { player?.seekToPrevious() },
                    onNext = { player?.seekToNext() },
                    onSeek = { player?.seekTo(it) },
                    onToggleShuffle = { player?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } },
                    onCycleLoop = {
                        player?.let {
                            it.repeatMode = when (it.repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                        }
                    },
                    onShare = {
                        ui.song?.let { song ->
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(Intent.EXTRA_STREAM, song.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Compartir canción"))
                        }
                    },
                    onClose = { showPlayer = false },
                )
            }
        }
    }

    if (showSettings) {
        SettingsDialog(store = store, onDismiss = { showSettings = false })
    }

    choosingCoverFor?.let { song ->
        CoverChooserDialog(
            presets = CustomCovers.presets,
            onPreset = { asset ->
                choosingCoverFor = null
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { runCatching { CustomCovers.setPreset(context, song.id, asset) }.isSuccess }
                    Toast.makeText(context, if (ok) "Portada cambiada" else "No se pudo cambiar la portada", Toast.LENGTH_SHORT).show()
                }
            },
            onGallery = { choosingCoverFor = null; pickFromGallery(song) },
            onVideo = {
                choosingCoverFor = null
                videoFor = song
                videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            },
            onDismiss = { choosingCoverFor = null },
        )
    }

    editing?.let { song ->
        EditInfoDialog(
            title = song.title,
            artist = song.artist,
            isEdited = store.edits.containsKey(song.id),
            onSave = { t, a -> store.saveEdit(song.id, SongEdit(t, a)); editing = null },
            onRestore = { store.saveEdit(song.id, null); editing = null },
            onDismiss = { editing = null },
        )
    }
    pastingLyricsFor?.let { song ->
        PasteLyricsDialog(
            onSave = { text ->
                LyricsStore.saveText(context, song.id, text)
                pastingLyricsFor = null
                Toast.makeText(context, "Letra guardada", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { pastingLyricsFor = null },
        )
    }

    if (showQueue) {
        QueueSheet(
            queue = queue,
            currentIndex = queueIndex,
            onJumpTo = { i -> controller?.let { it.seekTo(i, 0L); it.play() } },
            onRemove = { i -> controller?.removeMediaItem(i); saveQueueNow() },
            onDismiss = { showQueue = false },
        )
    }

    optionsFor?.let { (song, playlistId) ->
        SongOptionsDialog(
            title = song.title,
            subtitle = song.artist,
            isFavorite = store.isFavorite(song.id),
            inPlaylist = playlistId != null,
            hasCustomCover = CustomCovers.version >= 0 && CustomCovers.has(song.id),
            onPlayNext = { enqueue(song, next = true); optionsFor = null },
            onAddToQueue = { enqueue(song, next = false); optionsFor = null },
            onChangeCover = { optionsFor = null; pickCover(song) },
            onRemoveCover = { CustomCovers.remove(song.id); optionsFor = null },
            onDelete = { optionsFor = null; requestDelete(listOf(song)) },
            onEdit = { optionsFor = null; editing = song },
            onToggleFavorite = { store.toggleFavorite(song.id); optionsFor = null },
            onAddToPlaylist = { pickPlaylistFor = listOf(song); optionsFor = null },
            onRemoveFromPlaylist = { playlistId?.let { store.removeFromPlaylist(it, song.id) }; optionsFor = null },
            onDismiss = { optionsFor = null },
        )
    }
    pickPlaylistFor?.let { list ->
        PlaylistPickerDialog(
            playlists = store.playlists,
            onPick = { p ->
                store.addToPlaylist(p.id, list.map { it.id })
                val what = if (list.size == 1) "Agregada" else "${list.size} canciones agregadas"
                Toast.makeText(context, "$what a ${p.name}", Toast.LENGTH_SHORT).show()
                pickPlaylistFor = null
            },
            onNew = { newPlaylistFor = list; pickPlaylistFor = null },
            onDismiss = { pickPlaylistFor = null },
        )
    }
    if (creatingPlaylist || newPlaylistFor != null) {
        NameDialog(
            title = "Nueva playlist",
            initial = "",
            confirmLabel = "Crear",
            onConfirm = { name ->
                val list = newPlaylistFor
                store.createPlaylist(name, list.orEmpty().map { it.id })
                if (!list.isNullOrEmpty()) Toast.makeText(context, "Playlist \"$name\" creada", Toast.LENGTH_SHORT).show()
                creatingPlaylist = false; newPlaylistFor = null
            },
            onDismiss = { creatingPlaylist = false; newPlaylistFor = null },
        )
    }
    playlistOptions?.let { id ->
        val p = store.playlists.firstOrNull { it.id == id }
        if (p == null) playlistOptions = null else PlaylistOptionsDialog(
            name = p.name,
            onRename = { renamingPlaylist = id; playlistOptions = null },
            onDelete = { deletingPlaylist = id; playlistOptions = null },
            onDismiss = { playlistOptions = null },
        )
    }
    renamingPlaylist?.let { id ->
        val p = store.playlists.firstOrNull { it.id == id }
        NameDialog(
            title = "Cambiar nombre",
            initial = p?.name.orEmpty(),
            confirmLabel = "Guardar",
            onConfirm = { store.renamePlaylist(id, it); renamingPlaylist = null },
            onDismiss = { renamingPlaylist = null },
        )
    }
    deletingPlaylist?.let { id ->
        val p = store.playlists.firstOrNull { it.id == id }
        ConfirmDeleteDialog(
            name = p?.name.orEmpty(),
            onConfirm = { store.deletePlaylist(id); deletingPlaylist = null },
            onDismiss = { deletingPlaylist = null },
        )
    }
    }
}
