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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.niakimbo.recul.ui.C
import fr.niakimbo.recul.ui.EditScreen
import fr.niakimbo.recul.ui.Glow
import fr.niakimbo.recul.ui.HomeScreen
import fr.niakimbo.recul.ui.PickScreen
import fr.niakimbo.recul.ui.PinPad
import fr.niakimbo.recul.ui.ReculTheme
import fr.niakimbo.recul.ui.SettingsScreen
import fr.niakimbo.recul.ui.StatsScreen

sealed interface Screen {
    data object Home : Screen
    data object Pick : Screen
    data class Edit(val pkg: String) : Screen
    data object Stats : Screen
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
                var screen by androidx.compose.runtime.remember { mutableStateOf<Screen>(Screen.Home) }
                val go: (Screen) -> Unit = { screen = it }
                BackHandler(screen != Screen.Home) {
                    screen = when (val s = screen) {
                        is Screen.Edit -> Screen.Home
                        else -> Screen.Home.also { if (s == Screen.Home) finish() }
                    }
                }
                Box(Modifier.fillMaxSize().background(C.Bg).safeDrawingPadding()) {
                    if (Rules.masterHash != null && !unlocked) {
                        MasterLock { unlocked = true }
                    } else {
                        AnimatedContent(
                            screen,
                            transitionSpec = {
                                val forward = targetState != Screen.Home
                                (slideInHorizontally(tween(320)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(320))) togetherWith
                                    (slideOutHorizontally(tween(260)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(200)))
                            },
                            label = "nav",
                        ) { s ->
                            when (s) {
                                Screen.Home -> HomeScreen(resumeTick, go)
                                Screen.Pick -> PickScreen(go)
                                is Screen.Edit -> EditScreen(s.pkg, go)
                                Screen.Stats -> StatsScreen(resumeTick, go)
                                Screen.Settings -> SettingsScreen(resumeTick, go)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    override fun onStop() {
        super.onStop()
        unlocked = false
    }
}

@androidx.compose.runtime.Composable
private fun MasterLock(onOk: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        Glow(size = 120.dp)
        Spacer(Modifier.height(24.dp))
        PinPad("Code de Recul", onSubmit = { c -> Codes.check(c, Rules.masterHash).also { if (it) onOk() } })
    }
}
