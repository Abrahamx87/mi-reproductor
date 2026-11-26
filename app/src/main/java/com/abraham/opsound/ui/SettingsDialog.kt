package com.abraham.opsound.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
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
    var dgKey by remember { mutableStateOf(store.deepgramKey) }
    var provider by remember { mutableStateOf(store.aiProvider) }
    var choosingAnimation by remember { mutableStateOf(false) }
    if (choosingAnimation) {
        CoverAnimationDialog(
            current = store.coverAnimation,
            onPick = { store.changeCoverAnimation(it) },
            onDismiss = { choosingAnimation = false },
        )
    }
    fun save() {
        store.changeAiKey(key)
        store.changeDeepgramKey(dgKey)
        store.changeAiProvider(provider)
    }
    var showKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { save(); onDismiss() },
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
                    "Activa o quita la animación de las portadas.",
                    store.coverAnimation != "NONE",
                ) { on -> store.changeCoverAnimation(if (on) "LIVING" else "NONE") }
                if (store.coverAnimation != "NONE") {
                    Row(
                        Modifier.fillMaxWidth().clickable { choosingAnimation = true }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Estilo de animación", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(CoverAnimation.from(store.coverAnimation).label + "  ›", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.08f))

                Text("Letras con IA ✨", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Para tus canciones propias: la IA escucha la canción y escribe la letra sincronizada palabra por palabra. Elige quién lo hace y pon su clave gratis.",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProviderChip("Deepgram (recomendado)", provider == "deepgram") { provider = "deepgram"; showKey = false }
                    ProviderChip("Groq", provider == "groq") { provider = "groq"; showKey = false }
                }
                val isDeepgram = provider == "deepgram"
                Text(
                    if (isDeepgram) "Consigue tu clave gratis (te regalan $200 de crédito) →" else "Consigue tu clave gratis de Groq →",
                    color = Accent,
                    fontSize = 14.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .clickable {
                            val url = if (isDeepgram) "https://console.deepgram.com/signup" else "https://console.groq.com/keys"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                )
                OutlinedTextField(
                    value = if (isDeepgram) dgKey else key,
                    onValueChange = { if (isDeepgram) dgKey = it.trim() else key = it.trim() },
                    singleLine = true,
                    label = { Text(if (isDeepgram) "Clave de Deepgram" else "Clave de Groq (gsk_…)") },
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
            TextButton(onClick = { save(); onDismiss() }) { Text("Guardar", color = Accent) }
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

@Composable
private fun ProviderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.Black else Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Accent else Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
