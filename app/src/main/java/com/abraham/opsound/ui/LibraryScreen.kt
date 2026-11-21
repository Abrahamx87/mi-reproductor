package com.abraham.opsound.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abraham.opsound.Song
import com.abraham.opsound.formatTime

@Composable
fun LibraryScreen(
    songs: List<Song>?,
    playback: PlaybackUi,
    onSongClick: (Int) -> Unit,
    onOpenPlayer: () -> Unit,
    onPlayPause: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Text(
            "Opsound",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp),
        )

        when {
            songs == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }

            songs.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No encontré canciones en tu celular.\nGuarda archivos de música (MP3, M4A…) en la carpeta Música o Descargas.",
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }

            else -> {
                Text(
                    "${songs.size} canciones",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
                )
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                        SongRow(
                            song = song,
                            isCurrent = song.id == playback.song?.id,
                            onClick = { onSongClick(index) },
                        )
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

@Composable
private fun SongRow(song: Song, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
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
            Text(
                song.artist,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(formatTime(song.durationMs), color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
    }
}

@Composable
private fun SmallCover(song: Song, size: Dp) {
    val art = rememberArtwork(song, 200)
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
        }
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
