package fr.niakimbo.recul.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
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
import fr.niakimbo.recul.AppRule
import fr.niakimbo.recul.BuildConfig
import fr.niakimbo.recul.Codes
import fr.niakimbo.recul.Method
import fr.niakimbo.recul.ReculAccessibilityService
import fr.niakimbo.recul.Rules
import fr.niakimbo.recul.Screen
import fr.niakimbo.recul.Updater
import fr.niakimbo.recul.Usage
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

// ───────────────────────────── Accueil ─────────────────────────────

@Composable
fun HomeScreen(tick: Int, go: (Screen) -> Unit) {
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
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Recul", style = T.Title)
                    Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", FR)).replaceFirstChar { it.uppercase() }, style = T.Small)
                }
                RoundIcon(Icons.Outlined.BarChart) { go(Screen.Stats) }
                Spacer(Modifier.width(8.dp))
                RoundIcon(Icons.Outlined.Settings) { go(Screen.Settings) }
            }

            if (!hasBlockerService(ctx)) {
                Spacer(Modifier.height(20.dp))
                InstallFullCard()
            } else if (!hasUsage || !hasA11y) {
                Spacer(Modifier.height(20.dp))
                SetupCard(hasUsage, hasA11y)
            }

            Spacer(Modifier.height(36.dp))
            Text("AUJOURD'HUI", style = T.Label)
            Spacer(Modifier.height(4.dp))
            Text(formatDuration(total), style = T.Hero)
            Text(if (rules.isEmpty()) "Aucune app surveillée" else "sur tes apps surveillées", style = T.Small)
            Spacer(Modifier.height(28.dp))

            if (rules.isEmpty()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(20.dp))
                    Glow(size = 160.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("Ajoute une app pour commencer.", style = T.Body.copy(color = C.Muted))
                }
            } else {
                rules.sortedByDescending { used[it.pkg] ?: 0 }.forEach { r ->
                    AppRow(r, used[r.pkg] ?: 0L) { go(Screen.Edit(r.pkg)) }
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        PillButton("Ajouter une app", Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) { go(Screen.Pick) }
    }
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
                if (r.lockOnOpen) Badge(Icons.Outlined.Lock)
                if (r.hasLimit && r.strict) Badge(Icons.Outlined.Nightlight)
                Badge(methodIcon(r.method))
            }
            if (r.hasLimit) {
                Spacer(Modifier.height(14.dp))
                ThinBar(usedMs.toFloat() / allowed, color = if (over) C.Over else C.Amber)
            }
        }
    }
}

@Composable
private fun Badge(icon: ImageVector) {
    Box(Modifier.padding(start = 6.dp).size(30.dp).clip(CircleShape).background(C.Surface2), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = C.Muted, modifier = Modifier.size(16.dp))
    }
}

private fun methodIcon(m: Method) = when (m) {
    Method.BREATH -> Icons.Outlined.Air
    Method.WAIT -> Icons.Outlined.HourglassEmpty
    Method.CODE -> Icons.Outlined.Pin
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

// ───────────────────────────── Choisir une app ─────────────────────────────

private data class Launchable(val pkg: String, val label: String)

@Composable
fun PickScreen(go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val rules by Rules.rules.collectAsState()
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

    Column(Modifier.fillMaxSize().imePadding()) {
        Header("Ajouter une app") { go(Screen.Home) }
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Surface).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = C.Muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("TikTok, Instagram…", style = T.Body.copy(color = C.Muted))
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
                    val already = rules.any { it.pkg == app.pkg }
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (!already) Rules.upsert(AppRule(app.pkg, app.label, limitMin = 30))
                            go(Screen.Edit(app.pkg))
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

// ───────────────────────────── Réglage d'une app ─────────────────────────────

private val LIMITS = listOf(0, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240)

@Composable
fun EditScreen(pkg: String, go: (Screen) -> Unit) {
    val rules by Rules.rules.collectAsState()
    val rule = rules.firstOrNull { it.pkg == pkg } ?: run { LaunchedEffect(Unit) { go(Screen.Home) }; return }
    var settingCode by remember { mutableStateOf(false) }
    fun update(f: (AppRule) -> AppRule) = Rules.upsert(f(rule))

    if (settingCode) {
        NewCodeFlow("Code de ${rule.label}", onCancel = { settingCode = false }) { hash ->
            update { it.copy(codeHash = hash, method = Method.CODE) }
            settingCode = false
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Header(rule.label, icon = { AppIcon(rule.pkg, 30.dp) }) { go(Screen.Home) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {

            // Limite
            Section("Limite par jour") {
                Text(if (rule.hasLimit) formatDuration(rule.limitMin * 60_000L) else "Aucune", style = T.Hero.copy(fontSize = 44.sp))
                var idx by remember(pkg) { mutableFloatStateOf(LIMITS.indexOfClosest(rule.limitMin).toFloat()) }
                Slider(
                    value = idx,
                    onValueChange = { idx = it; update { r -> r.copy(limitMin = LIMITS[it.toInt()]) } },
                    valueRange = 0f..(LIMITS.size - 1).toFloat(),
                    steps = LIMITS.size - 2,
                    colors = SliderDefaults.colors(thumbColor = C.Amber, activeTrackColor = C.Amber, inactiveTrackColor = C.Surface2,
                        activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                )
                AnimatedVisibility(rule.hasLimit) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text("Quand c'est fini", style = T.Small)
                        Spacer(Modifier.height(8.dp))
                        Segmented(listOf("+5 min", "Bloqué jusqu'à demain"), if (rule.strict) 1 else 0) { i ->
                            update { it.copy(strict = i == 1) }
                        }
                        AnimatedVisibility(!rule.strict) {
                            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("+5 min autorisés par jour", style = T.Body, modifier = Modifier.weight(1f))
                                Stepper(rule.graceCount, 1..5) { n -> update { it.copy(graceCount = n) } }
                            }
                        }
                    }
                }
            }

            // Verrou
            Section(null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Verrouiller à l'ouverture", style = T.H2.copy(fontSize = 17.sp))
                        Text("Une épreuve à chaque fois que tu l'ouvres", style = T.Small)
                    }
                    Switch(rule.lockOnOpen, { v -> update { it.copy(lockOnOpen = v) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = C.Amber,
                            uncheckedThumbColor = C.Muted, uncheckedTrackColor = C.Surface2, uncheckedBorderColor = C.Line))
                }
            }

            // Méthode
            Section("Pour passer") {
                Segmented(Method.entries.map { it.label }, rule.method.ordinal) { i ->
                    val m = Method.entries[i]
                    if (m == Method.CODE && rule.codeHash == null) settingCode = true
                    else update { it.copy(method = m) }
                }
                Spacer(Modifier.height(14.dp))
                when (rule.method) {
                    Method.BREATH -> Text("3 respirations guidées, environ 30 s.", style = T.Small)
                    Method.WAIT -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Attendre", style = T.Body, modifier = Modifier.weight(1f))
                            Stepper(rule.waitSec, 10..120, step = 10, suffix = " s") { s -> update { it.copy(waitSec = s) } }
                        }
                    }
                    Method.CODE -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Code défini ✓", style = T.Body, modifier = Modifier.weight(1f))
                        Text("Changer", style = T.Body.copy(color = C.Amber), modifier = Modifier.clickable { settingCode = true }.padding(8.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    buildString {
                        append("Sert pour ")
                        append(listOfNotNull(if (rule.lockOnOpen) "ouvrir l'app" else null, if (rule.hasLimit && !rule.strict) "prendre +5 min" else null)
                            .joinToString(" et ").ifEmpty { "rien pour l'instant" })
                        append(".")
                    },
                    style = T.Small,
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("Retirer ${rule.label}", style = T.Body.copy(color = C.Over), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { Rules.remove(pkg); go(Screen.Home) }.padding(16.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
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
        Text("$value$suffix", style = T.H2, modifier = Modifier.width(64.dp), textAlign = TextAlign.Center)
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
fun StatsScreen(tick: Int, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val rules by Rules.rules.collectAsState()
    val week by produceState<List<Pair<LocalDate, Map<String, Long>>>?>(null, tick, rules) {
        value = withContext(Dispatchers.IO) { Usage.week(ctx, rules.map { it.pkg }.toSet()) }
    }
    Column(Modifier.fillMaxSize()) {
        Header("7 derniers jours") { go(Screen.Home) }
        val w = week
        if (w == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Glow(size = 90.dp) }; return@Column }
        val totals = w.map { it.second.values.sum() }
        val weekTotal = totals.sum()
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
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
                    Text(formatDuration(ms), style = T.Small)
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

    if (settingMaster) {
        NewCodeFlow("Code de Recul", onCancel = { settingMaster = false }) { h ->
            Rules.masterHash = h; master = true; settingMaster = false
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Header("Réglages") { go(Screen.Home) }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Section("Mise à jour") {
                Text("Version ${BuildConfig.VERSION_NAME}", style = T.H2)
                Spacer(Modifier.height(4.dp))
                Text(status ?: "Les nouvelles versions arrivent depuis GitHub.", style = T.Small)
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
                                .onSuccess { Updater.install(ctx, it); progress = null }
                                .onFailure { status = "Téléchargement raté"; progress = null }
                        }
                    }
                    else -> PillButton("Mettre à jour", Modifier.fillMaxWidth(), primary = false) {
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
            }

            Section("Installer un APK") {
                Text("Choisis un fichier Recul téléchargé : c'est Recul qui l'installe.", style = T.Small)
                Spacer(Modifier.height(12.dp))
                InstallApkButton("Choisir le fichier", primary = false)
            }

            Section("Accès") {
                AccessRow("Temps d'utilisation", hasUsage) {
                    ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                AccessRow("Bloqueur (Accessibilité)", hasA11y) {
                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
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

