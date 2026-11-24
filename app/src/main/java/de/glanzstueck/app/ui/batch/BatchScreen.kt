package de.glanzstueck.app.ui.batch

import android.app.Application
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import de.glanzstueck.app.data.ImageIO
import de.glanzstueck.app.glanz
import de.glanzstueck.app.imaging.Background
import de.glanzstueck.app.imaging.BgType
import de.glanzstueck.app.imaging.CanvasFormat
import de.glanzstueck.app.imaging.EditParams
import de.glanzstueck.app.imaging.MaskOps
import de.glanzstueck.app.imaging.RenderInputs
import de.glanzstueck.app.imaging.Renderer
import de.glanzstueck.app.imaging.ShadowSpec
import de.glanzstueck.app.imaging.ShadowType
import de.glanzstueck.app.ml.LocalModel
import de.glanzstueck.app.ml.SegEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ItemState { WAITING, WORKING, DONE, FAILED }
data class BatchItem(val uri: Uri, val state: ItemState = ItemState.WAITING, val thumb: ImageBitmap? = null)

data class BatchLook(val label: String, val bg: Background)

val BatchLooks = listOf(
    BatchLook("Weiß", Background(BgType.COLOR, 0xFFFFFFFF.toInt())),
    BatchLook("Hellgrau", Background(BgType.COLOR, 0xFFEDEBF0.toInt())),
    BatchLook("Creme", Background(BgType.COLOR, 0xFFF1EDE6.toInt())),
    BatchLook("Studio", Background(BgType.STUDIO, 0xFFFFFFFF.toInt(), 0xFFD9D6DE.toInt())),
    BatchLook("Rosé", Background(BgType.GRADIENT, 0xFFFFDDE1.toInt(), 0xFFEE9CA7.toInt())),
    BatchLook("Transparent", Background(BgType.TRANSPARENT)),
)

data class BatchUi(
    val items: List<BatchItem> = emptyList(),
    val look: Int = 0,
    val shadow: ShadowType = ShadowType.SOFT,
    val format: CanvasFormat = CanvasFormat.PORTRAIT_4_5,
    val running: Boolean = false,
    val finished: Boolean = false,
)

class BatchViewModel(app: Application) : AndroidViewModel(app) {
    private val g = app.glanz
    private val _ui = MutableStateFlow(BatchUi())
    val ui = _ui.asStateFlow()

    fun init(uris: List<Uri>) {
        if (_ui.value.items.isNotEmpty()) return
        _ui.update { it.copy(items = uris.map { u -> BatchItem(u) }) }
        viewModelScope.launch {
            g.settings.lastStyle()?.let { s ->
                val idx = BatchLooks.indexOfFirst { it.bg == s.background }.takeIf { it >= 0 } ?: 0
                _ui.update { it.copy(look = idx, shadow = s.shadow.type, format = s.format) }
            }
        }
    }

    fun setLook(i: Int) = _ui.update { it.copy(look = i) }
    fun setShadow(s: ShadowType) = _ui.update { it.copy(shadow = s) }
    fun setFormat(f: CanvasFormat) = _ui.update { it.copy(format = f) }

    fun start() {
        if (_ui.value.running) return
        _ui.update { it.copy(running = true, finished = false) }
        viewModelScope.launch {
            val st = _ui.value
            val settings = g.settings.current()
            val engine = if (settings.preferHd && g.models.isInstalled(LocalModel.HD_CUTOUT)) SegEngine.HD else SegEngine.STANDARD
            val params = EditParams(
                background = BatchLooks[st.look].bg,
                shadow = ShadowSpec(type = st.shadow),
                format = st.format,
            )
            st.items.forEachIndexed { i, item ->
                if (item.state == ItemState.DONE) return@forEachIndexed
                setItem(i) { it.copy(state = ItemState.WORKING) }
                try {
                    val src = ImageIO.load(getApplication(), item.uri)
                    val mask = g.segmenter.segment(src, engine)
                    val out = withContext(Dispatchers.Default) {
                        val cut = MaskOps.buildCutout(src, mask, params.edge)
                        val p = if (cut == null) params.copy(removeBg = false) else params
                        val inputs = RenderInputs(src, cut, null)
                        val (cw, ch) = Renderer.canvasSize(p, src.width, src.height, settings.exportSize)
                        Renderer.render(p, inputs, cw, ch, drawOverlays = false)
                    }
                    val png = params.background.type == BgType.TRANSPARENT
                    ImageIO.saveToGallery(getApplication(), out, png)
                    val thumb = withContext(Dispatchers.Default) { MaskOps.scaleToLongSide(out, 400).asImageBitmap() }
                    setItem(i) { it.copy(state = ItemState.DONE, thumb = thumb) }
                    out.recycle()
                } catch (t: Throwable) {
                    setItem(i) { it.copy(state = ItemState.FAILED) }
                }
            }
            if (settings.rememberStyle) g.settings.saveStyle(params)
            _ui.update { it.copy(running = false, finished = true) }
        }
    }

    private fun setItem(i: Int, f: (BatchItem) -> BatchItem) = _ui.update { s -> s.copy(items = s.items.mapIndexed { j, it -> if (j == i) f(it) else it }) }
}

@Composable
fun BatchScreen(vm: BatchViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    BackHandler(enabled = !ui.running) { onBack() }
    val done = ui.items.count { it.state == ItemState.DONE || it.state == ItemState.FAILED }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${ui.items.size} Fotos") },
                navigationIcon = { IconButton(onClick = onBack, enabled = !ui.running) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") } },
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).navigationBarsPadding().padding(16.dp)) {
                if (!ui.running && !ui.finished) {
                    Text("Hintergrund", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BatchLooks.forEachIndexed { i, l -> FilterChip(ui.look == i, { vm.setLook(i) }, { Text(l.label) }) }
                    }
                    Text("Schatten", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(ShadowType.NONE to "Keiner", ShadowType.SOFT to "Weich", ShadowType.GROUND to "Boden", ShadowType.FLOAT to "Schwebend")
                            .forEach { (t, l) -> FilterChip(ui.shadow == t, { vm.setShadow(t) }, { Text(l) }) }
                    }
                    Text("Format", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CanvasFormat.entries.forEach { f -> FilterChip(ui.format == f, { vm.setFormat(f) }, { Text(f.label) }) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = vm::start, modifier = Modifier.fillMaxWidth().height(56.dp), shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Alle verzaubern & in Galerie speichern")
                    }
                } else {
                    Text(if (ui.finished) "Fertig! Alle Fotos sind in der Galerie im Album „Glanzstück“." else "Bearbeite Foto ${done + 1} von ${ui.items.size} …",
                        style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))
                    LinearWavyProgressIndicator(progress = { if (ui.items.isEmpty()) 0f else done / ui.items.size.toFloat() }, modifier = Modifier.fillMaxWidth())
                    if (ui.finished) {
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Fertig") }
                    }
                }
            }
        },
    ) { pad ->
        LazyVerticalGrid(
            GridCells.Adaptive(104.dp),
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(ui.items) { _, item ->
                Box(Modifier.fillMaxWidth().aspectRatio(0.8f).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center) {
                    AnimatedContent(item.state, label = "st", transitionSpec = { (fadeIn() + scaleIn(spring(dampingRatio = 0.5f))) togetherWith fadeOut() }) { s ->
                        when (s) {
                            ItemState.WAITING -> Text("⏳", style = MaterialTheme.typography.headlineSmall)
                            ItemState.WORKING -> LoadingIndicator(Modifier.size(48.dp))
                            ItemState.FAILED -> Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                            ItemState.DONE -> Box(Modifier.fillMaxSize()) {
                                item.thumb?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).background(MaterialTheme.colorScheme.primary, MaterialShapes.Cookie6Sided.toShape()),
                                    contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
