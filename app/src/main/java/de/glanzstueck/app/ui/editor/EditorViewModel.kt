package de.glanzstueck.app.ui.editor

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.glanzstueck.app.data.AppSettings
import de.glanzstueck.app.data.ExportFormat
import de.glanzstueck.app.data.ImageIO
import de.glanzstueck.app.data.SaveTarget
import de.glanzstueck.app.glanz
import de.glanzstueck.app.imaging.BgType
import de.glanzstueck.app.imaging.Cutout
import de.glanzstueck.app.imaging.EditParams
import de.glanzstueck.app.imaging.MaskOps
import de.glanzstueck.app.imaging.Overlay
import de.glanzstueck.app.imaging.RenderInputs
import de.glanzstueck.app.imaging.Renderer
import de.glanzstueck.app.ml.LocalModel
import de.glanzstueck.app.ml.ModelState
import de.glanzstueck.app.ml.SegEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

enum class Tool(val label: String) {
    CUTOUT("Freistellen"), BACKGROUND("Hintergrund"), SHADOW("Schatten"), ERASER("Radierer"),
    MASK("Pinsel"), TEXT("Text"), STICKER("Sticker"), FORMAT("Format"), LIGHT("Licht")
}

enum class Phase { LOADING, SEGMENTING, READY, ERROR }

data class BrushStroke(val points: List<Offset>, val radius: Float, val restore: Boolean)

data class SavedResult(val galleryUri: Uri?, val inApp: Boolean, val png: Boolean)

data class EditorUi(
    val phase: Phase = Phase.LOADING,
    val status: String = "Foto wird geladen…",
    val tool: Tool = Tool.BACKGROUND,
    val params: EditParams = EditParams(),
    val preview: ImageBitmap? = null,
    val previewW: Int = 1,
    val previewH: Int = 1,
    val sourcePreview: ImageBitmap? = null,
    val originalPreview: ImageBitmap? = null,
    val maskTint: ImageBitmap? = null,
    val srcW: Int = 1,
    val srcH: Int = 1,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val selectedOverlay: Long? = null,
    val busy: String? = null,
    val brushSize: Float = 28f,
    val brushRestore: Boolean = false,
    val hdInstalled: Boolean = false,
    val lamaInstalled: Boolean = false,
    val engineUsed: SegEngine? = null,
    val revealKey: Int = 0,
    val hasMask: Boolean = false,
    val message: String? = null,
    val saved: SavedResult? = null,
    val settings: AppSettings = AppSettings(),
    val modelStates: Map<LocalModel, ModelState> = emptyMap(),
)

sealed interface EditorSource {
    data class Image(val uri: Uri) : EditorSource
    data class Project(val id: String) : EditorSource
}

class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val g = app.glanz
    private val _ui = MutableStateFlow(EditorUi())
    val ui: StateFlow<EditorUi> = _ui.asStateFlow()

    private var started = false
    private var original: Bitmap? = null
    private var source: Bitmap? = null
    private var sourceChanged = false
    private var mask: ByteArray? = null
    private var cutout: Cutout? = null
    private var cutoutKey: Any? = null
    private var bgImage: Bitmap? = null
    private var bgImageFile: File? = null
    private var previewInputs: RenderInputs? = null
    private var projectId: String? = null

    private data class Snap(val params: EditParams, val source: Bitmap, val mask: ByteArray?, val bgFile: File?)
    private val undo = ArrayDeque<Snap>()
    private val redo = ArrayDeque<Snap>()

    private val renderTick = MutableStateFlow(0L)
    @Volatile private var interactive = false

    init {
        viewModelScope.launch { g.settings.settings.collect { s -> _ui.update { it.copy(settings = s) } } }
        viewModelScope.launch {
            g.models.states.collect { st ->
                _ui.update {
                    it.copy(
                        modelStates = st,
                        hdInstalled = st[LocalModel.HD_CUTOUT] == ModelState.Installed,
                        lamaInstalled = st[LocalModel.LAMA] == ModelState.Installed,
                    )
                }
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            renderTick.collectLatest { if (it > 0) renderPreview() }
        }
    }

    fun start(src: EditorSource) {
        if (started) return
        started = true
        viewModelScope.launch {
            try {
                when (src) {
                    is EditorSource.Image -> {
                        val bmp = ImageIO.load(getApplication(), src.uri)
                        setSource(bmp, isOriginal = true)
                        val settings = g.settings.current()
                        val style = if (settings.rememberStyle) g.settings.lastStyle() else null
                        _ui.update { it.copy(params = style ?: EditParams()) }
                        segment(firstRun = true)
                    }
                    is EditorSource.Project -> {
                        val p = g.projects.load(src.id)
                        projectId = p.id
                        setSource(p.source, isOriginal = true)
                        mask = p.mask
                        bgImageFile = p.bgImage
                        bgImage = p.bgImage?.let { ImageIO.loadFile(it, 2048) }
                        _ui.update { it.copy(params = p.params, phase = Phase.READY, hasMask = p.mask != null, revealKey = it.revealKey + 1) }
                        invalidateCutout()
                    }
                }
            } catch (t: Throwable) {
                _ui.update { it.copy(phase = Phase.ERROR, status = "Das Foto konnte nicht geöffnet werden (${t.message ?: t.javaClass.simpleName})") }
            }
        }
    }

    private suspend fun setSource(bmp: Bitmap, isOriginal: Boolean) {
        source = bmp
        if (isOriginal) original = bmp
        val sp = withContext(Dispatchers.Default) { MaskOps.scaleToLongSide(bmp, 1600).asImageBitmap() }
        _ui.update {
            it.copy(
                sourcePreview = sp,
                originalPreview = if (isOriginal) sp else it.originalPreview,
                srcW = bmp.width, srcH = bmp.height,
                phase = if (it.phase == Phase.LOADING) Phase.SEGMENTING else it.phase,
            )
        }
    }

    // ---------------------------------------------------------------- segmentation

    fun resegment(engine: SegEngine? = null) {
        viewModelScope.launch {
            pushUndo()
            segment(firstRun = false, forced = engine)
        }
    }

    private suspend fun segment(firstRun: Boolean, forced: SegEngine? = null) {
        val src = source ?: return
        val settings = g.settings.current()
        val engine = forced ?: if (settings.preferHd && g.models.isInstalled(LocalModel.HD_CUTOUT)) SegEngine.HD else SegEngine.STANDARD
        _ui.update { it.copy(phase = Phase.SEGMENTING, status = "Motiv wird erkannt…") }
        try {
            val m = g.segmenter.segment(src, engine) { s -> _ui.update { it.copy(status = s) } }
            val box = MaskOps.bbox(m, src.width, src.height, 40)
            if (box == null) {
                mask = null
                _ui.update {
                    it.copy(phase = Phase.READY, params = it.params.copy(removeBg = false), hasMask = false,
                        message = "Kein Motiv gefunden – du kannst das Foto trotzdem bearbeiten.")
                }
            } else {
                mask = m
                _ui.update { it.copy(phase = Phase.READY, hasMask = true, engineUsed = engine, params = it.params.copy(removeBg = true), revealKey = it.revealKey + 1) }
            }
        } catch (t: Throwable) {
            val msg = if (engine == SegEngine.STANDARD)
                "Freistellen nicht möglich (${t.message}). Tipp: Lade in den Einstellungen das HD-Modell."
            else "HD-Freistellen fehlgeschlagen (${t.message})."
            _ui.update { it.copy(phase = Phase.READY, params = it.params.copy(removeBg = mask != null && it.params.removeBg), message = msg) }
        }
        invalidateCutout()
        if (firstRun) refreshMaskTint()
    }

    // ---------------------------------------------------------------- rendering

    private fun invalidateCutout() {
        cutoutKey = null
        requestRender()
    }

    fun requestRender() {
        renderTick.value = renderTick.value + 1
    }

    private fun ensureInputs(params: EditParams): RenderInputs? {
        val src = source ?: return null
        val key = Triple(System.identityHashCode(src), System.identityHashCode(mask), params.edge)
        if (cutoutKey != key) {
            val m = mask
            cutout = if (m != null) MaskOps.buildCutout(src, m, params.edge) else null
            cutoutKey = key
            previewInputs = null
        }
        if (previewInputs == null || previewInputs?.bgImage !== bgImage) {
            // downscaled copy of everything for a fast, fluid preview
            val f = 1600f / max(src.width, src.height)
            val k = if (f < 1f) f else 1f
            val ps = if (k < 1f) Bitmap.createScaledBitmap(src, (src.width * k).roundToInt(), (src.height * k).roundToInt(), true) else src
            val c = cutout
            val pc = if (c != null && k < 1f) {
                val bw = max(1, (c.bitmap.width * k).roundToInt()); val bh = max(1, (c.bitmap.height * k).roundToInt())
                Cutout(Bitmap.createScaledBitmap(c.bitmap, bw, bh, true),
                    android.graphics.Rect((c.box.left * k).roundToInt(), (c.box.top * k).roundToInt(), (c.box.left * k).roundToInt() + bw, (c.box.top * k).roundToInt() + bh))
            } else c
            previewInputs = RenderInputs(ps, pc, bgImage)
        }
        return previewInputs
    }

    private fun renderPreview() {
        val st = _ui.value
        if (st.phase != Phase.READY) return
        val params = st.params
        val inputs = ensureInputs(params) ?: return
        val long = if (interactive) 900 else 1500
        val (cw, ch) = Renderer.canvasSize(params, inputs.source.width, inputs.source.height, long)
        val bmp = Renderer.render(params, inputs, cw, ch, drawOverlays = false)
        _ui.update { it.copy(preview = bmp.asImageBitmap(), previewW = cw, previewH = ch) }
    }

    private fun refreshMaskTint() {
        viewModelScope.launch(Dispatchers.Default) {
            val m = mask
            val src = source ?: return@launch
            if (m == null) { _ui.update { it.copy(maskTint = null) }; return@launch }
            val k = 1600f / max(src.width, src.height)
            val tw = if (k < 1f) (src.width * k).roundToInt() else src.width
            val th = if (k < 1f) (src.height * k).roundToInt() else src.height
            val a8 = MaskOps.maskToAlphaBitmap(m, src.width, src.height)
            val scaled = Bitmap.createScaledBitmap(a8, tw, th, true)
            val bytes = MaskOps.alphaBitmapToMask(scaled)
            val px = IntArray(tw * th) {
                val v = bytes[it].toInt() and 0xFF
                val a = ((255 - v) * 0.6f).roundToInt()
                (a shl 24) or 0xFF2E63
            }
            val tint = Bitmap.createBitmap(px, tw, th, Bitmap.Config.ARGB_8888)
            _ui.update { it.copy(maskTint = tint.asImageBitmap()) }
        }
    }

    // ---------------------------------------------------------------- params / history

    private fun snapshot() = Snap(_ui.value.params, source!!, mask, bgImageFile)

    private fun pushUndo() {
        if (source == null) return
        undo.addLast(snapshot())
        while (undo.size > 30) undo.removeFirst()
        redo.clear()
        _ui.update { it.copy(canUndo = true, canRedo = false) }
    }

    fun undo() = restore(undo, redo)
    fun redo() = restore(redo, undo)

    private fun restore(from: ArrayDeque<Snap>, to: ArrayDeque<Snap>) {
        val s = from.removeLastOrNull() ?: return
        to.addLast(snapshot())
        val sourceDiff = s.source !== source
        val maskDiff = s.mask !== mask
        source = s.source
        mask = s.mask
        if (s.bgFile != bgImageFile) {
            bgImageFile = s.bgFile
            viewModelScope.launch { bgImage = s.bgFile?.let { ImageIO.loadFile(it, 2048) }; previewInputs = null; requestRender() }
        }
        _ui.update { it.copy(params = s.params, canUndo = undo.isNotEmpty(), canRedo = redo.isNotEmpty(), hasMask = mask != null) }
        if (sourceDiff) {
            sourceChanged = true
            viewModelScope.launch { setSource(source!!, isOriginal = false) }
        }
        if (sourceDiff || maskDiff) { invalidateCutout(); refreshMaskTint() } else requestRender()
    }

    /** Commit a parameter change (creates an undo step). */
    fun update(transform: (EditParams) -> EditParams) {
        val old = _ui.value.params
        val new = transform(old)
        if (new == old) return
        pushUndo()
        applyParams(old, new)
    }

    /** Live change during a slider drag / gesture – no undo step, call [commitLive] afterwards. */
    private var liveStart: EditParams? = null

    fun updateLive(transform: (EditParams) -> EditParams) {
        val old = _ui.value.params
        if (liveStart == null) liveStart = old
        val new = transform(old)
        if (new == old) return
        applyParams(old, new)
    }

    fun commitLive() {
        val start = liveStart ?: return
        liveStart = null
        interactive = false
        if (start != _ui.value.params && source != null) {
            undo.addLast(Snap(start, source!!, mask, bgImageFile))
            while (undo.size > 30) undo.removeFirst()
            redo.clear()
            _ui.update { it.copy(canUndo = true, canRedo = false) }
        }
        requestRender()
    }

    fun setInteractive(v: Boolean) { interactive = v }

    private fun applyParams(old: EditParams, new: EditParams) {
        _ui.update { it.copy(params = new) }
        if (old.copy(overlays = emptyList()) != new.copy(overlays = emptyList())) requestRender()
    }

    fun selectTool(t: Tool) {
        _ui.update { it.copy(tool = t, selectedOverlay = if (t == Tool.TEXT || t == Tool.STICKER) it.selectedOverlay else null) }
        if (t == Tool.MASK) refreshMaskTint()
    }

    fun clearMessage() = _ui.update { it.copy(message = null) }
    fun showMessage(m: String) = _ui.update { it.copy(message = m) }
    fun clearSaved() = _ui.update { it.copy(saved = null) }

    // ---------------------------------------------------------------- overlays

    fun addOverlay(o: Overlay) {
        update { it.copy(overlays = it.overlays + o) }
        _ui.update { it.copy(selectedOverlay = o.id) }
    }

    fun selectOverlay(id: Long?) = _ui.update { it.copy(selectedOverlay = id) }

    fun updateOverlay(id: Long, live: Boolean = false, f: (Overlay) -> Overlay) {
        val t: (EditParams) -> EditParams = { p -> p.copy(overlays = p.overlays.map { if (it.id == id) f(it) else it }) }
        if (live) updateLive(t) else update(t)
    }

    fun deleteOverlay(id: Long) {
        update { p -> p.copy(overlays = p.overlays.filterNot { it.id == id }) }
        _ui.update { it.copy(selectedOverlay = null) }
    }

    fun duplicateOverlay(id: Long) {
        val o = _ui.value.params.overlays.firstOrNull { it.id == id } ?: return
        addOverlay(o.copy(id = System.nanoTime(), x = (o.x + 0.05f).coerceAtMost(0.95f), y = (o.y + 0.05f).coerceAtMost(0.95f)))
    }

    fun bringToFront(id: Long) = update { p ->
        val o = p.overlays.firstOrNull { it.id == id } ?: return@update p
        p.copy(overlays = p.overlays.filterNot { it.id == id } + o)
    }

    // ---------------------------------------------------------------- background image

    fun setBackgroundImage(uri: Uri) {
        viewModelScope.launch {
            try {
                val bmp = ImageIO.load(getApplication(), uri, 2048)
                val f = File(g.projects.backgroundsDir(), "bg_${System.currentTimeMillis()}.jpg")
                withContext(Dispatchers.IO) { f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) } }
                pushUndo()
                bgImage = bmp
                bgImageFile = f
                previewInputs = null
                val old = _ui.value.params
                applyParams(old, old.copy(background = old.background.copy(type = BgType.IMAGE, imagePath = f.absolutePath)))
            } catch (t: Throwable) {
                showMessage("Hintergrundbild konnte nicht geladen werden")
            }
        }
    }

    // ---------------------------------------------------------------- brushes

    fun setBrushSize(v: Float) = _ui.update { it.copy(brushSize = v) }
    fun setBrushRestore(v: Boolean) = _ui.update { it.copy(brushRestore = v) }

    private fun rasterizeStroke(stroke: BrushStroke, w: Int, h: Int, soft: Boolean): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = stroke.radius * 2f
            color = 0xFF000000.toInt()
            if (soft && stroke.radius > 4f) maskFilter = BlurMaskFilter(stroke.radius * 0.18f, BlurMaskFilter.Blur.NORMAL)
        }
        val path = Path()
        stroke.points.forEachIndexed { i, o -> if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
        if (stroke.points.size == 1) path.lineTo(stroke.points[0].x + 0.1f, stroke.points[0].y)
        c.drawPath(path, p)
        val bytes = MaskOps.alphaBitmapToMask(bmp)
        bmp.recycle()
        return bytes
    }

    fun applyMaskStroke(stroke: BrushStroke) {
        val src = source ?: return
        viewModelScope.launch {
            pushUndo()
            val w = src.width; val h = src.height
            val newMask = withContext(Dispatchers.Default) {
                val s = rasterizeStroke(stroke, w, h, soft = true)
                val m = mask?.copyOf() ?: ByteArray(w * h)
                for (i in m.indices) {
                    val sv = s[i].toInt() and 0xFF
                    if (sv == 0) continue
                    val mv = m[i].toInt() and 0xFF
                    m[i] = (if (stroke.restore) max(mv, sv) else kotlin.math.min(mv, 255 - sv)).toByte()
                }
                m
            }
            mask = newMask
            _ui.update { it.copy(hasMask = true, params = it.params.copy(removeBg = true)) }
            invalidateCutout()
            refreshMaskTint()
        }
    }

    fun applyEraseStroke(stroke: BrushStroke) {
        val src = source ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = if (g.inpainter.hasAi) "KI radiert…" else "Wird entfernt…") }
            try {
                val hole = withContext(Dispatchers.Default) { rasterizeStroke(stroke, src.width, src.height, soft = false) }
                val result = g.inpainter.inpaint(src, hole)
                pushUndo()
                sourceChanged = true
                setSource(result, isOriginal = false)
                invalidateCutout()
            } catch (t: Throwable) {
                showMessage("Radieren fehlgeschlagen: ${t.message}")
            } finally {
                _ui.update { it.copy(busy = null) }
            }
        }
    }

    // ---------------------------------------------------------------- export

    private suspend fun renderExport(): Pair<Bitmap, Boolean> = withContext(Dispatchers.Default) {
        val st = _ui.value
        val params = st.params
        val src = source!!
        ensureInputs(params)
        val full = RenderInputs(src, cutout, bgImage)
        val long = st.settings.exportSize.coerceIn(800, 4096)
        val (cw, ch) = Renderer.canvasSize(params, src.width, src.height, long)
        val transparent = params.removeBg && cutout != null && params.background.type == BgType.TRANSPARENT
        val png = when (st.settings.exportFormat) {
            ExportFormat.PNG -> true
            ExportFormat.JPEG -> false
            ExportFormat.AUTO -> transparent
        }
        val bmp = Renderer.render(params, full, cw, ch, drawOverlays = true)
        if (!png && transparent) {
            // JPEG has no transparency – flatten on white
            val flat = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
            Canvas(flat).apply { drawColor(0xFFFFFFFF.toInt()); drawBitmap(bmp, 0f, 0f, null) }
            flat to false
        } else bmp to png
    }

    fun save(target: SaveTarget? = null) {
        if (source == null || _ui.value.busy != null) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = "Wird gespeichert…") }
            try {
                val st = _ui.value
                val t = target ?: st.settings.saveTarget
                val (bmp, png) = renderExport()
                var uri: Uri? = null
                if (t == SaveTarget.GALLERY || t == SaveTarget.BOTH) uri = ImageIO.saveToGallery(getApplication(), bmp, png)
                if (t == SaveTarget.APP || t == SaveTarget.BOTH) saveProject(bmp)
                if (st.settings.rememberStyle) g.settings.saveStyle(st.params)
                _ui.update { it.copy(saved = SavedResult(uri, t != SaveTarget.GALLERY, png)) }
            } catch (t: Throwable) {
                showMessage("Speichern fehlgeschlagen: ${t.message}")
            } finally {
                _ui.update { it.copy(busy = null) }
            }
        }
    }

    private suspend fun saveProject(rendered: Bitmap) {
        val id = projectId ?: g.projects.newId().also { projectId = it }
        val thumb = withContext(Dispatchers.Default) { MaskOps.scaleToLongSide(rendered, 512) }
        g.projects.save(id, source!!, sourceChanged, mask, _ui.value.params, thumb, bgImageFile)
        sourceChanged = false
    }

    fun share(toVinted: Boolean) {
        if (source == null || _ui.value.busy != null) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = "Wird vorbereitet…") }
            try {
                val (bmp, png) = renderExport()
                val uri = ImageIO.shareFile(getApplication(), bmp, png)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = if (png) "image/png" else "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val ctx = getApplication<Application>()
                if (toVinted) {
                    try {
                        ctx.startActivity(Intent(send).setPackage("fr.vinted").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        return@launch
                    } catch (_: ActivityNotFoundException) {
                        // fall through to the normal share sheet
                    }
                }
                ctx.startActivity(Intent.createChooser(send, "Foto teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (t: Throwable) {
                showMessage("Teilen fehlgeschlagen: ${t.message}")
            } finally {
                _ui.update { it.copy(busy = null) }
            }
        }
    }

    fun downloadModel(m: LocalModel) = g.models.download(m)

    /** Test hook: continue without segmentation (ML Kit is unavailable on the JVM). */
    internal fun forceReadyForTest() {
        _ui.update { it.copy(phase = Phase.READY, params = it.params.copy(removeBg = false)) }
        requestRender()
    }

    suspend fun awaitReady() = _ui.first { it.phase == Phase.READY }
}
