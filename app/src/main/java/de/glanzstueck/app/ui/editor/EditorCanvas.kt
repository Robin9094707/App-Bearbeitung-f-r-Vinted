package de.glanzstueck.app.ui.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import de.glanzstueck.app.imaging.BgType
import de.glanzstueck.app.imaging.Overlay
import de.glanzstueck.app.imaging.OverlayPainter
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private fun fitRect(box: Size, w: Float, h: Float, margin: Float): Rect {
    val bw = box.width - 2 * margin; val bh = box.height - 2 * margin
    val s = min(bw / w, bh / h)
    val dw = w * s; val dh = h * s
    val l = (box.width - dw) / 2f; val t = (box.height - dh) / 2f
    return Rect(l, t, l + dw, t + dh)
}

private fun DrawScope.checker(r: Rect, cell: Float) {
    clipRect(r.left, r.top, r.right, r.bottom) {
        drawRect(Color(0xFFF5F5F5), r.topLeft, r.size)
        var y = r.top; var row = 0
        while (y < r.bottom) {
            var x = r.left + if (row % 2 == 0) 0f else cell
            while (x < r.right) { drawRect(Color(0xFFDDDDDD), Offset(x, y), Size(cell, cell)); x += 2 * cell }
            y += cell; row++
        }
    }
}

private fun DrawScope.image(img: ImageBitmap, r: Rect, alpha: Float = 1f, blend: BlendMode = BlendMode.SrcOver) {
    drawImage(img, srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset(r.left.roundToInt(), r.top.roundToInt()),
        dstSize = IntSize(r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1)),
        alpha = alpha, blendMode = blend)
}

/** Composition view: shows the rendered result, live overlays and handles subject / overlay gestures. */
@Composable
fun CompositionCanvas(ui: EditorUi, vm: EditorViewModel, compare: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    val state by rememberUpdatedState(ui)
    val reveal = remember { Animatable(1f) }
    LaunchedEffect(ui.revealKey) {
        if (ui.revealKey > 0) { reveal.snapTo(0f); reveal.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val box = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val margin = with(density) { 16.dp.toPx() }
        val rect = fitRect(box, ui.previewW.toFloat(), ui.previewH.toFloat(), margin)
        val rectState by rememberUpdatedState(rect)
        val slop = with(density) { 12.dp.toPx() }
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val r = rectState
                        val s = state
                        val rw = r.width.roundToInt().coerceAtLeast(1); val rh = r.height.roundToInt().coerceAtLeast(1)
                        val p = down.position - r.topLeft
                        val hit: Overlay? = s.params.overlays.lastOrNull { OverlayPainter.hitTest(it, rw, rh, p.x, p.y, slop) }
                        var moved = false
                        var travel = 0f
                        while (true) {
                            val ev = awaitPointerEvent()
                            val pan = ev.calculatePan()
                            val zoom = ev.calculateZoom()
                            val rot = ev.calculateRotation()
                            travel += pan.getDistance()
                            val multi = ev.changes.count { it.pressed } > 1
                            if (!moved && (travel > viewConfiguration.touchSlop || multi)) {
                                moved = true
                                if (hit == null) vm.setInteractive(true)
                                if (hit != null) vm.selectOverlay(hit.id)
                            }
                            if (moved) {
                                if (hit != null) {
                                    vm.updateOverlay(hit.id, live = true) {
                                        it.copy(
                                            x = (it.x + pan.x / rw).coerceIn(-0.2f, 1.2f),
                                            y = (it.y + pan.y / rh).coerceIn(-0.2f, 1.2f),
                                            size = (it.size * zoom).coerceIn(0.015f, 0.5f),
                                            rotation = it.rotation + rot,
                                        )
                                    }
                                } else if (s.phase == Phase.READY) {
                                    vm.updateLive {
                                        it.copy(
                                            subjectOffsetX = (it.subjectOffsetX + pan.x / rw).coerceIn(-1f, 1f),
                                            subjectOffsetY = (it.subjectOffsetY + pan.y / rh).coerceIn(-1f, 1f),
                                            subjectScale = (it.subjectScale * zoom).coerceIn(0.2f, 5f),
                                            subjectRotation = it.subjectRotation + rot,
                                        )
                                    }
                                }
                                ev.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                            if (ev.changes.none { it.pressed }) break
                        }
                        if (moved) vm.commitLive()
                        else {
                            if (hit != null) {
                                vm.selectOverlay(hit.id)
                                if (s.tool != Tool.TEXT && s.tool != Tool.STICKER) vm.selectTool(Tool.TEXT)
                            } else vm.selectOverlay(null)
                        }
                    }
                },
        ) {
            val shadowPad = 6.dp.toPx()
            drawRoundRect(Color.Black.copy(alpha = 0.10f), rect.topLeft + Offset(0f, shadowPad / 2), rect.size,
                androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            val prev = ui.preview
            if (compare && ui.originalPreview != null) {
                val o = ui.originalPreview
                val r2 = fitRect(box, o.width.toFloat(), o.height.toFloat(), margin)
                image(o, r2)
                return@Canvas
            }
            if (ui.params.removeBg && ui.params.background.type == BgType.TRANSPARENT) checker(rect, 10.dp.toPx())
            if (prev != null) {
                val rv = reveal.value
                if (rv < 1f && ui.sourcePreview != null) {
                    // circular reveal from the centre over the original photo
                    val src = ui.sourcePreview
                    image(src, fitRect(box, src.width.toFloat(), src.height.toFloat(), margin).let { if (ui.params.sourcePlacement) rect else it }, alpha = 1f - rv)
                    val radius = hypot(rect.width, rect.height) * rv
                    val clip = Path().apply { addOval(Rect(rect.center, radius)) }
                    clipPath(clip) { image(prev, rect) }
                    drawCircle(Brush.radialGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.55f * (1 - rv)), Color.Transparent),
                        center = rect.center, radius = radius + 40f), radius = radius + 40f, center = rect.center)
                } else image(prev, rect)
            }
            // overlays drawn with the exact same painter as the export
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                nc.save()
                nc.clipRect(rect.left, rect.top, rect.right, rect.bottom)
                nc.translate(rect.left, rect.top)
                val rw = rect.width.roundToInt().coerceAtLeast(1); val rh = rect.height.roundToInt().coerceAtLeast(1)
                for (o in ui.params.overlays) {
                    OverlayPainter.draw(nc, o, rw, rh, selected = o.id == ui.selectedOverlay, selectionColor = primary, density = density.density)
                }
                nc.restore()
            }
        }
    }
}

/** Brush view for the mask brush and the object eraser. One finger paints, two fingers zoom & pan. */
@Composable
fun BrushCanvas(ui: EditorUi, maskMode: Boolean, onStroke: (BrushStroke) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    var viewScale by remember { mutableFloatStateOf(1f) }
    var viewOffset by remember { mutableStateOf(Offset.Zero) }
    var stroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var cursor by remember { mutableStateOf<Offset?>(null) }
    val state by rememberUpdatedState(ui)
    val primary = MaterialTheme.colorScheme.primary
    val inf = rememberInfiniteTransition(label = "ants")
    val dash by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "d")

    BoxWithConstraints(modifier.fillMaxSize()) {
        val box = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val margin = with(density) { 16.dp.toPx() }
        val base = fitRect(box, ui.srcW.toFloat(), ui.srcH.toFloat(), margin)
        val c = Offset(box.width / 2, box.height / 2)
        fun disp(scale: Float, off: Offset): Rect {
            val tl = (base.topLeft - c) * scale + c + off
            return Rect(tl, Size(base.width * scale, base.height * scale))
        }
        val dispRect = disp(viewScale, viewOffset)
        val brushPx = with(density) { ui.brushSize.dp.toPx() }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var drawing = true
                        val pts = ArrayList<Offset>()
                        pts += down.position
                        stroke = pts.toList()
                        cursor = down.position
                        while (true) {
                            val ev = awaitPointerEvent()
                            val pressed = ev.changes.count { it.pressed }
                            if (pressed > 1) {
                                if (drawing) { drawing = false; stroke = emptyList(); cursor = null }
                                val zoom = ev.calculateZoom()
                                val pan = ev.calculatePan()
                                val centroid = ev.changes.filter { it.pressed }.let { l -> l.fold(Offset.Zero) { a, b -> a + b.position } / l.size.toFloat() }
                                val newScale = (viewScale * zoom).coerceIn(1f, 10f)
                                val k = newScale / viewScale
                                // zoom around the fingers' centroid
                                viewOffset = (viewOffset + c - centroid) * k + centroid - c + pan
                                viewScale = newScale
                            } else if (drawing) {
                                val ch = ev.changes.firstOrNull { it.pressed }
                                if (ch != null) { pts += ch.position; stroke = pts.toList(); cursor = ch.position }
                            }
                            ev.changes.forEach { if (it.positionChanged()) it.consume() }
                            if (ev.changes.none { it.pressed }) break
                        }
                        cursor = null
                        if (drawing && pts.isNotEmpty()) {
                            val r = disp(viewScale, viewOffset)
                            val s = state
                            val k = s.srcW / r.width
                            val srcPts = pts.map { Offset((it.x - r.left) * k, (it.y - r.top) * k) }
                            onStroke(BrushStroke(srcPts, brushPx * k, s.brushRestore))
                        }
                        stroke = emptyList()
                    }
                },
        ) {
            val src = ui.sourcePreview ?: return@Canvas
            if (maskMode) checker(dispRect, 10.dp.toPx())
            image(src, dispRect)
            if (maskMode) ui.maskTint?.let { image(it, dispRect) }
            if (stroke.isNotEmpty()) {
                val path = Path().apply {
                    stroke.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
                    if (stroke.size == 1) lineTo(stroke[0].x + 0.1f, stroke[0].y)
                }
                val col = when {
                    !maskMode -> primary.copy(alpha = 0.55f)
                    state.brushRestore -> Color(0xFF22C55E).copy(alpha = 0.55f)
                    else -> Color(0xFFFF2E63).copy(alpha = 0.55f)
                }
                drawPath(path, col, style = Stroke(width = brushPx * 2, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            cursor?.let {
                drawCircle(Color.White, brushPx, it, style = Stroke(2.dp.toPx()))
                drawCircle(Color.Black.copy(alpha = 0.6f), brushPx, it, style = Stroke(1.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f), dash * 18f)))
            }
        }
    }
}

/** Animated "magic scan" shown while the AI analyses the photo. */
@Composable
fun ScanningOverlay(source: ImageBitmap?, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val inf = rememberInfiniteTransition(label = "scan")
    val y by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "y")
    val pulse by inf.animateFloat(0.6f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")
    val sparkle by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "s")
    val c1 = MaterialTheme.colorScheme.primary
    val c2 = Color(0xFFFF6FA5)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val box = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val margin = with(density) { 16.dp.toPx() }
        Canvas(Modifier.fillMaxSize()) {
            val src = source ?: return@Canvas
            val r = fitRect(box, src.width.toFloat(), src.height.toFloat(), margin)
            image(src, r)
            drawRect(Color.Black.copy(alpha = 0.18f), r.topLeft, r.size)
            val ly = r.top + r.height * y
            val band = r.height * 0.22f
            clipRect(r.left, r.top, r.right, r.bottom) {
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, c1.copy(alpha = 0.35f * pulse), c2.copy(alpha = 0.45f * pulse), Color.Transparent),
                    startY = ly - band, endY = ly + band), Offset(r.left, ly - band), Size(r.width, band * 2))
                drawLine(Color.White.copy(alpha = 0.9f), Offset(r.left, ly), Offset(r.right, ly), strokeWidth = 2.dp.toPx())
                // floating sparkles
                for (i in 0 until 14) {
                    val fx = ((i * 0.618f + sparkle * (0.3f + i % 3 * 0.1f)) % 1f)
                    val fy = ((i * 0.377f + sparkle * 0.5f * (if (i % 2 == 0) 1 else -1) + 1f) % 1f)
                    val pos = Offset(r.left + r.width * fx, r.top + r.height * fy)
                    val sz = (3 + i % 4).dp.toPx() * (0.5f + 0.5f * pulse)
                    drawCircle(Color.White.copy(alpha = 0.75f), sz / 2, pos)
                }
            }
        }
    }
}
