package de.glanzstueck.app.ui.home

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Texture
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.glanzstueck.app.data.ImageIO
import de.glanzstueck.app.data.ProjectInfo
import de.glanzstueck.app.glanz
import de.glanzstueck.app.ui.components.animatedBrandBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Tip(val icon: ImageVector, val title: String, val text: String)

private val tips = listOf(
    Tip(Icons.Rounded.LightMode, "Tageslicht", "Am Fenster fotografieren – ohne Blitz. Die App macht den Rest."),
    Tip(Icons.Rounded.Texture, "Glatt hinlegen", "Kleidung glatt streichen. Der Hintergrund ist egal, er wird ersetzt."),
    Tip(Icons.Rounded.Straighten, "Ganz drauf", "Das ganze Teil ins Bild, mit etwas Abstand zum Rand."),
    Tip(Icons.Rounded.Checkroom, "Details zeigen", "Etikett, Stoff und Mängel extra fotografieren – das schafft Vertrauen."),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onOpenImage: (Uri) -> Unit,
    onOpenProject: (String) -> Unit,
    onBatch: (List<Uri>) -> Unit,
    onSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val store = ctx.glanz.projects
    val projects by store.projects.collectAsState()
    val scope = rememberCoroutineScope()
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var deleteCandidate by remember { mutableStateOf<ProjectInfo?>(null) }

    LaunchedEffect(Unit) { store.refresh() }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        if (ok && u != null) onOpenImage(u)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onOpenImage(uri) }
    val multi = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris -> if (uris.isNotEmpty()) onBatch(uris) }

    val insets = WindowInsets.systemBars.asPaddingValues()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = insets.calculateTopPadding() + 8.dp, bottom = insets.calculateBottomPadding() + 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Entrance(0) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Hallo! 👋", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Glanzstück", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    }
                    FilledIconButton(
                        onClick = onSettings,
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(),
                    ) { Icon(Icons.Rounded.Settings, "Einstellungen") }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Entrance(1) {
                HeroCard(
                    onCamera = {
                        val u = ImageIO.newCameraUri(ctx)
                        cameraUri = u
                        camera.launch(u)
                    },
                    onGallery = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Entrance(2) {
                Card(
                    onClick = { multi.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.secondary, MaterialShapes.Clover4Leaf.toShape()), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Collections, null, tint = MaterialTheme.colorScheme.onSecondary)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Mehrere Fotos auf einmal", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text("Bis zu 30 Fotos automatisch freistellen – alle im gleichen Look.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Entrance(3) {
                Column {
                    Text("Tipps für Top-Fotos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(tips) { t ->
                            Card(Modifier.width(220.dp), shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                Column(Modifier.padding(14.dp)) {
                                    Icon(t.icon, null, tint = MaterialTheme.colorScheme.tertiary)
                                    Spacer(Modifier.height(6.dp))
                                    Text(t.title, style = MaterialTheme.typography.titleSmall)
                                    Text(t.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Entrance(4) {
                Text(if (projects.isEmpty()) "Meine Projekte" else "Meine Projekte (${projects.size})", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (projects.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Entrance(5) {
                    Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(28.dp),
                        contentAlignment = Alignment.Center) {
                        Text("Hier erscheinen Fotos, die du „in der App“ speicherst – zum späteren Weiterbearbeiten.",
                            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(projects, key = { it.id }) { p ->
                val thumb = rememberThumb(p)
                Card(
                    modifier = Modifier.animateItem().combinedClickable(onClick = { onOpenProject(p.id) }, onLongClick = { deleteCandidate = p }),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.8f).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                        if (thumb != null) Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    deleteCandidate?.let { p ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text("Projekt löschen?") },
            text = { Text("Das Projekt wird aus der App entfernt. Bereits in der Galerie gespeicherte Fotos bleiben erhalten.") },
            confirmButton = { TextButton(onClick = { scope.launch { store.delete(p.id) }; deleteCandidate = null }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun rememberThumb(p: ProjectInfo): ImageBitmap? {
    val v by produceState<ImageBitmap?>(null, p.id, p.modified) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(p.thumb.absolutePath)?.asImageBitmap() }
    }
    return v
}

/** Staggered spring entrance for home screen sections. */
@Composable
private fun Entrance(index: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(70L * index)
        a.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 220f))
    }
    Box(Modifier.graphicsLayer {
        alpha = a.value.coerceIn(0f, 1f)
        translationY = (1f - a.value) * 60.dp.toPx()
        val s = 0.94f + 0.06f * a.value
        scaleX = s; scaleY = s
    }) { content() }
}

@Composable
private fun HeroCard(onCamera: () -> Unit, onGallery: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "hero")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(18000, easing = LinearEasing)), label = "rot")
    val bob by inf.animateFloat(-6f, 6f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "bob")
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(animatedBrandBrush()),
    ) {
        // decorative rotating expressive shapes
        Box(
            Modifier.size(190.dp).align(Alignment.TopEnd).offset(x = 60.dp, y = (-50).dp)
                .graphicsLayer { rotationZ = rot }
                .background(Color.White.copy(alpha = 0.14f), MaterialShapes.Cookie12Sided.toShape()),
        )
        Box(
            Modifier.size(90.dp).align(Alignment.BottomEnd).offset(x = (-24).dp, y = 30.dp)
                .graphicsLayer { rotationZ = -rot * 1.5f }
                .background(Color.White.copy(alpha = 0.12f), MaterialShapes.Flower.toShape()),
        )
        Column(Modifier.padding(22.dp)) {
            Box(
                Modifier.size(56.dp).graphicsLayer { translationY = bob.dp.toPx() / 2 }
                    .background(Color.White, MaterialShapes.Sunny.toShape()),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFF7B5CFF)) }
            Spacer(Modifier.height(14.dp))
            Text("Was verkaufst du heute?", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text("Foto machen – Hintergrund weg, Schatten drauf, fertig fürs Inserat.",
                style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onCamera,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF4B2BB8)),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Rounded.PhotoCamera, null); Spacer(Modifier.width(10.dp))
                Text("Foto aufnehmen", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onGallery,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.22f), contentColor = Color.White),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Rounded.PhotoLibrary, null); Spacer(Modifier.width(10.dp))
                Text("Aus der Galerie wählen", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
