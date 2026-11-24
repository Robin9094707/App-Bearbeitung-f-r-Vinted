package de.glanzstueck.app.ml

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class LocalModel(
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val title: String,
    val description: String,
) {
    HD_CUTOUT(
        "rmbg14.onnx",
        "https://huggingface.co/briaai/RMBG-1.4/resolve/main/onnx/model.onnx",
        176_153_355L,
        "HD-Freisteller (RMBG)",
        "Auf Produktfotos trainiert: besonders saubere Kanten bei Kleidung, Taschen & Schuhen. Läuft komplett auf dem Handy.",
    ),
    LAMA(
        "lama_fp32.onnx",
        "https://huggingface.co/Carve/LaMa-ONNX/resolve/main/lama_fp32.onnx",
        208_044_816L,
        "KI-Radierer (LaMa)",
        "Entfernt Gegenstände sauber und füllt den Untergrund realistisch auf.",
    ),
}

sealed interface ModelState {
    data object NotInstalled : ModelState
    data class Downloading(val progress: Float) : ModelState
    data object Installed : ModelState
    data class Failed(val message: String) : ModelState
}

/** Downloads and stores optional on-device neural network models. */
class ModelManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<LocalModel, Job>()
    private val _states = MutableStateFlow(LocalModel.entries.associateWith { stateOf(it) })
    val states: StateFlow<Map<LocalModel, ModelState>> = _states.asStateFlow()

    private val dir get() = File(context.filesDir, "models").apply { mkdirs() }

    fun file(model: LocalModel) = File(dir, model.fileName)

    fun isInstalled(model: LocalModel) = file(model).let { it.exists() && it.length() == model.sizeBytes }

    private fun stateOf(model: LocalModel): ModelState = if (isInstalled(model)) ModelState.Installed else ModelState.NotInstalled

    private fun set(model: LocalModel, s: ModelState) = _states.update { it + (model to s) }

    fun download(model: LocalModel) {
        if (jobs[model]?.isActive == true || isInstalled(model)) return
        jobs[model] = scope.launch {
            set(model, ModelState.Downloading(0f))
            val part = File(dir, model.fileName + ".part")
            try {
                var url = URL(model.url)
                var conn: HttpURLConnection
                var redirects = 0
                val existing = if (part.exists()) part.length() else 0L
                while (true) {
                    conn = url.openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = false
                    conn.connectTimeout = 20_000
                    conn.readTimeout = 30_000
                    conn.setRequestProperty("User-Agent", "Glanzstueck-Android")
                    if (existing > 0) conn.setRequestProperty("Range", "bytes=$existing-")
                    val code = conn.responseCode
                    if (code in 300..399) {
                        val loc = conn.getHeaderField("Location") ?: error("Weiterleitung ohne Ziel")
                        url = URL(url, loc)
                        conn.disconnect()
                        if (++redirects > 8) error("Zu viele Weiterleitungen")
                        continue
                    }
                    if (code !in 200..299) error("Server antwortet mit $code")
                    break
                }
                val append = conn.responseCode == 206
                var done = if (append) existing else 0L
                conn.inputStream.use { input ->
                    java.io.FileOutputStream(part, append).use { out ->
                        val buf = ByteArray(1 shl 16)
                        var last = 0L
                        while (isActive) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (done - last > 512 * 1024) {
                                last = done
                                set(model, ModelState.Downloading((done.toFloat() / model.sizeBytes).coerceIn(0f, 1f)))
                            }
                        }
                    }
                }
                conn.disconnect()
                if (!isActive) return@launch
                if (part.length() != model.sizeBytes) {
                    part.delete()
                    error("Download unvollständig – bitte erneut versuchen")
                }
                part.renameTo(file(model))
                set(model, ModelState.Installed)
            } catch (t: Throwable) {
                set(model, ModelState.Failed(t.message ?: "Download fehlgeschlagen"))
            }
        }
    }

    fun cancel(model: LocalModel) {
        jobs[model]?.cancel()
        File(dir, model.fileName + ".part").delete()
        set(model, stateOf(model))
    }

    fun delete(model: LocalModel) {
        cancel(model)
        file(model).delete()
        OnnxSessions.release(model)
        set(model, ModelState.NotInstalled)
    }
}
