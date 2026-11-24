package de.glanzstueck.app.ui.intro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animated opening sequence: a morphing Material shape blooms, sparkles burst out
 * and the app name rises letter by letter. Tap anywhere to skip.
 */
@Composable
fun IntroScreen(onFinished: () -> Unit) {
    val finish by rememberUpdatedState(onFinished)
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val morph2 = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.SoftBurst) }
    val bloom = remember { Animatable(0f) }
    val shape = remember { Animatable(0f) }
    val spin = remember { Animatable(-90f) }
    val burst = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val letters = "Glanzstück"

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { bloom.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 120f)) }
            launch { spin.animateTo(0f, tween(1300, easing = FastOutSlowInEasing)) }
            launch { shape.animateTo(2f, tween(1300, easing = FastOutSlowInEasing)) }
            launch { delay(380); burst.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
            val t = async { delay(450); title.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
            t.await()
        }
        delay(450)
        exit.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        finish()
    }

    val infinite = rememberInfiniteTransition(label = "intro")
    val shimmer by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "sh")
    val sparks = remember { List(18) { Triple(Random.nextFloat() * 360f, 0.6f + Random.nextFloat() * 0.8f, 2f + Random.nextFloat() * 5f) } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF241A4F), Color(0xFF4B2BB8), Color(0xFFB2387A))))
            .clickable(remember { MutableInteractionSource() }, indication = null) { finish() }
            .graphicsLayer {
                alpha = 1f - exit.value
                val s = 1f + exit.value * 0.15f
                scaleX = s; scaleY = s
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f - 60.dp.toPx()
            val r = min(size.width, size.height) * 0.22f * bloom.value
            // glow
            drawCircle(Brush.radialGradient(listOf(Color(0x66FFFFFF), Color.Transparent), center = Offset(cx, cy), radius = r * 2.6f + 1f), r * 2.6f + 1f, Offset(cx, cy))
            // morphing hero shape
            val p = shape.value
            val path: Path = if (p <= 1f) morph.toPath(p) else morph2.toPath(p - 1f)
            val b = path.getBounds()
            val scale = if (b.width > 0f) (2 * r) / b.width else 1f
            withTransform({
                translate(cx, cy)
                rotate(spin.value, Offset.Zero)
            }) {
                val m = Matrix()
                m.translate(-b.center.x * scale, -b.center.y * scale)
                m.scale(scale, scale)
                val tp = Path().apply { addPath(path) ; transform(m) }
                drawPath(tp, Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFFFD6E8)), start = Offset(-r, -r), end = Offset(r, r)))
            }
            // sparkles
            val bv = burst.value
            if (bv > 0f) {
                sparks.forEachIndexed { i, (ang, dist, sz) ->
                    val a = Math.toRadians((ang + shimmer * 40f).toDouble())
                    val d = r * (1.1f + dist * bv * 1.4f)
                    val alpha = (1f - bv).coerceIn(0f, 1f) * 0.9f + 0.1f * (if (i % 3 == 0) 1f else 0f)
                    val pos = Offset(cx + (cos(a) * d).toFloat(), cy + (sin(a) * d).toFloat())
                    drawSparkle(pos, sz.dp.toPx() * (0.6f + bv), Color.White.copy(alpha = alpha))
                }
            }
        }
        Icon(
            Icons.Rounded.Checkroom,
            contentDescription = null,
            tint = Color(0xFF5B3FE0),
            modifier = Modifier
                .graphicsLayer {
                    translationY = -60.dp.toPx()
                    val s = bloom.value
                    scaleX = s; scaleY = s
                    rotationZ = spin.value
                }
                .size(72.dp),
        )
        Column(
            Modifier.graphicsLayer { translationY = 110.dp.toPx() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.Center) {
                letters.forEachIndexed { i, ch ->
                    val start = i / letters.length.toFloat() * 0.6f
                    val local = ((title.value - start) / 0.4f).coerceIn(0f, 1f)
                    Text(
                        ch.toString(),
                        color = Color.White,
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.graphicsLayer {
                            alpha = local
                            translationY = (1f - local) * 40.dp.toPx()
                            rotationZ = (1f - local) * 12f
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Verkaufsfotos wie vom Profi",
                color = Color.White.copy(alpha = 0.85f * title.value),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.graphicsLayer { translationY = (1f - title.value) * 16.dp.toPx() },
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSparkle(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x + r * 0.12f, c.y - r * 0.12f, c.x + r, c.y)
        quadraticTo(c.x + r * 0.12f, c.y + r * 0.12f, c.x, c.y + r)
        quadraticTo(c.x - r * 0.12f, c.y + r * 0.12f, c.x - r, c.y)
        quadraticTo(c.x - r * 0.12f, c.y - r * 0.12f, c.x, c.y - r)
        close()
    }
    drawPath(p, color)
}
