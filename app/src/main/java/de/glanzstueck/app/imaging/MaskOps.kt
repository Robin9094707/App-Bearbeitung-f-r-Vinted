package de.glanzstueck.app.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pixel-level helpers for alpha masks. All masks are ByteArrays (0..255, unsigned) in row-major order.
 */
object MaskOps {

    fun scaleToLongSide(src: Bitmap, longSide: Int): Bitmap {
        val l = max(src.width, src.height)
        if (l <= longSide) return src
        val s = longSide.toFloat() / l
        return Bitmap.createScaledBitmap(src, max(1, (src.width * s).roundToInt()), max(1, (src.height * s).roundToInt()), true)
    }

    /** Separable mean filter with clamped borders, radius r. Writes result into [dst] (may equal src only via tmp). */
    fun boxFilter(src: FloatArray, w: Int, h: Int, r: Int, dst: FloatArray, tmp: FloatArray) {
        // horizontal
        for (y in 0 until h) {
            val row = y * w
            var sum = 0f
            var cnt = 0
            for (x in 0..min(r, w - 1)) { sum += src[row + x]; cnt++ }
            for (x in 0 until w) {
                tmp[row + x] = sum / cnt
                val add = x + r + 1
                if (add < w) { sum += src[row + add]; cnt++ }
                val rem = x - r
                if (rem >= 0) { sum -= src[row + rem]; cnt-- }
            }
        }
        // vertical
        for (x in 0 until w) {
            var sum = 0f
            var cnt = 0
            for (y in 0..min(r, h - 1)) { sum += tmp[y * w + x]; cnt++ }
            for (y in 0 until h) {
                dst[y * w + x] = sum / cnt
                val add = y + r + 1
                if (add < h) { sum += tmp[add * w + x]; cnt++ }
                val rem = y - r
                if (rem >= 0) { sum -= tmp[rem * w + x]; cnt-- }
            }
        }
    }

    /**
     * Colour guided filter computed at low resolution, coefficients upsampled and applied at full resolution
     * ("fast guided filter"). Produces a crisp full-resolution alpha mask that follows real image edges.
     *
     * @param lowMask mask in 0..1 at size lw x lh
     * @param full full resolution guide image
     */
    fun guidedUpsample(lowMask: FloatArray, lw: Int, lh: Int, full: Bitmap, radius: Int = 3, eps: Float = 1.2e-4f): ByteArray {
        val n = lw * lh
        // Tighten the network's uncertain "haze": low confidence goes to 0, high to 1.
        // Real edge softness is recovered from the photo by the guided filter below.
        for (i in 0 until n) {
            val t = ((lowMask[i] - 0.25f) / 0.5f).coerceIn(0f, 1f)
            lowMask[i] = t * t * (3f - 2f * t)
        }
        val guideLow = Bitmap.createScaledBitmap(full, lw, lh, true)
        val px = IntArray(n)
        guideLow.getPixels(px, 0, lw, 0, 0, lw, lh)
        if (guideLow !== full) guideLow.recycle()

        val ir = FloatArray(n); val ig = FloatArray(n); val ib = FloatArray(n)
        for (i in 0 until n) {
            val c = px[i]
            ir[i] = ((c shr 16) and 0xFF) / 255f
            ig[i] = ((c shr 8) and 0xFF) / 255f
            ib[i] = (c and 0xFF) / 255f
        }
        val tmp = FloatArray(n)
        val buf = FloatArray(n)
        fun mean(a: FloatArray): FloatArray { val o = FloatArray(n); boxFilter(a, lw, lh, radius, o, tmp); return o }
        fun meanOfProduct(a: FloatArray, b: FloatArray): FloatArray {
            for (i in 0 until n) buf[i] = a[i] * b[i]
            val o = FloatArray(n); boxFilter(buf, lw, lh, radius, o, tmp); return o
        }

        val mR = mean(ir); val mG = mean(ig); val mB = mean(ib)
        val mP = mean(lowMask)
        val cRP = meanOfProduct(ir, lowMask); val cGP = meanOfProduct(ig, lowMask); val cBP = meanOfProduct(ib, lowMask)
        val vRR = meanOfProduct(ir, ir); val vRG = meanOfProduct(ir, ig); val vRB = meanOfProduct(ir, ib)
        val vGG = meanOfProduct(ig, ig); val vGB = meanOfProduct(ig, ib); val vBB = meanOfProduct(ib, ib)

        val aR = FloatArray(n); val aG = FloatArray(n); val aB = FloatArray(n); val bb = FloatArray(n)
        for (i in 0 until n) {
            val mr = mR[i]; val mg = mG[i]; val mb = mB[i]; val mp = mP[i]
            val covR = cRP[i] - mr * mp
            val covG = cGP[i] - mg * mp
            val covB = cBP[i] - mb * mp
            val rr = vRR[i] - mr * mr + eps
            val rg = vRG[i] - mr * mg
            val rb = vRB[i] - mr * mb
            val gg = vGG[i] - mg * mg + eps
            val gb = vGB[i] - mg * mb
            val b2 = vBB[i] - mb * mb + eps
            // inverse of symmetric 3x3
            val i00 = gg * b2 - gb * gb
            val i01 = gb * rb - rg * b2
            val i02 = rg * gb - gg * rb
            val i11 = rr * b2 - rb * rb
            val i12 = rb * rg - rr * gb
            val i22 = rr * gg - rg * rg
            val det = rr * i00 + rg * i01 + rb * i02
            if (det == 0f) {
                aR[i] = 0f; aG[i] = 0f; aB[i] = 0f; bb[i] = mp
                continue
            }
            val inv = 1f / det
            val ar = (i00 * covR + i01 * covG + i02 * covB) * inv
            val ag = (i01 * covR + i11 * covG + i12 * covB) * inv
            val ab = (i02 * covR + i12 * covG + i22 * covB) * inv
            aR[i] = ar; aG[i] = ag; aB[i] = ab
            bb[i] = mp - ar * mr - ag * mg - ab * mb
        }
        val maR = mean(aR); val maG = mean(aG); val maB = mean(aB); val mbb = mean(bb)

        // Uncertainty band: only pixels near an edge use the guided result.
        val band = BooleanArray(n)
        run {
            val mn = FloatArray(n); val mx = FloatArray(n)
            val rr = radius + 2
            // separable min / max
            val t1 = FloatArray(n); val t2 = FloatArray(n)
            for (y in 0 until lh) for (x in 0 until lw) {
                var lo = 1f; var hi = 0f
                for (k in max(0, x - rr)..min(lw - 1, x + rr)) { val v = lowMask[y * lw + k]; if (v < lo) lo = v; if (v > hi) hi = v }
                t1[y * lw + x] = lo; t2[y * lw + x] = hi
            }
            for (y in 0 until lh) for (x in 0 until lw) {
                var lo = 1f; var hi = 0f
                for (k in max(0, y - rr)..min(lh - 1, y + rr)) { val a = t1[k * lw + x]; val b = t2[k * lw + x]; if (a < lo) lo = a; if (b > hi) hi = b }
                mn[y * lw + x] = lo; mx[y * lw + x] = hi
            }
            for (i in 0 until n) band[i] = mx[i] - mn[i] > 0.04f
        }

        val fw = full.width; val fh = full.height
        val out = ByteArray(fw * fh)
        val row = IntArray(fw)
        val sx = lw.toFloat() / fw
        val sy = lh.toFloat() / fh
        for (y in 0 until fh) {
            full.getPixels(row, 0, fw, 0, y, fw, 1)
            val fy = ((y + 0.5f) * sy - 0.5f).coerceIn(0f, (lh - 1).toFloat())
            val y0 = fy.toInt(); val y1 = min(y0 + 1, lh - 1); val wy = fy - y0
            for (x in 0 until fw) {
                val fx = ((x + 0.5f) * sx - 0.5f).coerceIn(0f, (lw - 1).toFloat())
                val x0 = fx.toInt(); val x1 = min(x0 + 1, lw - 1); val wx = fx - x0
                val i00 = y0 * lw + x0; val i01 = y0 * lw + x1; val i10 = y1 * lw + x0; val i11 = y1 * lw + x1
                val w00 = (1 - wx) * (1 - wy); val w01 = wx * (1 - wy); val w10 = (1 - wx) * wy; val w11 = wx * wy
                val nearest = if (wy < 0.5f) (if (wx < 0.5f) i00 else i01) else (if (wx < 0.5f) i10 else i11)
                val p = lowMask[i00] * w00 + lowMask[i01] * w01 + lowMask[i10] * w10 + lowMask[i11] * w11
                val v = if (band[nearest] || band[i00] || band[i11]) {
                    val c = row[x]
                    val r = ((c shr 16) and 0xFF) / 255f
                    val g = ((c shr 8) and 0xFF) / 255f
                    val b = (c and 0xFF) / 255f
                    val q = (maR[i00] * w00 + maR[i01] * w01 + maR[i10] * w10 + maR[i11] * w11) * r +
                        (maG[i00] * w00 + maG[i01] * w01 + maG[i10] * w10 + maG[i11] * w11) * g +
                        (maB[i00] * w00 + maB[i01] * w01 + maB[i10] * w10 + maB[i11] * w11) * b +
                        (mbb[i00] * w00 + mbb[i01] * w01 + mbb[i10] * w10 + mbb[i11] * w11)
                    // slight contrast tightening to remove haze but keep soft edges
                    ((q - 0.05f) / 0.9f)
                } else p
                out[y * fw + x] = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
            }
        }
        return out
    }

    /**
     * Removes detached blobs (e.g. a pattern on the carpet) that are much smaller than the main subject.
     * Works in place on a low resolution soft mask. Pairs of shoes etc. survive because both parts are large.
     */
    fun keepMainComponents(mask: FloatArray, w: Int, h: Int, minRatio: Float = 0.2f) {
        val n = w * h
        val label = IntArray(n)
        val sizes = ArrayList<Int>()
        sizes.add(0)
        val stack = IntArray(n)
        var next = 1
        for (start in 0 until n) {
            if (label[start] != 0 || mask[start] < 0.2f) continue
            var sp = 0
            stack[sp++] = start
            label[start] = next
            var count = 0
            while (sp > 0) {
                val i = stack[--sp]
                count++
                val x = i % w; val y = i / w
                if (x > 0) { val j = i - 1; if (label[j] == 0 && mask[j] >= 0.2f) { label[j] = next; stack[sp++] = j } }
                if (x < w - 1) { val j = i + 1; if (label[j] == 0 && mask[j] >= 0.2f) { label[j] = next; stack[sp++] = j } }
                if (y > 0) { val j = i - w; if (label[j] == 0 && mask[j] >= 0.2f) { label[j] = next; stack[sp++] = j } }
                if (y < h - 1) { val j = i + w; if (label[j] == 0 && mask[j] >= 0.2f) { label[j] = next; stack[sp++] = j } }
            }
            sizes.add(count)
            next++
        }
        if (next <= 2) return
        val largest = sizes.max()
        val keep = BooleanArray(next) { it == 0 || sizes[it] >= largest * minRatio }
        for (i in 0 until n) {
            val l = label[i]
            if (l != 0 && !keep[l]) mask[i] = 0f
        }
    }

    /** Morphological erode (negative) / dilate (positive) followed by optional feather. */
    fun applyEdge(mask: ByteArray, w: Int, h: Int, edge: EdgeSpec): ByteArray {
        var m = mask
        val shift = edge.shift.roundToInt()
        if (shift != 0) m = minMax(m, w, h, kotlin.math.abs(shift), dilate = shift > 0)
        if (edge.feather >= 0.25f) m = blurBytes(m, w, h, edge.feather)
        return m
    }

    private fun minMax(src: ByteArray, w: Int, h: Int, r: Int, dilate: Boolean): ByteArray {
        val tmp = ByteArray(w * h)
        val out = ByteArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var v = if (dilate) 0 else 255
                for (k in max(0, x - r)..min(w - 1, x + r)) {
                    val s = src[row + k].toInt() and 0xFF
                    v = if (dilate) max(v, s) else min(v, s)
                }
                tmp[row + x] = v.toByte()
            }
        }
        for (x in 0 until w) {
            for (y in 0 until h) {
                var v = if (dilate) 0 else 255
                for (k in max(0, y - r)..min(h - 1, y + r)) {
                    val s = tmp[k * w + x].toInt() and 0xFF
                    v = if (dilate) max(v, s) else min(v, s)
                }
                out[y * w + x] = v.toByte()
            }
        }
        return out
    }

    /** Approximate gaussian blur of a byte mask (3 box passes). */
    fun blurBytes(src: ByteArray, w: Int, h: Int, sigma: Float): ByteArray {
        val n = w * h
        val f = FloatArray(n) { (src[it].toInt() and 0xFF).toFloat() }
        val tmp = FloatArray(n)
        val o = FloatArray(n)
        val r = max(1, (sigma * 0.9f).roundToInt())
        var a = f; var b = o
        repeat(if (sigma < 1.2f) 1 else 3) {
            boxFilter(a, w, h, r, b, tmp)
            val t = a; a = b; b = t
        }
        return ByteArray(n) { (a[it] + 0.5f).toInt().coerceIn(0, 255).toByte() }
    }

    /**
     * Push-pull hole filling. [rgb] holds 3 floats per pixel, [weight] 0..1 confidence (0 = unknown).
     * Unknown pixels are filled smoothly from their surroundings. Works in place.
     */
    fun pushPull(rgb: FloatArray, weight: FloatArray, w: Int, h: Int) {
        if (w <= 1 && h <= 1) return
        val cw = (w + 1) / 2
        val ch = (h + 1) / 2
        val crgb = FloatArray(cw * ch * 3)
        val cwt = FloatArray(cw * ch)
        for (cy in 0 until ch) for (cx in 0 until cw) {
            var sr = 0f; var sg = 0f; var sb = 0f; var sw = 0f
            for (dy in 0..1) for (dx in 0..1) {
                val x = cx * 2 + dx; val y = cy * 2 + dy
                if (x < w && y < h) {
                    val i = y * w + x
                    val wt = weight[i]
                    if (wt > 0f) {
                        sr += rgb[i * 3] * wt; sg += rgb[i * 3 + 1] * wt; sb += rgb[i * 3 + 2] * wt; sw += wt
                    }
                }
            }
            val ci = cy * cw + cx
            if (sw > 0f) {
                crgb[ci * 3] = sr / sw; crgb[ci * 3 + 1] = sg / sw; crgb[ci * 3 + 2] = sb / sw
            }
            cwt[ci] = min(1f, sw)
        }
        if (cw < w || ch < h) {
            var anyMissing = false
            for (v in cwt) if (v < 1f) { anyMissing = true; break }
            if (anyMissing) pushPull(crgb, cwt, cw, ch)
        }
        // push: fill fine level from coarse (bilinear)
        for (y in 0 until h) {
            val fy = ((y + 0.5f) / 2f - 0.5f).coerceIn(0f, (ch - 1).toFloat())
            val y0 = fy.toInt(); val y1 = min(y0 + 1, ch - 1); val wy = fy - y0
            for (x in 0 until w) {
                val i = y * w + x
                val wt = weight[i]
                if (wt >= 1f) continue
                val fx = ((x + 0.5f) / 2f - 0.5f).coerceIn(0f, (cw - 1).toFloat())
                val x0 = fx.toInt(); val x1 = min(x0 + 1, cw - 1); val wx = fx - x0
                for (c in 0..2) {
                    val v = (crgb[(y0 * cw + x0) * 3 + c] * (1 - wx) + crgb[(y0 * cw + x1) * 3 + c] * wx) * (1 - wy) +
                        (crgb[(y1 * cw + x0) * 3 + c] * (1 - wx) + crgb[(y1 * cw + x1) * 3 + c] * wx) * wy
                    rgb[i * 3 + c] = rgb[i * 3 + c] * wt + v * (1 - wt)
                }
                weight[i] = 1f
            }
        }
    }

    fun bbox(mask: ByteArray, w: Int, h: Int, threshold: Int = 10): Rect? {
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if ((mask[row + x].toInt() and 0xFF) > threshold) {
                    if (x < l) l = x
                    if (x > r) r = x
                    if (y < t) t = y
                    if (y > b) b = y
                }
            }
        }
        if (r < 0) return null
        return Rect(l, t, r + 1, b + 1)
    }

    /**
     * Builds the cut-out subject (cropped to its bounding box) with colour decontamination:
     * edge pixels get the colour of the nearby foreground instead of the old background's tint.
     * Opaque interior pixels are untouched so no structure/detail changes.
     */
    fun buildCutout(src: Bitmap, mask: ByteArray, edge: EdgeSpec): Cutout? {
        val w = src.width; val h = src.height
        val m = applyEdge(mask, w, h, edge)
        val box = bbox(m, w, h) ?: return null
        val pad = 2
        box.set(max(0, box.left - pad), max(0, box.top - pad), min(w, box.right + pad), min(h, box.bottom + pad))
        val bw = box.width(); val bh = box.height()
        val px = IntArray(bw * bh)
        src.getPixels(px, 0, bw, box.left, box.top, bw, bh)
        val alpha = ByteArray(bw * bh)
        for (y in 0 until bh) System.arraycopy(m, (box.top + y) * w + box.left, alpha, y * bw, bw)

        if (edge.decontaminate) decontaminate(px, alpha, bw, bh)

        for (i in px.indices) {
            val a = alpha[i].toInt() and 0xFF
            px[i] = (a shl 24) or (px[i] and 0x00FFFFFF)
        }
        val out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        out.setHasAlpha(true)
        out.setPixels(px, 0, bw, 0, 0, bw, bh)
        return Cutout(out, box)
    }

    private fun decontaminate(px: IntArray, alpha: ByteArray, w: Int, h: Int) {
        // work at reduced resolution for the smooth colour fields
        val f = max(1, (max(w, h) / 640f).roundToInt())
        val lw = (w + f - 1) / f; val lh = (h + f - 1) / f
        val fg = FloatArray(lw * lh * 3); val fgW = FloatArray(lw * lh)
        val bg = FloatArray(lw * lh * 3); val bgW = FloatArray(lw * lh)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val a = alpha[i].toInt() and 0xFF
            val li = (y / f) * lw + x / f
            val c = px[i]
            val r = ((c shr 16) and 0xFF).toFloat(); val g = ((c shr 8) and 0xFF).toFloat(); val b = (c and 0xFF).toFloat()
            if (a >= 245) { fg[li * 3] += r; fg[li * 3 + 1] += g; fg[li * 3 + 2] += b; fgW[li] += 1f }
            else if (a <= 10) { bg[li * 3] += r; bg[li * 3 + 1] += g; bg[li * 3 + 2] += b; bgW[li] += 1f }
        }
        for (i in 0 until lw * lh) {
            if (fgW[i] > 0) { fg[i * 3] /= fgW[i]; fg[i * 3 + 1] /= fgW[i]; fg[i * 3 + 2] /= fgW[i]; fgW[i] = min(1f, fgW[i] / (f * f * 0.5f)) }
            if (bgW[i] > 0) { bg[i * 3] /= bgW[i]; bg[i * 3 + 1] /= bgW[i]; bg[i * 3 + 2] /= bgW[i]; bgW[i] = min(1f, bgW[i] / (f * f * 0.5f)) }
        }
        val hasBg = bgW.any { it > 0f }
        val hasFg = fgW.any { it > 0f }
        if (!hasFg) return
        pushPull(fg, fgW, lw, lh)
        if (hasBg) pushPull(bg, bgW, lw, lh)

        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val a = alpha[i].toInt() and 0xFF
            if (a >= 245 || a <= 2) continue
            val li = (y / f) * lw + x / f
            val af = a / 255f
            val c = px[i]
            val ir = ((c shr 16) and 0xFF).toFloat(); val ig = ((c shr 8) and 0xFF).toFloat(); val ib = (c and 0xFF).toFloat()
            val pr = fg[li * 3]; val pg = fg[li * 3 + 1]; val pb = fg[li * 3 + 2]
            var er = pr; var eg = pg; var eb = pb
            if (hasBg) {
                // closed form F = (I - (1-a)B) / a
                er = ((ir - (1 - af) * bg[li * 3]) / af).coerceIn(0f, 255f)
                eg = ((ig - (1 - af) * bg[li * 3 + 1]) / af).coerceIn(0f, 255f)
                eb = ((ib - (1 - af) * bg[li * 3 + 2]) / af).coerceIn(0f, 255f)
            }
            val t = ((af - 0.35f) / 0.55f).coerceIn(0f, 1f)
            val r = (er * t + pr * (1 - t)).toInt().coerceIn(0, 255)
            val g = (eg * t + pg * (1 - t)).toInt().coerceIn(0, 255)
            val b = (eb * t + pb * (1 - t)).toInt().coerceIn(0, 255)
            px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    /** Draws a byte mask into an ALPHA_8 bitmap (for previews and shadows). */
    fun maskToAlphaBitmap(mask: ByteArray, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        bmp.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(mask))
        return bmp
    }

    fun alphaBitmapToMask(bmp: Bitmap): ByteArray {
        val a8 = if (bmp.config == Bitmap.Config.ALPHA_8) bmp else {
            val b = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ALPHA_8)
            Canvas(b).drawBitmap(bmp, 0f, 0f, Paint())
            b
        }
        val rowBytes = a8.rowBytes
        val buf = java.nio.ByteBuffer.allocate(rowBytes * a8.height)
        a8.copyPixelsToBuffer(buf)
        val arr = buf.array()
        if (rowBytes == a8.width) return arr
        val out = ByteArray(a8.width * a8.height)
        for (y in 0 until a8.height) System.arraycopy(arr, y * rowBytes, out, y * a8.width, a8.width)
        return out
    }

    /** Grayscale PNG friendly encoding of a mask (stored as ARGB with gray value). */
    fun maskToGrayBitmap(mask: ByteArray, w: Int, h: Int): Bitmap {
        val px = IntArray(w * h) { val v = mask[it].toInt() and 0xFF; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    fun grayBitmapToMask(bmp: Bitmap): ByteArray {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        return ByteArray(w * h) { ((px[it] shr 8) and 0xFF).toByte() }
    }

    /** Small stack-blur style box blur for ALPHA_8 shadow bitmaps (in place on byte array). */
    fun blurAlpha(bytes: ByteArray, w: Int, h: Int, radius: Int): ByteArray {
        if (radius <= 0) return bytes
        val n = w * h
        val f = FloatArray(n) { (bytes[it].toInt() and 0xFF).toFloat() }
        val tmp = FloatArray(n)
        val o = FloatArray(n)
        var a = f; var b = o
        val r = max(1, radius / 2)
        repeat(3) { boxFilter(a, w, h, r, b, tmp); val t = a; a = b; b = t }
        return ByteArray(n) { (a[it] + 0.5f).toInt().coerceIn(0, 255).toByte() }
    }
}

class Cutout(val bitmap: Bitmap, val box: Rect)
