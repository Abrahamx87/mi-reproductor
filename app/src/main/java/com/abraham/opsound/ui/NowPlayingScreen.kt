package com.abraham.opsound.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.delay
import java.io.File
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.runtime.saveable.rememberSaveable
import com.abraham.opsound.Lyrics
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.animation.core.tween
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    position: () -> Long,
    onOpenQueue: () -> Unit,
    onChangeCover: () -> Unit,
    onEditInfo: () -> Unit,
    lyrics: Lyrics?,
    lyricsStatus: LyricsStatus,
    hasSavedLyrics: Boolean,
    hasAiKey: Boolean,
    animatedCover: Boolean,
    videoCover: File?,
    onLoadLyricsFile: () -> Unit,
    onPasteLyrics: () -> Unit,
    onRemoveLyrics: () -> Unit,
    onSearchLyrics: () -> Unit,
    onGenerateLyrics: () -> Unit,
) {
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    // En modo letra, los controles se esconden solos; toca la pantalla para verlos.
    var controlsVisible by remember { mutableStateOf(true) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    LaunchedEffect(showLyrics, controlsVisible, lastTouch) {
        if (showLyrics && controlsVisible) {
            delay(4_000)
            controlsVisible = false
        }
    }
    LaunchedEffect(showLyrics) { if (!showLyrics) controlsVisible = true }
    // Compartir letra: índice de la línea con la que se abrió (-1 = cerrado).
    var shareFrom by remember { mutableStateOf<Int?>(null) }
    val song = playback.song
    val cover = rememberArtwork(song, 1024)
    val background = rememberBlurredBackground(song, cover)
    val smooth = rememberSmoothPosition(position, playback.durationMs, playback.isPlaying)
    var menuOpen by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            // Nada del fondo se puede salir del reproductor (evita la franja que quedaba colgada).
            .clipToBounds()
            .background(AppBackground)
            // Evita que los toques pasen a la lista que está debajo.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Crossfade(targetState = background, animationSpec = tween(450), label = "fondo") { art ->
            BlurredCoverBackground(art, Modifier.fillMaxSize())
        }

        // Menú ⋮ (se usa en los dos modos).
        val menu: @Composable () -> Unit = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Más opciones", tint = Color.White)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Ver fila de reproducción") }, onClick = { menuOpen = false; onOpenQueue() })
                    DropdownMenuItem(text = { Text("Agregar a playlist") }, onClick = { menuOpen = false; onAddToPlaylist() })
                    DropdownMenuItem(text = { Text("Cambiar portada") }, onClick = { menuOpen = false; onChangeCover() })
                    DropdownMenuItem(text = { Text("Editar información") }, onClick = { menuOpen = false; onEditInfo() })
                    DropdownMenuItem(text = { Text("Letra: buscar en internet") }, onClick = { menuOpen = false; showLyrics = true; onSearchLyrics() })
                    DropdownMenuItem(
                        text = { Text(if (lyrics != null && !lyrics.synced) "Letra: sincronizar con IA ✨" else "Letra: generar con IA ✨") },
                        onClick = { menuOpen = false; showLyrics = true; onGenerateLyrics() },
                    )
                    DropdownMenuItem(text = { Text("Letra: cargar archivo .lrc") }, onClick = { menuOpen = false; onLoadLyricsFile() })
                    DropdownMenuItem(text = { Text("Letra: pegar texto") }, onClick = { menuOpen = false; onPasteLyrics() })
                    if (hasSavedLyrics) {
                        DropdownMenuItem(text = { Text("Quitar letra guardada") }, onClick = { menuOpen = false; onRemoveLyrics() })
                    }
                    if (lyrics != null && lyrics.lines.any { it.text.isNotBlank() }) {
                        DropdownMenuItem(text = { Text("Compartir letra") }, onClick = {
                            menuOpen = false
                            val t = smooth.displayMs
                            val shareLines = lyrics.lines.filter { it.text.isNotBlank() }
                            shareFrom = shareLines.indexOfLast { (it.timeMs ?: 0L) <= t }.coerceAtLeast(0)
                        })
                    }
                    DropdownMenuItem(text = { Text("Compartir canción") }, onClick = { menuOpen = false; onShare() })
                    DropdownMenuItem(text = { Text("Volver a la biblioteca") }, onClick = { menuOpen = false; onClose() })
                }
            }
        }

        val heart: @Composable () -> Unit = {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isFavorite) "Quitar de favoritos" else "Agregar a favoritos",
                    tint = if (isFavorite) FavoriteColor else Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // Barra de tiempo + controles + botones de abajo (iguales en los dos modos).
        val controls: @Composable () -> Unit = {
            Column {
                SmoothSeekBar(
                    activeColor = Accent,
                    smooth = smooth,
                    durationMs = playback.durationMs,
                    onSeek = { lastTouch = System.nanoTime(); onSeek(it) },
                    modifier = Modifier.fillMaxWidth(),
                )
                // El texto solo cambia una vez por segundo (no en cada cuadro).
                val elapsed by remember(smooth) { derivedStateOf { formatTime(smooth.displayMs) } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(elapsed, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    Text(formatTime(playback.durationMs), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
                Spacer(Modifier.height(if (showLyrics) 12.dp else 24.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { lastTouch = System.nanoTime(); onToggleShuffle() }) {
                        Icon(
                            Icons.Rounded.Shuffle, contentDescription = "Aleatorio", tint = if (playback.shuffle) Accent else Color.White,
                            modifier = Modifier.size(28.dp).alpha(if (playback.shuffle) 1f else 0.35f),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { lastTouch = System.nanoTime(); onPrevious() }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior", tint = Color.White, modifier = Modifier.size(38.dp))
                        }
                        Spacer(Modifier.width(18.dp))
                        IconButton(onClick = { lastTouch = System.nanoTime(); onPlayPause() }, modifier = Modifier.size(76.dp)) {
                            if (playback.isPlaying) {
                                SquarePauseIcon(Modifier.size(66.dp))
                            } else {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir", tint = Color.White, modifier = Modifier.size(72.dp))
                            }
                        }
                        Spacer(Modifier.width(18.dp))
                        IconButton(onClick = { lastTouch = System.nanoTime(); onNext() }, enabled = playback.hasNext, modifier = Modifier.size(52.dp)) {
                            Icon(
                                Icons.Filled.SkipNext, contentDescription = "Siguiente", tint = Color.White,
                                modifier = Modifier.size(38.dp).alpha(if (playback.hasNext) 1f else 0.35f),
                            )
                        }
                    }
                    IconButton(onClick = { lastTouch = System.nanoTime(); onCycleLoop() }) {
                        Icon(
                            if (playback.loop == LoopMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = "Repetir", tint = if (playback.loop == LoopMode.OFF) Color.White else Accent,
                            modifier = Modifier.size(28.dp).alpha(if (playback.loop == LoopMode.OFF) 0.35f else 1f),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = if (showLyrics) 4.dp else 22.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(
                            Icons.Rounded.Subtitles,
                            contentDescription = if (showLyrics) "Ver portada" else "Ver letra",
                            tint = if (showLyrics) Accent else Color.White.copy(alpha = if (lyrics != null) 1f else 0.6f),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    IconButton(onClick = onOpenQueue) {
                        Icon(Icons.Rounded.FormatListBulleted, contentDescription = "Fila de reproducción", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
        ) {
            if (!showLyrics) {
                // ── MODO PORTADA
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Reproduciendo desde", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            playingFrom,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    menu()
                }

                Spacer(Modifier.height(34.dp))

                CoverArt(
                    cover = cover,
                    videoFile = videoCover,
                    animated = animatedCover,
                    isPlaying = playback.isPlaying,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    onTap = onPlayPause,
                    onSwipeLeft = onNext,
                    onSwipeRight = onPrevious,
                )

                Spacer(Modifier.height(34.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            song?.title.orEmpty(),
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            song?.artist.orEmpty(),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    heart()
                    IconButton(onClick = onShare) {
                        Icon(Icons.Rounded.Share, contentDescription = "Compartir", tint = Color.White)
                    }
                }

                Spacer(Modifier.height(24.dp))
                controls()
                Spacer(Modifier.weight(1f))
            } else {
                // ── MODO LETRA (pantalla completa, como Apple Music)
                Row(
                    Modifier.padding(top = 6.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { showLyrics = false },
                    ) {
                        if (cover != null) Image(cover, contentDescription = "Volver a la portada", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(song?.title.orEmpty(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song?.artist.orEmpty(), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    heart()
                    menu()
                }

                LyricsView(
                    lyrics = lyrics,
                    status = lyricsStatus,
                    smooth = smooth,
                    durationMs = playback.durationMs,
                    hasAiKey = hasAiKey,
                    onSeek = { lastTouch = System.nanoTime(); onSeek(it) },
                    onTapEmpty = { controlsVisible = !controlsVisible; lastTouch = System.nanoTime() },
                    onSearchOnline = onSearchLyrics,
                    onGenerateAi = onGenerateLyrics,
                    onLoadFile = onLoadLyricsFile,
                    onPaste = onPasteLyrics,
                    onShareLine = { line ->
                        val shareLines = lyrics?.lines.orEmpty().filter { it.text.isNotBlank() }
                        shareFrom = shareLines.indexOf(line).coerceAtLeast(0)
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )

                AnimatedVisibility(
                    visible = controlsVisible || lyrics == null,
                    enter = fadeIn(tween(250)) + expandVertically(),
                    exit = fadeOut(tween(250)) + shrinkVertically(),
                ) {
                    Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) { controls() }
                }
            }
        }
        shareFrom?.let { start ->
            val shareLines = lyrics?.lines.orEmpty().filter { it.text.isNotBlank() }.map { it.text }
            ShareLyricsScreen(
                lines = shareLines,
                startIndex = start,
                cover = cover,
                title = song?.title.orEmpty(),
                artist = song?.artist.orEmpty(),
                onDismiss = { shareFrom = null },
            )
        }
    }
}

/** Fondo: la portada ampliada, muy desenfocada, con un velo oscuro que baja a casi negro. */
@Composable
private fun BlurredCoverBackground(cover: ImageBitmap?, modifier: Modifier = Modifier) {
    Box(modifier.clipToBounds().background(AppBackground)) {
        if (cover != null) {
            Image(
                bitmap = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // Ya viene desenfocada y chiquita; al estirarla queda suave sin costo por cuadro.
                filterQuality = FilterQuality.High,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = 1.25f; scaleY = 1.25f },
                // Más color de la portada en el fondo.
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.7f) }),
            )
        }
        // Orillas más oscuras (viñeta) + degradado hacia negro abajo.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0.0f to Color.Black.copy(alpha = 0.10f),
                        1.0f to Color.Black.copy(alpha = 0.55f),
                        radius = 1400f,
                        center = Offset(540f, 700f),
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Black.copy(alpha = 0.20f),
                        0.50f to Color.Black.copy(alpha = 0.45f),
                        1.00f to Color.Black.copy(alpha = 0.93f),
                    )
                )
        )
    }
}

@Composable
private fun CoverArt(
    cover: ImageBitmap?,
    videoFile: File?,
    animated: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
) {
    // Toca la portada para pausar; deslízala a los lados para cambiar de canción.
    val dragX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val tap by rememberUpdatedState(onTap)
    val left by rememberUpdatedState(onSwipeLeft)
    val right by rememberUpdatedState(onSwipeRight)
    // Como Apple Music: al pausar, la portada se hace un poco más chica.
    val pauseScale by animateFloatAsState(
        if (isPlaying) 1f else 0.86f,
        spring(dampingRatio = 0.7f, stiffness = 220f),
        label = "pausa",
    )

    Box(
        modifier
            .graphicsLayer {
                translationX = dragX.value
                scaleX = pauseScale; scaleY = pauseScale
                alpha = 1f - (abs(dragX.value) / (size.width * 1.6f)).coerceIn(0f, 0.6f)
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { tap() }) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val limit = size.width * 0.25f
                        val moved = dragX.value
                        scope.launch {
                            when {
                                moved < -limit -> left()
                                moved > limit -> right()
                            }
                            dragX.animateTo(0f)
                        }
                    },
                    onDragCancel = { scope.launch { dragX.animateTo(0f) } },
                ) { change, amount ->
                    change.consume()
                    scope.launch { dragX.snapTo(dragX.value + amount) }
                }
            }
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            videoFile != null -> VideoCover(videoFile, playing = isPlaying, modifier = Modifier.fillMaxSize())
            cover != null -> LivingCover(cover, animated = animated, modifier = Modifier.fillMaxSize())
            else -> Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(96.dp))
        }
    }
}

/** Pausa con barras casi cuadradas, como en BandLab. */
@Composable
private fun SquarePauseIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val barW = size.width * 0.19f
        val barH = size.height * 0.62f
        val top = (size.height - barH) / 2f
        val gap = size.width * 0.16f
        val left = (size.width - (barW * 2 + gap)) / 2f
        val r = CornerRadius(size.width * 0.04f)
        drawRoundRect(Color.White, Offset(left, top), Size(barW, barH), r)
        drawRoundRect(Color.White, Offset(left + barW + gap, top), Size(barW, barH), r)
    }
}
