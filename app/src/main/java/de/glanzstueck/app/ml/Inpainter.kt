package de.glanzstueck.app.ml

import ai.onnxruntime.OnnxTensor
import android.graphics.Bitmap
import android.graphics.Rect
import de.glanzstueck.app.imaging.MaskOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Object eraser: LaMa neural inpainting if installed, otherwise a smooth built-in fill. */
class Inpainter(private val models: ModelManager) {

    val hasAi get() = models.isInstalled(LocalModel.LAMA)

    suspend fun inpaint(src: Bitmap, hole: ByteArray): Bitmap = withContext(Dispatchers.Default) {
        val w = src.width; val h = src.height
        val box = MaskOps.bbox(hole, w, h, 0) ?: return@withContext src
        val bw = box.width(); val bh = box.height()
        val long = max(w, h)
        val dil = max(3, (long * 0.004f).roundToInt())

        val side = max(max(bw, bh) * 2.2f, 320f).roundToInt()
        val cw = min(w, side); val ch = min(h, side)
        val cx = box.centerX(); val cy = box.centerY()
        val left = (cx - cw / 2).coerceIn(0, w - cw)
        val top = (cy - ch / 2).coerceIn(0, h - ch)
        val crop = Rect(left, top, left + cw, top + ch)

        // local hole mask, dilated a little to catch outlines / shadows of the removed object
        val local = ByteArray(cw * ch)
        for (y in 0 until ch) System.arraycopy(hole, (top + y) * w + left, local, y * cw, cw)
        for (i in local.indices) local[i] = if ((local[i].toInt() and 0xFF) > 20) 0xFF.toByte() else 0
        val dilated = MaskOps.applyEdge(local, cw, ch, de.glanzstueck.app.imaging.EdgeSpec(feather = 0f, shift = dil.toFloat(), decontaminate = false))

        val cropBmp = Bitmap.createBitmap(src, crop.left, crop.top, cw, ch)
        val filled: Bitmap = if (hasAi) runLama(cropBmp, dilated, cw, ch) else pushPullFill(cropBmp, dilated, cw, ch)

        // feathered composite back into a copy of the full image
        val alpha = MaskOps.blurBytes(dilated, cw, ch, 1.5f)
        val orig = IntArray(cw * ch); cropBmp.getPixels(orig, 0, cw, 0, 0, cw, ch)
        val fill = IntArray(cw * ch); filled.getPixels(fill, 0, cw, 0, 0, cw, ch)
        for (i in orig.indices) {
            val a = (alpha[i].toInt() and 0xFF) / 255f
            if (a <= 0f) continue
            val o = orig[i]; val f = fill[i]
            val r = (((o shr 16) and 0xFF) * (1 - a) + ((f shr 16) and 0xFF) * a).roundToInt()
            val g = (((o shr 8) and 0xFF) * (1 - a) + ((f shr 8) and 0xFF) * a).roundToInt()
            val b = ((o and 0xFF) * (1 - a) + (f and 0xFF) * a).roundToInt()
            orig[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        out.setPixels(orig, 0, cw, crop.left, crop.top, cw, ch)
        out
    }

    private fun runLama(crop: Bitmap, mask: ByteArray, cw: Int, ch: Int): Bitmap {
        val s = 512
        val session = OnnxSessions.get(models, LocalModel.LAMA)
        val scaled = Bitmap.createScaledBitmap(crop, s, s, true)
        val px = IntArray(s * s); scaled.getPixels(px, 0, s, 0, 0, s, s)
        val plane = s * s
        val img = FloatBuffer.allocate(3 * plane)
        for (i in 0 until plane) {
            val c = px[i]
            img.put(i, ((c shr 16) and 0xFF) / 255f)
            img.put(plane + i, ((c shr 8) and 0xFF) / 255f)
            img.put(2 * plane + i, (c and 0xFF) / 255f)
        }
        // nearest-ish downscale of the mask with max pooling so thin strokes survive
        val mb = FloatBuffer.allocate(plane)
        for (y in 0 until s) {
            val y0 = y * ch / s; val y1 = max(y0 + 1, (y + 1) * ch / s)
            for (x in 0 until s) {
                val x0 = x * cw / s; val x1 = max(x0 + 1, (x + 1) * cw / s)
                var v = 0f
                loop@ for (yy in y0 until min(y1, ch)) for (xx in x0 until min(x1, cw)) {
                    if ((mask[yy * cw + xx].toInt() and 0xFF) > 127) { v = 1f; break@loop }
                }
                mb.put(y * s + x, v)
            }
        }
        val env = OnnxSessions.env
        val names = session.inputNames.toList()
        val imgName = names.firstOrNull { it.contains("image") } ?: names[0]
        val maskName = names.firstOrNull { it.contains("mask") } ?: names[1]
        val out = IntArray(plane)
        OnnxTensor.createTensor(env, img, longArrayOf(1, 3, s.toLong(), s.toLong())).use { ti ->
            OnnxTensor.createTensor(env, mb, longArrayOf(1, 1, s.toLong(), s.toLong())).use { tm ->
                session.run(mapOf(imgName to ti, maskName to tm)).use { res ->
                    val ob = (res[0] as OnnxTensor).floatBuffer
                    // output range is 0..255 for this export; detect 0..1 just in case
                    var maxV = 0f
                    for (i in 0 until min(plane, 4096)) maxV = max(maxV, ob.get(i))
                    val k = if (maxV <= 1.5f) 255f else 1f
                    for (i in 0 until plane) {
                        val r = (ob.get(i) * k).roundToInt().coerceIn(0, 255)
                        val g = (ob.get(plane + i) * k).roundToInt().coerceIn(0, 255)
                        val b = (ob.get(2 * plane + i) * k).roundToInt().coerceIn(0, 255)
                        out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
            }
        }
        val small = Bitmap.createBitmap(out, s, s, Bitmap.Config.ARGB_8888)
        return Bitmap.createScaledBitmap(small, cw, ch, true)
    }

    private fun pushPullFill(crop: Bitmap, mask: ByteArray, cw: Int, ch: Int): Bitmap {
        val px = IntArray(cw * ch); crop.getPixels(px, 0, cw, 0, 0, cw, ch)
        val rgb = FloatArray(cw * ch * 3)
        val wt = FloatArray(cw * ch)
        for (i in px.indices) {
            val c = px[i]
            rgb[i * 3] = ((c shr 16) and 0xFF).toFloat(); rgb[i * 3 + 1] = ((c shr 8) and 0xFF).toFloat(); rgb[i * 3 + 2] = (c and 0xFF).toFloat()
            wt[i] = if ((mask[i].toInt() and 0xFF) > 127) 0f else 1f
        }
        MaskOps.pushPull(rgb, wt, cw, ch)
        // add a touch of grain so the filled area does not look plastic
        val rnd = java.util.Random(7)
        for (i in px.indices) {
            if ((mask[i].toInt() and 0xFF) <= 127) continue
            val n = (rnd.nextGaussian() * 2.2).toFloat()
            val r = (rgb[i * 3] + n).roundToInt().coerceIn(0, 255)
            val g = (rgb[i * 3 + 1] + n).roundToInt().coerceIn(0, 255)
            val b = (rgb[i * 3 + 2] + n).roundToInt().coerceIn(0, 255)
            px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(px, cw, ch, Bitmap.Config.ARGB_8888)
    }
}
