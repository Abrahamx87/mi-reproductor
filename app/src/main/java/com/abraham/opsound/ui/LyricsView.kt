package com.abraham.opsound.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abraham.opsound.LyricLine
import com.abraham.opsound.LyricWord
import com.abraham.opsound.Lyrics
import kotlin.math.abs
import kotlin.math.min

/** Qué está pasando con la letra de la canción actual. */
sealed interface LyricsStatus {
    data object Idle : LyricsStatus
    data object Searching : LyricsStatus
    data object Generating : LyricsStatus
    data class Failed(val message: String) : LyricsStatus
}

/** Un renglón en pantalla: una línea de letra o los puntitos de una parte sin voz. */
private sealed interface Row2 {
    val startMs: Long
    data class Line(val line: LyricLine, override val startMs: Long, val endMs: Long, val words: List<LyricWord>) : Row2
    data class Dots(override val startMs: Long, val endMs: Long) : Row2
}

/** Prepara las líneas: tiempos por palabra (reales o estimados) y puntitos en las partes instrumentales. */
private fun buildRows(lines: List<LyricLine>, durationMs: Long): List<Row2> {
    val rows = mutableListOf<Row2>()
    val timed = lines.filter { it.timeMs != null }
    if (timed.isEmpty()) return rows
    val first = timed.first().timeMs!!
    if (first > 3_000) rows += Row2.Dots(0, first)

    timed.forEachIndexed { i, line ->
        val start = line.timeMs!!
        val next = timed.getOrNull(i + 1)?.timeMs ?: maxOf(durationMs, start + 4_000)
        val words = line.words.ifEmpty { estimateWords(line.text, start, min(next, start + 6_000)) }
        // Fin real de la voz: última palabra + un poco (o el inicio de la siguiente línea).
        val vocalEnd = if (line.words.isNotEmpty()) min(next, line.words.last().timeMs + 900) else next
        rows += Row2.Line(line, start, vocalEnd, words)
        if (next - vocalEnd > 5_000 && line.text.isNotBlank()) rows += Row2.Dots(vocalEnd, next)
    }
    return rows
}

/** Si la letra solo trae tiempo por línea, repartimos las palabras a lo largo de la línea. */
private fun estimateWords(text: String, start: Long, end: Long): List<LyricWord> {
    val parts = text.split(' ').filter { it.isNotBlank() }
    if (parts.isEmpty()) return emptyList()
    val total = parts.sumOf { it.length + 1 }.toFloat()
    var acc = 0f
    return parts.map { word ->
        val t = start + ((end - start) * (acc / total)).toLong()
        acc += word.length + 1
        LyricWord(t, word)
    }
}

/**
 * Letra estilo Apple Music: la línea actual brilla palabra por palabra, las demás se ven tenues y borrosas,
 * se mueve sola y, si te desplazas con el dedo, aparece "Sincronizar de nuevo".
 */
@Composable
fun LyricsView(
    lyrics: Lyrics?,
    status: LyricsStatus,
    smooth: SmoothPosition,
    durationMs: Long,
    hasAiKey: Boolean,
    onSeek: (Long) -> Unit,
    onTapEmpty: () -> Unit,
    onSearchOnline: () -> Unit,
    onGenerateAi: () -> Unit,
    onLoadFile: () -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.pointerInput(Unit) { detectTapGestures { onTapEmpty() } }) {
        when {
            lyrics == null || lyrics.lines.isEmpty() -> EmptyLyrics(status, hasAiKey, onSearchOnline, onGenerateAi, onLoadFile, onPaste)
            lyrics.synced -> SyncedLyrics(lyrics, smooth, durationMs, onSeek)
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp)) {
                lyrics.lines.forEach { line ->
                    Text(
                        line.text,
                        color = Color.White.copy(alpha = if (line.text.isBlank()) 0f else 0.9f),
                        fontSize = 24.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Letra sin tiempos. Toca ✨ para que la IA la sincronice.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun SyncedLyrics(lyrics: Lyrics, smooth: SmoothPosition, durationMs: Long, onSeek: (Long) -> Unit) {
    val rows = remember(lyrics, durationMs) { buildRows(lyrics.lines, durationMs) }
    val current by remember(rows) {
        derivedStateOf {
            val t = smooth.displayMs + 150
            rows.indexOfLast { it.startMs <= t }.coerceAtLeast(0)
        }
    }
    val listState = rememberLazyListState()
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var manual by remember { mutableStateOf(false) }
    LaunchedEffect(dragged) { if (dragged) manual = true }

    val topOffset = with(LocalDensity.current) { 90.dp.roundToPx() }
    LaunchedEffect(current, manual) {
        if (!manual) listState.animateScrollToItem(current, -topOffset)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 90.dp, bottom = 320.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(rows) { index, row ->
                val distance = abs(index - current)
                val isNow = index == current
                // Apple: las líneas lejanas se ven más borrosas (solo cuando la letra va sola).
                val blurDp = if (manual || isNow) 0f else min(distance, 3) * 1.3f
                val scale by animateFloatAsState(if (isNow) 1f else 0.95f, tween(350, easing = FastOutSlowInEasing), label = "escala")
                Box(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale; scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .then(if (blurDp > 0f) Modifier.blur(blurDp.dp) else Modifier)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { manual = false; onSeek(row.startMs) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                ) {
                    when (row) {
                        is Row2.Dots -> InstrumentalDots(row, smooth, isNow)
                        is Row2.Line -> if (isNow) {
                            KaraokeLine(row, smooth)
                        } else {
                            Text(
                                row.line.text.ifBlank { "♪" },
                                color = Color.White.copy(alpha = if (index < current) 0.3f else 0.38f),
                                fontSize = 27.sp,
                                lineHeight = 33.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        // Si te moviste por la letra: botón para volver a seguir la canción.
        AnimatedVisibility(
            visible = manual,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        ) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF2A333B))
                    .clickable { manual = false }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Sync, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sincronizar de nuevo", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** La línea que suena: cada palabra se ilumina justo cuando se canta. */
@Composable
private fun KaraokeLine(row: Row2.Line, smooth: SmoothPosition) {
    val t = smooth.displayMs + 120
    val words = row.words
    val text = buildAnnotatedString {
        if (words.isEmpty()) {
            withStyle(SpanStyle(color = Color.White)) { append(row.line.text.ifBlank { "♪" }) }
        } else {
            words.forEachIndexed { i, w ->
                val end = words.getOrNull(i + 1)?.timeMs ?: row.endMs
                val progress = ((t - w.timeMs).toFloat() / (end - w.timeMs).coerceAtLeast(120)).coerceIn(0f, 1f)
                val alpha = 0.38f + 0.62f * progress
                withStyle(SpanStyle(color = Color.White.copy(alpha = alpha))) { append(w.text) }
                if (i < words.lastIndex) append(' ')
            }
        }
    }
    Text(text, fontSize = 27.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold)
}

/** Tres puntitos que respiran y se llenan durante las partes sin voz. */
@Composable
private fun InstrumentalDots(row: Row2.Dots, smooth: SmoothPosition, isNow: Boolean) {
    val breathe = rememberInfiniteTransition(label = "puntitos")
    val pulse by breathe.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulso",
    )
    val progress = if (isNow) ((smooth.displayMs - row.startMs).toFloat() / (row.endMs - row.startMs).coerceAtLeast(1)).coerceIn(0f, 1f) else 0f
    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) { i ->
            val fill = ((progress * 3f) - i).coerceIn(0f, 1f)
            Box(
                Modifier
                    .size(12.dp)
                    .graphicsLayer {
                        val s = if (isNow) pulse else 1f
                        scaleX = s; scaleY = s
                    }
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (isNow) 0.3f + 0.7f * fill else 0.3f))
            )
        }
    }
}

@Composable
private fun EmptyLyrics(
    status: LyricsStatus,
    hasAiKey: Boolean,
    onSearchOnline: () -> Unit,
    onGenerateAi: () -> Unit,
    onLoadFile: () -> Unit,
    onPaste: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (status) {
            LyricsStatus.Searching, LyricsStatus.Generating -> {
                CircularProgressIndicator(color = Accent)
                Spacer(Modifier.height(16.dp))
                Text(
                    if (status == LyricsStatus.Searching) "Buscando la letra en internet…" else "La IA está escuchando la canción…",
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                )
                if (status == LyricsStatus.Generating) {
                    Spacer(Modifier.height(6.dp))
                    Text("Tarda entre 10 y 40 segundos.", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                }
            }
            else -> {
                Text("Esta canción aún no tiene letra", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (status is LyricsStatus.Failed) {
                    Spacer(Modifier.height(6.dp))
                    Text(status.message, color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(20.dp))
                LyricsAction(Icons.Rounded.Language, "Buscar en internet", "Letras sincronizadas de canciones conocidas", onSearchOnline)
                LyricsAction(
                    Icons.Rounded.AutoAwesome, "Generar con IA",
                    if (hasAiKey) "La IA escucha la canción y la sincroniza palabra por palabra" else "Primero pon tu clave gratis en Ajustes",
                    onGenerateAi,
                )
                LyricsAction(Icons.Rounded.FileOpen, "Cargar archivo .lrc", "Si ya tienes la letra sincronizada", onLoadFile)
                LyricsAction(Icons.Rounded.ContentPaste, "Pegar letra", "Escríbela o pégala tú", onPaste)
            }
        }
    }
}

@Composable
private fun LyricsAction(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        }
    }
}
