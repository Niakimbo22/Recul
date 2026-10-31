package fr.niakimbo.recul.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Noir profond + un seul accent ambre. */
object C {
    val Bg = Color(0xFF0B0B0C)
    val Surface = Color(0xFF161618)
    val Surface2 = Color(0xFF1F1F22)
    val Line = Color(0xFF2A2A2E)
    val Text = Color(0xFFF4F1EC)
    val Muted = Color(0xFF8C8A86)
    val Amber = Color(0xFFFFB547)
    val AmberDim = Color(0x33FFB547)
    val Over = Color(0xFFFF6B5A)
}

object T {
    val Hero = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp, color = C.Text)
    val Title = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, color = C.Text)
    val H2 = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = C.Text)
    val Body = TextStyle(fontSize = 15.sp, color = C.Text)
    val Small = TextStyle(fontSize = 13.sp, color = C.Muted)
    val Label = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp, color = C.Muted)
}

@Composable
fun ReculTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Amber, onPrimary = Color.Black, background = C.Bg, surface = C.Surface,
            onSurface = C.Text, onBackground = C.Text, surfaceVariant = C.Surface2,
            onSurfaceVariant = C.Muted, outline = C.Line,
        ),
        content = content,
    )
}
