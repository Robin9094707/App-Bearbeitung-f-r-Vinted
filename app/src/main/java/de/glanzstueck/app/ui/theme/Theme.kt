package de.glanzstueck.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.glanzstueck.app.data.ThemeMode

val Brand = Color(0xFF6B4EFF)
val BrandPink = Color(0xFFFF6FA5)
val BrandPeach = Color(0xFFFFB38A)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B3FE0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF1B0063),
    secondary = Color(0xFFB0306B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E4),
    onSecondaryContainer = Color(0xFF3E0021),
    tertiary = Color(0xFF8A5000),
    tertiaryContainer = Color(0xFFFFDCC0),
    background = Color(0xFFFDF8FF),
    surface = Color(0xFFFDF8FF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F1FD),
    surfaceContainer = Color(0xFFF2ECF8),
    surfaceContainerHigh = Color(0xFFECE6F3),
    surfaceContainerHighest = Color(0xFFE6E0ED),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCBBEFF),
    onPrimary = Color(0xFF2E0F99),
    primaryContainer = Color(0xFF4527C8),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFFFFB0CB),
    onSecondary = Color(0xFF640038),
    secondaryContainer = Color(0xFF8E1552),
    onSecondaryContainer = Color(0xFFFFD9E4),
    tertiary = Color(0xFFFFB877),
    tertiaryContainer = Color(0xFF693C00),
    background = Color(0xFF141218),
    surface = Color(0xFF141218),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun GlanzTheme(mode: ThemeMode = ThemeMode.SYSTEM, dynamic: Boolean = true, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val ctx = LocalContext.current
    val colors = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}

val BrandGradient = listOf(Color(0xFF7B5CFF), Color(0xFFA05CFF), Color(0xFFFF6FA5))
