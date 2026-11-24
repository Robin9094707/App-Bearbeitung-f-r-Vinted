package de.glanzstueck.app

import android.app.Application
import de.glanzstueck.app.data.ProjectStore
import de.glanzstueck.app.data.SettingsRepository
import de.glanzstueck.app.ml.Inpainter
import de.glanzstueck.app.ml.ModelManager
import de.glanzstueck.app.ml.Segmenter

class GlanzApp : Application() {
    lateinit var settings: SettingsRepository; private set
    lateinit var models: ModelManager; private set
    lateinit var projects: ProjectStore; private set
    lateinit var segmenter: Segmenter; private set
    lateinit var inpainter: Inpainter; private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        models = ModelManager(this)
        projects = ProjectStore(this)
        segmenter = Segmenter(this, models)
        inpainter = Inpainter(models)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) de.glanzstueck.app.ml.OnnxSessions.releaseAll()
    }
}

val android.content.Context.glanz: GlanzApp get() = applicationContext as GlanzApp
