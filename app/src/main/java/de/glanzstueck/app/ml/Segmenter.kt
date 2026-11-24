package de.glanzstueck.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import de.glanzstueck.app.imaging.MaskOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Low resolution soft mask (0..1) as produced by a network. */
class LowMask(val data: FloatArray, val width: Int, val height: Int)

enum class SegEngine { STANDARD, HD }

object OnnxSessions {
    private val sessions = mutableMapOf<LocalModel, OrtSession>()
    val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    @Synchronized
    fun get(manager: ModelManager, model: LocalModel): OrtSession {
        sessions[model]?.let { return it }
        val opts = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(max(2, min(6, Runtime.getRuntime().availableProcessors())))
            // lower peak memory on phones
            setCPUArenaAllocator(false)
            setMemoryPatternOptimization(false)
        }
        val s = env.createSession(manager.file(model).absolutePath, opts)
        sessions[model] = s
        return s
    }

    @Synchronized
    fun release(model: LocalModel) {
        sessions.remove(model)?.close()
    }

    @Synchronized
    fun releaseAll() {
        sessions.values.forEach { runCatching { it.close() } }
        sessions.clear()
    }
}

class Segmenter(private val context: Context, private val models: ModelManager) {

    /** Long side used for the network pass; the guided filter brings the mask back to full resolution. */
    private val lowSide = 1024

    suspend fun segment(full: Bitmap, engine: SegEngine, onStatus: (String) -> Unit = {}): ByteArray = withContext(Dispatchers.Default) {
        val low = when {
            engine == SegEngine.HD && models.isInstalled(LocalModel.HD_CUTOUT) -> {
                onStatus("HD-KI analysiert Kanten…")
                runHdModel(full)
            }
            else -> {
                onStatus("Motiv wird erkannt…")
                runMlKit(full, onStatus)
            }
        }
        MaskOps.keepMainComponents(low.data, low.width, low.height)
        onStatus("Kanten werden geschärft…")
        MaskOps.guidedUpsample(low.data, low.width, low.height, full)
    }

    private suspend fun runMlKit(full: Bitmap, onStatus: (String) -> Unit): LowMask {
        val small = MaskOps.scaleToLongSide(full, lowSide)
        val options = SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build()
        val client = SubjectSegmentation.getClient(options)
        try {
            val moduleClient = ModuleInstall.getClient(context)
            val available = runCatching { moduleClient.areModulesAvailable(client).await().areModulesAvailable() }.getOrDefault(true)
            if (!available) {
                onStatus("KI-Modul wird einmalig eingerichtet…")
                runCatching {
                    withTimeout(120_000) {
                        moduleClient.installModules(ModuleInstallRequest.newBuilder().addApi(client).build()).await()
                    }
                }
            }
            var lastError: Throwable? = null
            repeat(20) { attempt ->
                try {
                    val result = withTimeout(90_000) { client.process(InputImage.fromBitmap(small, 0)).await() }
                    val buf: FloatBuffer = result.foregroundConfidenceMask ?: error("Kein Motiv gefunden")
                    buf.rewind()
                    val arr = FloatArray(small.width * small.height)
                    buf.get(arr)
                    return LowMask(arr, small.width, small.height)
                } catch (t: Throwable) {
                    lastError = t
                    val msg = t.message.orEmpty().lowercase()
                    if (!(msg.contains("download") || msg.contains("waiting") || msg.contains("module"))) throw t
                    if (attempt == 0) onStatus("KI-Modul wird geladen…")
                    kotlinx.coroutines.delay(1500)
                }
            }
            throw lastError ?: IllegalStateException("Segmentierung fehlgeschlagen")
        } finally {
            client.close()
            if (small !== full) small.recycle()
        }
    }

    private fun runHdModel(full: Bitmap): LowMask {
        val size = 1024
        val session = OnnxSessions.get(models, LocalModel.HD_CUTOUT)
        val scaled = Bitmap.createScaledBitmap(full, size, size, true)
        val px = IntArray(size * size)
        scaled.getPixels(px, 0, size, 0, 0, size, size)
        if (scaled !== full) scaled.recycle()
        val fb = FloatBuffer.allocate(3 * size * size)
        val plane = size * size
        for (i in 0 until plane) {
            val c = px[i]
            fb.put(i, ((c shr 16) and 0xFF) / 255f - 0.5f)
            fb.put(plane + i, ((c shr 8) and 0xFF) / 255f - 0.5f)
            fb.put(2 * plane + i, (c and 0xFF) / 255f - 0.5f)
        }
        val env = OnnxSessions.env
        val input = OnnxTensor.createTensor(env, fb, longArrayOf(1, 3, size.toLong(), size.toLong()))
        val outSquare = FloatArray(plane)
        input.use {
            session.run(mapOf(session.inputNames.first() to input)).use { res ->
                val t = res[0] as OnnxTensor
                val ob = t.floatBuffer
                var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
                for (i in 0 until plane) { val v = ob.get(i); if (v < lo) lo = v; if (v > hi) hi = v }
                val range = if (hi - lo > 1e-6f) hi - lo else 1f
                for (i in 0 until plane) outSquare[i] = (ob.get(i) - lo) / range
            }
        }
        // resample square output back to the aspect-correct low resolution
        val l = max(full.width, full.height)
        val lw = max(1, (full.width * lowSide.toFloat() / l).roundToInt())
        val lh = max(1, (full.height * lowSide.toFloat() / l).roundToInt())
        val out = FloatArray(lw * lh)
        for (y in 0 until lh) {
            val fy = ((y + 0.5f) * size / lh - 0.5f).coerceIn(0f, (size - 1).toFloat())
            val y0 = fy.toInt(); val y1 = min(y0 + 1, size - 1); val wy = fy - y0
            for (x in 0 until lw) {
                val fx = ((x + 0.5f) * size / lw - 0.5f).coerceIn(0f, (size - 1).toFloat())
                val x0 = fx.toInt(); val x1 = min(x0 + 1, size - 1); val wx = fx - x0
                out[y * lw + x] = (outSquare[y0 * size + x0] * (1 - wx) + outSquare[y0 * size + x1] * wx) * (1 - wy) +
                    (outSquare[y1 * size + x0] * (1 - wx) + outSquare[y1 * size + x1] * wx) * wy
            }
        }
        return LowMask(out, lw, lh)
    }
}
