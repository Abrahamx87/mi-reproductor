package com.abraham.opsound.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abraham.opsound.formatTime
import kotlin.math.roundToInt

/**
 * Pantalla "Reproduciendo" estilo BandLab clásico:
 * fondo = la misma portada difuminada y oscurecida, portada grande con esquinas redondeadas,
 * título + artista, barra de progreso delgada y controles abajo.
 */
@Composable
fun NowPlayingScreen(
    playback: PlaybackUi,
    playingFrom: String,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleLoop: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    val song = playback.song
    val cover = rememberArtwork(song, 1024)
    var menuOpen by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(AppBackground)
            // Evita que los toques pasen a la lista que está debajo.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Crossfade(targetState = cover, label = "fondo") { art ->
            BlurredCoverBackground(art, Modifier.fillMaxSize())
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            // ── Encabezado: bajar · "Reproduciendo desde" · menú ⋮
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Cerrar", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Reproduciendo desde", color = Color.White.copy(alpha = 0.55f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        playingFrom,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Más opciones", tint = Color.White)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Compartir canción") }, onClick = { menuOpen = false; onShare() })
                        DropdownMenuItem(text = { Text("Volver a la biblioteca") }, onClick = { menuOpen = false; onClose() })
                    }
                }
            }

            Spacer(Modifier.weight(0.6f))

            // ── Portada grande con esquinas redondeadas
            CoverArt(cover, Modifier.fillMaxWidth().aspectRatio(1f))

            Spacer(Modifier.weight(1f))

            // ── Título + artista + compartir
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        song?.title.orEmpty(),
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        song?.artist.orEmpty(),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Rounded.Share, contentDescription = "Compartir", tint = Color.White)
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Barra de progreso + tiempos
            val progress = if (playback.durationMs > 0) {
                (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f)
            } else 0f

            ThinSeekBar(
                progress = progress,
                onSeekFinished = { fraction -> onSeek((fraction * playback.durationMs).toLong()) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(playback.positionMs), color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                Text(formatTime(playback.durationMs), color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            }

            Spacer(Modifier.height(18.dp))

            // ── Controles: aleatorio · anterior · play/pausa · siguiente · repetir
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        Icons.Rounded.Shuffle, contentDescription = "Aleatorio", tint = Color.White,
                        modifier = Modifier.size(28.dp).alpha(if (playback.shuffle) 1f else 0.35f),
                    )
                }
                IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Anterior", tint = Color.White, modifier = Modifier.size(44.dp))
                }
                IconButton(onClick = onPlayPause, modifier = Modifier.size(80.dp)) {
                    Icon(
                        if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playback.isPlaying) "Pausar" else "Reproducir",
                        tint = Color.White,
                        modifier = Modifier.size(72.dp),
                    )
                }
                IconButton(onClick = onNext, enabled = playback.hasNext, modifier = Modifier.size(56.dp)) {
                    Icon(
                        Icons.Rounded.SkipNext, contentDescription = "Siguiente", tint = Color.White,
                        modifier = Modifier.size(44.dp).alpha(if (playback.hasNext) 1f else 0.35f),
                    )
                }
                IconButton(onClick = onCycleLoop) {
                    Icon(
                        if (playback.loop == LoopMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = "Repetir", tint = Color.White,
                        modifier = Modifier.size(28.dp).alpha(if (playback.loop == LoopMode.OFF) 0.35f else 1f),
                    )
                }
            }

            // ── Lista de canciones (abajo a la derecha)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.QueueMusic, contentDescription = "Lista de canciones", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}

/** Fondo: la portada ampliada, muy desenfocada, con un velo oscuro que baja a casi negro. */
@Composable
private fun BlurredCoverBackground(cover: ImageBitmap?, modifier: Modifier = Modifier) {
    Box(modifier.background(AppBackground)) {
        if (cover != null) {
            Image(
                bitmap = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = 1.4f; scaleY = 1.4f }
                    .blur(60.dp, BlurredEdgeTreatment.Unbounded),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Black.copy(alpha = 0.30f),
                        0.55f to Color.Black.copy(alpha = 0.55f),
                        1.00f to Color.Black.copy(alpha = 0.92f),
                    )
                )
        )
    }
}

@Composable
private fun CoverArt(cover: ImageBitmap?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        if (cover != null) {
            Image(bitmap = cover, contentDescription = "Portada", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(96.dp))
        }
    }
}

/** Barra delgada con bolita blanca; se puede tocar o arrastrar. */
@Composable
private fun ThinSeekBar(
    progress: Float,
    onSeekFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableIntStateOf(1) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val onFinished by rememberUpdatedState(onSeekFinished)
    val shown = dragFraction ?: progress

    Box(
        modifier
            .height(28.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectTapGestures { offset -> onFinished((offset.x / widthPx).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> dragFraction = (offset.x / widthPx).coerceIn(0f, 1f) },
                    onDragEnd = {
                        dragFraction?.let(onFinished)
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                ) { change, _ ->
                    change.consume()
                    dragFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                }
            }
    ) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.25f), CircleShape))
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(shown).height(2.dp).background(Color.White, CircleShape))
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((shown * widthPx - 6.dp.toPx()).roundToInt(), 0) }
                .size(12.dp)
                .background(Color.White, CircleShape)
        )
    }
}
