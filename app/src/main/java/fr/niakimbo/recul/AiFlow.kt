package fr.niakimbo.recul

import android.os.SystemClock
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.niakimbo.recul.ui.C
import fr.niakimbo.recul.ui.PillButton
import fr.niakimbo.recul.ui.T
import fr.niakimbo.recul.ui.ThinBar
import fr.niakimbo.recul.ui.WaitRing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface AiState {
    data object Talking : AiState
    data class Allowed(val minutes: Int) : AiState
    data object Denied : AiState
    data class Failed(val why: String) : AiState
}

/**
 * Conversation d'1 min max avec l'IA ; à la fin elle décide.
 * [forGrace] : on demande +5 min (limite atteinte) au lieu d'ouvrir l'app.
 */
@Composable
fun ColumnScope.AiFlow(rule: AppRule, session: Int, forGrace: Boolean, onLeave: () -> Unit, onAllow: (minutes: Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val msgs = remember(session) {
        mutableStateListOf(Ai.Msg(false, if (forGrace) "Limite atteinte sur ${rule.label}. Pourquoi 5 min de plus ?" else "Tu ouvres ${rule.label}. Pour quoi faire ?"))
    }
    var state by remember(session) { mutableStateOf<AiState>(AiState.Talking) }
    var input by remember(session) { mutableStateOf("") }
    var sending by remember(session) { mutableStateOf(false) }
    var turns by remember(session) { mutableIntStateOf(0) }
    val start = remember(session) { SystemClock.elapsedRealtime() }
    var now by remember(session) { mutableLongStateOf(start) }
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState()

    val cooldownLeft = Ai.deniedUntil(rule.pkg) - System.currentTimeMillis()
    if (!Ai.ready || cooldownLeft > 0) {
        // Pas de clé, ou refus récent : pas de conversation.
        AiBlocked(rule, session, if (!Ai.ready) null else cooldownLeft, forGrace, onLeave, onAllow)
        return
    }

    val usage = remember(session) { Ai.Situation(rule.label, Usage.todayMs(ctx, rule.pkg) / 60_000, rule.limitMin, Rules.opensToday(rule.pkg)) }

    fun send(force: Boolean) {
        if (sending || state != AiState.Talking) return
        val text = input.trim()
        if (text.isNotEmpty()) { msgs.add(Ai.Msg(true, text)); input = ""; turns++ }
        if (msgs.none { it.fromUser }) return
        sending = true
        scope.launch {
            val last = force || turns >= Ai.MAX_TURNS || Ai.TOTAL_MS - (SystemClock.elapsedRealtime() - start) < 12_000
            runCatching { Ai.next(msgs.toList(), usage, last) }
                .onSuccess { t ->
                    msgs.add(Ai.Msg(false, t.reply))
                    state = when (t.decision) {
                        Ai.Decision.ALLOW -> AiState.Allowed(t.minutes)
                        Ai.Decision.DENY -> { Ai.markDenied(rule.pkg); AiState.Denied }
                        Ai.Decision.ASK -> AiState.Talking
                    }
                }
                .onFailure { e -> state = AiState.Failed(if (e is Ai.AiError) e.message ?: "Erreur" else "Pas de connexion") }
            sending = false
        }
    }

    // Chrono d'1 min : à la fin, l'IA doit trancher.
    LaunchedEffect(session) {
        while (state == AiState.Talking) {
            delay(200); now = SystemClock.elapsedRealtime()
            if (now - start >= Ai.TOTAL_MS && !sending && state == AiState.Talking) {
                if (msgs.none { it.fromUser }) {
                    msgs.add(Ai.Msg(false, "Pas de réponse. Non."))
                    Ai.markDenied(rule.pkg); state = AiState.Denied
                } else send(force = true)
            }
        }
    }
    LaunchedEffect(msgs.size, sending) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.size - 1 + if (sending) 1 else 0) }
    LaunchedEffect(session) { delay(300); runCatching { focus.requestFocus() } }

    Spacer(Modifier.height(20.dp))
    val left = (Ai.TOTAL_MS - (now - start)).coerceAtLeast(0)
    if (state == AiState.Talking) {
        ThinBar(left / Ai.TOTAL_MS.toFloat(), color = if (left < 15_000) C.Over else C.Amber)
        Spacer(Modifier.height(6.dp))
        Text("${(left + 999) / 1000} s", style = T.Small, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
    }

    LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(msgs) { _, m -> Bubble(m) }
        if (sending) item { TypingDots() }
    }

    Spacer(Modifier.height(12.dp))
    when (val s = state) {
        AiState.Talking -> Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(C.Surface).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                if (input.isEmpty()) Text("Ta réponse…", style = T.Body.copy(color = C.Muted))
                BasicTextField(
                    input, { input = it.take(200) },
                    textStyle = T.Body, cursorBrush = SolidColor(C.Amber),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(false) }),
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            val canSend = input.isNotBlank() && !sending
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(if (canSend) C.Amber else C.Surface2).clickable(enabled = canSend) { send(false) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = if (canSend) Color.Black else C.Muted, modifier = Modifier.size(20.dp)) }
        }
        is AiState.Allowed -> {
            PillButton("Je ferme quand même", Modifier.fillMaxWidth(), onClick = onLeave)
            Spacer(Modifier.height(10.dp))
            PillButton(if (forGrace) "Prendre +5 min" else "Entrer · ${s.minutes} min", Modifier.fillMaxWidth(), primary = false) { onAllow(s.minutes) }
        }
        AiState.Denied -> PillButton("Je ferme", Modifier.fillMaxWidth(), onClick = onLeave)
        is AiState.Failed -> {
            Text("IA indisponible (${s.why}) → attente classique.", style = T.Small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            FallbackWait(rule, session, forGrace, onLeave, onAllow)
        }
    }
    Spacer(Modifier.height(16.dp))
}

/** Pas de clé ou refus récent. */
@Composable
private fun ColumnScope.AiBlocked(rule: AppRule, session: Int, cooldownMs: Long?, forGrace: Boolean, onLeave: () -> Unit, onAllow: (Int) -> Unit) {
    Spacer(Modifier.weight(1f))
    if (cooldownMs != null) {
        Text("Refusé.", style = T.Title, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Réessaie dans ${(cooldownMs + 59_999) / 60_000} min.", style = T.Body.copy(color = C.Muted), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        PillButton("Je ferme", Modifier.fillMaxWidth(), onClick = onLeave)
    } else {
        Text("Pas de clé IA.\nAjoute-la dans Recul › Réglages.", style = T.Body.copy(color = C.Muted), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        FallbackWait(rule, session, forGrace, onLeave, onAllow)
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun FallbackWait(rule: AppRule, session: Int, forGrace: Boolean, onLeave: () -> Unit, onAllow: (Int) -> Unit) {
    var done by remember(session) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { WaitRing(rule.waitSec, key = session, size = 120.dp) { done = true } }
    Spacer(Modifier.height(12.dp))
    PillButton("Je ferme", Modifier.fillMaxWidth(), onClick = onLeave)
    Spacer(Modifier.height(10.dp))
    PillButton(if (!done) "Patiente…" else if (forGrace) "Prendre +5 min" else "Ouvrir ${rule.label}",
        Modifier.fillMaxWidth(), primary = false, enabled = done) { onAllow(10) }
}

@Composable
private fun Bubble(m: Ai.Msg) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
        Text(
            m.text, style = T.Body.copy(color = if (m.fromUser) Color.Black else C.Text),
            modifier = Modifier.widthIn(max = 280.dp).heightIn(min = 20.dp)
                .clip(RoundedCornerShape(20.dp, 20.dp, if (m.fromUser) 6.dp else 20.dp, if (m.fromUser) 20.dp else 6.dp))
                .background(if (m.fromUser) C.Amber else C.Surface)
                .padding(horizontal = 16.dp, vertical = 11.dp),
        )
    }
}

@Composable
private fun TypingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(C.Surface).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(3) { i ->
            val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(500, delayMillis = i * 150), RepeatMode.Reverse), label = "d$i")
            Box(Modifier.size(7.dp).alpha(a).clip(CircleShape).background(C.Muted))
        }
    }
}
