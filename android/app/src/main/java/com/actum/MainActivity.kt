package com.actum

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.actum.ui.home.HomeScreen
import androidx.compose.ui.graphics.toArgb
import com.actum.ui.theme.ActumTheme
import com.actum.ui.theme.Ink
import com.actum.ui.theme.Look
import com.actum.model.GemmaModel
import kotlinx.coroutines.launch
import com.actum.model.GemmaRouter
import com.actum.ui.gate.ModelGateScreen

class MainActivity : ComponentActivity() {

    /** The model check, in place of the home screen. */
    private var measuring by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // must run before super.onCreate() so the splash theme is swapped out
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Edge to edge; the bars' icons are set with the look, below.
        enableEdgeToEdge()
        val gate = (application as ActumApp).modelGate
        setContent {
            // Ink or paper, as the person chose; the masthead's sun or moon switches it.
            ActumTheme {
                val dark = Look.dark
                LaunchedEffect(dark) { dressWindow(dark) }
                if (measuring) {
                    BackHandler { measuring = false }
                    val state by gate.state.collectAsStateWithLifecycle()
                    ModelGateScreen(state, onMeasure = gate::measure)
                } else {
                    // A long press on the wordmark opens the model check, in debug builds only.
                    HomeScreen(onOpenDebug = if (BuildConfig.DEBUG) ({ measuring = true }) else null)
                }
            }
        }
        if (savedInstanceState == null) measureIfAsked(intent)
    }

    /**
     * The window under the app, and the system bars' icons over it: light
     * icons on ink, dark ones on paper. The window's own colour too, so
     * nothing of the other look flashes as the keyboard resizes it.
     */
    private fun dressWindow(dark: Boolean) {
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        window.setBackgroundDrawable(ColorDrawable(Ink.toArgb()))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        measureIfAsked(intent)
    }

    /**
     * `adb shell am start -n com.actum.app/com.actum.MainActivity --es measure cpu` (or gpu, or eval for the test set).
     * `--es model gemma3-1b` (or gemma4-e2b, gemma3-270m) chooses the model first, and the choice is kept.
     */
    private fun measureIfAsked(intent: Intent?) {
        GemmaModel.Kind.byId(intent?.getStringExtra(EXTRA_MODEL))?.let { (application as ActumApp).gemma.choose(it) }
        // `--es lessons dump`: what the rules could learn, to `adb logcat -s ActumLessons`, for whoever writes the next rules.
        if (intent?.getStringExtra(EXTRA_LESSONS) == "dump") {
            val app = application as ActumApp
            app.applicationScope.launch {
                val lessons = app.database.lessonDao().list()
                lessons.forEach { android.util.Log.i(LESSONS_TAG, "${it.kind}\t${it.intent}\t${it.words}") }
                android.util.Log.i(LESSONS_TAG, "${lessons.size} lessons")
            }
        }
        val asked = intent?.getStringExtra(EXTRA_MEASURE) ?: return
        if (asked == "eval") {
            measuring = true
            (application as ActumApp).modelGate.evaluate()
            return
        }
        val processor = GemmaRouter.Processor.entries.firstOrNull { it.name.equals(asked, ignoreCase = true) } ?: return
        measuring = true
        (application as ActumApp).modelGate.measure(processor)
    }

    private companion object {
        const val EXTRA_MEASURE = "measure"
        const val EXTRA_MODEL = "model"
        const val EXTRA_LESSONS = "lessons"
        const val LESSONS_TAG = "ActumLessons"
    }
}
