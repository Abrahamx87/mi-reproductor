package com.abraham.opsound.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.abraham.opsound.CardStyle
import com.abraham.opsound.LyricsCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Compartir letra (como Apple Music): elige hasta 150 caracteres, escoge el estilo
 * y comparte la tarjeta a Instagram, WhatsApp, etc. o guárdala en tu galería.
 */
@Composable
fun ShareLyricsScreen(
    lines: List<String>,
    startIndex: Int,
    cover: ImageBitmap?,
    title: String,
    artist: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(if (startIndex in lines.indices) setOf(startIndex) else emptySet()) }
    var style by remember { mutableStateOf(CardStyle.COVER) }
    val chosen = selected.sorted().map { lines[it] }
    val chars = chosen.sumOf { it.length }

    // Vista previa: se vuelve a dibujar un instante después de cada cambio.
    val preview by produceState<ImageBitmap?>(null, chosen, style, cover) {
        delay(120)
        value = withContext(Dispatchers.Default) {
            runCatching {
                LyricsCard.render(context, cover?.asAndroidBitmap(), chosen.ifEmpty { listOf("Elige una línea…") }, title, artist, style).asImageBitmap()
            }.getOrNull()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF070B0F))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Encabezado
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = Color.White) }
                Text("Compartir letra", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    "$chars/${LyricsCard.MAX_CHARS}",
                    color = if (chars > LyricsCard.MAX_CHARS) FavoriteColor else Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }

            // Vista previa de la tarjeta
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(0.9f)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                val img = preview
                if (img != null) {
                    Image(
                        img,
                        contentDescription = "Vista previa",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxHeight().aspectRatio(1080f / 1920f).clip(RoundedCornerShape(14.dp)),
                    )
                } else {
                    CircularProgressIndicator(color = Accent)
                }
            }

            // Estilos de color
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CardStyle.entries.forEach { s ->
                    val sel = s == style
                    Text(
                        s.label,
                        color = if (sel) Color.Black else Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (sel) Accent else Color.White.copy(alpha = 0.1f))
                            .clickable { style = s }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            // Líneas para elegir
            Text(
                "Toca las líneas que quieres compartir",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp, top = 6.dp),
            )
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = (startIndex - 1).coerceAtLeast(0))
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(0.6f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            ) {
                itemsIndexed(lines) { i, line ->
                    val isSel = i in selected
                    Text(
                        line,
                        color = if (isSel) Color.White else Color.White.copy(alpha = 0.45f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) Accent.copy(alpha = 0.22f) else Color.Transparent)
                            .clickable {
                                selected = when {
                                    isSel -> selected - i
                                    chars + line.length > LyricsCard.MAX_CHARS -> {
                                        Toast.makeText(context, "Máximo ${LyricsCard.MAX_CHARS} caracteres", Toast.LENGTH_SHORT).show()
                                        selected
                                    }
                                    else -> selected + i
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            // Botones para compartir
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                val ready = preview != null && chosen.isNotEmpty()
                fun withImage(action: (android.graphics.Bitmap) -> Unit) {
                    if (!ready) {
                        Toast.makeText(context, "Elige al menos una línea", Toast.LENGTH_SHORT).show()
                        return
                    }
                    action(preview!!.asAndroidBitmap())
                }
                ShareButton(Icons.Rounded.PhotoCamera, "Instagram") {
                    withImage { bmp ->
                        if (!LyricsCard.shareToInstagram(context, bmp)) {
                            Toast.makeText(context, "No encontré Instagram en tu celular", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                ShareButton(Icons.Rounded.IosShare, "Compartir") {
                    withImage { bmp -> LyricsCard.share(context, bmp, "\"${chosen.joinToString(" / ")}\" — $title, $artist") }
                }
                ShareButton(Icons.Rounded.Download, "Guardar") {
                    withImage { bmp ->
                        val ok = LyricsCard.saveToGallery(context, bmp)
                        Toast.makeText(context, if (ok) "Guardada en Imágenes › Opsound" else "No se pudo guardar", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(54.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}

@Suppress("unused")
private val unusedWidth = Modifier.width(0.dp)
