package fr.niakimbo.recul

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Mise à jour automatique : regarde la dernière Release GitHub, la télécharge et l'installe
 * via PackageInstaller. Comme Recul s'est installé lui-même, Android 12+ peut accepter
 * la mise à jour sans confirmation ; sinon la confirmation s'affiche à la prochaine ouverture de Recul.
 */
object AutoUpdate {
    private const val TAG = "ReculUpdate"

    /** Confirmation demandée par Android, à montrer quand Recul est ouvert. */
    @Volatile var pendingConfirm: Intent? = null

    suspend fun run(ctx: Context) {
        val c = Updater.check()
        if (c !is Updater.Check.Available) return
        if (!Updater.canInstall(ctx)) return
        runCatching {
            val apk = Updater.download(ctx, c.release) {}
            installSession(ctx, apk)
        }.onFailure { Log.w(TAG, "MAJ auto ratée", it) }
    }

    fun installSession(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { s ->
            s.openWrite("recul.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; s.fsync(out) }
            val pi = PendingIntent.getBroadcast(
                ctx, id, Intent(ctx, InstallResultReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            s.commit(pi.intentSender)
        }
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (MainActivity.visible) ctx.startActivity(confirm) else AutoUpdate.pendingConfirm = confirm
            }
            PackageInstaller.STATUS_SUCCESS -> Log.i("ReculUpdate", "MAJ installée")
            else -> Log.w("ReculUpdate", "MAJ : " + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }
}
