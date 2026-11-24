package de.glanzstueck.app

import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.takahirom.roborazzi.captureRoboImage
import de.glanzstueck.app.ui.editor.EditorScreen
import de.glanzstueck.app.ui.editor.EditorSource
import de.glanzstueck.app.ui.editor.EditorViewModel
import de.glanzstueck.app.ui.editor.Tool
import de.glanzstueck.app.ui.home.HomeScreen
import de.glanzstueck.app.ui.intro.IntroScreen
import de.glanzstueck.app.ui.settings.SettingsScreen
import de.glanzstueck.app.ui.theme.GlanzTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Developer screenshots of the main screens (pass -Dglanz.testdata=dir). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class UiScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val dir = System.getProperty("glanz.testdata").orEmpty()
    private fun out(n: String) = File(dir, "ui/$n.png").absolutePath

    @Test fun home() {
        assumeTrue(dir.isNotEmpty())
        rule.setContent { GlanzTheme(dynamic = false) { HomeScreen({}, {}, {}, {}) } }
        rule.mainClock.advanceTimeBy(2000)
        rule.onRoot().captureRoboImage(out("home"))
    }

    @Test fun intro() {
        assumeTrue(dir.isNotEmpty())
        rule.mainClock.autoAdvance = false
        rule.setContent { GlanzTheme(dynamic = false) { IntroScreen {} } }
        rule.mainClock.advanceTimeBy(1300)
        rule.onRoot().captureRoboImage(out("intro"))
    }

    @Test fun settings() {
        assumeTrue(dir.isNotEmpty())
        rule.setContent { GlanzTheme(dynamic = false) { SettingsScreen {} } }
        rule.onRoot().captureRoboImage(out("settings"))
    }

    @Test fun editor() {
        assumeTrue(dir.isNotEmpty())
        var vmRef: EditorViewModel? = null
        rule.setContent {
            GlanzTheme(dynamic = false) {
                val vm: EditorViewModel = viewModel()
                vmRef = vm
                androidx.compose.runtime.LaunchedEffect(Unit) { vm.start(EditorSource.Image(Uri.fromFile(File(dir, "handbag.jpg")))) }
                EditorScreen(vm, {}, {})
            }
        }
        runCatching { rule.waitUntil(8_000) { vmRef?.ui?.value?.phase == de.glanzstueck.app.ui.editor.Phase.READY || vmRef?.ui?.value?.phase == de.glanzstueck.app.ui.editor.Phase.ERROR } }
        rule.mainClock.advanceTimeBy(1200)
        rule.onRoot().captureRoboImage(out("editor_scan"))
        rule.runOnIdle { vmRef!!.forceReadyForTest() }
        rule.mainClock.advanceTimeBy(3000)
        rule.onRoot().captureRoboImage(out("editor_bg"))
        for (t in listOf(Tool.SHADOW, Tool.TEXT, Tool.STICKER, Tool.ERASER)) {
            rule.runOnIdle { vmRef!!.selectTool(t) }
            rule.mainClock.advanceTimeBy(1500)
            rule.onRoot().captureRoboImage(out("editor_" + t.name.lowercase()))
        }
        println("editor message: " + vmRef?.ui?.value?.message + " status=" + vmRef?.ui?.value?.status)
    }
}
