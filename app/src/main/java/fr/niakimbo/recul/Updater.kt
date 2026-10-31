package fr.niakimbo.recul

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Mises à jour via les Releases GitHub (Niakimbo22/Recul). */
object Updater {
    private const val API = "https://api.github.com/repos/Niakimbo22/Recul/releases/latest"

    data class Release(val versionCode: Int, val name: String, val notes: String, val apkUrl: String)

    sealed interface Check {
        data object UpToDate : Check
        data class Available(val release: Release) : Check
        data class Error(val message: String) : Check
    }

    suspend fun check(): Check = withContext(Dispatchers.IO) {
        try {
            val c = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            if (c.responseCode == 404) return@withContext Check.Error("Aucune version publiée pour l'instant")
            if (c.responseCode != 200) return@withContext Check.Error("GitHub répond ${c.responseCode}")
            val json = JSONObject(c.inputStream.bufferedReader().readText())
            val tag = json.getString("tag_name")                      // ex. v0.1.12
            val code = tag.substringAfterLast('.').toIntOrNull() ?: 0
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") }
                ?: return@withContext Check.Error("Pas d'APK dans la dernière version")
            val rel = Release(code, tag, json.optString("body"), apk.getString("browser_download_url"))
            if (code > BuildConfig.VERSION_CODE) Check.Available(rel) else Check.UpToDate
        } catch (e: Exception) {
            Check.Error("Pas de connexion")
        }
    }

    /** Télécharge l'APK, [onProgress] entre 0 et 1. */
    suspend fun download(ctx: Context, rel: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "recul-${rel.versionCode}.apk")
        var url = URL(rel.apkUrl)
        var c: HttpURLConnection
        // Suit les redirections GitHub → serveur de fichiers.
        while (true) {
            c = (url.openConnection() as HttpURLConnection).apply { instanceFollowRedirects = false; connectTimeout = 15_000; readTimeout = 30_000 }
            if (c.responseCode in 300..399) { url = URL(c.getHeaderField("Location")); continue }
            break
        }
        val total = c.contentLengthLong.takeIf { it > 0 } ?: -1L
        c.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var read = 0L
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    output.write(buf, 0, n); read += n
                    if (total > 0) onProgress(read.toFloat() / total)
                }
            }
        }
        out
    }

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    fun askInstallPermission(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
