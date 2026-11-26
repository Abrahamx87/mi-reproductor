package com.abraham.opsound.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Todo lo que se puede hacer desde el menú del reproductor. */
class PlayerMenuActions(
    val onQueue: () -> Unit,
    val onAddToPlaylist: () -> Unit,
    val onShareSong: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onShowLyrics: () -> Unit,
    val onEditInfo: () -> Unit,
    val onChangeCover: () -> Unit,
    val onChangeAnimation: () -> Unit,
    val onSearchLyrics: () -> Unit,
    val onGenerateLyrics: () -> Unit,
    val onEditLyrics: () -> Unit,
    val onShareLyrics: () -> Unit,
    val onCopyLyrics: () -> Unit,
    val onImportLyrics: () -> Unit,
    val onExportLyrics: () -> Unit,
    val onPasteLyrics: () -> Unit,
    val onRemoveLyrics: () -> Unit,
)

private val SheetColor = Color(0xFF14191F)
private val CardColor = Color(0xFF1C232B)

/**
 * Menú del reproductor (como Apple Music / Spotify): sube desde abajo, muestra la canción,
 * botones rápidos y las opciones agrupadas en tarjetas. Las de la letra tienen su propia página.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMenuSheet(
    cover: ImageBitmap?,
    title: String,
    artist: String,
    isFavorite: Boolean,
    hasLyrics: Boolean,
    lyricsSynced: Boolean,
    hasSavedLyrics: Boolean,
    animationLabel: String,
    actions: PlayerMenuActions,
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf(0) } // 0 = principal, 1 = letra
    // Cada opción cierra el menú y luego hace su acción.
    fun go(action: () -> Unit): () -> Unit = { onDismiss(); action() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetColor,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 40.dp, height = 5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
        },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            // ── Encabezado: la canción
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(CardColor), contentAlignment = Alignment.Center) {
                    if (cover != null) Image(cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp))
                    else Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(artist, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = actions.onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isFavorite) "Quitar de favoritos" else "Agregar a favoritos",
                        tint = if (isFavorite) FavoriteColor else Color.White,
                    )
                }
            }

            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(260)) { it * dir } + fadeIn(tween(200)))
                        .togetherWith(slideOutHorizontally(tween(260)) { -it * dir } + fadeOut(tween(150)))
                },
                label = "pagina-menu",
            ) { current ->
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    if (current == 0) {
                        // ── Botones rápidos
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            QuickAction(Icons.Rounded.QueueMusic, "Fila", go(actions.onQueue), Modifier.weight(1f))
                            QuickAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Playlist", go(actions.onAddToPlaylist), Modifier.weight(1f))
                            QuickAction(Icons.Rounded.Subtitles, "Letra", go(actions.onShowLyrics), Modifier.weight(1f))
                            QuickAction(Icons.Rounded.Share, "Compartir", go(actions.onShareSong), Modifier.weight(1f))
                        }

                        SectionTitle("Canción")
                        Card {
                            MenuRow(Icons.Rounded.Edit, "Editar información", onClick = go(actions.onEditInfo))
                            Divider()
                            MenuRow(Icons.Rounded.Image, "Cambiar portada", onClick = go(actions.onChangeCover))
                            Divider()
                            MenuRow(Icons.Rounded.Animation, "Animación de portada", value = animationLabel, onClick = go(actions.onChangeAnimation))
                        }

                        SectionTitle("Letra")
                        Card {
                            if (hasLyrics) {
                                MenuRow(Icons.Rounded.FormatQuote, "Compartir letra", subtitle = "Crea una tarjeta para Instagram", onClick = go(actions.onShareLyrics))
                                Divider()
                                MenuRow(Icons.Rounded.EditNote, "Editar letra", subtitle = "Corrige palabras y tiempos", onClick = go(actions.onEditLyrics))
                                Divider()
                            }
                            MenuRow(
                                Icons.Rounded.AutoAwesome,
                                if (hasLyrics && !lyricsSynced) "Sincronizar con IA" else "Generar con IA",
                                subtitle = "La IA escucha la canción",
                                accent = true,
                                onClick = go(actions.onGenerateLyrics),
                            )
                            Divider()
                            MenuRow(Icons.Rounded.Language, "Buscar en internet", onClick = go(actions.onSearchLyrics))
                            Divider()
                            MenuRow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Más opciones de letra", trailingArrow = true, onClick = { page = 1 })
                        }
                    } else {
                        // ── Página de letra
                        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { page = 0 }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                            }
                            Text("Opciones de letra", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                        Card {
                            MenuRow(Icons.Rounded.ContentCopy, "Copiar de otra canción", subtitle = "Usa la letra y tiempos de otra versión", onClick = go(actions.onCopyLyrics))
                            Divider()
                            MenuRow(Icons.Rounded.FileUpload, "Importar archivo .lrc", onClick = go(actions.onImportLyrics))
                            if (hasLyrics) {
                                Divider()
                                MenuRow(Icons.Rounded.FileDownload, "Exportar archivo .lrc", onClick = go(actions.onExportLyrics))
                            }
                            Divider()
                            MenuRow(Icons.Rounded.ContentPaste, "Pegar letra", onClick = go(actions.onPasteLyrics))
                        }
                        if (hasSavedLyrics) {
                            Spacer(Modifier.height(12.dp))
                            Card {
                                MenuRow(Icons.Rounded.DeleteOutline, "Quitar letra guardada", danger = true, onClick = go(actions.onRemoveLyrics))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardColor)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = Color.White.copy(alpha = 0.45f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 28.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardColor)
    ) { content() }
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(start = 60.dp), thickness = 0.5.dp, color = Color.White.copy(alpha = 0.08f))
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    accent: Boolean = false,
    danger: Boolean = false,
    trailingArrow: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = when {
        danger -> FavoriteColor
        accent -> Accent
        else -> Color.White
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (trailingArrow) Icons.Rounded.Subtitles else icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (danger) FavoriteColor else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
        if (value != null) {
            Text(value, color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp)
            Spacer(Modifier.width(4.dp))
        }
        if (value != null || trailingArrow) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.4f))
        }
    }
}
