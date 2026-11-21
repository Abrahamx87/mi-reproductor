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
import com.abraham.opsound.PlaybackService
import com.abraham.opsound.Song
import com.abraham.opsound.loadSongs
import com.abraham.opsound.toMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

val AppBackground = Color(0xFF070B0F)
val Accent = Color(0xFF4FC3F7)

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
    val positionMs: Long = 0,
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
        positionMs = player.currentPosition.coerceAtLeast(0),
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
    var songs by remember { mutableStateOf<List<Song>?>(null) }
    LaunchedEffect(Unit) {
        songs = withContext(Dispatchers.IO) { runCatching { loadSongs(context) }.getOrDefault(emptyList()) }
    }
    val songsById = remember(songs) { songs.orEmpty().associateBy { it.id.toString() } }

    val controller = rememberMediaController()
    var ui by remember { mutableStateOf(PlaybackUi()) }
    LaunchedEffect(controller, songsById) {
        val player = controller ?: return@LaunchedEffect
        while (true) {
            ui = readPlayback(player, songsById)
            delay(250)
        }
    }

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
        val openFraction = (1f - offsetY.value / heightPx).coerceIn(0f, 1f)

        LibraryScreen(
            songs = songs,
            playback = ui,
            onSongClick = { list, index ->
                controller?.let { player ->
                    player.setMediaItems(list.map { it.toMediaItem() }, index, 0L)
                    player.prepare()
                    player.play()
                    showPlayer = true
                }
            },
            onOpenPlayer = { showPlayer = true },
            onPlayPause = ::playPause,
        )

        // Oscurece la lista mientras el reproductor sube o baja (transición suave).
        if (openFraction > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = openFraction * 0.7f }
                    .background(Color.Black)
            )
        }

        if (ui.song != null && offsetY.value < heightPx) {
            val player = controller
            BackHandler(enabled = showPlayer) { showPlayer = false }
            Box(
                Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, offsetY.value.roundToInt()) }
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
            ) {
                NowPlayingScreen(
                    playback = ui,
                    playingFrom = "Tu música",
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
}
