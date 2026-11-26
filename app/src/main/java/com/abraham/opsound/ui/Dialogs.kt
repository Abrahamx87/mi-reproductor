package com.abraham.opsound.ui

import androidx.compose.foundation.clickable
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.AddToQueue
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abraham.opsound.Playlist

/** Una opción de menú grande y fácil de tocar. */
@Composable
private fun Option(text: String, color: Color = Color.White, onClick: () -> Unit) {
    Text(
        text,
        color = color,
        fontSize = 16.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

/** Opción con ícono, grande y fácil de tocar. */
@Composable
private fun IconOption(icon: ImageVector, text: String, color: Color = Color.White, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, color = color, fontSize = 16.sp)
    }
}

@Composable
fun SongOptionsDialog(
    title: String,
    subtitle: String,
    isFavorite: Boolean,
    inPlaylist: Boolean,
    hasCustomCover: Boolean,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    onChangeCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                IconOption(Icons.Rounded.QueuePlayNext, "Reproducir siguiente") { onPlayNext() }
                IconOption(Icons.Rounded.AddToQueue, "Agregar a la fila") { onAddToQueue() }
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.08f))
                IconOption(
                    if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (isFavorite) "Quitar de favoritos" else "Agregar a favoritos",
                    if (isFavorite) FavoriteColor else Color.White,
                ) { onToggleFavorite() }
                IconOption(Icons.AutoMirrored.Rounded.PlaylistAdd, "Agregar a playlist") { onAddToPlaylist() }
                if (inPlaylist) IconOption(Icons.Rounded.RemoveCircleOutline, "Quitar de esta playlist") { onRemoveFromPlaylist() }
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.08f))
                IconOption(Icons.Rounded.Edit, "Editar información") { onEdit() }
                IconOption(Icons.Rounded.Image, "Cambiar portada") { onChangeCover() }
                if (hasCustomCover) IconOption(Icons.Rounded.HideImage, "Quitar portada personalizada") { onRemoveCover() }
                IconOption(Icons.Rounded.Delete, "Eliminar del celular", FavoriteColor) { onDelete() }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = Accent) } },
    )
}

@Composable
fun PlaylistPickerDialog(
    playlists: List<Playlist>,
    onPick: (Playlist) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar a playlist") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Option("＋  Nueva playlist", Accent) { onNew() }
                playlists.forEach { p -> Option("${p.name}  (${p.songIds.size})") { onPick(p) } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Accent) } },
    )
}

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                singleLine = true,
                placeholder = { Text("Mis favoritas del gym") },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, cursorColor = Accent),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(confirmLabel, color = if (name.isNotBlank()) Accent else Color.Gray)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun PlaylistOptionsDialog(name: String, onRename: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column {
                Option("✎  Cambiar nombre") { onRename() }
                Option("🗑  Borrar playlist", FavoriteColor) { onDelete() }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = Accent) } },
    )
}

@Composable
fun ConfirmDeleteDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Borrar \"$name\"?") },
        text = { Text("Solo se borra la playlist. Tus canciones no se tocan.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Borrar", color = FavoriteColor) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun ConfirmSongDeleteDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Eliminar \"$title\"?") },
        text = { Text("Se mueve a la papelera de tu celular. Podrás recuperarla desde ahí durante unos días.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Eliminar", color = FavoriteColor) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun EditInfoDialog(
    title: String,
    artist: String,
    isEdited: Boolean,
    onSave: (title: String, artist: String) -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    var newTitle by remember { mutableStateOf(title) }
    var newArtist by remember { mutableStateOf(artist) }
    val colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, cursorColor = Accent, focusedLabelColor = Accent)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar información") },
        text = {
            Column {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it.take(120) },
                    label = { Text("Título") },
                    singleLine = true,
                    colors = colors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = newArtist,
                    onValueChange = { newArtist = it.take(120) },
                    label = { Text("Artista") },
                    singleLine = true,
                    colors = colors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Solo cambia cómo se ve en Opsound; tu archivo no se modifica.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.6f),
                )
                if (isEdited) {
                    TextButton(onClick = onRestore) { Text("Restaurar original", color = FavoriteColor) }
                }
            }
        },
        confirmButton = {
            val ok = newTitle.isNotBlank()
            TextButton(onClick = { if (ok) onSave(newTitle.trim(), newArtist.trim().ifEmpty { "Artista desconocido" }) }, enabled = ok) {
                Text("Guardar", color = if (ok) Accent else Color.Gray)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun PasteLyricsDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pegar letra") },
        text = {
            Column {
                Text(
                    "Pega la letra real (un renglón por línea). Después toca \"Sincronizar con IA ✨\" y la IA pondrá el tiempo de cada palabra.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Escribe o pega aquí…") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, cursorColor = Accent),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onSave(text) }, enabled = text.isNotBlank()) {
                Text("Guardar", color = if (text.isNotBlank()) Accent else Color.Gray)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Cambiar portada: elegir una de las predeterminadas o importar de la galería. */
@Composable
fun CoverChooserDialog(
    presets: List<String>,
    onPreset: (String) -> Unit,
    onGallery: () -> Unit,
    onVideo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar portada") },
        text = {
            Column {
                Text("Usar portada predeterminada", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.size(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    presets.forEach { asset ->
                        val thumb = remember(asset) {
                            runCatching {
                                context.assets.open(asset).use {
                                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 })
                                }?.asImageBitmap()
                            }.getOrNull()
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable { onPreset(asset) },
                        ) {
                            if (thumb != null) {
                                Image(thumb, contentDescription = "Portada predeterminada", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.08f))
                IconOption(Icons.Rounded.PhotoLibrary, "Importar de galería") { onGallery() }
                IconOption(Icons.Rounded.Movie, "Portada en video (animada)") { onVideo() }
                Text(
                    "Usa un video corto (5 a 15 s); se repite en bucle y sin sonido.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Accent) } },
    )
}
