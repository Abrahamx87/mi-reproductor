package com.abraham.opsound.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abraham.opsound.OpsoundStore
import com.abraham.opsound.update.Updater

@Composable
fun SettingsDialog(store: OpsoundStore, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var key by remember { mutableStateOf(store.aiKey) }
    var showKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { store.changeAiKey(key); onDismiss() },
        title = { Text("Ajustes") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingSwitch(
                    "Buscar letras automáticamente",
                    "Al poner una canción sin letra, Opsound la busca sola en internet (LRCLIB).",
                    store.autoLyrics,
                ) { store.changeAutoLyrics(it) }
                SettingSwitch(
                    "Portadas animadas",
                    "Zoom lento, destello y partículas en la portada, como en Apple Music.",
                    store.animatedCovers,
                ) { store.changeAnimatedCovers(it) }

                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.08f))

                Text("Letras con IA ✨", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Para tus canciones propias: la IA (Whisper) escucha la canción y escribe la letra sincronizada palabra por palabra. Necesita una clave gratis de Groq.",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                )
                Text(
                    "Consigue tu clave gratis aquí →",
                    color = Accent,
                    fontSize = 14.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys")))
                        },
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim() },
                    singleLine = true,
                    label = { Text("Clave de Groq (gsk_…)") },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = "Mostrar clave")
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, cursorColor = Accent, focusedLabelColor = Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Tu clave se guarda solo en este celular, dentro de Opsound.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )

                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.08f))
                val version = runCatching { Updater.installedVersionCode(context) }.getOrDefault(0L)
                Text("Opsound v1.0.$version", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { store.changeAiKey(key); onDismiss() }) { Text("Guardar", color = Accent) }
        },
    )
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White),
        )
    }
}
