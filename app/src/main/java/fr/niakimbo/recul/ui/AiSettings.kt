package fr.niakimbo.recul.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import fr.niakimbo.recul.Ai
import kotlinx.coroutines.launch

/** Clé Gemini (gratuite) : collée ici, reste sur le téléphone. */
@Composable
fun AiSettings() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(Ai.apiKey) }
    var status by remember { mutableStateOf<String?>(if (Ai.ready) "Clé enregistrée" else null) }

    Column {
        Text("Clé Gemini gratuite. Tes messages au « videur » partent chez Google.", style = T.Small)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Surface2).padding(horizontal = 16.dp, vertical = 14.dp)) {
            if (key.isEmpty()) Text("Colle ta clé ici", style = T.Body.copy(color = C.Muted))
            BasicTextField(
                key, { key = it.trim(); Ai.apiKey = key; status = null },
                singleLine = true, textStyle = T.Body, cursorBrush = SolidColor(C.Amber),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row {
            PillButton("Créer une clé", Modifier.weight(1f), primary = false) {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            Spacer(Modifier.width(10.dp))
            PillButton("Tester", Modifier.weight(1f), primary = false, enabled = key.isNotBlank()) {
                status = "Test…"
                scope.launch { status = Ai.test() }
            }
        }
        status?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = T.Small.copy(color = if (it.contains("✓") || it == "Clé enregistrée") C.Amber else C.Muted))
        }
    }
}

