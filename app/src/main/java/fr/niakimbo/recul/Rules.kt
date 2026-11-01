package fr.niakimbo.recul

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate

class ReculApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Rules.init(this)
        Locks.init(this)
    }
}

/** Ce qu'il faut faire pour passer une pause (ou gagner +5 min). */
enum class Method(val label: String) { BREATH("Respiration"), WAIT("Attente") }

const val WAIT_MIN = 2
const val WAIT_MAX = 60

/** Une app surveillée : limite par jour et / ou pause à l'ouverture. */
data class AppRule(
    val pkg: String,
    val label: String,
    /** Minutes par jour, 0 = pas de limite. */
    val limitMin: Int = 0,
    /** Limite atteinte → bloqué jusqu'à demain, aucun moyen de passer. */
    val strict: Boolean = false,
    /** Nombre de « +5 min » autorisés par jour (si pas strict). */
    val graceCount: Int = 2,
    /** Une pause à chaque ouverture. */
    val pauseOnOpen: Boolean = false,
    val method: Method = Method.WAIT,
    val waitSec: Int = 5,
    val breaths: Int = 2,
) {
    val hasLimit get() = limitMin > 0

    fun toJson() = JSONObject().apply {
        put("pkg", pkg); put("label", label); put("limitMin", limitMin); put("strict", strict)
        put("graceCount", graceCount); put("pauseOnOpen", pauseOnOpen); put("method", method.name)
        put("waitSec", waitSec); put("breaths", breaths)
    }

    companion object {
        fun fromJson(o: JSONObject) = AppRule(
            pkg = o.getString("pkg"),
            label = o.optString("label", o.getString("pkg")),
            limitMin = o.optInt("limitMin", 0),
            strict = o.optBoolean("strict", false),
            graceCount = o.optInt("graceCount", 2),
            // « lockOnOpen » = ancien nom (v0.1.1 → 0.1.3).
            pauseOnOpen = o.optBoolean("pauseOnOpen", o.optBoolean("lockOnOpen", false)),
            method = runCatching { Method.valueOf(o.optString("method")) }.getOrDefault(Method.WAIT),
            waitSec = o.optInt("waitSec", 5).coerceIn(WAIT_MIN, WAIT_MAX),
            breaths = o.optInt("breaths", 2).coerceIn(1, 5),
        )
    }
}

/** Règles de temps / pause, stockées en local (JSON). */
object Rules {
    private lateinit var prefs: SharedPreferences
    private val _rules = MutableStateFlow<List<AppRule>>(emptyList())
    val rules: StateFlow<List<AppRule>> = _rules

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("recul", Context.MODE_PRIVATE)
        _rules.value = load()
    }

    private fun load(): List<AppRule> {
        val raw = prefs.getString("rules", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { AppRule.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    /** Anciennes règles avec un code (avant la rubrique Codes) : à déplacer vers [Locks]. */
    fun legacyCodes(): List<Triple<String, String, String>> {
        val raw = prefs.getString("rules", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it) }
                .filter { it.has("codeHash") }
                .map { Triple(it.getString("pkg"), it.optString("label"), it.getString("codeHash")) }
        }.getOrDefault(emptyList())
    }

    private fun save(list: List<AppRule>) {
        _rules.value = list
        prefs.edit().putString("rules", JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    fun get(pkg: String): AppRule? = _rules.value.firstOrNull { it.pkg == pkg }

    fun upsert(rule: AppRule) {
        val list = _rules.value.toMutableList()
        val i = list.indexOfFirst { it.pkg == rule.pkg }
        if (i >= 0) list[i] = rule else list.add(rule)
        save(list)
    }

    fun remove(pkg: String) = save(_rules.value.filterNot { it.pkg == pkg })

    // ---- Compteurs du jour (+5 min, ouvertures), remis à zéro chaque jour ----
    private fun today() = LocalDate.now().toString()

    private fun daily(key: String): Int =
        if (prefs.getString("day", "") == today()) prefs.getInt(key, 0) else 0

    private fun bump(key: String) {
        val e = prefs.edit()
        if (prefs.getString("day", "") != today()) {
            prefs.all.keys.filter { it.startsWith("d_") }.forEach { e.remove(it) }
            e.putString("day", today())
        }
        e.putInt(key, daily(key) + 1).apply()
    }

    fun graceUsed(pkg: String) = daily("d_grace_$pkg")
    fun useGrace(pkg: String) = bump("d_grace_$pkg")

    /** Nombre de fois où la pause s'est affichée aujourd'hui pour cette app. */
    fun opensToday(pkg: String) = daily("d_open_$pkg")
    fun countOpen(pkg: String) = bump("d_open_$pkg")

    /** Temps autorisé aujourd'hui (limite + bonus), en ms. */
    fun allowedMs(rule: AppRule): Long =
        (rule.limitMin + 5L * graceUsed(rule.pkg)) * 60_000L

    // ---- Code de Recul (protège Recul lui-même) ----
    var masterHash: String?
        get() = prefs.getString("master", null)
        set(v) { prefs.edit().apply { if (v == null) remove("master") else putString("master", v) }.apply() }

    // ---- Mise à jour auto ----
    var lastUpdateCheck: Long
        get() = prefs.getLong("upd_check", 0L)
        set(v) { prefs.edit().putLong("upd_check", v).apply() }
}

/** Une app protégée par un code : il faut le code à chaque ouverture. */
data class AppLock(val pkg: String, val label: String, val hash: String) {
    fun toJson() = JSONObject().put("pkg", pkg).put("label", label).put("hash", hash)
}

/** Rubrique « Codes » : séparée des limites. */
object Locks {
    private lateinit var prefs: SharedPreferences
    private val _locks = MutableStateFlow<List<AppLock>>(emptyList())
    val locks: StateFlow<List<AppLock>> = _locks

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("recul_codes", Context.MODE_PRIVATE)
        _locks.value = runCatching {
            val arr = JSONArray(prefs.getString("locks", "[]"))
            (0 until arr.length()).map { arr.getJSONObject(it) }
                .map { AppLock(it.getString("pkg"), it.optString("label"), it.getString("hash")) }
        }.getOrDefault(emptyList())
        // Reprise des codes créés dans l'ancienne version.
        if (!prefs.getBoolean("migrated", false)) {
            Rules.legacyCodes().forEach { (pkg, label, hash) -> if (get(pkg) == null) set(AppLock(pkg, label, hash)) }
            prefs.edit().putBoolean("migrated", true).apply()
        }
    }

    fun get(pkg: String) = _locks.value.firstOrNull { it.pkg == pkg }

    fun set(lock: AppLock) = save(_locks.value.filterNot { it.pkg == lock.pkg } + lock)

    fun remove(pkg: String) = save(_locks.value.filterNot { it.pkg == pkg })

    private fun save(list: List<AppLock>) {
        _locks.value = list
        prefs.edit().putString("locks", JSONArray(list.map { it.toJson() }).toString()).apply()
    }
}

/** Codes stockés hachés (sel + SHA-256), jamais en clair. */
object Codes {
    fun hash(code: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return salt.hex() + ":" + digest(salt, code)
    }

    fun check(code: String, stored: String?): Boolean {
        if (stored == null) return false
        val parts = stored.split(":")
        if (parts.size != 2) return false
        val salt = parts[0].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return MessageDigest.isEqual(digest(salt, code).toByteArray(), parts[1].toByteArray())
    }

    private fun digest(salt: ByteArray, code: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt); md.update(code.toByteArray())
        return md.digest().hex()
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}
