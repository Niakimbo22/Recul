package fr.niakimbo.recul

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quelle app est devant, et quelles apps ont été « passées » récemment. */
object Gate {
    /** Après avoir quitté une app passée, on a ce délai pour y revenir sans tout refaire. */
    const val RELOCK_MS = 60_000L

    @Volatile var current: String? = null
        private set
    private val granted = HashSet<String>()
    private val leftAt = HashMap<String, Long>()
    /** Accès accordé par l'IA pour une durée précise (fin = elapsedRealtime). */
    private val until = HashMap<String, Long>()

    @Synchronized fun onForeground(pkg: String) {
        val prev = current
        if (prev != null && prev != pkg) leftAt[prev] = SystemClock.elapsedRealtime()
        current = pkg
    }

    @Synchronized fun grant(pkg: String) {
        granted.add(pkg)
        leftAt.remove(pkg)
        until.remove(pkg)
    }

    @Synchronized fun grantFor(pkg: String, ms: Long) {
        until[pkg] = SystemClock.elapsedRealtime() + ms
        granted.remove(pkg); leftAt.remove(pkg)
    }

    /** Vrai si un accès à durée limitée vient d'expirer (→ on rebloque). */
    @Synchronized fun timedExpired(pkg: String): Boolean {
        val end = until[pkg] ?: return false
        return SystemClock.elapsedRealtime() >= end
    }

    @Synchronized fun isOpen(pkg: String): Boolean {
        until[pkg]?.let { end ->
            if (SystemClock.elapsedRealtime() < end) return true
            until.remove(pkg); return false
        }
        if (pkg !in granted) return false
        val left = leftAt[pkg] ?: return true
        if (SystemClock.elapsedRealtime() - left < RELOCK_MS) { leftAt.remove(pkg); return true }
        granted.remove(pkg); leftAt.remove(pkg)
        return false
    }
}

class ReculAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ignored = setOf<String>()
    private var lastLaunchPkg: String? = null
    private var lastLaunchAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            // Limite atteinte pendant qu'on est dans l'app.
            Gate.current?.let { pkg -> if (Rules.get(pkg)?.hasLimit == true || Gate.timedExpired(pkg)) evaluate(pkg) }
            // Mise à jour auto, au plus toutes les 6 h.
            if (System.currentTimeMillis() - Rules.lastUpdateCheck > 6 * 3_600_000L) {
                Rules.lastUpdateCheck = System.currentTimeMillis()
                scope.launch { AutoUpdate.run(applicationContext) }
            }
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onServiceConnected() {
        running = true
        val imm = getSystemService(InputMethodManager::class.java)
        ignored = buildSet {
            add("com.android.systemui")
            imm.enabledInputMethodList.forEach { add(it.packageName) }
        }
        handler.post(tick)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in ignored) return
        Gate.onForeground(pkg)
        if (pkg != packageName) evaluate(pkg)
    }

    private fun evaluate(pkg: String) {
        val rule = Rules.get(pkg)
        val lock = Locks.get(pkg)
        if (rule == null && lock == null) return

        val reason = when {
            rule != null && rule.hasLimit && Usage.todayMs(this, pkg) >= Rules.allowedMs(rule) -> BlockActivity.REASON_LIMIT
            Gate.isOpen(pkg) -> return
            lock != null -> BlockActivity.REASON_CODE
            rule?.pauseOnOpen == true -> BlockActivity.REASON_PAUSE
            else -> return
        }

        // L'écran Recul est déjà ouvert pour cette app (l'app est juste passée devant) :
        // on le remet devant SANS le relancer, la pause continue là où elle en était.
        val showing = BlockActivity.showing
        if (showing == pkg) {
            startActivity(BlockActivity.intent(this, pkg, reason))
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (pkg == lastLaunchPkg && now - lastLaunchAt < 700) return
        lastLaunchPkg = pkg; lastLaunchAt = now
        if (reason == BlockActivity.REASON_PAUSE) Rules.countOpen(pkg)
        startActivity(BlockActivity.intent(this, pkg, reason))
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(tick)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile var running = false
            private set

        fun isEnabled(ctx: Context): Boolean {
            val enabled = android.provider.Settings.Secure.getString(
                ctx.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.startsWith(ctx.packageName + "/") }
        }

        fun goHome(ctx: Context) {
            ctx.startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
