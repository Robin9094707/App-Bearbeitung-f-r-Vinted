package de.glanzstueck.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import de.glanzstueck.app.imaging.Background
import de.glanzstueck.app.imaging.BgType
import de.glanzstueck.app.imaging.CanvasFormat
import de.glanzstueck.app.imaging.EditParams
import de.glanzstueck.app.imaging.MaskOps
import de.glanzstueck.app.imaging.Overlay
import de.glanzstueck.app.imaging.OverlayStyle
import de.glanzstueck.app.imaging.RenderInputs
import de.glanzstueck.app.imaging.Renderer
import de.glanzstueck.app.imaging.ShadowSpec
import de.glanzstueck.app.imaging.ShadowType
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Developer test: runs the full cut-out pipeline on sample photos (pass -Dglanz.testdata=dir). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PipelineTest {
    private val dir = System.getProperty("glanz.testdata").orEmpty()

    private fun readMask(f: File): Triple<FloatArray, Int, Int> {
        val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val w = b.int; val h = b.int
        val arr = FloatArray(w * h) { b.float }
        return Triple(arr, w, h)
    }

    private fun save(b: Bitmap, name: String) {
        val out = File(dir, "out").apply { mkdirs() }
        File(out, name).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun pipeline() {
        assumeTrue(dir.isNotEmpty() && File(dir).exists())
        for (name in listOf("handbag", "sneakers", "person")) {
            val src = BitmapFactory.decodeFile(File(dir, "$name.jpg").absolutePath)
                .copy(Bitmap.Config.ARGB_8888, false)
            val (low, lw, lh) = readMask(File(dir, "$name.jpg.mask"))
            val t0 = System.currentTimeMillis()
            MaskOps.keepMainComponents(low, lw, lh)
            val mask = MaskOps.guidedUpsample(low, lw, lh, src)
            val t1 = System.currentTimeMillis()
            val params = EditParams()
            val cut = MaskOps.buildCutout(src, mask, params.edge)!!
            val t2 = System.currentTimeMillis()
            println("$name ${src.width}x${src.height} guided=${t1 - t0}ms cutout=${t2 - t1}ms")
            save(MaskOps.maskToGrayBitmap(mask, src.width, src.height), "${name}_mask.png")
            val inputs = RenderInputs(src, cut, null)
            val looks = listOf(
                "white_soft" to params,
                "studio_ground" to params.copy(background = Background(BgType.STUDIO, 0xFFFFFFFF.toInt(), 0xFFD9D6DE.toInt()), shadow = ShadowSpec(ShadowType.GROUND, 0.45f)),
                "pink_float" to params.copy(background = Background(BgType.GRADIENT, 0xFFFFDDE1.toInt(), 0xFFEE9CA7.toInt()), shadow = ShadowSpec(ShadowType.FLOAT, 0.4f)),
                "blur" to params.copy(background = Background(BgType.BLUR, blur = 0.6f), shadow = ShadowSpec(ShadowType.NONE)),
                "cast_text" to params.copy(format = CanvasFormat.SQUARE, background = Background(BgType.COLOR, 0xFFF1EDE6.toInt()), shadow = ShadowSpec(ShadowType.CAST, 0.4f),
                    overlays = listOf(Overlay(1, "NEU", 0.82f, 0.15f, 0.06f, color = -1, accent = 0xFFE4405F.toInt(), style = OverlayStyle.BURST),
                        Overlay(2, "Größe M", 0.5f, 0.92f, 0.05f, style = OverlayStyle.PILL, color = -1, accent = 0xFF1C1B1F.toInt()))),
                "reflection" to params.copy(background = Background(BgType.COLOR, 0xFFFFFFFF.toInt()), shadow = ShadowSpec(ShadowType.REFLECTION, 0.4f, distance = 0.4f)),
                "transparent" to params.copy(background = Background(BgType.TRANSPARENT), shadow = ShadowSpec(ShadowType.NONE)),
            )
            for ((look, p) in looks) {
                val (cw, ch) = Renderer.canvasSize(p, src.width, src.height, 1400)
                val t = System.currentTimeMillis()
                val r = Renderer.render(p, inputs, cw, ch, drawOverlays = true)
                println("  $look render=${System.currentTimeMillis() - t}ms")
                save(r, "${name}_$look.png")
            }
        }
    }
}
