package de.glanzstueck.app.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import de.glanzstueck.app.data.SaveTarget
import de.glanzstueck.app.imaging.Overlay
import de.glanzstueck.app.imaging.OverlayStyle

private val toolIcons: Map<Tool, ImageVector> = mapOf(
    Tool.CUTOUT to Icons.Rounded.ContentCut,
    Tool.BACKGROUND to Icons.Rounded.Wallpaper,
    Tool.SHADOW to Icons.Rounded.Layers,
    Tool.ERASER to Icons.Rounded.AutoFixHigh,
    Tool.MASK to Icons.Rounded.Brush,
    Tool.TEXT to Icons.Rounded.TextFields,
    Tool.STICKER to Icons.Rounded.Sell,
    Tool.FORMAT to Icons.Rounded.AspectRatio,
    Tool.LIGHT to Icons.Rounded.LightMode,
)

private val toolOrder = listOf(Tool.BACKGROUND, Tool.SHADOW, Tool.CUTOUT, Tool.ERASER, Tool.FORMAT, Tool.TEXT, Tool.STICKER, Tool.LIGHT, Tool.MASK)

@Composable
fun EditorScreen(vm: EditorViewModel, onBack: () -> Unit, onNewPhoto: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val snack = remember { SnackbarHostState() }
    var textDialog by remember { mutableStateOf<Pair<Boolean, Overlay?>?>(null) }
    var showSheet by remember { mutableStateOf(false) }
    var compare by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    LaunchedEffect(ui.message) {
        ui.message?.let { snack.showSnackbar(it); vm.clearMessage() }
    }
    LaunchedEffect(ui.revealKey) {
        if (ui.revealKey > 0 && ui.settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // ------------------------------------------------ top bar
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") }
                IconButton(onClick = vm::undo, enabled = ui.canUndo) { Icon(Icons.AutoMirrored.Rounded.Undo, "Rückgängig") }
                IconButton(onClick = vm::redo, enabled = ui.canRedo) { Icon(Icons.AutoMirrored.Rounded.Redo, "Wiederholen") }
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown()
                                compare = true
                                waitForUpOrCancellation()
                                compare = false
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Compare, "Vorher/Nachher (gedrückt halten)", tint = if (compare) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showSheet = true }, enabled = ui.phase == Phase.READY) { Icon(Icons.Rounded.MoreVert, "Weitere Optionen") }
                Button(
                    onClick = { if (ui.settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.Confirm); vm.save() },
                    enabled = ui.phase == Phase.READY && ui.busy == null,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Icon(Icons.Rounded.SaveAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Speichern")
                }
            }

            // ------------------------------------------------ canvas
            Box(
                Modifier.weight(1f).fillMaxWidth().clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = when {
                        ui.phase == Phase.LOADING || ui.phase == Phase.SEGMENTING -> 0
                        ui.phase == Phase.ERROR -> 3
                        ui.tool == Tool.ERASER -> 1
                        ui.tool == Tool.MASK -> 2
                        else -> 4
                    },
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "canvas",
                ) { mode ->
                    when (mode) {
                        0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ScanningOverlay(ui.sourcePreview)
                            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f), tonalElevation = 6.dp) {
                                Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    LoadingIndicator(Modifier.size(40.dp))
                                    Spacer(Modifier.width(12.dp))
                                    AnimatedContent(ui.status, label = "st", transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) }) {
                                        Text(it, style = MaterialTheme.typography.titleSmall)
                                    }
                                }
                            }
                        }
                        1 -> BrushCanvas(ui, maskMode = false, onStroke = vm::applyEraseStroke)
                        2 -> BrushCanvas(ui, maskMode = true, onStroke = vm::applyMaskStroke)
                        3 -> Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("😕", style = MaterialTheme.typography.displayMedium)
                            Text(ui.status, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.size(16.dp))
                            Button(onClick = onBack) { Text("Zurück") }
                        }
                        else -> CompositionCanvas(ui, vm, compare)
                    }
                }
                androidx.compose.animation.AnimatedVisibility(ui.busy != null, enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut() + scaleOut(targetScale = 0.8f)) {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHighest, tonalElevation = 8.dp) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            ContainedLoadingIndicator(Modifier.size(64.dp))
                            Spacer(Modifier.size(12.dp))
                            Text(ui.busy ?: "", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                SnackbarHost(snack, Modifier.align(Alignment.BottomCenter))
            }

            // ------------------------------------------------ tool panel + tool bar
            if (ui.phase == Phase.READY) {
                AnimatedContent(
                    targetState = ui.tool,
                    transitionSpec = {
                        (fadeIn(spring(stiffness = 500f)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) { it / 3 }) togetherWith
                            fadeOut(spring(stiffness = 800f)) using SizeTransform(clip = false)
                    },
                    label = "panel",
                ) { _ ->
                    Box(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        ToolPanel(ui, vm) { o -> textDialog = true to o }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                LazyRow(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                ) {
                    items(toolOrder) { t ->
                        de.glanzstueck.app.ui.components.ToolButton(toolIcons.getValue(t), t.label, ui.tool == t, {
                            if (ui.settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            vm.selectTool(t)
                        })
                    }
                }
            }
        }

        // ------------------------------------------------ success celebration
        androidx.compose.animation.AnimatedVisibility(ui.saved != null, enter = fadeIn(), exit = fadeOut()) {
            SavedCelebration(ui, onShare = { vm.clearSaved(); vm.share(false) }, onVinted = { vm.clearSaved(); vm.share(true) },
                onNew = { vm.clearSaved(); onNewPhoto() }, onContinue = vm::clearSaved)
        }
    }

    textDialog?.let { (_, o) ->
        TextEditDialog(o, onDismiss = { textDialog = null }) { text ->
            if (o == null) {
                vm.addOverlay(Overlay(System.nanoTime(), text, x = 0.5f, y = 0.88f, size = 0.07f, color = 0xFF1C1B1F.toInt(),
                    accent = 0xFFFFFFFF.toInt(), style = OverlayStyle.PLAIN))
                vm.selectTool(Tool.TEXT)
            } else vm.updateOverlay(o.id) { it.copy(text = text) }
            textDialog = null
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text("Speichern & Teilen", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                SheetItem(Icons.Rounded.PhotoLibrary, "In Galerie speichern", "Album „Glanzstück“ – sofort in Vinted auswählbar") { showSheet = false; vm.save(SaveTarget.GALLERY) }
                SheetItem(Icons.Rounded.Layers, "Nur in der App speichern", "Als Projekt – später weiter bearbeiten") { showSheet = false; vm.save(SaveTarget.APP) }
                SheetItem(Icons.Rounded.Check, "Galerie + App", "Beides auf einmal") { showSheet = false; vm.save(SaveTarget.BOTH) }
                SheetItem(Icons.Rounded.Storefront, "Direkt an Vinted senden", "Öffnet Vinted mit dem Foto") { showSheet = false; vm.share(true) }
                SheetItem(Icons.Rounded.Share, "Teilen…", "WhatsApp, Kleinanzeigen, Instagram …") { showSheet = false; vm.share(false) }
            }
        }
    }
}

@Composable
private fun SheetItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie6Sided.toShape()), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clip(MaterialTheme.shapes.large).clickableNoRipple(onClick).padding(horizontal = 8.dp),
    )
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit) = this.then(Modifier.pointerInput(onClick) {
    awaitEachGesture {
        awaitFirstDown()
        if (waitForUpOrCancellation() != null) onClick()
    }
})

@Composable
private fun SavedCelebration(ui: EditorUi, onShare: () -> Unit, onVinted: () -> Unit, onNew: () -> Unit, onContinue: () -> Unit) {
    val s = ui.saved ?: return
    val pop = remember { Animatable(0f) }
    val spin = remember { Animatable(-120f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            launch { pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f)) }
            launch { spin.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 120f)) }
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).pointerInput(Unit) { awaitEachGesture { awaitFirstDown() } }, contentAlignment = Alignment.Center) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.padding(28.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(96.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value; rotationZ = spin.value }
                        .background(MaterialTheme.colorScheme.primary, MaterialShapes.Cookie12Sided.toShape()),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(48.dp).graphicsLayer { rotationZ = -spin.value }) }
                Spacer(Modifier.size(16.dp))
                Text("Gespeichert! ✨", style = MaterialTheme.typography.headlineSmall)
                Text(
                    buildString {
                        if (s.galleryUri != null) append("In der Galerie im Album „Glanzstück“")
                        if (s.galleryUri != null && s.inApp) append(" und ")
                        if (s.inApp) append("in deinen Projekten")
                        append(if (s.png) " (PNG)." else ".")
                    },
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.size(20.dp))
                Button(onClick = onVinted, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Storefront, null); Spacer(Modifier.width(8.dp)); Text("An Vinted senden") }
                FilledTonalButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(8.dp)); Text("Teilen") }
                OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("Nächstes Foto") }
                TextButton(onClick = onContinue) { Text("Weiter bearbeiten") }
            }
        }
    }
}
