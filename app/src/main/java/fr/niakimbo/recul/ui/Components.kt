package fr.niakimbo.recul.ui

import android.content.pm.PackageManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val iconCache = HashMap<String, ImageBitmap?>()

@Composable
fun AppIcon(pkg: String, size: Dp = 44.dp) {
    val ctx = LocalContext.current
    val bmp = remember(pkg) {
        iconCache.getOrPut(pkg) {
            try {
                ctx.packageManager.getApplicationIcon(pkg).toBitmap(144, 144).asImageBitmap()
            } catch (_: PackageManager.NameNotFoundException) { null }
        }
    }
    if (bmp != null) Image(bmp, null, Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)))
    else Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(C.Surface2))
}

@Composable
fun Panel(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(C.Surface)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(20.dp)
    ) { content() }
}

@Composable
fun PillButton(text: String, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = when { !enabled -> C.Surface2; primary -> C.Amber; else -> C.Surface2 }
    val fg = when { !enabled -> C.Muted; primary -> Color.Black; else -> C.Text }
    Box(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun ThinBar(progress: Float, modifier: Modifier = Modifier, color: Color = C.Amber) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "bar")
    Box(modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(C.Surface2)) {
        Box(Modifier.fillMaxWidth(p).height(5.dp).clip(CircleShape).background(color))
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Surface2).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (on) C.Amber else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) Color.Black else C.Text, fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

/** Halo ambre qui respire doucement (décor). */
@Composable
fun Glow(modifier: Modifier = Modifier, size: Dp = 220.dp) {
    val t = rememberInfiniteTransition(label = "glow")
    val s by t.animateFloat(0.86f, 1f, infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
    Canvas(modifier.size(size).scale(s)) {
        drawCircle(Brush.radialGradient(listOf(C.Amber.copy(alpha = 0.28f), Color.Transparent)))
        drawCircle(C.Amber.copy(alpha = 0.55f), radius = this.size.minDimension * 0.18f)
    }
}

/** Exercice de respiration : [cycles] × (inspire 4 s, expire 5 s). */
@Composable
fun BreathExercise(cycles: Int = 3, onDone: () -> Unit) {
    val scale = remember { Animatable(0.45f) }
    var phase by remember { mutableStateOf("Prêt ?") }
    var left by remember { mutableIntStateOf(cycles) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        delay(600)
        repeat(cycles) {
            phase = "Inspire"; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scale.animateTo(1f, tween(4000, easing = FastOutSlowInEasing))
            phase = "Expire"; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scale.animateTo(0.45f, tween(5000, easing = FastOutSlowInEasing))
            left--
        }
        phase = "C'est bon"
        onDone()
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                drawCircle(C.Line, r, style = Stroke(1.5.dp.toPx()))
                drawCircle(Brush.radialGradient(listOf(C.Amber.copy(alpha = 0.35f), Color.Transparent), radius = r * scale.value * 1.2f), r * scale.value * 1.2f)
                drawCircle(C.Amber.copy(alpha = 0.9f), r * scale.value * 0.62f)
            }
            Text(phase, color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
        Text(if (left > 0) "$left respiration${if (left > 1) "s" else ""}" else " ", style = T.Small)
    }
}

/** Compte à rebours en anneau. */
@Composable
fun WaitRing(seconds: Int, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    var remaining by remember { mutableIntStateOf(seconds) }
    LaunchedEffect(Unit) {
        launch { progress.animateTo(1f, tween(seconds * 1000, easing = LinearEasing)) }
        while (remaining > 0) { delay(1000); remaining-- }
        onDone()
    }
    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val w = 6.dp.toPx()
            drawArc(C.Surface2, 0f, 360f, false, style = Stroke(w))
            drawArc(C.Amber, -90f, 360f * progress.value, false, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$remaining", style = T.Hero)
            Text("secondes", style = T.Small)
        }
    }
}

/** Pavé de code. [onSubmit] renvoie false si le code est faux (secousse). */
@Composable
fun PinPad(title: String, onSubmit: (String) -> Boolean, minLength: Int = 4, maxLength: Int = 8) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    fun submit() {
        if (code.length < minLength) return
        if (onSubmit(code)) { code = ""; error = false }
        else {
            error = true; code = ""
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch {
                shake.snapTo(0f)
                shake.animateTo(0f, spring(dampingRatio = 0.2f, stiffness = 1200f), initialVelocity = 2400f)
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (error) "Raté, réessaie" else title, style = T.Body.copy(color = if (error) C.Over else C.Text), textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.offset { IntOffset(shake.value.toInt(), 0) }.height(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            val n = maxOf(minLength, code.length)
            repeat(n) { i ->
                val filled = i < code.length
                Box(Modifier.size(14.dp).clip(CircleShape)
                    .background(if (filled) C.Amber else Color.Transparent)
                    .border(1.5.dp, if (filled) C.Amber else C.Line, CircleShape))
            }
        }
        Spacer(Modifier.height(28.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                row.forEach { k ->
                    val isOk = k == "OK"
                    val okReady = code.length >= minLength
                    Box(
                        Modifier.size(74.dp).clip(CircleShape)
                            .background(if (isOk && okReady) C.Amber else if (k == "⌫") Color.Transparent else C.Surface)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.material3.ripple(),
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                when (k) {
                                    "⌫" -> code = code.dropLast(1)
                                    "OK" -> submit()
                                    else -> if (code.length < maxLength) { code += k; error = false }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (k) {
                            "⌫" -> Icon(Icons.AutoMirrored.Outlined.Backspace, null, tint = C.Muted)
                            "OK" -> Text("OK", color = if (okReady) Color.Black else C.Muted, fontWeight = FontWeight.SemiBold)
                            else -> Text(k, color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Light)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
fun Dot(color: Color = C.Amber) = Box(Modifier.size(6.dp).clip(CircleShape).background(color))
