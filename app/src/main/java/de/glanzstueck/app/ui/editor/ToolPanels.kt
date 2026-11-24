package de.glanzstueck.app.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.glanzstueck.app.imaging.BgType
import de.glanzstueck.app.imaging.CanvasFormat
import de.glanzstueck.app.imaging.EditParams
import de.glanzstueck.app.imaging.Overlay
import de.glanzstueck.app.imaging.OverlayFont
import de.glanzstueck.app.imaging.OverlayStyle
import de.glanzstueck.app.imaging.ShadowType
import de.glanzstueck.app.ml.LocalModel
import de.glanzstueck.app.ml.ModelState
import de.glanzstueck.app.ml.SegEngine
import de.glanzstueck.app.ui.components.ColorSwatch
import de.glanzstueck.app.ui.components.LabeledSlider
import de.glanzstueck.app.ui.components.SectionTitle

val SolidColors = listOf(
    0xFFFFFFFF, 0xFFF7F5F2, 0xFFF1EDE6, 0xFFEDEBF0, 0xFFE3E3E3, 0xFFC9C9C9, 0xFF2B2B2B, 0xFF000000,
    0xFFF8E1E7, 0xFFFFD6E0, 0xFFFCE7D6, 0xFFFFF1C9, 0xFFE3F4E1, 0xFFD8F0EC, 0xFFDDEBFA, 0xFFE6E0FA,
    0xFFE8D9C5, 0xFFC7B299, 0xFF9FB7A0, 0xFF8FA8C8, 0xFFB79FD1, 0xFFE59AB0, 0xFFF2B880, 0xFF5B4B8A,
).map { it.toInt() }

val Gradients = listOf(
    0xFFFFFFFF to 0xFFE8E4F2, 0xFFFDFBFB to 0xFFEBEDEE, 0xFFFFF1EB to 0xFFACE0F9, 0xFFFFDDE1 to 0xFFEE9CA7,
    0xFFE0C3FC to 0xFF8EC5FC, 0xFFFBC2EB to 0xFFA6C1EE, 0xFFFFECD2 to 0xFFFCB69F, 0xFFD4FC79 to 0xFF96E6A1,
    0xFFF5F7FA to 0xFFC3CFE2, 0xFFFAD0C4 to 0xFFFFD1FF, 0xFF89F7FE to 0xFF66A6FF, 0xFF434343 to 0xFF000000,
).map { it.first.toInt() to it.second.toInt() }

val StudioPresets = listOf(
    0xFFFFFFFF to 0xFFD9D6DE, 0xFFFFFFFF to 0xFFE9DDD0, 0xFFFDF6F8 to 0xFFE8C9D3, 0xFFF5FAFF to 0xFFC5D5E8,
    0xFFF6FFF8 to 0xFFC6DDCB, 0xFF6A6A6A to 0xFF1E1E1E, 0xFFFFF8EC to 0xFFDCC7A6, 0xFFF4F0FF to 0xFFC8BCEB,
).map { it.first.toInt() to it.second.toInt() }

@Composable
fun ToolPanel(ui: EditorUi, vm: EditorViewModel, onEditText: (Overlay?) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        when (ui.tool) {
            Tool.CUTOUT -> CutoutPanel(ui, vm)
            Tool.MASK -> BrushPanel(ui, vm, mask = true)
            Tool.ERASER -> BrushPanel(ui, vm, mask = false)
            Tool.BACKGROUND -> BackgroundPanel(ui, vm)
            Tool.SHADOW -> ShadowPanel(ui, vm)
            Tool.FORMAT -> FormatPanel(ui, vm)
            Tool.LIGHT -> LightPanel(ui, vm)
            Tool.TEXT -> TextPanel(ui, vm, onEditText)
            Tool.STICKER -> StickerPanel(vm)
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ModelDownloadCard(model: LocalModel, state: ModelState?, onDownload: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(model.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(model.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Spacer(Modifier.height(8.dp))
            when (state) {
                is ModelState.Downloading -> {
                    LinearWavyProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Text("${(state.progress * 100).toInt()} % von ${model.sizeBytes / 1_000_000} MB", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(top = 4.dp))
                }
                is ModelState.Failed -> {
                    Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onDownload) { Text("Erneut versuchen") }
                }
                ModelState.Installed -> Text("Installiert ✓", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                else -> FilledTonalButton(onClick = onDownload) {
                    Icon(Icons.Rounded.CloudDownload, null); Spacer(Modifier.width(8.dp)); Text("Laden (${model.sizeBytes / 1_000_000} MB, einmalig)")
                }
            }
        }
    }
}

@Composable
private fun CutoutPanel(ui: EditorUi, vm: EditorViewModel) {
    val p = ui.params
    SwitchRow("Hintergrund entfernen", if (ui.hasMask) "Motiv freigestellt" else "Kein Motiv erkannt", p.removeBg && ui.hasMask) { on ->
        if (on && !ui.hasMask) vm.resegment() else vm.update { it.copy(removeBg = on) }
    }
    SectionTitle("KI-Modell")
    Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween), modifier = Modifier.fillMaxWidth()) {
        ToggleButton(
            checked = ui.engineUsed != SegEngine.HD,
            onCheckedChange = { vm.resegment(SegEngine.STANDARD) },
            modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
        ) { Text("Standard") }
        ToggleButton(
            checked = ui.engineUsed == SegEngine.HD,
            onCheckedChange = { if (ui.hdInstalled) vm.resegment(SegEngine.HD) else vm.downloadModel(LocalModel.HD_CUTOUT) },
            modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
        ) { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("HD") }
    }
    if (!ui.hdInstalled) {
        Spacer(Modifier.height(8.dp))
        ModelDownloadCard(LocalModel.HD_CUTOUT, ui.modelStates[LocalModel.HD_CUTOUT]) { vm.downloadModel(LocalModel.HD_CUTOUT) }
    }
    AnimatedVisibility(p.removeBg && ui.hasMask) {
        Column {
            Spacer(Modifier.height(6.dp))
            LabeledSlider("Kantenweichheit", p.edge.feather, { v -> vm.updateLive { it.copy(edge = it.edge.copy(feather = v)) } }, vm::commitLive,
                0f..4f, { String.format("%.1f px", it) })
            LabeledSlider("Kante", p.edge.shift, { v -> vm.updateLive { it.copy(edge = it.edge.copy(shift = v)) } }, vm::commitLive,
                -5f..5f, { v -> when { v < -0.5f -> "${(-v).toInt()} px enger"; v > 0.5f -> "${v.toInt()} px weiter"; else -> "genau" } })
            SwitchRow("Farbsaum entfernen", "Entfernt Farbreste vom alten Hintergrund an den Kanten", p.edge.decontaminate) { on ->
                vm.update { it.copy(edge = it.edge.copy(decontaminate = on)) }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { vm.selectTool(Tool.MASK) }) { Icon(Icons.Rounded.Brush, null); Spacer(Modifier.width(6.dp)); Text("Nachbessern") }
        OutlinedButton(onClick = { vm.resegment() }) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Neu erkennen") }
    }
}

@Composable
private fun BrushPanel(ui: EditorUi, vm: EditorViewModel, mask: Boolean) {
    if (mask) {
        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween), modifier = Modifier.fillMaxWidth()) {
            ToggleButton(
                checked = !ui.brushRestore, onCheckedChange = { vm.setBrushRestore(false) },
                modifier = Modifier.weight(1f), shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                colors = ToggleButtonDefaults.colors(checkedContainerColor = Color(0xFFFF2E63), checkedContentColor = Color.White),
            ) { Text("Entfernen") }
            ToggleButton(
                checked = ui.brushRestore, onCheckedChange = { vm.setBrushRestore(true) },
                modifier = Modifier.weight(1f), shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                colors = ToggleButtonDefaults.colors(checkedContainerColor = Color(0xFF16A34A), checkedContentColor = Color.White),
            ) { Text("Wiederherstellen") }
        }
        Text("Rot markiert = wird entfernt. Mit zwei Fingern zoomen für feine Details.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    } else {
        Text("Male über Fusseln, Flecken oder Gegenstände – sie verschwinden sofort. Mit zwei Fingern zoomen.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!ui.lamaInstalled) {
            Spacer(Modifier.height(8.dp))
            ModelDownloadCard(LocalModel.LAMA, ui.modelStates[LocalModel.LAMA]) { vm.downloadModel(LocalModel.LAMA) }
        }
    }
    LabeledSlider("Pinselgröße", ui.brushSize, vm::setBrushSize, {}, 6f..80f, { "${it.toInt()}" })
}

private enum class BgTab(val label: String) { COLOR("Farbe"), GRADIENT("Verlauf"), STUDIO("Studio"), BLUR("Unscharf"), IMAGE("Foto"), TRANSPARENT("Transparent") }

@Composable
private fun BackgroundPanel(ui: EditorUi, vm: EditorViewModel) {
    val bg = ui.params.background
    if (!ui.params.removeBg || !ui.hasMask) {
        Text("Der Hintergrund kann geändert werden, sobald das Motiv freigestellt ist.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = { if (ui.hasMask) vm.update { it.copy(removeBg = true) } else vm.resegment() }) {
            Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Hintergrund entfernen")
        }
        return
    }
    val current = when (bg.type) {
        BgType.COLOR -> BgTab.COLOR; BgType.GRADIENT -> BgTab.GRADIENT; BgType.STUDIO -> BgTab.STUDIO
        BgType.BLUR -> BgTab.BLUR; BgType.IMAGE -> BgTab.IMAGE; BgType.TRANSPARENT -> BgTab.TRANSPARENT
    }
    var tab by rememberSaveable { mutableStateOf(current) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.setBackgroundImage(uri) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BgTab.entries.forEach { t ->
            FilterChip(selected = tab == t, onClick = {
                tab = t
                when (t) {
                    BgTab.TRANSPARENT -> vm.update { it.copy(background = it.background.copy(type = BgType.TRANSPARENT)) }
                    BgTab.BLUR -> vm.update { it.copy(background = it.background.copy(type = BgType.BLUR)) }
                    else -> {}
                }
            }, label = { Text(t.label) })
        }
    }
    Spacer(Modifier.height(10.dp))
    fun set(f: (EditParams) -> EditParams) = vm.update(f)
    when (tab) {
        BgTab.COLOR -> {
            SwatchRow {
                SolidColors.forEach { c ->
                    ColorSwatch(Color(c), bg.type == BgType.COLOR && bg.color1 == c, { set { it.copy(background = it.background.copy(type = BgType.COLOR, color1 = c)) } })
                }
            }
            HuePicker(bg.color1, vm::commitLive) { c -> vm.updateLive { it.copy(background = it.background.copy(type = BgType.COLOR, color1 = c)) } }
        }
        BgTab.GRADIENT -> SwatchRow {
            Gradients.forEach { (a, b) ->
                ColorSwatch(Color(a), bg.type == BgType.GRADIENT && bg.color1 == a && bg.color2 == b,
                    { set { it.copy(background = it.background.copy(type = BgType.GRADIENT, color1 = a, color2 = b)) } },
                    brush = Brush.linearGradient(listOf(Color(a), Color(b))))
            }
        }
        BgTab.STUDIO -> {
            SwatchRow {
                StudioPresets.forEach { (a, b) ->
                    ColorSwatch(Color(a), bg.type == BgType.STUDIO && bg.color1 == a && bg.color2 == b,
                        { set { it.copy(background = it.background.copy(type = BgType.STUDIO, color1 = a, color2 = b)) } },
                        brush = Brush.radialGradient(listOf(Color(a), Color(b))))
                }
            }
            Text("Weiches Studiolicht wie in einer Fotobox.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BgTab.BLUR -> {
            LabeledSlider("Unschärfe", bg.blur, { v -> vm.updateLive { it.copy(background = it.background.copy(type = BgType.BLUR, blur = v)) } }, vm::commitLive)
            LabeledSlider("Abdunkeln", bg.dim, { v -> vm.updateLive { it.copy(background = it.background.copy(dim = v)) } }, vm::commitLive)
        }
        BgTab.IMAGE -> {
            FilledTonalButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Icon(Icons.Rounded.Image, null); Spacer(Modifier.width(8.dp)); Text("Eigenes Hintergrundfoto wählen")
            }
            if (bg.type == BgType.IMAGE) {
                LabeledSlider("Abdunkeln", bg.dim, { v -> vm.updateLive { it.copy(background = it.background.copy(dim = v)) } }, vm::commitLive)
            }
        }
        BgTab.TRANSPARENT -> Text("Transparent wird als PNG gespeichert – ideal zum Weiterverwenden.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwatchRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** Simple hue + lightness picker for custom colours. */
@Composable
private fun HuePicker(color: Int, onDone: () -> Unit, onColor: (Int) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) } }
    var hue by remember { mutableStateOf(hsv[0]) }
    var light by remember { mutableStateOf(if (hsv[1] < 0.02f) hsv[2] * 0.5f + (if (hsv[2] > 0.99f) 0.5f else 0f) else if (hsv[2] < 0.99f) hsv[2] * 0.5f else 1f - hsv[1] / 1.7f) }
    fun emit() {
        val l = light
        val c = if (l >= 0.5f) android.graphics.Color.HSVToColor(floatArrayOf(hue, (1 - l) * 2f * 0.85f, 1f))
        else android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.85f, l * 2f))
        onColor(c)
    }
    val hues = remember { (0..12).map { Color.hsv(it * 30f % 360f, 0.8f, 1f) } }
    Box(Modifier.fillMaxWidth().height(14.dp).padding(horizontal = 10.dp).clip(CircleShape).background(Brush.horizontalGradient(hues)))
    androidx.compose.material3.Slider(value = hue, onValueChange = { hue = it; emit() }, valueRange = 0f..360f, onValueChangeFinished = onDone)
    Box(Modifier.fillMaxWidth().height(14.dp).padding(horizontal = 10.dp).clip(CircleShape)
        .background(Brush.horizontalGradient(listOf(Color.Black, Color.hsv(hue, 0.85f, 1f), Color.White))))
    androidx.compose.material3.Slider(value = light, onValueChange = { light = it; emit() }, valueRange = 0f..1f, onValueChangeFinished = onDone)
}

private data class ShadowOption(val type: ShadowType, val label: String)
private val shadowOptions = listOf(
    ShadowOption(ShadowType.NONE, "Keiner"), ShadowOption(ShadowType.SOFT, "Weich"), ShadowOption(ShadowType.GROUND, "Boden"),
    ShadowOption(ShadowType.FLOAT, "Schwebend"), ShadowOption(ShadowType.CAST, "Schräg"), ShadowOption(ShadowType.REFLECTION, "Spiegelung"),
)

@Composable
private fun ShadowPanel(ui: EditorUi, vm: EditorViewModel) {
    val sh = ui.params.shadow
    if (!ui.params.removeBg || !ui.hasMask) {
        Text("Schatten gibt es für freigestellte Motive.", style = MaterialTheme.typography.bodyMedium); return
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        shadowOptions.forEach { o ->
            FilterChip(selected = sh.type == o.type, onClick = { vm.update { it.copy(shadow = it.shadow.copy(type = o.type)) } }, label = { Text(o.label) })
        }
    }
    AnimatedVisibility(sh.type != ShadowType.NONE) {
        Column {
            LabeledSlider("Stärke", sh.opacity, { v -> vm.updateLive { it.copy(shadow = it.shadow.copy(opacity = v)) } }, vm::commitLive)
            if (sh.type != ShadowType.REFLECTION)
                LabeledSlider("Weichheit", sh.softness, { v -> vm.updateLive { it.copy(shadow = it.shadow.copy(softness = v)) } }, vm::commitLive)
            LabeledSlider(if (sh.type == ShadowType.REFLECTION) "Länge" else if (sh.type == ShadowType.FLOAT) "Höhe" else "Abstand",
                sh.distance, { v -> vm.updateLive { it.copy(shadow = it.shadow.copy(distance = v)) } }, vm::commitLive)
            if (sh.type == ShadowType.SOFT || sh.type == ShadowType.CAST)
                LabeledSlider("Lichtrichtung", sh.angle, { v -> vm.updateLive { it.copy(shadow = it.shadow.copy(angle = v)) } }, vm::commitLive,
                    0f..180f, { "${it.toInt()}°" })
        }
    }
}

@Composable
private fun FormatPanel(ui: EditorUi, vm: EditorViewModel) {
    val p = ui.params
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CanvasFormat.entries.forEach { f ->
            FilterChip(selected = p.format == f, onClick = { vm.update { it.copy(format = f) } }, label = { Text(f.label) })
        }
    }
    Text("Tipp: Motiv mit einem Finger verschieben, mit zwei Fingern drehen & zoomen.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
    if (!p.sourcePlacement)
        LabeledSlider("Rand", p.padding, { v -> vm.updateLive { it.copy(padding = v) } }, vm::commitLive, 0f..0.35f)
    LabeledSlider("Größe", p.subjectScale, { v -> vm.updateLive { it.copy(subjectScale = v) } }, vm::commitLive, 0.3f..2.5f, { "${(it * 100).toInt()} %" })
    FilledTonalButton(onClick = { vm.update { it.copy(subjectScale = 1f, subjectOffsetX = 0f, subjectOffsetY = 0f, subjectRotation = 0f) } }) {
        Icon(Icons.Rounded.CenterFocusStrong, null); Spacer(Modifier.width(8.dp)); Text("Zentrieren & gerade")
    }
}

@Composable
private fun LightPanel(ui: EditorUi, vm: EditorViewModel) {
    val a = ui.params.adjust
    val pct: (Float) -> String = { v -> (if (v > 0) "+" else "") + (v * 100).toInt() }
    LabeledSlider("Helligkeit", a.brightness, { v -> vm.updateLive { it.copy(adjust = it.adjust.copy(brightness = v)) } }, vm::commitLive, -1f..1f, pct)
    LabeledSlider("Kontrast", a.contrast, { v -> vm.updateLive { it.copy(adjust = it.adjust.copy(contrast = v)) } }, vm::commitLive, -1f..1f, pct)
    LabeledSlider("Sättigung", a.saturation, { v -> vm.updateLive { it.copy(adjust = it.adjust.copy(saturation = v)) } }, vm::commitLive, -1f..1f, pct)
    LabeledSlider("Wärme", a.warmth, { v -> vm.updateLive { it.copy(adjust = it.adjust.copy(warmth = v)) } }, vm::commitLive, -1f..1f, pct)
    OutlinedButton(onClick = { vm.update { it.copy(adjust = de.glanzstueck.app.imaging.Adjust()) } }) {
        Icon(Icons.Rounded.RestartAlt, null); Spacer(Modifier.width(8.dp)); Text("Zurücksetzen")
    }
}

val TextColors = listOf(0xFF1C1B1F, 0xFFFFFFFF, 0xFF5B3FE0, 0xFFE4405F, 0xFFFF8A00, 0xFF16A34A, 0xFF0EA5E9, 0xFFFFD60A, 0xFF8B5E3C).map { it.toInt() }

private val styleLabels = listOf(
    OverlayStyle.PLAIN to "Schlicht", OverlayStyle.SHADOW to "Schatten", OverlayStyle.PILL to "Pille", OverlayStyle.OUTLINE to "Rahmen",
    OverlayStyle.BURST to "Stern", OverlayStyle.CIRCLE to "Kreis", OverlayStyle.RIBBON to "Band",
)

fun composeFont(f: OverlayFont): Pair<FontFamily, FontWeight> = when (f) {
    OverlayFont.MODERN -> FontFamily.SansSerif to FontWeight.Medium
    OverlayFont.BOLD -> FontFamily.SansSerif to FontWeight.Black
    OverlayFont.ELEGANT -> FontFamily.Serif to FontWeight.Normal
    OverlayFont.SCRIPT -> FontFamily.Cursive to FontWeight.Bold
    OverlayFont.PLAYFUL -> FontFamily.Cursive to FontWeight.Normal
    OverlayFont.CONDENSED -> FontFamily.SansSerif to FontWeight.Bold
    OverlayFont.MONO -> FontFamily.Monospace to FontWeight.Bold
}

@Composable
private fun TextPanel(ui: EditorUi, vm: EditorViewModel, onEditText: (Overlay?) -> Unit) {
    val sel = ui.params.overlays.firstOrNull { it.id == ui.selectedOverlay }
    if (sel == null) {
        Text("Füge Text hinzu, z. B. Marke, Größe oder Preis. Tippe auf einen Text im Bild, um ihn zu bearbeiten.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = { onEditText(null) }) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Text hinzufügen") }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(onClick = { onEditText(sel) }) { Icon(Icons.Rounded.Edit, null); Spacer(Modifier.width(6.dp)); Text("Bearbeiten") }
        androidx.compose.material3.IconButton(onClick = { vm.duplicateOverlay(sel.id) }) { Icon(Icons.Rounded.ContentCopy, "Duplizieren") }
        androidx.compose.material3.IconButton(onClick = { vm.bringToFront(sel.id) }) { Icon(Icons.Rounded.FlipToFront, "Nach vorne") }
        androidx.compose.material3.IconButton(onClick = { vm.deleteOverlay(sel.id) }) { Icon(Icons.Rounded.Delete, "Löschen", tint = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { onEditText(null) }) { Icon(Icons.Rounded.Add, null); Text("Neu") }
    }
    SectionTitle("Schrift")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OverlayFont.entries.forEach { f ->
            val (fam, w) = composeFont(f)
            FilterChip(selected = sel.font == f, onClick = { vm.updateOverlay(sel.id) { it.copy(font = f) } },
                label = { Text(f.label, fontFamily = fam, fontWeight = w, fontStyle = if (f == OverlayFont.ELEGANT) FontStyle.Italic else FontStyle.Normal) })
        }
    }
    SectionTitle("Stil")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        styleLabels.forEach { (s, l) -> FilterChip(selected = sel.style == s, onClick = { vm.updateOverlay(sel.id) { it.copy(style = s) } }, label = { Text(l) }) }
    }
    SectionTitle("Textfarbe")
    SwatchRow { TextColors.forEach { c -> ColorSwatch(Color(c), sel.color == c, { vm.updateOverlay(sel.id) { it.copy(color = c) } }, size = 36.dp) } }
    if (sel.style !in listOf(OverlayStyle.PLAIN, OverlayStyle.SHADOW, OverlayStyle.OUTLINE)) {
        SectionTitle("Hintergrundfarbe")
        SwatchRow { TextColors.forEach { c -> ColorSwatch(Color(c), sel.accent == c, { vm.updateOverlay(sel.id) { it.copy(accent = c) } }, size = 36.dp) } }
    }
    LabeledSlider("Größe", sel.size, { v -> vm.updateOverlay(sel.id, live = true) { it.copy(size = v) } }, vm::commitLive, 0.02f..0.25f, { "${(it * 1000).toInt()}" })
}

data class StickerPreset(val text: String, val style: OverlayStyle, val color: Int, val accent: Int, val font: OverlayFont = OverlayFont.BOLD, val size: Float = 0.055f)

val StickerPresets = listOf(
    StickerPreset("NEU", OverlayStyle.BURST, 0xFFFFFFFF.toInt(), 0xFFE4405F.toInt(), size = 0.06f),
    StickerPreset("NEU mit\nEtikett", OverlayStyle.BURST, 0xFFFFFFFF.toInt(), 0xFF5B3FE0.toInt(), size = 0.042f),
    StickerPreset("Wie neu", OverlayStyle.PILL, 0xFFFFFFFF.toInt(), 0xFF16A34A.toInt()),
    StickerPreset("Sehr gut", OverlayStyle.PILL, 0xFF1C1B1F.toInt(), 0xFFFFD60A.toInt()),
    StickerPreset("Top Zustand", OverlayStyle.RIBBON, 0xFFFFFFFF.toInt(), 0xFF0EA5E9.toInt()),
    StickerPreset("SALE", OverlayStyle.CIRCLE, 0xFFFFFFFF.toInt(), 0xFFFF8A00.toInt(), size = 0.06f),
    StickerPreset("Bundle-Rabatt", OverlayStyle.RIBBON, 0xFFFFFFFF.toInt(), 0xFFE4405F.toInt()),
    StickerPreset("Größe M", OverlayStyle.OUTLINE, 0xFF1C1B1F.toInt(), 0, OverlayFont.MODERN),
    StickerPreset("Original", OverlayStyle.PILL, 0xFFFFFFFF.toInt(), 0xFF1C1B1F.toInt(), OverlayFont.CONDENSED),
    StickerPreset("Nichtraucher", OverlayStyle.PILL, 0xFF1C1B1F.toInt(), 0xFFE3F4E1.toInt(), OverlayFont.MODERN, 0.045f),
    StickerPreset("Tierfrei", OverlayStyle.PILL, 0xFF1C1B1F.toInt(), 0xFFFCE7D6.toInt(), OverlayFont.MODERN, 0.045f),
    StickerPreset("Versand\nmöglich", OverlayStyle.CIRCLE, 0xFFFFFFFF.toInt(), 0xFF5B3FE0.toInt(), OverlayFont.BOLD, 0.038f),
    StickerPreset("✨", OverlayStyle.PLAIN, 0xFF1C1B1F.toInt(), 0, OverlayFont.MODERN, 0.09f),
    StickerPreset("💖", OverlayStyle.PLAIN, 0xFF1C1B1F.toInt(), 0, OverlayFont.MODERN, 0.09f),
    StickerPreset("🔥", OverlayStyle.PLAIN, 0xFF1C1B1F.toInt(), 0, OverlayFont.MODERN, 0.09f),
    StickerPreset("⭐", OverlayStyle.PLAIN, 0xFF1C1B1F.toInt(), 0, OverlayFont.MODERN, 0.09f),
)

@Composable
private fun StickerPanel(vm: EditorViewModel) {
    Text("Tippe auf einen Sticker – danach kannst du ihn verschieben, drehen und vergrößern.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StickerPresets.forEach { s -> StickerChip(s) {
            vm.addOverlay(Overlay(System.nanoTime(), s.text, x = 0.78f, y = 0.16f, size = s.size, color = s.color, accent = s.accent, style = s.style, font = s.font))
            vm.selectTool(Tool.TEXT)
        } }
    }
}

@Composable
private fun StickerChip(s: StickerPreset, onClick: () -> Unit) {
    val shape = when (s.style) {
        OverlayStyle.BURST -> androidx.compose.material3.MaterialShapes.SoftBurst.toShape()
        OverlayStyle.CIRCLE -> CircleShape
        OverlayStyle.RIBBON -> RoundedCornerShape(6.dp)
        else -> RoundedCornerShape(50)
    }
    val (fam, w) = composeFont(s.font)
    val bgMod = when (s.style) {
        OverlayStyle.PLAIN, OverlayStyle.SHADOW -> Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
        OverlayStyle.OUTLINE -> Modifier.border(2.dp, Color(s.color), RoundedCornerShape(50)).background(Color.White, RoundedCornerShape(50))
        else -> Modifier.background(Color(s.accent), shape)
    }
    val round = s.style == OverlayStyle.BURST || s.style == OverlayStyle.CIRCLE
    Box(
        Modifier
            .then(if (round) Modifier.size(74.dp) else Modifier.height(40.dp))
            .clip(if (round) shape else RoundedCornerShape(50))
            .then(bgMod)
            .clickable(onClick = onClick)
            .padding(horizontal = if (round) 6.dp else 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(s.text, color = Color(s.color), fontFamily = fam, fontWeight = w, fontSize = if (round) 13.sp else 15.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 14.sp)
    }
}

@Composable
fun TextEditDialog(initial: Overlay?, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial?.text ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Edit, null) },
        title = { Text(if (initial == null) "Text hinzufügen" else "Text bearbeiten") },
        text = {
            Column {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Text") }, minLines = 1, maxLines = 4, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Größe S", "Größe M", "Größe L", "Neu", "Zara", "H&M", "Nike", "Wie neu", "Ungetragen").forEach { s ->
                        androidx.compose.material3.SuggestionChip(onClick = { text = s }, label = { Text(s) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) else onDismiss() }) { Text("Fertig") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
