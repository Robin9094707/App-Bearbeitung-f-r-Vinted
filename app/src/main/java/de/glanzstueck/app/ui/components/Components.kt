package de.glanzstueck.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.glanzstueck.app.ui.theme.BrandGradient

/** Transparency checkerboard like in pro photo editors. */
fun Modifier.checkerboard(cell: Dp = 10.dp, light: Color = Color(0xFFF4F4F4), dark: Color = Color(0xFFDCDCDC)) = drawBehind {
    val c = cell.toPx()
    drawRect(light)
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else c
        while (x < size.width) {
            drawRect(dark, Offset(x, y), Size(c, c))
            x += 2 * c
        }
        y += c; row++
    }
}

/** Slowly flowing brand gradient used on hero surfaces. */
@Composable
fun animatedBrandBrush(durationMs: Int = 6000): Brush {
    val t = rememberInfiniteTransition(label = "grad")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Reverse), label = "shift")
    return Brush.linearGradient(
        colors = BrandGradient,
        start = Offset(0f + shift * 600f, 0f),
        end = Offset(900f + shift * 300f, 900f - shift * 400f),
    )
}

@Composable
fun ToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.45f, stiffness = 600f), label = "s")
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, label = "bg")
    val fg by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, label = "fg")
    val shape = if (selected) MaterialShapes.Cookie9Sided.toShape() else CircleShape
    val rot by animateFloatAsState(if (selected) 0f else -60f, spring(dampingRatio = 0.5f, stiffness = 200f), label = "r")
    Column(
        modifier = modifier
            .width(72.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .scale(scale)
                .graphicsLayer { rotationZ = rot }
                .background(bg, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.graphicsLayer { rotationZ = -rot })
        }
        Spacer(Modifier.padding(top = 4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    valueText: (Float) -> String = { "${(it * 100).toInt()} %" },
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(valueText(value), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, onValueChangeFinished = onDone, valueRange = range)
    }
}

@Composable
fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit, brush: Brush? = null, size: Dp = 44.dp) {
    val scale by animateFloatAsState(if (selected) 1.1f else 1f, spring(dampingRatio = 0.4f), label = "sw")
    val shape = if (selected) MaterialShapes.Cookie6Sided.toShape() else CircleShape
    Box(
        Modifier
            .size(size)
            .scale(scale)
            .clip(shape)
            .then(if (brush != null) Modifier.background(brush) else Modifier.background(color))
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            val c = if (color.luminance() > 0.5f) Color.Black else Color.White
            Icon(Icons.Rounded.Check, null, tint = c, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(vertical = 6.dp))
}
