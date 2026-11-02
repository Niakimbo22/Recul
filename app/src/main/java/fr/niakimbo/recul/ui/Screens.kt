package fr.niakimbo.recul.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.niakimbo.recul.AppLock
import fr.niakimbo.recul.AppRule
import fr.niakimbo.recul.BuildConfig
import fr.niakimbo.recul.Codes
import fr.niakimbo.recul.Locks
import fr.niakimbo.recul.Method
import fr.niakimbo.recul.ReculAccessibilityService
import fr.niakimbo.recul.Rules
import fr.niakimbo.recul.Screen
import fr.niakimbo.recul.Updater
import fr.niakimbo.recul.Usage
import fr.niakimbo.recul.WAIT_MAX
import fr.niakimbo.recul.WAIT_MIN
import fr.niakimbo.recul.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val FR = Locale.FRENCH

// ───────────────────────────── Onglets ─────────────────────────────

enum class Tab(val label: String, val icon: ImageVector) {
    Limits("Limites", Icons.Outlined.Timer),
    Codes("Codes", Icons.Outlined.Key),
    Stats("Stats", Icons.Outlined.BarChart),
}

@Composable
fun TabsScreen(tab: Tab, tick: Int, go: (Screen) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        AnimatedContent(tab, Modifier.weight(1f), transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "tab") { t ->
            when (t) {
                Tab.Limits -> LimitsTab(tick, go)
                Tab.Codes -> CodesTab(go)
                Tab.Stats -> StatsTab(tick)
            }
        }
        BottomBar(tab) { go(Screen.Tabs(it)) }
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth()
            .clip(RoundedCornerShape(30.dp)).background(C.Surface).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Tab.entries.forEach { t ->
            val on = t == selected
            Row(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp))
                    .background(if (on) C.AmberDim else Color.Transparent)
                    .clickable { onSelect(t) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(t.icon, null, tint = if (on) C.Amber else C.Muted, modifier = Modifier.size(20.dp))
                if (on) {
                    Spacer(Modifier.width(8.dp))
                    Text(t.label, color = C.Amber, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TopTitle(title: String, sub: String?, go: (Screen) -> Unit) {
    Row(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.Title)
            if (sub != null) Text(sub, style = T.Small)
        }
        RoundIcon(Icons.Outlined.Settings) { go(Screen.Settings) }
    }
}

// ───────────────────────────── Limites ─────────────────────────────

@Composable
private fun LimitsTab(tick: Int, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val rules by Rules.rules.collectAsState()
    val hasUsage = remember(tick) { Usage.hasAccess(ctx) }
    val hasA11y = remember(tick) { ReculAccessibilityService.isEnabled(ctx) }
    val used by produceState(emptyMap<String, Long>(), tick, rules) {
        while (true) {
            value = withContext(Dispatchers.IO) { Usage.todayAll(ctx, rules.map { it.pkg }.toSet()) }
            delay(30_000)
        }
    }
    val total = used.values.sum()

    Column(Modifier.fillMaxSize()) {
        TopTitle("Recul", LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", FR)).replaceFirstChar { it.uppercase() }, go)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            if (!hasBlockerService(ctx)) {
                Spacer(Modifier.height(20.dp)); InstallFullCard()
            } else if (!hasUsage || !hasA11y) {
                Spacer(Modifier.height(20.dp)); SetupCard(hasUsage, hasA11y)
            }

            Spacer(Modifier.height(32.dp))
            Text("AUJOURD'HUI", style = T.Label)
            Spacer(Modifier.height(4.dp))
            Text(formatDuration(total), style = T.Hero)
            Text(if (rules.isEmpty()) "Aucune app surveillée" else "sur tes apps surveillées", style = T.Small)
            Spacer(Modifier.height(26.dp))

            if (rules.isEmpty()) {
                Empty("Ajoute TikTok, Insta…\npour poser une limite ou une pause.")
            } else {
                rules.sortedByDescending { used[it.pkg] ?: 0 }.forEach { r ->
                    AppRow(r, used[r.pkg] ?: 0L) { go(Screen.Edit(r.pkg)) }
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            AddButton("Ajouter une app") { go(Screen.Pick(forCodes = false)) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Empty(text: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        Glow(size = 140.dp)
        Spacer(Modifier.height(14.dp))
        Text(text, style = T.Body.copy(color = C.Muted), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AddButton(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
            .background(C.Surface).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) { Text("＋  $label", color = C.Amber, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun AppRow(r: AppRule, usedMs: Long, onClick: () -> Unit) {
    val allowed = Rules.allowedMs(r)
    val over = r.hasLimit && usedMs >= allowed
    Panel(onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(r.pkg)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.label, style = T.H2.copy(fontSize = 17.sp))
                    Text(
                        if (r.hasLimit) "${formatDuration(usedMs)} / ${formatDuration(allowed)}" else formatDuration(usedMs),
                        style = T.Small.copy(color = if (over) C.Over else C.Muted),
                    )
                }
                if (r.pauseOnOpen) when (r.method) {
                    Method.BREATH -> Badge(Icons.Outlined.Air)
                    Method.WAIT -> Badge(Icons.Outlined.HourglassEmpty, "${r.waitSec}s")
                    Method.AI -> Badge(Icons.Outlined.AutoAwesome, "IA")
                }
                if (r.hasLimit && r.strict) Badge(Icons.Outlined.Nightlight)
                if (Locks.get(r.pkg) != null) Badge(Icons.Outlined.Lock)
            }
            if (r.hasLimit) {
                Spacer(Modifier.height(14.dp))
                ThinBar(usedMs.toFloat() / allowed, color = if (over) C.Over else C.Amber)
            }
        }
    }
}

@Composable
private fun Badge(icon: ImageVector, text: String? = null) {
    Row(
        Modifier.padding(start = 6.dp).height(30.dp).clip(RoundedCornerShape(15.dp)).background(C.Surface2).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = C.Muted, modifier = Modifier.size(15.dp))
        if (text != null) { Spacer(Modifier.width(4.dp)); Text(text, style = T.Small.copy(fontSize = 12.sp)) }
    }
}

@Composable
fun RoundIcon(icon: ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(C.Surface).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = C.Text, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SetupCard(hasUsage: Boolean, hasA11y: Boolean) {
    val ctx = LocalContext.current
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(C.AmberDim).padding(20.dp)) {
        Column {
            Text("2 accès à donner", style = T.H2)
            Spacer(Modifier.height(4.dp))
            Text("Sans eux, Recul ne voit rien.", style = T.Small.copy(color = C.Text.copy(alpha = 0.7f)))
            Spacer(Modifier.height(16.dp))
            SetupStep("1", "Temps d'utilisation", hasUsage) {
                ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            Spacer(Modifier.height(8.dp))
            SetupStep("2", "Bloqueur (Accessibilité)", hasA11y) {
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            if (!hasA11y) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Grisé ? Infos de l'appli Recul › ⋮ › Autoriser les paramètres restreints.",
                    style = T.Small.copy(color = C.Text.copy(alpha = 0.7f)),
                    modifier = Modifier.clickable {
                        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                )
            }
        }
    }
}

@Composable
private fun SetupStep(n: String, label: String, done: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Bg.copy(alpha = 0.55f))
            .clickable(enabled = !done, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(if (done) C.Amber else C.Surface2), contentAlignment = Alignment.Center) {
            if (done) Icon(Icons.Outlined.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
            else Text(n, color = C.Text, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = T.Body, modifier = Modifier.weight(1f))
        if (!done) Icon(Icons.Outlined.ChevronRight, null, tint = C.Muted)
    }
}

// ───────────────────────────── Codes ─────────────────────────────

@Composable
private fun CodesTab(go: (Screen) -> Unit) {
    val locks by Locks.locks.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopTitle("Codes", "Un code pour entrer dans une app", go)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(24.dp))
            if (locks.isEmpty()) {
                Empty("Aucune app protégée.\nBanque, photos, messages…")
            } else {
                locks.sortedBy { it.label.lowercase(FR) }.forEach { l ->
                    Panel(onClick = { go(Screen.CodeEdit(l.pkg, l.label)) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(l.pkg)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(l.label, style = T.H2.copy(fontSize = 17.sp))
                                Text("•••• à chaque ouverture", style = T.Small)
                            }
                            Icon(Icons.Outlined.Lock, null, tint = C.Amber, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            AddButton("Protéger une app") { go(Screen.Pick(forCodes = true)) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Créer / changer / retirer le code d'une app. */
@Composable
fun CodeEditScreen(pkg: String, label: String, go: (Screen) -> Unit) {
    val existing = Locks.get(pkg)
    var setting by remember { mutableStateOf(existing == null) }
    val back = { go(Screen.Tabs(Tab.Codes)) }

    if (setting) {
        NewCodeFlow("Code de $label", onCancel = { if (existing == null) back() else setting = false }) { hash ->
            Locks.set(AppLock(pkg, label, hash))
            back()
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Header(label, icon = { AppIcon(pkg, 30.dp) }, onBack = back)
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            Panel {
                Column {
                    Text("Code actif", style = T.H2)
                    Text("Demandé à chaque ouverture. Tu as 1 min pour revenir dans l'app sans le retaper.", style = T.Small)
                }
            }
            Spacer(Modifier.height(16.dp))
            PillButton("Changer le code", Modifier.fillMaxWidth(), primary = false) { setting = true }
            Spacer(Modifier.height(10.dp))
            Text("Retirer le code", style = T.Body.copy(color = C.Over), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { Locks.remove(pkg); back() }.padding(16.dp))
        }
    }
}

// ───────────────────────────── Choisir une app ─────────────────────────────

private data class Launchable(val pkg: String, val label: String)

@Composable
fun PickScreen(forCodes: Boolean, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val rules by Rules.rules.collectAsState()
    val locks by Locks.locks.collectAsState()
    var query by remember { mutableStateOf("") }
    val apps by produceState<List<Launchable>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
                .map { Launchable(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .filter { it.pkg != ctx.packageName }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase(FR) }
        }
    }
    val back = { go(Screen.Tabs(if (forCodes) Tab.Codes else Tab.Limits)) }

    Column(Modifier.fillMaxSize().imePadding()) {
        Header(if (forCodes) "Protéger une app" else "Ajouter une app", onBack = back)
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Surface).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = C.Muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Rechercher…", style = T.Body.copy(color = C.Muted))
                BasicTextField(query, { query = it }, singleLine = true, textStyle = T.Body, cursorBrush = SolidColor(C.Amber), modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(8.dp))
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Glow(size = 90.dp) }
        } else {
            val filtered = list.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.pkg }) { app ->
                    val already = if (forCodes) locks.any { it.pkg == app.pkg } else rules.any { it.pkg == app.pkg }
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (forCodes) go(Screen.CodeEdit(app.pkg, app.label))
                            else {
                                if (!already) Rules.upsert(AppRule(app.pkg, app.label, limitMin = 30, pauseOnOpen = true))
                                go(Screen.Edit(app.pkg))
                            }
                        }.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app.pkg, 40.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(app.label, style = T.Body, modifier = Modifier.weight(1f))
                        if (already) Dot()
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Réglage d'une app (limite + pause) ─────────────────────────────

private val LIMITS = listOf(0, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240)

@Composable
fun EditScreen(pkg: String, go: (Screen) -> Unit) {
    val rules by Rules.rules.collectAsState()
    val rule = rules.firstOrNull { it.pkg == pkg } ?: run { LaunchedEffect(Unit) { go(Screen.Tabs(Tab.Limits)) }; return }
    fun update(f: (AppRule) -> AppRule) = Rules.upsert(f(rule))
    val back = { go(Screen.Tabs(Tab.Limits)) }

    Column(Modifier.fillMaxSize()) {
        Header(rule.label, icon = { AppIcon(rule.pkg, 30.dp) }, onBack = back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {

            // Pause à l'ouverture
            Section(null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pause à l'ouverture", style = T.H2.copy(fontSize = 17.sp))
                        Text("Un temps d'arrêt avant d'entrer", style = T.Small)
                    }
                    ReculSwitch(rule.pauseOnOpen) { v -> update { it.copy(pauseOnOpen = v) } }
                }
                AnimatedVisibility(rule.pauseOnOpen || (rule.hasLimit && !rule.strict)) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        Segmented(Method.entries.map { it.label }, rule.method.ordinal) { i -> update { it.copy(method = Method.entries[i]) } }
                        Spacer(Modifier.height(16.dp))
                        when (rule.method) {
                            Method.WAIT -> {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text("${rule.waitSec}", style = T.Hero.copy(fontSize = 44.sp))
                                    Text("  secondes", style = T.Small, modifier = Modifier.padding(bottom = 10.dp))
                                }
                                var v by remember(pkg) { mutableFloatStateOf(rule.waitSec.toFloat()) }
                                Slider(
                                    value = v,
                                    onValueChange = { v = it; update { r -> r.copy(waitSec = it.toInt()) } },
                                    valueRange = WAIT_MIN.toFloat()..WAIT_MAX.toFloat(),
                                    colors = sliderColors(),
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(2, 5, 10, 30).forEach { s ->
                                        Chip("$s s", rule.waitSec == s, Modifier.weight(1f)) { v = s.toFloat(); update { it.copy(waitSec = s) } }
                                    }
                                }
                            }
                            Method.BREATH -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Respirations", style = T.Body)
                                    Text("≈ ${rule.breaths * 9} s", style = T.Small)
                                }
                                Stepper(rule.breaths, 1..5) { n -> update { it.copy(breaths = n) } }
                            }
                            Method.AI -> Column {
                                Text("1 min de discussion. L'IA décide si tu entres, et pour combien de temps.", style = T.Body)
                                Spacer(Modifier.height(6.dp))
                                Text("Refusé → réessai possible dans 5 min.", style = T.Small)
                                if (!fr.niakimbo.recul.Ai.ready) {
                                    Spacer(Modifier.height(10.dp))
                                    Text("⚠️ Ajoute ta clé IA dans Réglages", style = T.Small.copy(color = C.Amber),
                                        modifier = Modifier.clickable { go(Screen.Settings) })
                                }
                            }
                        }
                    }
                }
            }

            // Limite
            Section("Limite par jour") {
                Text(if (rule.hasLimit) formatDuration(rule.limitMin * 60_000L) else "Aucune", style = T.Hero.copy(fontSize = 44.sp))
                var idx by remember(pkg) { mutableFloatStateOf(LIMITS.indexOfClosest(rule.limitMin).toFloat()) }
                Slider(
                    value = idx,
                    onValueChange = { idx = it; update { r -> r.copy(limitMin = LIMITS[it.toInt()]) } },
                    valueRange = 0f..(LIMITS.size - 1).toFloat(),
                    steps = LIMITS.size - 2,
                    colors = sliderColors(),
                )
                AnimatedVisibility(rule.hasLimit) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text("Quand c'est fini", style = T.Small)
                        Spacer(Modifier.height(8.dp))
                        Segmented(listOf("+5 min possible", "Fini jusqu'à demain"), if (rule.strict) 1 else 0) { i ->
                            update { it.copy(strict = i == 1) }
                        }
                        AnimatedVisibility(!rule.strict) {
                            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("+5 min par jour", style = T.Body, modifier = Modifier.weight(1f))
                                Stepper(rule.graceCount, 1..5) { n -> update { it.copy(graceCount = n) } }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Retirer ${rule.label}", style = T.Body.copy(color = C.Over), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { Rules.remove(pkg); back() }.padding(16.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = C.Amber, activeTrackColor = C.Amber, inactiveTrackColor = C.Surface2,
    activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
)

@Composable
private fun ReculSwitch(checked: Boolean, onChange: (Boolean) -> Unit) = Switch(
    checked, onChange,
    colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = C.Amber,
        uncheckedThumbColor = C.Muted, uncheckedTrackColor = C.Surface2, uncheckedBorderColor = C.Line),
)

@Composable
private fun Chip(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (on) C.Amber else C.Surface2).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (on) Color.Black else C.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}

private fun List<Int>.indexOfClosest(v: Int) = indices.minBy { kotlin.math.abs(this[it] - v) }

@Composable
private fun Section(title: String?, content: @Composable () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Panel {
        Column {
            if (title != null) { Text(title.uppercase(FR), style = T.Label); Spacer(Modifier.height(10.dp)) }
            content()
        }
    }
}

@Composable
private fun Stepper(value: Int, range: IntRange, step: Int = 1, suffix: String = "", onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepKey("−", value - step >= range.first) { onChange(value - step) }
        Text("$value$suffix", style = T.H2, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
        StepKey("+", value + step <= range.last) { onChange(value + step) }
    }
}

@Composable
private fun StepKey(s: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(CircleShape).background(C.Surface2).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(s, color = if (enabled) C.Text else C.Line, fontSize = 20.sp)
    }
}

/** Choisir un code puis le confirmer. Renvoie le hash. */
@Composable
fun NewCodeFlow(title: String, onCancel: () -> Unit, onDone: (String) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Header(title, onBack = onCancel)
        Spacer(Modifier.weight(1f))
        if (first == null) {
            PinPad("Choisis un code (4 chiffres ou plus)", onSubmit = { c -> first = c; true })
        } else {
            PinPad("Encore une fois", onSubmit = { c ->
                if (c == first) { onDone(Codes.hash(c)); true } else { first = null; false }
            })
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun Header(title: String, icon: (@Composable () -> Unit)? = null, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIcon(Icons.AutoMirrored.Outlined.ArrowBack, onBack)
        Spacer(Modifier.width(12.dp))
        if (icon != null) { icon(); Spacer(Modifier.width(10.dp)) }
        Text(title, style = T.H2)
    }
}

// ───────────────────────────── Stats ─────────────────────────────

@Composable
private fun StatsTab(tick: Int) {
    val ctx = LocalContext.current
    val rules by Rules.rules.collectAsState()
    val week by produceState<List<Pair<LocalDate, Map<String, Long>>>?>(null, tick, rules) {
        value = withContext(Dispatchers.IO) { Usage.week(ctx, rules.map { it.pkg }.toSet()) }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) {
            Text("Stats", style = T.Title)
            Text("7 derniers jours", style = T.Small)
        }
        val w = week
        if (w == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Glow(size = 90.dp) }; return@Column }
        val totals = w.map { it.second.values.sum() }
        val weekTotal = totals.sum()
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(28.dp))
            Text("MOYENNE PAR JOUR", style = T.Label)
            Text(formatDuration(weekTotal / 7), style = T.Hero)
            Text("${formatDuration(weekTotal)} sur la semaine", style = T.Small)
            Spacer(Modifier.height(24.dp))
            Panel { WeekBars(w.map { it.first }, totals) }
            Spacer(Modifier.height(18.dp))
            rules.map { r -> r to w.sumOf { it.second[r.pkg] ?: 0L } }.sortedByDescending { it.second }.forEach { (r, ms) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(r.pkg, 36.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.label, style = T.Body)
                        Spacer(Modifier.height(6.dp))
                        ThinBar(if (weekTotal > 0) ms.toFloat() / weekTotal else 0f)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatDuration(ms), style = T.Small.copy(color = C.Text))
                        Text("${Rules.opensToday(r.pkg)} pause${if (Rules.opensToday(r.pkg) > 1) "s" else ""} auj.", style = T.Small.copy(fontSize = 11.sp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WeekBars(days: List<LocalDate>, values: List<Long>) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(60_000L)
    Column {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val n = values.size
            val gap = 14.dp.toPx()
            val bw = (size.width - gap * (n - 1)) / n
            values.forEachIndexed { i, v ->
                val h = (v.toFloat() / max) * size.height
                val x = i * (bw + gap)
                drawRoundRect(C.Surface2, Offset(x, 0f), Size(bw, size.height), CornerRadius(10.dp.toPx()))
                if (h > 0) drawRoundRect(if (i == n - 1) C.Amber else C.Amber.copy(alpha = 0.45f),
                    Offset(x, size.height - h), Size(bw, h), CornerRadius(10.dp.toPx()))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEach { d ->
                Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, FR), style = T.Small, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
    }
}

// ───────────────────────────── Réglages ─────────────────────────────

@Composable
fun SettingsScreen(tick: Int, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var settingMaster by remember { mutableStateOf(false) }
    var master by remember { mutableStateOf(Rules.masterHash != null) }
    var status by remember { mutableStateOf<String?>(null) }
    var available by remember { mutableStateOf<Updater.Release?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    val hasUsage = remember(tick) { Usage.hasAccess(ctx) }
    val hasA11y = remember(tick) { ReculAccessibilityService.isEnabled(ctx) }
    val back = { go(Screen.Tabs(Tab.Limits)) }

    if (settingMaster) {
        NewCodeFlow("Code de Recul", onCancel = { settingMaster = false }) { h ->
            Rules.masterHash = h; master = true; settingMaster = false
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Header("Réglages", onBack = back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Section("Mise à jour") {
                Text("Version ${BuildConfig.VERSION_NAME}", style = T.H2)
                Spacer(Modifier.height(4.dp))
                Text(status ?: "Automatique. Tu peux aussi vérifier à la main.", style = T.Small)
                Spacer(Modifier.height(14.dp))
                val rel = available
                val p = progress
                when {
                    p != null -> ThinBar(p)
                    rel != null -> PillButton("Installer ${rel.name}", Modifier.fillMaxWidth()) {
                        if (!Updater.canInstall(ctx)) { Updater.askInstallPermission(ctx); return@PillButton }
                        scope.launch {
                            progress = 0f
                            runCatching { Updater.download(ctx, rel) { progress = it } }
                                .onSuccess { fr.niakimbo.recul.AutoUpdate.installSession(ctx, it); progress = null; status = "Installation…" }
                                .onFailure { status = "Téléchargement raté"; progress = null }
                        }
                    }
                    else -> PillButton("Vérifier", Modifier.fillMaxWidth(), primary = false) {
                        scope.launch {
                            status = "Recherche…"
                            when (val c = Updater.check()) {
                                Updater.Check.UpToDate -> status = "Tu as la dernière version ✓"
                                is Updater.Check.Available -> { available = c.release; status = "${c.release.name} dispo" }
                                is Updater.Check.Error -> status = c.message
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                InstallApkButton("Installer un fichier APK", primary = false)
            }

            Section("IA") { AiSettings() }

            Section("Accès") {
                AccessRow("Temps d'utilisation", hasUsage) {
                    ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                AccessRow("Bloqueur (Accessibilité)", hasA11y) {
                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                AccessRow("Installer les MAJ", Updater.canInstall(ctx)) { Updater.askInstallPermission(ctx) }
            }

            Section("Protéger Recul") {
                Text("Un code pour ouvrir Recul, pour ne pas tout désactiver sur un coup de tête.", style = T.Small)
                Spacer(Modifier.height(12.dp))
                if (master) {
                    Row {
                        PillButton("Changer", Modifier.weight(1f), primary = false) { settingMaster = true }
                        Spacer(Modifier.width(10.dp))
                        PillButton("Retirer", Modifier.weight(1f), primary = false) { Rules.masterHash = null; master = false }
                    }
                } else PillButton("Choisir un code", Modifier.fillMaxWidth(), primary = false) { settingMaster = true }
            }
            Spacer(Modifier.height(24.dp))
            Text("Recul ne lit ni n'envoie rien : seul le nom de l'app ouverte est utilisé, sur ton téléphone.",
                style = T.Small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AccessRow(label: String, ok: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = T.Body, modifier = Modifier.weight(1f))
        Text(if (ok) "Activé" else "À activer", style = T.Small.copy(color = if (ok) C.Amber else C.Over, fontWeight = FontWeight.Medium))
    }
}
