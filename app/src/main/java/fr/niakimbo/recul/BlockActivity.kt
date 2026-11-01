package fr.niakimbo.recul

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.niakimbo.recul.ui.AppIcon
import fr.niakimbo.recul.ui.BreathExercise
import fr.niakimbo.recul.ui.C
import fr.niakimbo.recul.ui.Glow
import fr.niakimbo.recul.ui.PillButton
import fr.niakimbo.recul.ui.PinPad
import fr.niakimbo.recul.ui.ReculTheme
import fr.niakimbo.recul.ui.T
import fr.niakimbo.recul.ui.WaitRing

/** L'écran qui passe devant une app : pause, code ou limite atteinte. */
class BlockActivity : ComponentActivity() {

    private var pkg by mutableStateOf("")
    private var reason by mutableIntStateOf(REASON_PAUSE)
    /** Change seulement quand il s'agit d'une AUTRE app / raison → l'écran repart de zéro. */
    private var session by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        read(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
        setContent {
            ReculTheme {
                val label = Rules.get(pkg)?.label ?: Locks.get(pkg)?.label ?: ""
                if (label.isEmpty()) { finishAndRemoveTask(); return@ReculTheme }
                Block(pkg, label, reason, session, onLeave = ::leave, onPass = ::pass)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val newPkg = intent.getStringExtra(EXTRA_PKG) ?: return
        val newReason = intent.getIntExtra(EXTRA_REASON, REASON_PAUSE)
        // Même app, même raison : l'app est juste repassée devant → on garde la pause en cours.
        if (newPkg == pkg && newReason == reason) return
        read(intent)
        session++
    }

    private fun read(i: Intent) {
        pkg = i.getStringExtra(EXTRA_PKG) ?: ""
        reason = i.getIntExtra(EXTRA_REASON, REASON_PAUSE)
        showing = pkg
    }

    override fun onDestroy() {
        if (showing == pkg) showing = null
        super.onDestroy()
    }

    private fun leave() {
        showing = null
        ReculAccessibilityService.goHome(this)
        finishAndRemoveTask()
    }

    private fun pass(grace: Boolean) {
        if (grace) Rules.useGrace(pkg)
        Gate.grant(pkg)
        showing = null
        // On ouvre vraiment l'app (sinon Android peut revenir à l'accueil).
        packageManager.getLaunchIntentForPackage(pkg)?.let {
            runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        finishAndRemoveTask()
    }

    companion object {
        const val REASON_PAUSE = 0
        const val REASON_LIMIT = 1
        const val REASON_CODE = 2
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_REASON = "reason"

        /** App pour laquelle l'écran est ouvert en ce moment (même s'il est caché). */
        @Volatile var showing: String? = null

        fun intent(ctx: Context, pkg: String, reason: Int) =
            Intent(ctx, BlockActivity::class.java)
                .putExtra(EXTRA_PKG, pkg).putExtra(EXTRA_REASON, reason)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}

private val INTENTIONS = listOf("💬 Un message", "🎯 Un truc précis", "🌀 Juste scroller")

@Composable
private fun Block(pkg: String, label: String, reason: Int, session: Int, onLeave: () -> Unit, onPass: (grace: Boolean) -> Unit) {
    val ctx = LocalContext.current
    val rule = Rules.get(pkg)

    Box(Modifier.fillMaxSize().background(C.Bg).safeDrawingPadding().padding(horizontal = 28.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(32.dp))
            AppIcon(pkg, 52.dp)
            Spacer(Modifier.height(10.dp))
            Text(label, style = T.Small)

            when (reason) {
                BlockActivity.REASON_CODE -> {
                    Spacer(Modifier.weight(1f))
                    PinPad("Code de $label", onSubmit = { c ->
                        Codes.check(c, Locks.get(pkg)?.hash).also { ok -> if (ok) onPass(false) }
                    })
                    Spacer(Modifier.weight(1f))
                    Text("Laisser tomber", style = T.Body.copy(color = C.Muted),
                        modifier = Modifier.clickable(onClick = onLeave).padding(16.dp))
                    Spacer(Modifier.height(12.dp))
                }

                BlockActivity.REASON_PAUSE -> if (rule != null) {
                    PauseFlow(rule, session, onLeave) { onPass(false) }
                }

                BlockActivity.REASON_LIMIT -> if (rule != null) {
                    LimitFlow(rule, session, Usage.todayMs(ctx, pkg), onLeave) { onPass(true) }
                }
            }
        }
    }
}

/** Pause façon Ascent : le compte à rebours démarre tout de suite, on choisit son intention pendant ce temps. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PauseFlow(rule: AppRule, session: Int, onLeave: () -> Unit, onOpen: () -> Unit) {
    var done by remember(session) { mutableStateOf(false) }
    var intention by remember(session) { mutableIntStateOf(-1) }
    val opens = remember(session) { Rules.opensToday(rule.pkg) }

    Spacer(Modifier.weight(0.6f))
    Box(contentAlignment = Alignment.Center) {
        when (rule.method) {
            Method.WAIT -> WaitRing(rule.waitSec, key = session) { done = true }
            Method.BREATH -> BreathExercise(rule.breaths, key = session) { done = true }
        }
    }
    Spacer(Modifier.height(18.dp))
    Text(
        when {
            opens <= 1 -> "1re ouverture aujourd'hui"
            else -> "${opens}e ouverture aujourd'hui"
        },
        style = T.Small,
    )
    Spacer(Modifier.weight(0.4f))

    Text("Tu viens faire quoi ?", style = T.H2)
    Spacer(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        INTENTIONS.forEachIndexed { i, s ->
            val on = i == intention
            Text(
                s, fontSize = 15.sp, color = if (on) C.Bg else C.Text,
                modifier = Modifier.clip(RoundedCornerShape(20.dp))
                    .background(if (on) C.Amber else C.Surface)
                    .border(1.dp, if (on) C.Amber else C.Line, RoundedCornerShape(20.dp))
                    .clickable { intention = i }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
    AnimatedVisibility(intention == 2) {
        Text("Ça peut sûrement attendre 🙂", style = T.Small, modifier = Modifier.padding(top = 10.dp))
    }

    Spacer(Modifier.weight(0.6f))
    PillButton("Je ferme", Modifier.fillMaxWidth(), onClick = onLeave)
    Spacer(Modifier.height(10.dp))
    PillButton(if (done) "Ouvrir ${rule.label}" else "Patiente…", Modifier.fillMaxWidth(), primary = false, enabled = done, onClick = onOpen)
    Spacer(Modifier.height(20.dp))
}

private enum class LimitStage { Intro, Challenge }

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.LimitFlow(rule: AppRule, session: Int, usedMs: Long, onLeave: () -> Unit, onGrace: () -> Unit) {
    var stage by remember(session) { mutableStateOf(LimitStage.Intro) }
    var done by remember(session) { mutableStateOf(false) }
    val graceLeft = (rule.graceCount - Rules.graceUsed(rule.pkg)).coerceAtLeast(0)
    val over = rule.strict || graceLeft == 0

    AnimatedContent(stage, Modifier.weight(1f), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "limit") { s ->
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            when (s) {
                LimitStage.Intro -> {
                    Spacer(Modifier.weight(1f))
                    Glow(size = 190.dp)
                    Spacer(Modifier.height(26.dp))
                    Text(if (over) "C'est fini pour aujourd'hui." else "Ta limite est atteinte.", style = T.Title, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (over) "${formatDuration(usedMs)} aujourd'hui. À demain 🌙" else "${formatDuration(usedMs)} aujourd'hui sur ${rule.label}.",
                        style = T.Body.copy(color = C.Muted), textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.weight(1f))
                    PillButton("Je ferme", Modifier.fillMaxWidth(), onClick = onLeave)
                    if (!over) {
                        Spacer(Modifier.height(10.dp))
                        PillButton("+5 min · $graceLeft restante${if (graceLeft > 1) "s" else ""}", Modifier.fillMaxWidth(), primary = false) {
                            stage = LimitStage.Challenge
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
                LimitStage.Challenge -> {
                    Spacer(Modifier.weight(1f))
                    when (rule.method) {
                        Method.WAIT -> WaitRing(rule.waitSec, key = session) { done = true }
                        Method.BREATH -> BreathExercise(rule.breaths, key = session) { done = true }
                    }
                    Spacer(Modifier.weight(1f))
                    PillButton("Je ferme", Modifier.fillMaxWidth(), onClick = onLeave)
                    Spacer(Modifier.height(10.dp))
                    PillButton(if (done) "Prendre +5 min" else "Patiente…", Modifier.fillMaxWidth(), primary = false, enabled = done, onClick = onGrace)
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

