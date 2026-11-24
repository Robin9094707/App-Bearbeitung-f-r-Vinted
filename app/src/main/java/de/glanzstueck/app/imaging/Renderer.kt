package de.glanzstueck.app.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Everything needed to draw a composition. Bitmaps are shared and never mutated by the renderer. */
class RenderInputs(
    val source: Bitmap,
    val cutout: Cutout?,
    val bgImage: Bitmap?,
) {
    @Volatile private var blurred: Pair<Float, Bitmap>? = null
    @Volatile private var shadowAlpha: Triple<Int, Float, ShadowBitmap>? = null

    fun blurredSource(amount: Float): Bitmap {
        blurred?.let { if (it.first == amount) return it.second }
        // Blur at small resolution: smoother, faster, upscaled with filtering.
        val small = MaskOps.scaleToLongSide(source, (420 - amount * 300).roundToInt().coerceAtLeast(64))
        val w = small.width; val h = small.height
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        val r = max(1, (amount * 14).roundToInt())
        val ch = Array(3) { c -> FloatArray(w * h) { ((px[it] shr (16 - 8 * c)) and 0xFF).toFloat() } }
        val tmp = FloatArray(w * h)
        val o = FloatArray(w * h)
        for (c in 0..2) {
            var a = ch[c]; var b = o.copyOf()
            repeat(3) { MaskOps.boxFilter(a, w, h, r, b, tmp); val t = a; a = b; b = t }
            ch[c] = a
        }
        for (i in px.indices) {
            px[i] = (0xFF shl 24) or (ch[0][i].toInt().coerceIn(0, 255) shl 16) or (ch[1][i].toInt().coerceIn(0, 255) shl 8) or ch[2][i].toInt().coerceIn(0, 255)
        }
        val out = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        blurred = amount to out
        return out
    }

    /** Blurred silhouette of the subject (small, padded). */
    fun shadowBitmap(softness: Float): ShadowBitmap? {
        val c = cutout ?: return null
        val key = System.identityHashCode(c.bitmap)
        shadowAlpha?.let { if (it.first == key && it.second == softness) return it.third }
        val maxSide = 360
        val f = min(1f, maxSide.toFloat() / max(c.bitmap.width, c.bitmap.height))
        val sw = max(1, (c.bitmap.width * f).roundToInt())
        val sh = max(1, (c.bitmap.height * f).roundToInt())
        val blurPx = (2 + softness * 34).roundToInt()
        val pad = blurPx * 2 + 2
        val w = sw + pad * 2; val h = sh + pad * 2
        val a8 = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val cv = Canvas(a8)
        cv.drawBitmap(c.bitmap, null, RectF(pad.toFloat(), pad.toFloat(), (pad + sw).toFloat(), (pad + sh).toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        val bytes = MaskOps.alphaBitmapToMask(a8)
        val blurredBytes = MaskOps.blurAlpha(bytes, w, h, blurPx)
        val out = MaskOps.maskToAlphaBitmap(blurredBytes, w, h)
        val sb = ShadowBitmap(out, pad.toFloat(), f)
        shadowAlpha = Triple(key, softness, sb)
        return sb
    }
}

class ShadowBitmap(val bitmap: Bitmap, val pad: Float, val scale: Float)

object Renderer {

    fun canvasSize(params: EditParams, srcW: Int, srcH: Int, longSide: Int): Pair<Int, Int> {
        val ratio = params.format.ratio ?: (srcW.toFloat() / srcH)
        return if (ratio >= 1f) longSide to max(1, (longSide / ratio).roundToInt())
        else max(1, (longSide * ratio).roundToInt()) to longSide
    }

    /** Matrix mapping source-image coordinates to canvas coordinates (cover fit, centred). */
    fun sourceToCanvas(srcW: Int, srcH: Int, cw: Int, ch: Int): Matrix {
        val s = max(cw.toFloat() / srcW, ch.toFloat() / srcH)
        return Matrix().apply {
            setScale(s, s)
            postTranslate((cw - srcW * s) / 2f, (ch - srcH * s) / 2f)
        }
    }

    /** Matrix mapping the cut-out bitmap (local coords) to the canvas. */
    fun subjectMatrix(params: EditParams, inputs: RenderInputs, cw: Int, ch: Int): Matrix {
        val src = inputs.source
        val m = Matrix()
        val cut = inputs.cutout
        if (params.sourcePlacement || cut == null) {
            if (cut != null) m.setTranslate(cut.box.left.toFloat(), cut.box.top.toFloat())
            m.postConcat(sourceToCanvas(src.width, src.height, cw, ch))
        } else {
            val bw = cut.box.width().toFloat(); val bh = cut.box.height().toFloat()
            val p = params.padding.coerceIn(0f, 0.4f)
            val availW = cw * (1 - 2 * p); val availH = ch * (1 - 2 * p)
            val s = min(availW / bw, availH / bh)
            m.setScale(s, s)
            m.postTranslate((cw - bw * s) / 2f, (ch - bh * s) / 2f)
        }
        // user transform around the subject centre
        val center = subjectCenter(m, inputs)
        m.postScale(params.subjectScale, params.subjectScale, center[0], center[1])
        m.postRotate(params.subjectRotation, center[0], center[1])
        m.postTranslate(params.subjectOffsetX * cw, params.subjectOffsetY * ch)
        return m
    }

    private fun subjectCenter(m: Matrix, inputs: RenderInputs): FloatArray {
        val cut = inputs.cutout
        val pts = if (cut != null) floatArrayOf(cut.bitmap.width / 2f, cut.bitmap.height / 2f)
        else floatArrayOf(inputs.source.width / 2f, inputs.source.height / 2f)
        m.mapPoints(pts)
        return pts
    }

    fun colorFilter(adjust: Adjust): ColorMatrixColorFilter? {
        if (adjust.isIdentity) return null
        val cm = ColorMatrix()
        // saturation
        cm.setSaturation(1f + adjust.saturation)
        // contrast + brightness
        val c = 1f + adjust.contrast * 0.6f
        val t = (1f - c) * 128f + adjust.brightness * 60f
        cm.postConcat(ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        )))
        // warmth
        val w = adjust.warmth * 18f
        cm.postConcat(ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, w,
            0f, 1f, 0f, 0f, w * 0.25f,
            0f, 0f, 1f, 0f, -w,
            0f, 0f, 0f, 1f, 0f,
        )))
        return ColorMatrixColorFilter(cm)
    }

    fun render(
        params: EditParams,
        inputs: RenderInputs,
        cw: Int,
        ch: Int,
        drawOverlays: Boolean,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888,
    ): Bitmap {
        val out = Bitmap.createBitmap(cw, ch, config)
        val canvas = Canvas(out)
        draw(canvas, params, inputs, cw, ch)
        if (drawOverlays) for (o in params.overlays) OverlayPainter.draw(canvas, o, cw, ch)
        return out
    }

    fun draw(canvas: Canvas, params: EditParams, inputs: RenderInputs, cw: Int, ch: Int) {
        val src = inputs.source
        val filter = colorFilter(params.adjust)
        val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply { colorFilter = filter }

        val cut = inputs.cutout
        if (!params.removeBg || cut == null) {
            canvas.drawColor(0xFFFFFFFF.toInt())
            val m = subjectMatrix(params.copy(removeBg = false), RenderInputs(src, null, null), cw, ch)
            canvas.drawBitmap(src, m, bmpPaint)
            return
        }

        drawBackground(canvas, params, inputs, cw, ch)

        val m = subjectMatrix(params, inputs, cw, ch)
        val bounds = RectF(0f, 0f, cut.bitmap.width.toFloat(), cut.bitmap.height.toFloat())
        val drawn = RectF(); m.mapRect(drawn, bounds)
        val unit = min(cw, ch).toFloat()
        val sh = params.shadow

        if (sh.type == ShadowType.FLOAT) {
            // lift the subject, keep the shadow on the "floor"
            val lift = unit * (0.02f + sh.distance * 0.06f)
            drawEllipseShadow(canvas, drawn.centerX(), drawn.bottom, drawn.width() * (0.30f + 0.15f * (1 - sh.distance)),
                sh.opacity * 0.8f, 0.10f + sh.softness * 0.08f)
            m.postTranslate(0f, -lift)
        } else {
            drawShadow(canvas, params, inputs, m, drawn, unit)
        }

        if (sh.type == ShadowType.REFLECTION) drawReflection(canvas, cut, m, drawn, sh, bmpPaint)
        canvas.drawBitmap(cut.bitmap, m, bmpPaint)
    }

    private fun drawBackground(canvas: Canvas, params: EditParams, inputs: RenderInputs, cw: Int, ch: Int) {
        val bg = params.background
        val full = RectF(0f, 0f, cw.toFloat(), ch.toFloat())
        when (bg.type) {
            BgType.TRANSPARENT -> canvas.drawColor(0, PorterDuff.Mode.CLEAR)
            BgType.COLOR -> canvas.drawColor(bg.color1)
            BgType.GRADIENT -> {
                val a = Math.toRadians(bg.angle.toDouble())
                val dx = cos(a).toFloat(); val dy = sin(a).toFloat()
                val half = (kotlin.math.abs(dx) * cw + kotlin.math.abs(dy) * ch) / 2f
                val cx = cw / 2f; val cy = ch / 2f
                val p = Paint().apply {
                    shader = LinearGradient(cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half, bg.color1, bg.color2, Shader.TileMode.CLAMP)
                    isDither = true
                }
                canvas.drawRect(full, p)
            }
            BgType.STUDIO -> {
                canvas.drawColor(bg.color2)
                val p = Paint().apply {
                    shader = RadialGradient(cw / 2f, ch * 0.42f, max(cw, ch) * 0.75f, intArrayOf(bg.color1, bg.color1, bg.color2), floatArrayOf(0f, 0.25f, 1f), Shader.TileMode.CLAMP)
                    isDither = true
                }
                canvas.drawRect(full, p)
            }
            BgType.BLUR -> {
                val b = inputs.blurredSource(bg.blur)
                val m = sourceToCanvas(b.width, b.height, cw, ch)
                canvas.drawBitmap(b, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
                if (bg.dim > 0f) canvas.drawColor(((bg.dim * 0.6f * 255).roundToInt() shl 24) or 0x000000)
            }
            BgType.IMAGE -> {
                canvas.drawColor(0xFFFFFFFF.toInt())
                val img = inputs.bgImage
                if (img != null) {
                    val m = sourceToCanvas(img.width, img.height, cw, ch)
                    canvas.drawBitmap(img, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
                    if (bg.dim > 0f) canvas.drawColor(((bg.dim * 0.6f * 255).roundToInt() shl 24) or 0x000000)
                }
            }
        }
    }

    private fun drawShadow(canvas: Canvas, params: EditParams, inputs: RenderInputs, m: Matrix, drawn: RectF, unit: Float) {
        val sh = params.shadow
        if (sh.opacity <= 0.001f) return
        when (sh.type) {
            ShadowType.NONE, ShadowType.REFLECTION, ShadowType.FLOAT -> {
                if (sh.type == ShadowType.REFLECTION) {
                    drawEllipseShadow(canvas, drawn.centerX(), drawn.bottom, drawn.width() * 0.45f, sh.opacity * 0.5f, 0.05f)
                }
            }
            ShadowType.SOFT -> {
                val s = inputs.shadowBitmap(sh.softness) ?: return
                val a = Math.toRadians(sh.angle.toDouble())
                val dist = unit * sh.distance * 0.05f
                val sm = Matrix()
                sm.setTranslate(-s.pad, -s.pad)
                sm.postScale(1f / s.scale, 1f / s.scale)
                sm.postConcat(m)
                sm.postTranslate(cos(a).toFloat() * dist, sin(a).toFloat() * dist)
                val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply { color = 0xFF000000.toInt(); alpha = (sh.opacity * 255).roundToInt().coerceIn(0, 255) }
                canvas.drawBitmap(s.bitmap, sm, p)
            }
            ShadowType.GROUND -> {
                drawEllipseShadow(canvas, drawn.centerX(), drawn.bottom - drawn.height() * 0.005f,
                    drawn.width() * (0.42f + sh.distance * 0.25f), sh.opacity, 0.06f + sh.softness * 0.10f)
                // tighter contact core
                drawEllipseShadow(canvas, drawn.centerX(), drawn.bottom - drawn.height() * 0.003f,
                    drawn.width() * 0.30f, sh.opacity * 0.7f, 0.035f)
            }
            ShadowType.CAST -> {
                val s = inputs.shadowBitmap(sh.softness * 0.6f) ?: return
                val cut = inputs.cutout ?: return
                val bh = cut.bitmap.height.toFloat()
                val k = 0.25f + sh.distance * 1.1f
                val flat = 0.32f
                val sign = if (cos(Math.toRadians(sh.angle.toDouble())) >= 0) 1f else -1f
                val sm = Matrix()
                sm.setTranslate(-s.pad, -s.pad)
                sm.postScale(1f / s.scale, 1f / s.scale)
                // project onto the floor: anchor at bottom, squash vertically and skew sideways
                val proj = Matrix()
                proj.setValues(floatArrayOf(
                    1f, -k * sign, k * sign * bh,
                    0f, flat, (1 - flat) * bh,
                    0f, 0f, 1f,
                ))
                sm.postConcat(proj)
                sm.postConcat(m)
                val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply { color = 0xFF000000.toInt(); alpha = (sh.opacity * 0.85f * 255).roundToInt().coerceIn(0, 255) }
                canvas.drawBitmap(s.bitmap, sm, p)
                drawEllipseShadow(canvas, drawn.centerX(), drawn.bottom, drawn.width() * 0.38f, sh.opacity * 0.6f, 0.04f)
            }
        }
    }

    private fun drawEllipseShadow(canvas: Canvas, cx: Float, cy: Float, rx: Float, opacity: Float, flatness: Float) {
        if (rx <= 1f || opacity <= 0f) return
        val ry = max(1f, rx * flatness * 2.2f)
        val a = (opacity * 255).roundToInt().coerceIn(0, 255)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        val shader = RadialGradient(0f, 0f, 1f,
            intArrayOf(a shl 24, ((a * 0.55f).roundToInt() shl 24), ((a * 0.18f).roundToInt() shl 24), 0),
            floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
        val sm = Matrix(); sm.setScale(rx, ry); sm.postTranslate(cx, cy)
        shader.setLocalMatrix(sm)
        p.shader = shader
        canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), p)
    }

    private fun drawReflection(canvas: Canvas, cut: Cutout, m: Matrix, drawn: RectF, sh: ShadowSpec, paint: Paint) {
        val h = drawn.height()
        val gap = h * 0.01f
        val layerRect = RectF(0f, drawn.bottom, canvas.width.toFloat(), min(canvas.height.toFloat(), drawn.bottom + h * 0.6f + gap))
        if (layerRect.height() <= 1f) return
        val save = canvas.saveLayer(layerRect, null)
        val rm = Matrix(m)
        rm.postScale(1f, -1f, 0f, drawn.bottom + gap / 2f)
        val p = Paint(paint).apply { alpha = (sh.opacity * 0.9f * 255).roundToInt().coerceIn(0, 255) }
        canvas.drawBitmap(cut.bitmap, rm, p)
        val fade = Paint().apply {
            shader = LinearGradient(0f, drawn.bottom, 0f, drawn.bottom + h * (0.15f + 0.45f * sh.distance),
                0xFF000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawRect(layerRect, fade)
        canvas.restoreToCount(save)
    }
}
