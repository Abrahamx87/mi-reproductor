package com.abraham.opsound.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abraham.opsound.Lyrics

/** Letra de la canción. Si está sincronizada, resalta la línea actual y se desplaza sola; toca una línea para saltar ahí. */
@Composable
fun LyricsView(
    lyrics: Lyrics?,
    smooth: SmoothPosition,
    onSeek: (Long) -> Unit,
    onLoadFile: () -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.25f)),
    ) {
        when {
            lyrics == null || lyrics.lines.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Esta canción no tiene letra", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Carga un archivo .lrc (letra sincronizada) o pega la letra.",
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onLoadFile) { Text("Cargar archivo .lrc", color = Accent) }
                TextButton(onClick = onPaste) { Text("Pegar letra", color = Accent) }
            }

            lyrics.synced -> {
                val lines = lyrics.lines
                val current by remember(lines) {
                    derivedStateOf {
                        val t = smooth.displayMs + 250 // un poquito antes, se siente más natural
                        lines.indexOfLast { (it.timeMs ?: 0L) <= t }
                    }
                }
                val listState = rememberLazyListState()
                val centerOffset = with(LocalDensity.current) { 110.dp.roundToPx() }
                LaunchedEffect(current) {
                    if (current >= 0) listState.animateScrollToItem(current, -centerOffset)
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 120.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(lines) { index, line ->
                        val isNow = index == current
                        Text(
                            line.text.ifBlank { "♪" },
                            color = Color.White.copy(alpha = if (isNow) 1f else 0.4f),
                            fontSize = if (isNow) 22.sp else 19.sp,
                            fontWeight = if (isNow) FontWeight.Bold else FontWeight.SemiBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { line.timeMs?.let(onSeek) }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }

            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            ) {
                lyrics.lines.forEach { line ->
                    Text(line.text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
        }
    }
}
