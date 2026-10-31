package fr.niakimbo.recul

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/** État de la journée en mémoire : quelle app est devant, quelles séances sont débloquées. */
object Gate {
    /** Après avoir quitté une app débloquée, on a ce délai pour y revenir sans repasser l'épreuve. */
    const val RELOCK_MS = 60_000L

    @Volatile var current: String? = null
        private set
    private val granted = HashMap<String, Long>()
    private val leftAt = HashMap<String, Long>()

    @Synchronized fun onForeground(pkg: String) {
        val prev = current
        if (prev != null && prev != pkg) leftAt[prev] = SystemClock.elapsedRealtime()
        current = pkg
    }

    @Synchronized fun grant(pkg: String) {
        granted[pkg] = SystemClock.elapsedRealtime()
        leftAt.remove(pkg)
    }

    @Synchronized fun isOpen(pkg: String): Boolean {
        if (!granted.containsKey(pkg)) return false
        val left = leftAt[pkg] ?: return true
        val ok = SystemClock.elapsedRealtime() - left < RELOCK_MS
        if (!ok) granted.remove(pkg)
        return ok
    }

    @Synchronized fun lockAll() { granted.clear(); leftAt.clear() }
}

class ReculAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var ignored = setOf<String>()
    private var lastLaunchPkg: String? = null
    private var lastLaunchAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            Gate.current?.let { pkg -> if (Rules.get(pkg)?.hasLimit == true) evaluate(pkg) }
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onServiceConnected() {
        running = true
        val imm = getSystemService(InputMethodManager::class.java)
        ignored = buildSet {
            add(packageName)
            add("com.android.systemui")
            imm.enabledInputMethodList.forEach { add(it.packageName) }
        }
        handler.post(tick)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) { Gate.onForeground(pkg); return }
        if (pkg in ignored) return
        Gate.onForeground(pkg)
        evaluate(pkg)
    }

    private fun evaluate(pkg: String) {
        val rule = Rules.get(pkg) ?: return
        val reason = when {
            rule.hasLimit && Usage.todayMs(this, pkg) >= Rules.allowedMs(rule) -> BlockActivity.REASON_LIMIT
            rule.lockOnOpen && !Gate.isOpen(pkg) -> BlockActivity.REASON_LOCK
            else -> return
        }
        val now = SystemClock.elapsedRealtime()
        if (pkg == lastLaunchPkg && now - lastLaunchAt < 1500) return
        lastLaunchPkg = pkg; lastLaunchAt = now
        startActivity(BlockActivity.intent(this, pkg, reason))
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(tick)
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
