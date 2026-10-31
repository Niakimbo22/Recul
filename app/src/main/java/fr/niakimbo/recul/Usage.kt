package fr.niakimbo.recul

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

/** Temps passé par app, calculé à partir des événements système (premier plan / arrière-plan). */
object Usage {

    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun startOfDay(daysAgo: Long = 0): Long =
        LocalDate.now().minusDays(daysAgo).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Temps de premier plan aujourd'hui pour une app (ms). */
    fun todayMs(ctx: Context, pkg: String): Long =
        range(ctx, startOfDay(), System.currentTimeMillis(), setOf(pkg))[pkg] ?: 0L

    fun todayAll(ctx: Context, pkgs: Set<String>): Map<String, Long> =
        range(ctx, startOfDay(), System.currentTimeMillis(), pkgs)

    /** 7 derniers jours (le plus ancien en premier), une map app → ms par jour. */
    fun week(ctx: Context, pkgs: Set<String>): List<Pair<LocalDate, Map<String, Long>>> =
        (6 downTo 0).map { d ->
            val start = startOfDay(d.toLong())
            val end = if (d == 0) System.currentTimeMillis() else startOfDay(d - 1L)
            LocalDate.now().minusDays(d.toLong()) to range(ctx, start, end, pkgs)
        }

    private fun range(ctx: Context, start: Long, end: Long, pkgs: Set<String>): Map<String, Long> {
        if (pkgs.isEmpty() || !hasAccess(ctx)) return emptyMap()
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val events = usm.queryEvents(start, end) ?: return emptyMap()
        val total = HashMap<String, Long>()
        val openSince = HashMap<String, Long>()             // app → début de la séance
        val openActivities = HashMap<String, MutableSet<String>>() // app → activités visibles
        val e = UsageEvents.Event()

        fun close(pkg: String, t: Long) {
            val s = openSince.remove(pkg) ?: return
            openActivities.remove(pkg)
            total[pkg] = (total[pkg] ?: 0L) + (t - s).coerceAtLeast(0)
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> if (pkg in pkgs) {
                    val set = openActivities.getOrPut(pkg) { mutableSetOf() }
                    if (set.isEmpty()) openSince[pkg] = e.timeStamp
                    set.add(e.className ?: "")
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> if (pkg in pkgs) {
                    val set = openActivities[pkg] ?: continue
                    set.remove(e.className ?: "")
                    if (set.isEmpty()) close(pkg, e.timeStamp)
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN ->
                    openSince.keys.toList().forEach { close(it, e.timeStamp) }
            }
        }
        // Encore ouverte maintenant : on compte jusqu'à la fin de la plage.
        openSince.keys.toList().forEach { close(it, end) }
        return total
    }
}

fun formatDuration(ms: Long): String {
    val min = ms / 60_000
    return when {
        min < 1 -> "0 min"
        min < 60 -> "$min min"
        min % 60 == 0L -> "${min / 60} h"
        else -> "${min / 60} h ${"%02d".format(min % 60)}"
    }
}
