package de.glanzstueck.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.glanzstueck.app.imaging.EditParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

enum class SaveTarget(val label: String) { GALLERY("Galerie"), APP("Nur in der App"), BOTH("Galerie + App") }
enum class ExportFormat(val label: String) { AUTO("Automatisch"), JPEG("JPEG"), PNG("PNG") }
enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Hell"), DARK("Dunkel") }

data class AppSettings(
    val saveTarget: SaveTarget = SaveTarget.GALLERY,
    val exportFormat: ExportFormat = ExportFormat.AUTO,
    val exportSize: Int = 2560,
    val preferHd: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val showIntro: Boolean = true,
    val rememberStyle: Boolean = true,
    val haptics: Boolean = true,
)

private val Context.dataStore by preferencesDataStore("settings")

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

class SettingsRepository(private val context: Context) {
    private object K {
        val save = stringPreferencesKey("save_target")
        val format = stringPreferencesKey("export_format")
        val size = intPreferencesKey("export_size")
        val hd = booleanPreferencesKey("prefer_hd")
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic")
        val intro = booleanPreferencesKey("intro")
        val remember = booleanPreferencesKey("remember_style")
        val haptics = booleanPreferencesKey("haptics")
        val style = stringPreferencesKey("last_style")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        saveTarget = this[K.save]?.let { runCatching { SaveTarget.valueOf(it) }.getOrNull() } ?: SaveTarget.GALLERY,
        exportFormat = this[K.format]?.let { runCatching { ExportFormat.valueOf(it) }.getOrNull() } ?: ExportFormat.AUTO,
        exportSize = this[K.size] ?: 2560,
        preferHd = this[K.hd] ?: true,
        themeMode = this[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        dynamicColor = this[K.dynamic] ?: true,
        showIntro = this[K.intro] ?: true,
        rememberStyle = this[K.remember] ?: true,
        haptics = this[K.haptics] ?: true,
    )

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.save] = s.saveTarget.name
            p[K.format] = s.exportFormat.name
            p[K.size] = s.exportSize
            p[K.hd] = s.preferHd
            p[K.theme] = s.themeMode.name
            p[K.dynamic] = s.dynamicColor
            p[K.intro] = s.showIntro
            p[K.remember] = s.rememberStyle
            p[K.haptics] = s.haptics
        }
    }

    /** Last used look (background, shadow, format) so a whole series of photos matches. */
    suspend fun lastStyle(): EditParams? {
        val raw = context.dataStore.data.first()[K.style] ?: return null
        return runCatching { AppJson.decodeFromString(EditParams.serializer(), raw) }.getOrNull()
    }

    suspend fun saveStyle(p: EditParams) {
        val style = EditParams(
            background = if (p.background.type == de.glanzstueck.app.imaging.BgType.IMAGE) EditParams().background else p.background,
            shadow = p.shadow, format = p.format, padding = p.padding, edge = p.edge,
        )
        context.dataStore.edit { it[K.style] = AppJson.encodeToString(EditParams.serializer(), style) }
    }
}
