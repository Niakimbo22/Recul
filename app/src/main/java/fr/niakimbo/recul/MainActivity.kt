package fr.niakimbo.recul

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.niakimbo.recul.ui.C
import fr.niakimbo.recul.ui.CodeEditScreen
import fr.niakimbo.recul.ui.EditScreen
import fr.niakimbo.recul.ui.Glow
import fr.niakimbo.recul.ui.PickScreen
import fr.niakimbo.recul.ui.PinPad
import fr.niakimbo.recul.ui.ReculTheme
import fr.niakimbo.recul.ui.SettingsScreen
import fr.niakimbo.recul.ui.Tab
import fr.niakimbo.recul.ui.TabsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed interface Screen {
    data class Tabs(val tab: Tab) : Screen
    data class Pick(val forCodes: Boolean) : Screen
    data class Edit(val pkg: String) : Screen
    data class CodeEdit(val pkg: String, val label: String) : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {

    /** Change à chaque retour dans l'app → les écrans revérifient permissions et temps. */
    private var resumeTick by mutableIntStateOf(0)
    private var unlocked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReculTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Tabs(Tab.Limits)) }
                var lastTab by remember { mutableStateOf(Tab.Limits) }
                val go: (Screen) -> Unit = { s -> if (s is Screen.Tabs) lastTab = s.tab; screen = s }
                BackHandler(screen !is Screen.Tabs) { screen = Screen.Tabs(lastTab) }
                Box(Modifier.fillMaxSize().background(C.Bg).safeDrawingPadding()) {
                    if (Rules.masterHash != null && !unlocked) {
                        MasterLock { unlocked = true }
                    } else {
                        AnimatedContent(
                            screen,
                            contentKey = { if (it is Screen.Tabs) "tabs" else it.toString() },
                            transitionSpec = {
                                val forward = targetState !is Screen.Tabs
                                (slideInHorizontally(tween(300)) { if (forward) it / 5 else -it / 5 } + fadeIn(tween(300))) togetherWith
                                    (slideOutHorizontally(tween(240)) { if (forward) -it / 5 else it / 5 } + fadeOut(tween(180)))
                            },
                            label = "nav",
                        ) { s ->
                            when (s) {
                                is Screen.Tabs -> TabsScreen(s.tab, resumeTick, go)
                                is Screen.Pick -> PickScreen(s.forCodes, go)
                                is Screen.Edit -> EditScreen(s.pkg, go)
                                is Screen.CodeEdit -> CodeEditScreen(s.pkg, s.label, go)
                                Screen.Settings -> SettingsScreen(resumeTick, go)
                            }
                        }
                    }
                }
                val scope = rememberCoroutineScope()
                androidx.compose.runtime.LaunchedEffect(resumeTick) {
                    // Mise à jour auto : vérifie au plus toutes les heures quand Recul est ouvert.
                    if (System.currentTimeMillis() - Rules.lastUpdateCheck > 3_600_000L) {
                        Rules.lastUpdateCheck = System.currentTimeMillis()
                        scope.launch(Dispatchers.IO) { AutoUpdate.run(applicationContext) }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        visible = true
        resumeTick++
        AutoUpdate.pendingConfirm?.let { AutoUpdate.pendingConfirm = null; runCatching { startActivity(it) } }
    }

    override fun onPause() {
        visible = false
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        unlocked = false
    }

    companion object {
        @Volatile var visible = false
    }
}

@Composable
private fun MasterLock(onOk: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        Glow(size = 120.dp)
        Spacer(Modifier.height(24.dp))
        PinPad("Code de Recul", onSubmit = { c -> Codes.check(c, Rules.masterHash).also { if (it) onOk() } })
    }
}
