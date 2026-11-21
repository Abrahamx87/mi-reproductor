package com.abraham.opsound.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abraham.opsound.update.UpdateInfo
import com.abraham.opsound.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface UpdateState {
    data object Hidden : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class NeedPermission(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data object Installing : UpdateState
    data class Failed(val info: UpdateInfo, val message: String) : UpdateState
}

/** Revisa GitHub al abrir la app y, si hay versión nueva, ofrece actualizar desde adentro. */
@Composable
fun UpdateDialog() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Hidden) }

    LaunchedEffect(Unit) {
        val info = withContext(Dispatchers.IO) { runCatching { Updater.checkForUpdate(context) }.getOrNull() }
        if (info != null) state = UpdateState.Available(info)
    }

    fun startUpdate(info: UpdateInfo) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            state = UpdateState.NeedPermission(info)
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            )
            return
        }
        state = UpdateState.Downloading(info, 0f)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val apk = Updater.download(context, info) { p ->
                        state = UpdateState.Downloading(info, p)
                    }
                    Updater.install(context, apk)
                }
            }
            state = result.fold(
                onSuccess = { UpdateState.Installing },
                onFailure = { UpdateState.Failed(info, it.message ?: "Revisa tu conexión a internet.") },
            )
        }
    }

    when (val s = state) {
        UpdateState.Hidden -> Unit

        is UpdateState.Available -> AlertDialog(
            onDismissRequest = { state = UpdateState.Hidden },
            title = { Text("¡Nueva versión de Opsound! 🎉", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    Text("Versión ${s.info.versionName} (${s.info.sizeBytes / 1_000_000} MB)")
                    if (s.info.notes.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text("Novedades:", fontWeight = FontWeight.SemiBold)
                        Text(s.info.notes, color = Color.White.copy(alpha = 0.8f))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { startUpdate(s.info) }) { Text("Actualizar", color = Accent) } },
            dismissButton = { TextButton(onClick = { state = UpdateState.Hidden }) { Text("Después") } },
        )

        is UpdateState.NeedPermission -> AlertDialog(
            onDismissRequest = { state = UpdateState.Hidden },
            title = { Text("Un permiso más") },
            text = { Text("Activa \"Permitir de esta fuente\" para Opsound y regresa aquí. Solo se hace una vez.") },
            confirmButton = { TextButton(onClick = { startUpdate(s.info) }) { Text("Ya lo activé", color = Accent) } },
            dismissButton = { TextButton(onClick = { state = UpdateState.Hidden }) { Text("Después") } },
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = { },
            title = { Text("Descargando actualización…") },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { s.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Accent,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("${(s.progress * 100).toInt()} %")
                }
            },
            confirmButton = { },
        )

        UpdateState.Installing -> AlertDialog(
            onDismissRequest = { state = UpdateState.Hidden },
            title = { Text("Casi listo") },
            text = { Text("Toca \"Actualizar\" en la ventana de Android. Opsound se cerrará y abrirá ya actualizada.") },
            confirmButton = { TextButton(onClick = { state = UpdateState.Hidden }) { Text("Entendido", color = Accent) } },
        )

        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = { state = UpdateState.Hidden },
            title = { Text("No se pudo actualizar") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = { startUpdate(s.info) }) { Text("Reintentar", color = Accent) } },
            dismissButton = { TextButton(onClick = { state = UpdateState.Hidden }) { Text("Cerrar") } },
        )
    }
}
