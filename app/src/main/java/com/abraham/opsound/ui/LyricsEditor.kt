package com.abraham.opsound.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abraham.opsound.LyricLine
import com.abraham.opsound.LyricWord
import com.abraham.opsound.Lyrics
import com.abraham.opsound.LyricsStore
import com.abraham.opsound.formatTime

/** Un renglón mientras se edita: recuerda sus tiempos originales para no perder la sincronización. */
private class EditLine(
    val id: Long,
    var time: Long?,
    var text: String,
    val origTime: Long?,
    val origWords: List<LyricWord>,
)

/** Recalcula los tiempos de cada palabra después de editar un renglón. */
private fun retime(line: EditLine, nextStart: Long?): List<LyricWord> {
    val start = line.time ?: return emptyList()
    val parts = line.text.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (parts.isEmpty() || line.origWords.isEmpty()) return emptyList()
    val shift = start - (line.origTime ?: start)
    // Mismo número de palabras: cada palabra conserva su tiempo (solo cambia lo escrito).
    if (parts.size == line.origWords.size) {
        return parts.mapIndexed { i, w -> LyricWord(line.origWords[i].timeMs + shift, w) }
    }
    // Cambió el número de palabras: se reparten entre el inicio y el final del renglón.
    val end = (line.origWords.last().timeMs + shift + 400).let { e -> if (nextStart != null) minOf(e, nextStart) else e }
        .coerceAtLeast(start + 300)
    val total = parts.sumOf { it.length + 1 }.toFloat()
    var acc = 0f
    return parts.map { w ->
        val t = start + ((end - start) * (acc / total)).toLong()
        acc += w.length + 1
        LyricWord(t, w)
    }
}

/**
 * Editor de letra: corrige palabras sin perder la sincronización, ajusta tiempos,
 * agrega o borra renglones y mueve toda la letra si está adelantada o atrasada.
 */
@Composable
fun LyricsEditor(
    lyrics: Lyrics,
    smooth: SmoothPosition,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSave: (Lyrics) -> Unit,
    onCancel: () -> Unit,
) {
    val synced = lyrics.synced
    var nextId by remember { mutableLongStateOf(lyrics.lines.size.toLong() + 1) }
    val lines = remember {
        mutableStateListOf<EditLine>().apply {
            lyrics.lines.filter { !synced || it.timeMs != null }.forEachIndexed { i, l ->
                add(EditLine(i.toLong(), l.timeMs, l.text, l.timeMs, l.words))
            }
        }
    }
    var revision by remember { mutableLongStateOf(0L) } // fuerza a redibujar al cambiar tiempos
    var offset by remember { mutableLongStateOf(0L) }
    val now by remember { derivedStateOf { smooth.displayMs } }

    fun save() {
        val sorted = if (synced) lines.sortedBy { it.time ?: 0L } else lines.toList()
        val result = sorted.mapIndexed { i, l ->
            val next = sorted.getOrNull(i + 1)?.time
            LyricLine(l.time, l.text.trim(), retime(l, next))
        }.filter { it.text.isNotEmpty() || !synced }
        onSave(Lyrics(result))
    }

    BackHandler(onBack = onCancel)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF070B0F))
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Encabezado
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, contentDescription = "Cancelar", tint = Color.White) }
                Text("Editar letra", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onPlayPause) {
                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Reproducir o pausar", tint = Color.White)
                }
                Text(formatTime(now), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                TextButton(onClick = ::save) { Text("Guardar", color = Accent, fontWeight = FontWeight.Bold) }
            }

            if (synced) {
                // Mover toda la letra (por ejemplo, si la copiaste de otra versión de la canción).
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Mover toda la letra", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, modifier = Modifier.weight(1f))
                    listOf(-500L to "−0.5 s", 500L to "+0.5 s").forEach { (d, label) ->
                        Text(
                            label,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f))
                                .clickable {
                                    offset += d
                                    lines.forEach { l -> l.time = l.time?.let { (it + d).coerceAtLeast(0) } }
                                    revision++
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                if (offset != 0L) {
                    Text(
                        "Movida ${if (offset > 0) "+" else ""}${"%.1f".format(offset / 1000f)} s",
                        color = Accent, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp),
                    )
                }
                Text(
                    "⏱ pone el renglón en el segundo que está sonando. − / + lo mueven 0.1 s.",
                    color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            val listState = rememberLazyListState()
            LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
                itemsIndexed(lines, key = { _, l -> l.id }) { index, line ->
                    val rev = revision // al cambiar tiempos, este renglón se vuelve a dibujar
                    var text by remember(line.id) { mutableStateOf(line.text) }
                    val isNow = synced && line.time != null && now >= line.time!! &&
                        (lines.getOrNull(index + 1)?.time?.let { now < it } ?: true)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isNow) Accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        if (synced) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    line.time?.let { LyricsStore.stamp(it) } ?: "--:--",
                                    color = if (isNow) Accent else Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                SmallChip("−") { line.time = ((line.time ?: 0L) - 100).coerceAtLeast(0); revision++ }
                                SmallChip("+") { line.time = (line.time ?: 0L) + 100; revision++ }
                                IconButton(onClick = { line.time = now; revision++ }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Rounded.Timer, contentDescription = "Poner el tiempo actual", tint = Accent, modifier = Modifier.size(20.dp))
                                }
                                IconButton(
                                    onClick = {
                                        val t = (line.time ?: now) + 2_000
                                        lines.add(index + 1, EditLine(nextId++, t, "", t, emptyList()))
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) { Icon(Icons.Rounded.Add, contentDescription = "Agregar renglón abajo", tint = Color.White, modifier = Modifier.size(20.dp)) }
                                IconButton(onClick = { lines.remove(line) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Borrar renglón", tint = FavoriteColor, modifier = Modifier.size(20.dp))
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                IconButton(onClick = { lines.add(index + 1, EditLine(nextId++, null, "", null, emptyList())) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.Add, contentDescription = "Agregar renglón abajo", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { lines.remove(line) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Borrar renglón", tint = FavoriteColor, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it; line.text = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Montserrat),
                            cursorBrush = SolidColor(Accent),
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
