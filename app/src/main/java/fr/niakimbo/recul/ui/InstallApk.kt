package fr.niakimbo.recul.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.niakimbo.recul.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Vrai si cette version contient le bloqueur (service d'accessibilité). */
fun hasBlockerService(ctx: Context): Boolean = runCatching {
    ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_SERVICES).services?.isNotEmpty() == true
}.getOrDefault(false)

/** Choisir un .apk dans les fichiers : Recul le copie puis l'installe lui-même. */
@Composable
fun InstallApkButton(label: String, primary: Boolean = true) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = "Préparation…"
            runCatching {
                withContext(Dispatchers.IO) {
                    val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                    val out = File(dir, "recul-local.apk")
                    ctx.contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                    out
                }
            }.onSuccess { Updater.install(ctx, it); status = null }
             .onFailure { status = "Fichier illisible" }
        }
    }
    Column {
        PillButton(status ?: label, Modifier.fillMaxWidth(), primary = primary) {
            if (!Updater.canInstall(ctx)) { Updater.askInstallPermission(ctx); return@PillButton }
            pick.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
        }
    }
}

@Composable
fun InstallFullCard() {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(C.AmberDim).padding(20.dp)) {
        Column {
            Text("Dernière étape", style = T.H2)
            Spacer(Modifier.height(4.dp))
            Text("Choisis Recul-0.1.3.apk dans tes téléchargements. Recul s'installe en entier.",
                style = T.Small.copy(color = C.Text.copy(alpha = 0.75f)))
            Spacer(Modifier.height(14.dp))
            InstallApkButton("Choisir le fichier")
        }
    }
}
