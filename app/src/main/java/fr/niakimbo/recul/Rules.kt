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
    }
}

/** Comment on passe le verrou (ou on gagne +5 min). */
enum class Method(val label: String) { BREATH("Respiration"), WAIT("Attente"), CODE("Code") }

data class AppRule(
    val pkg: String,
    val label: String,
    /** Minutes par jour, 0 = pas de limite. */
    val limitMin: Int = 0,
    /** Limite atteinte → bloqué jusqu'à demain, aucun moyen de passer. */
    val strict: Boolean = false,
    /** Nombre de « +5 min » autorisés par jour (si pas strict). */
    val graceCount: Int = 2,
    /** Demande de passer l'épreuve à chaque ouverture. */
    val lockOnOpen: Boolean = false,
    val method: Method = Method.BREATH,
    val waitSec: Int = 30,
    val codeHash: String? = null,
) {
    val hasLimit get() = limitMin > 0
    fun toJson() = JSONObject().apply {
        put("pkg", pkg); put("label", label); put("limitMin", limitMin); put("strict", strict)
        put("graceCount", graceCount); put("lockOnOpen", lockOnOpen); put("method", method.name)
        put("waitSec", waitSec); if (codeHash != null) put("codeHash", codeHash)
    }

    companion object {
        fun fromJson(o: JSONObject) = AppRule(
            pkg = o.getString("pkg"),
            label = o.optString("label", o.getString("pkg")),
            limitMin = o.optInt("limitMin", 0),
            strict = o.optBoolean("strict", false),
            graceCount = o.optInt("graceCount", 2),
            lockOnOpen = o.optBoolean("lockOnOpen", false),
            method = runCatching { Method.valueOf(o.optString("method")) }.getOrDefault(Method.BREATH),
            waitSec = o.optInt("waitSec", 30),
            codeHash = if (o.has("codeHash")) o.getString("codeHash") else null,
        )
    }
}

/** Règles + petits états du jour, stockés en local (SharedPreferences, JSON). */
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

    // ---- +5 min de grâce, remis à zéro chaque jour ----
    private fun today() = LocalDate.now().toString()

    fun graceUsed(pkg: String): Int =
        if (prefs.getString("grace_day", "") == today()) prefs.getInt("grace_$pkg", 0) else 0

    fun useGrace(pkg: String) {
        val e = prefs.edit()
        if (prefs.getString("grace_day", "") != today()) {
            prefs.all.keys.filter { it.startsWith("grace_") }.forEach { e.remove(it) }
            e.putString("grace_day", today())
        }
        e.putInt("grace_$pkg", graceUsed(pkg) + 1).apply()
    }

    /** Temps autorisé aujourd'hui (limite + bonus), en ms. */
    fun allowedMs(rule: AppRule): Long =
        (rule.limitMin + 5L * graceUsed(rule.pkg)) * 60_000L

    // ---- Code maître (protège Recul lui-même) ----
    var masterHash: String?
        get() = prefs.getString("master", null)
        set(v) { prefs.edit().apply { if (v == null) remove("master") else putString("master", v) }.apply() }

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) { prefs.edit().putBoolean("onboarded", v).apply() }
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
