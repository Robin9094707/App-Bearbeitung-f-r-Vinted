package de.glanzstueck.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.glanzstueck.app.data.AppSettings
import de.glanzstueck.app.ui.batch.BatchScreen
import de.glanzstueck.app.ui.batch.BatchViewModel
import de.glanzstueck.app.ui.editor.EditorScreen
import de.glanzstueck.app.ui.editor.EditorSource
import de.glanzstueck.app.ui.editor.EditorViewModel
import de.glanzstueck.app.ui.home.HomeScreen
import de.glanzstueck.app.ui.intro.IntroScreen
import de.glanzstueck.app.ui.settings.SettingsScreen
import de.glanzstueck.app.ui.theme.GlanzTheme
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface Screen {
    data object Intro : Screen
    data object Home : Screen
    data class Editor(val source: EditorSource) : Screen
    data object Settings : Screen
    data class Batch(val uris: List<Uri>) : Screen
}

/** A back-stack entry with its own ViewModel scope (cleared when the screen is left). */
class Entry(val screen: Screen) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    val id = System.nanoTime()
}

class MainActivity : ComponentActivity() {
    private val incoming = MutableStateFlow<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splash.setOnExitAnimationListener { provider ->
                val icon: View = provider.iconView
                val set = AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.6f),
                        ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.6f),
                        ObjectAnimator.ofFloat(provider.view, View.ALPHA, 1f, 0f),
                    )
                    interpolator = AnticipateInterpolator(1.2f)
                    duration = 380
                }
                set.doOnEndCompat { provider.remove() }
                set.start()
            }
        }

        handleIntent(intent)
        val app = glanz
        setContent {
            val settings by app.settings.settings.collectAsState(initial = null)
            val s = settings ?: AppSettings()
            GlanzTheme(mode = s.themeMode, dynamic = s.dynamicColor) {
                if (settings != null) AppRoot(s, incoming)
                else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intentStream(intent))
            Intent.ACTION_SEND_MULTIPLE -> intentStreams(intent)
            Intent.ACTION_EDIT -> listOfNotNull(intent.data)
            else -> emptyList()
        }
        if (uris.isNotEmpty()) incoming.value = uris
    }

    @Suppress("DEPRECATION")
    private fun intentStream(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun intentStreams(i: Intent): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableArrayListExtra(Intent.EXTRA_STREAM)) ?: emptyList()
}

private fun AnimatorSet.doOnEndCompat(action: () -> Unit) {
    addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) = action()
    })
}

@Composable
private fun AppRoot(settings: AppSettings, incoming: MutableStateFlow<List<Uri>>) {
    val stack = remember { mutableStateListOf(Entry(if (settings.showIntro) Screen.Intro else Screen.Home)) }
    var forward by remember { mutableStateOf(true) }
    val shared by incoming.collectAsState()

    fun push(s: Screen) { forward = true; stack.add(Entry(s)) }
    fun pop() {
        if (stack.size <= 1) return
        forward = false
        val e = stack.removeAt(stack.lastIndex)
        e.viewModelStore.clear()
    }
    fun replaceTop(s: Screen) {
        forward = true
        val e = stack.removeAt(stack.lastIndex)
        e.viewModelStore.clear()
        stack.add(Entry(s))
    }

    // Images shared from other apps (e.g. "Teilen → Glanzstück" in the gallery)
    androidx.compose.runtime.LaunchedEffect(shared) {
        if (shared.isNotEmpty()) {
            val uris = shared
            incoming.value = emptyList()
            while (stack.size > 1) pop()
            if (stack.last().screen == Screen.Intro) replaceTop(Screen.Home)
            if (uris.size == 1) push(Screen.Editor(EditorSource.Image(uris[0]))) else push(Screen.Batch(uris))
        }
    }

    DisposableEffect(Unit) { onDispose { stack.forEach { it.viewModelStore.clear() } } }

    val top = stack.last()
    AnimatedContent(
        targetState = top,
        transitionSpec = {
            if (initialState.screen == Screen.Intro) {
                ContentTransform(fadeIn(tween(450)) + scaleIn(initialScale = 1.08f, animationSpec = tween(600)), fadeOut(tween(300)))
            } else if (forward) {
                (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 380f)) { it / 3 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(spring(stiffness = 380f)) { -it / 6 } + fadeOut(tween(180)) + scaleOut(targetScale = 0.96f))
            } else {
                (slideInHorizontally(spring(stiffness = 380f)) { -it / 6 } + fadeIn(tween(220)) + scaleIn(initialScale = 0.96f)) togetherWith
                    (slideOutHorizontally(spring(dampingRatio = 0.85f, stiffness = 380f)) { it / 3 } + fadeOut(tween(180)))
            }
        },
        contentKey = { it.id },
        label = "nav",
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
    ) { entry ->
        CompositionLocalProvider(LocalViewModelStoreOwner provides entry) {
            when (val s = entry.screen) {
                Screen.Intro -> IntroScreen(onFinished = { if (stack.last() === entry) replaceTop(Screen.Home) })
                Screen.Home -> HomeScreen(
                    onOpenImage = { push(Screen.Editor(EditorSource.Image(it))) },
                    onOpenProject = { push(Screen.Editor(EditorSource.Project(it))) },
                    onBatch = { push(Screen.Batch(it)) },
                    onSettings = { push(Screen.Settings) },
                )
                is Screen.Editor -> {
                    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as GlanzApp
                    val vm: EditorViewModel = viewModel(factory = viewModelFactory { initializer { EditorViewModel(app) } })
                    androidx.compose.runtime.LaunchedEffect(Unit) { vm.start(s.source) }
                    EditorScreen(vm, onBack = { pop() }, onNewPhoto = { pop() })
                }
                Screen.Settings -> SettingsScreen(onBack = { pop() })
                is Screen.Batch -> {
                    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as GlanzApp
                    val vm: BatchViewModel = viewModel(factory = viewModelFactory { initializer { BatchViewModel(app) } })
                    androidx.compose.runtime.LaunchedEffect(Unit) { vm.init(s.uris) }
                    BatchScreen(vm, onBack = { pop() })
                }
            }
        }
    }
}
