package com.abraham.opsound.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Información de la versión más nueva publicada en GitHub. */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

object Updater {
    private const val LATEST_RELEASE =
        "https://api.github.com/repos/Abrahamx87/mi-reproductor/releases/latest"

    fun installedVersionCode(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    // Recordamos la última respuesta: si GitHub dice "no cambió" (304) no gasta nuestro límite de consultas.
    private var etag: String? = null
    private var lastJson: String? = null

    /** Regresa la versión nueva si es mayor que la instalada; si no, null. (Llamar fuera del hilo principal.) */
    fun checkForUpdate(context: Context): UpdateInfo? {
        val conn = (URL(LATEST_RELEASE).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            useCaches = false
            setRequestProperty("Accept", "application/vnd.github+json")
            etag?.let { setRequestProperty("If-None-Match", it) }
        }
        try {
            val body = when (conn.responseCode) {
                200 -> conn.inputStream.bufferedReader().use { it.readText() }.also {
                    etag = conn.getHeaderField("ETag")
                    lastJson = it
                }
                304 -> lastJson ?: return null
                else -> return null
            }
            val json = JSONObject(body)
            val tag = json.optString("tag_name") // ejemplo: v1.0.3
            val code = tag.substringAfterLast('.').toLongOrNull() ?: return null
            if (code <= installedVersionCode(context)) return null

            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk")) {
                    return UpdateInfo(
                        versionCode = code,
                        versionName = tag.removePrefix("v"),
                        notes = json.optString("body").trim(),
                        apkUrl = asset.getString("browser_download_url"),
                        sizeBytes = asset.optLong("size"),
                    )
                }
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    /** Descarga el APK a la memoria interna de la app, avisando el progreso (0..1). */
    fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File {
        val file = File(context.cacheDir, "opsound-update.apk")
        val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) error("Error ${conn.responseCode} al descargar")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        return file
    }

    /** Le pasa el APK al instalador de Android. Android muestra un aviso para confirmar. */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("opsound.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
    }
}
