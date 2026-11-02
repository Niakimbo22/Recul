package fr.niakimbo.recul

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Le « videur » : courte conversation avec Gemini (gratuit) avant d'ouvrir une app. */
object Ai {
    const val TOTAL_MS = 60_000L      // 1 min max de conversation
    const val MAX_TURNS = 3           // réponses de l'utilisateur avant décision forcée
    const val DENY_COOLDOWN_MS = 5 * 60_000L

    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
    private const val DEFAULT_MODEL = "gemini-3.5-flash-lite"

    private lateinit var prefs: SharedPreferences
    fun init(ctx: Context) { prefs = ctx.getSharedPreferences("recul_ai", Context.MODE_PRIVATE) }

    var apiKey: String
        get() = prefs.getString("key", "") ?: ""
        set(v) { prefs.edit().putString("key", v.trim()).apply() }

    private var model: String
        get() = prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) { prefs.edit().putString("model", v).apply() }

    val ready get() = apiKey.isNotBlank()

    // ---- Refus récents : on ne peut pas retenter tout de suite ----
    fun deniedUntil(pkg: String) = prefs.getLong("deny_$pkg", 0L)
    fun markDenied(pkg: String) { prefs.edit().putLong("deny_$pkg", System.currentTimeMillis() + DENY_COOLDOWN_MS).apply() }

    data class Msg(val fromUser: Boolean, val text: String)

    enum class Decision { ASK, ALLOW, DENY }
    data class Turn(val reply: String, val decision: Decision, val minutes: Int)

    data class Situation(val app: String, val usedMin: Long, val limitMin: Int, val opensToday: Int)

    class AiError(message: String) : Exception(message)

    private fun systemPrompt(c: Situation, force: Boolean) = """
Tu es le filtre de l'app Recul. L'utilisateur veut ouvrir ${c.app}. Tu décides s'il entre.
Ton : froid, sec, factuel. Pas de jugement, pas de morale, pas d'encouragements, pas d'emoji. Tutoiement. 1 ou 2 phrases très courtes maximum. Français.
Contexte : il est ${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))}. Déjà ${c.usedMin} min sur ${c.app} aujourd'hui${if (c.limitMin > 0) " (limite ${c.limitMin} min)" else ""}. ${c.opensToday}e tentative d'ouverture aujourd'hui.
Règles :
- Raison précise et limitée dans le temps (répondre à un message, poster, chercher une chose précise, quelqu'un lui a envoyé une vidéo) → "allow", avec "minutes" juste suffisantes (entre 2 et 15).
- Raison vague, ennui, habitude, "juste regarder", "scroller", pas de vraie raison → "deny".
- Si c'est flou, pose UNE question courte pour préciser → "ask".
- Plus il a déjà passé de temps ou fait de tentatives aujourd'hui, plus tu es exigeant.
- Il ne peut pas négocier à l'infini : ne te laisse pas convaincre par l'insistance seule.
${if (force) "- C'EST LA FIN DE LA CONVERSATION : tu dois répondre \"allow\" ou \"deny\", jamais \"ask\"." else ""}
Réponds UNIQUEMENT en JSON : {"reply": "...", "decision": "ask" | "allow" | "deny", "minutes": nombre}
""".trimIndent()

    /** Envoie la conversation, renvoie la réponse et la décision. */
    suspend fun next(history: List<Msg>, c: Situation, force: Boolean): Turn = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt(c, force)))))
            put("contents", JSONArray().apply {
                // Gemini veut que la conversation commence par l'utilisateur.
                history.dropWhile { !it.fromUser }.forEach { m ->
                    put(JSONObject().put("role", if (m.fromUser) "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", m.text))))
                }
            })
            put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("temperature", 0.4).put("maxOutputTokens", 200))
        }
        var res = post(model, body)
        if (res.first == 404) {
            // Modèle retiré : on prend le « flash-lite » le plus récent disponible.
            pickModel()?.let { model = it; res = post(it, body) }
        }
        val (code, text) = res
        when (code) {
            200 -> parse(text, force)
            400, 401, 403 -> throw AiError("Clé refusée")
            429 -> throw AiError("Quota gratuit atteint")
            else -> throw AiError("Erreur $code")
        }
    }

    /** Petit test de la clé (Réglages). */
    suspend fun test(): String = try {
        val t = next(listOf(Msg(true, "Test de connexion, réponds juste ok.")), Situation("Test", 0, 0, 1), force = false)
        "Connectée ✓ (${t.reply.take(40)})"
    } catch (e: AiError) { e.message ?: "Erreur" } catch (e: Exception) { "Pas de connexion" }

    private fun post(m: String, body: JSONObject): Pair<Int, String> {
        val c = (URL("$BASE/models/$m:generateContent").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 8_000; readTimeout = 15_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
        return code to text
    }

    private fun pickModel(): String? = runCatching {
        val c = (URL("$BASE/models?pageSize=200").openConnection() as HttpURLConnection).apply {
            setRequestProperty("x-goog-api-key", apiKey); connectTimeout = 8_000; readTimeout = 10_000
        }
        val arr = JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("models")
        val names = (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { o -> o.optJSONArray("supportedGenerationMethods")?.let { a -> (0 until a.length()).any { a.getString(it) == "generateContent" } } == true }
            .map { it.getString("name").removePrefix("models/") }
        names.filter { "flash-lite" in it && "preview" !in it && "image" !in it }.maxOrNull()
            ?: names.filter { "flash" in it && "preview" !in it && "image" !in it }.maxOrNull()
    }.getOrNull()

    private fun parse(raw: String, force: Boolean): Turn {
        val text = JSONObject(raw).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
            .let { p -> (0 until p.length()).joinToString("") { p.getJSONObject(it).optString("text") } }
        val json = text.substring(text.indexOf('{').coerceAtLeast(0), text.lastIndexOf('}') + 1)
        val o = JSONObject(json)
        var d = when (o.optString("decision").lowercase()) {
            "allow" -> Decision.ALLOW
            "deny" -> Decision.DENY
            else -> Decision.ASK
        }
        if (force && d == Decision.ASK) d = Decision.DENY
        return Turn(o.optString("reply").ifBlank { if (d == Decision.DENY) "Non." else "Pourquoi ?" }, d, o.optInt("minutes", 5).coerceIn(1, 30))
    }
}
