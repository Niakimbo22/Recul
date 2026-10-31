package fr.niakimbo.recul

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.niakimbo.recul.ui.AppIcon
import fr.niakimbo.recul.ui.BreathExercise
import fr.niakimbo.recul.ui.C
import fr.niakimbo.recul.ui.Glow
import fr.niakimbo.recul.ui.PillButton
import fr.niakimbo.recul.ui.PinPad
import fr.niakimbo.recul.ui.ReculTheme
import fr.niakimbo.recul.ui.T
import fr.niakimbo.recul.ui.WaitRing

/** L'écran qui passe devant une app verrouillée ou dont la limite est atteinte. */
class BlockActivity : ComponentActivity() {

    private var pkg by mutableStateOf("")
    private var reason by mutableStateOf(REASON_LOCK)
    private var nonce by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        read(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
        setContent {
            ReculTheme {
                val rule = Rules.get(pkg)
                if (rule == null) { finishAndRemoveTask(); return@ReculTheme }
                Block(rule, reason, nonce, onLeave = ::leave, onPass = ::pass)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        read(intent)
        nonce++
    }

    private fun read(i: Intent) {
        pkg = i.getStringExtra(EXTRA_PKG) ?: ""
        reason = i.getIntExtra(EXTRA_REASON, REASON_LOCK)
    }

    private fun leave() {
        ReculAccessibilityService.goHome(this)
        finishAndRemoveTask()
    }

    private fun pass(grace: Boolean) {
        if (grace) Rules.useGrace(pkg)
        Gate.grant(pkg)
        finishAndRemoveTask()
    }

    companion object {
        const val REASON_LOCK = 0
        const val REASON_LIMIT = 1
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_REASON = "reason"

        fun intent(ctx: Context, pkg: String, reason: Int) =
            Intent(ctx, BlockActivity::class.java)
                .putExtra(EXTRA_PKG, pkg).putExtra(EXTRA_REASON, reason)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}

private enum class Stage { Intro, Challenge, Done }

@Composable
private fun Block(rule: AppRule, reason: Int, nonce: Int, onLeave: () -> Unit, onPass: (grace: Boolean) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var stage by remember(nonce, rule.pkg) { mutableStateOf(Stage.Intro) }
    val isLimit = reason == BlockActivity.REASON_LIMIT
    val graceLeft = (rule.graceCount - Rules.graceUsed(rule.pkg)).coerceAtLeast(0)
    val canPass = !isLimit || (!rule.strict && graceLeft > 0)
    val method = if (rule.method == Method.CODE && rule.codeHash == null) Method.BREATH else rule.method
    val used = remember(nonce) { if (isLimit) Usage.todayMs(ctx, rule.pkg) else 0L }

    Box(Modifier.fillMaxSize().background(C.Bg).safeDrawingPadding().padding(horizontal = 28.dp)) {
        AnimatedContent(stage, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "stage") { s ->
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(36.dp))
                AppIcon(rule.pkg, 52.dp)
                Spacer(Modifier.height(10.dp))
                Text(rule.label, style = T.Small)

                when (s) {
                    Stage.Intro -> {
                        Spacer(Modifier.weight(1f))
                        Glow(size = 200.dp)
                        Spacer(Modifier.height(28.dp))
                        Text(
                            when {
                                !isLimit -> "Prends du recul."
                                rule.strict || graceLeft == 0 -> "C'est fini pour aujourd'hui."
                                else -> "Ta limite est atteinte."
                            },
                            style = T.Title, textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            when {
                                !isLimit -> "Tu ouvres ${rule.label}. Tu viens faire quoi, vraiment ?"
                                rule.strict || graceLeft == 0 -> "${formatDuration(used)} aujourd'hui. On se retrouve demain 🌙"
                                else -> "${formatDuration(used)} aujourd'hui sur ${rule.label}."
                            },
                            style = T.Body.copy(color = C.Muted), textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.weight(1f))
                        PillButton("Retour à l'accueil", Modifier.fillMaxWidth(), onClick = onLeave)
                        if (canPass) {
                            Spacer(Modifier.height(10.dp))
                            val label = if (isLimit) "+5 min · $graceLeft restante${if (graceLeft > 1) "s" else ""}"
                                        else "Continuer (${method.label.lowercase()})"
                            PillButton(label, Modifier.fillMaxWidth(), primary = false) { stage = Stage.Challenge }
                        }
                        Spacer(Modifier.height(24.dp))
                    }

                    Stage.Challenge -> {
                        Spacer(Modifier.weight(1f))
                        when (method) {
                            Method.BREATH -> {
                                BreathExercise { stage = Stage.Done }
                            }
                            Method.WAIT -> {
                                WaitRing(rule.waitSec) { stage = Stage.Done }
                                Spacer(Modifier.height(16.dp))
                                Text("Pose ton téléphone une seconde.", style = T.Small)
                            }
                            Method.CODE -> PinPad("Code de ${rule.label}", onSubmit = { c ->
                                Codes.check(c, rule.codeHash).also { ok -> if (ok) onPass(isLimit) }
                            })
                        }
                        Spacer(Modifier.weight(1f))
                        Text("Laisser tomber", style = T.Body.copy(color = C.Muted),
                            modifier = Modifier.clickable(onClick = onLeave).padding(16.dp))
                        Spacer(Modifier.height(16.dp))
                    }

                    Stage.Done -> {
                        Spacer(Modifier.weight(1f))
                        Text("Toujours envie ?", style = T.Title, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        Text("Tu peux encore changer d'avis.", style = T.Body.copy(color = C.Muted))
                        Spacer(Modifier.weight(1f))
                        PillButton("Non, je ferme", Modifier.fillMaxWidth(), onClick = onLeave)
                        Spacer(Modifier.height(10.dp))
                        PillButton(if (isLimit) "Oui, +5 min" else "Oui, ouvrir ${rule.label}", Modifier.fillMaxWidth(), primary = false) { onPass(isLimit) }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

