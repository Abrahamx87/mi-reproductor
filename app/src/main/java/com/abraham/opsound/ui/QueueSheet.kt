package com.abraham.opsound.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abraham.opsound.Song

/** La fila: lo que está sonando y lo que sigue. Toca una para saltar a ella o quítala con la ✕. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<Song>,
    currentIndex: Int,
    onJumpTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF12181E),
    ) {
        Column(Modifier.fillMaxHeight(0.85f)) {
            Text(
                "Fila de reproducción",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, bottom = 4.dp),
            )
            val upcoming = (queue.size - currentIndex - 1).coerceAtLeast(0)
            Text(
                if (upcoming == 1) "1 canción a continuación" else "$upcoming canciones a continuación",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
            )
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0))
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(queue) { index, song ->
                    val isCurrent = index == currentIndex
                    val isPast = index < currentIndex
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (isCurrent) Accent.copy(alpha = 0.10f) else Color.Transparent)
                            .clickable { onJumpTo(index) }
                            .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isCurrent) {
                            Icon(Icons.Rounded.GraphicEq, contentDescription = "Sonando", tint = Accent, modifier = Modifier.size(22.dp))
                        } else {
                            Text(
                                "${index + 1}",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 13.sp,
                                modifier = Modifier.width(22.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                song.title,
                                color = when {
                                    isCurrent -> Accent
                                    isPast -> Color.White.copy(alpha = 0.45f)
                                    else -> Color.White
                                },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                song.artist,
                                color = Color.White.copy(alpha = if (isPast) 0.3f else 0.6f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!isCurrent) {
                            IconButton(onClick = { onRemove(index) }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Quitar de la fila", tint = Color.White.copy(alpha = 0.5f))
                            }
                        } else {
                            Spacer(Modifier.width(48.dp))
                        }
                    }
                }
            }
        }
    }
}
