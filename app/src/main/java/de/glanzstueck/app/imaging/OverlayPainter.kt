package de.glanzstueck.app.imaging

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Draws text / badge overlays. Shared by the live preview and the export so both look identical. */
object OverlayPainter {

    fun typeface(font: OverlayFont): Typeface = when (font) {
        OverlayFont.MODERN -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        OverlayFont.BOLD -> Typeface.create("sans-serif-black", Typeface.NORMAL)
        OverlayFont.ELEGANT -> Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        OverlayFont.SCRIPT -> Typeface.create("cursive", Typeface.BOLD)
        OverlayFont.PLAYFUL -> Typeface.create("casual", Typeface.BOLD)
        OverlayFont.CONDENSED -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
        OverlayFont.MONO -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private class Laid(val layout: StaticLayout, val box: RectF, val padX: Float, val padY: Float)

    private fun layout(o: Overlay, cw: Int): Laid {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = typeface(o.font)
            textSize = max(4f, o.size * cw)
            color = o.color
        }
        val text = o.text.ifEmpty { " " }
        var maxLine = 0f
        for (line in text.split('\n')) maxLine = max(maxLine, tp.measureText(line))
        val width = max(1, kotlin.math.ceil(maxLine).toInt() + 2)
        val sl = StaticLayout.Builder.obtain(text, 0, text.length, tp, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()
        val ts = tp.textSize
        val (padX, padY) = when (o.style) {
            OverlayStyle.PLAIN, OverlayStyle.SHADOW -> 0f to 0f
            OverlayStyle.PILL, OverlayStyle.OUTLINE -> ts * 0.7f to ts * 0.35f
            OverlayStyle.RIBBON -> ts * 1.0f to ts * 0.35f
            OverlayStyle.BURST, OverlayStyle.CIRCLE -> ts * 0.6f to ts * 0.6f
        }
        var w = sl.width + padX * 2
        var h = sl.height + padY * 2
        if (o.style == OverlayStyle.BURST || o.style == OverlayStyle.CIRCLE) {
            val d = max(w, h) * 1.12f
            w = d; h = d
        }
        return Laid(sl, RectF(-w / 2, -h / 2, w / 2, h / 2), padX, padY)
    }

    /** Unrotated bounds centred at the overlay position, in canvas pixels. */
    fun bounds(o: Overlay, cw: Int, ch: Int): RectF {
        val l = layout(o, cw)
        return RectF(l.box).apply { offset(o.x * cw, o.y * ch) }
    }

    fun hitTest(o: Overlay, cw: Int, ch: Int, px: Float, py: Float, slop: Float): Boolean {
        val cx = o.x * cw; val cy = o.y * ch
        val a = -o.rotation * PI / 180.0
        val dx = px - cx; val dy = py - cy
        val rx = (dx * cos(a) - dy * sin(a)).toFloat()
        val ry = (dx * sin(a) + dy * cos(a)).toFloat()
        val b = layout(o, cw).box
        return rx >= b.left - slop && rx <= b.right + slop && ry >= b.top - slop && ry <= b.bottom + slop
    }

    fun draw(canvas: Canvas, o: Overlay, cw: Int, ch: Int, selected: Boolean = false, selectionColor: Int = 0xFF6B4EFF.toInt(), density: Float = 1f) {
        val l = layout(o, cw)
        val ts = l.layout.paint.textSize
        canvas.save()
        canvas.translate(o.x * cw, o.y * ch)
        canvas.rotate(o.rotation)
        val box = l.box
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = o.accent }
        when (o.style) {
            OverlayStyle.PLAIN -> {}
            OverlayStyle.SHADOW -> l.layout.paint.setShadowLayer(ts * 0.12f, 0f, ts * 0.05f, 0x99000000.toInt())
            OverlayStyle.PILL -> {
                val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0; setShadowLayer(ts * 0.25f, 0f, ts * 0.08f, 0x33000000) }
                shadow.color = o.accent
                canvas.drawRoundRect(box, box.height() / 2, box.height() / 2, shadow)
            }
            OverlayStyle.OUTLINE -> {
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ts * 0.09f; color = o.color }
                val r = RectF(box); r.inset(stroke.strokeWidth / 2, stroke.strokeWidth / 2)
                canvas.drawRoundRect(r, r.height() / 2, r.height() / 2, stroke)
            }
            OverlayStyle.BURST -> {
                fill.setShadowLayer(ts * 0.25f, 0f, ts * 0.08f, 0x33000000)
                canvas.drawPath(burstPath(box.width() / 2, 14, 0.86f), fill)
            }
            OverlayStyle.CIRCLE -> {
                fill.setShadowLayer(ts * 0.25f, 0f, ts * 0.08f, 0x33000000)
                canvas.drawCircle(0f, 0f, box.width() / 2, fill)
            }
            OverlayStyle.RIBBON -> {
                fill.setShadowLayer(ts * 0.2f, 0f, ts * 0.06f, 0x33000000)
                val notch = box.height() * 0.35f
                val p = Path().apply {
                    moveTo(box.left, box.top)
                    lineTo(box.right, box.top)
                    lineTo(box.right - notch, box.centerY())
                    lineTo(box.right, box.bottom)
                    lineTo(box.left, box.bottom)
                    lineTo(box.left + notch, box.centerY())
                    close()
                }
                canvas.drawPath(p, fill)
            }
        }
        canvas.translate(-l.layout.width / 2f, -l.layout.height / 2f)
        l.layout.draw(canvas)
        canvas.restore()

        if (selected) {
            canvas.save()
            canvas.translate(o.x * cw, o.y * ch)
            canvas.rotate(o.rotation)
            val sel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f * density
                color = selectionColor
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f * density, 6f * density), 0f)
            }
            val r = RectF(box); r.inset(-8f * density, -8f * density)
            canvas.drawRoundRect(r, 12f * density, 12f * density, sel)
            canvas.restore()
        }
    }

    private fun burstPath(radius: Float, points: Int, inner: Float): Path {
        val p = Path()
        val n = points * 2
        for (i in 0 until n) {
            val r = if (i % 2 == 0) radius else radius * inner
            val a = (i.toDouble() / n) * 2 * PI - PI / 2
            val x = (cos(a) * r).toFloat(); val y = (sin(a) * r).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return p
    }
}
