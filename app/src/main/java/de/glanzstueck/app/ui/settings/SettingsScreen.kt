package de.glanzstueck.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.glanzstueck.app.BuildConfig
import de.glanzstueck.app.data.AppSettings
import de.glanzstueck.app.data.ExportFormat
import de.glanzstueck.app.data.SaveTarget
import de.glanzstueck.app.data.ThemeMode
import de.glanzstueck.app.glanz
import de.glanzstueck.app.ml.LocalModel
import de.glanzstueck.app.ml.ModelState
import de.glanzstueck.app.ui.editor.ModelDownloadCard
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val g = ctx.glanz
    val s by g.settings.settings.collectAsState(initial = AppSettings())
    val models by g.models.states.collectAsState()
    val scope = rememberCoroutineScope()
    fun set(f: (AppSettings) -> AppSettings) { scope.launch { g.settings.update(f) } }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Einstellungen") },
                subtitle = { Text("Alles läuft lokal auf deinem Handy") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") } },
                scrollBehavior = scroll,
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Group("Speichern") {
                Label("Standard beim Tippen auf „Speichern“")
                Chips(SaveTarget.entries, s.saveTarget, { it.label }) { v -> set { it.copy(saveTarget = v) } }
                Label("Dateiformat")
                Chips(ExportFormat.entries, s.exportFormat, { it.label }) { v -> set { it.copy(exportFormat = v) } }
                Hint("Automatisch: JPEG für Verkaufsfotos, PNG bei transparentem Hintergrund.")
                Label("Bildgröße (lange Seite)")
                Chips(listOf(1600, 2048, 2560, 3072), s.exportSize, { "$it px" }) { v -> set { it.copy(exportSize = v) } }
                Hint("Vinted zeigt Fotos bis ca. 1600 px. Größer = mehr Reserve beim Zoomen.")
                Toggle("Letzten Look merken", "Neue Fotos bekommen automatisch den gleichen Hintergrund, Schatten & Format – perfekt für einheitliche Inserate.", s.rememberStyle) { v -> set { it.copy(rememberStyle = v) } }
            }
            Group("KI-Modelle (offline)") {
                Hint("Das Standard-Freistellen (Google ML Kit) ist sofort dabei. Diese Modelle sind optional und arbeiten komplett ohne Internet, sobald sie geladen sind.")
                LocalModel.entries.forEach { m ->
                    ModelDownloadCard(m, models[m]) { g.models.download(m) }
                    if (models[m] == ModelState.Installed) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (m == LocalModel.HD_CUTOUT) {
                                Toggle("HD-Modell automatisch verwenden", null, s.preferHd, Modifier.weight(1f)) { v -> set { it.copy(preferHd = v) } }
                            } else Spacer(Modifier.weight(1f))
                            TextButton(onClick = { g.models.delete(m) }) { Icon(Icons.Rounded.Delete, null); Text("Löschen") }
                        }
                    } else if (models[m] is ModelState.Downloading) {
                        TextButton(onClick = { g.models.cancel(m) }) { Text("Abbrechen") }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            Group("Darstellung") {
                Label("Design")
                Chips(ThemeMode.entries, s.themeMode, { it.label }) { v -> set { it.copy(themeMode = v) } }
                Toggle("Farben vom Hintergrundbild (Material You)", null, s.dynamicColor) { v -> set { it.copy(dynamicColor = v) } }
                Toggle("Start-Animation", null, s.showIntro) { v -> set { it.copy(showIntro = v) } }
                Toggle("Haptisches Feedback", null, s.haptics) { v -> set { it.copy(haptics = v) } }
            }
            Group("Über") {
                Text("Glanzstück ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)
                Hint("Deine Fotos verlassen nie das Handy. Freistellen, Radieren und Speichern passiert vollständig lokal.")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable private fun Label(t: String) = Text(t, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
@Composable private fun Hint(t: String) = Text(t, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun <T> Chips(values: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { v -> FilterChip(selected = v == selected, onClick = { onSelect(v) }, label = { Text(label(v)) }) }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String?, checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Hint(subtitle)
        }
        Switch(checked, onChange)
    }
}
