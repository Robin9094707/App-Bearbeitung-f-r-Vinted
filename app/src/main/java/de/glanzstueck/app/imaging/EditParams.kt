package de.glanzstueck.app.imaging

import kotlinx.serialization.Serializable

@Serializable
enum class BgType { TRANSPARENT, COLOR, GRADIENT, STUDIO, BLUR, IMAGE }

@Serializable
data class Background(
    val type: BgType = BgType.COLOR,
    val color1: Int = 0xFFFFFFFF.toInt(),
    val color2: Int = 0xFFE9E4F0.toInt(),
    val angle: Float = 90f,
    val blur: Float = 0.6f,
    val dim: Float = 0f,
    val imagePath: String? = null,
)

@Serializable
enum class ShadowType { NONE, SOFT, GROUND, FLOAT, CAST, REFLECTION }

@Serializable
data class ShadowSpec(
    val type: ShadowType = ShadowType.SOFT,
    val opacity: Float = 0.35f,
    val softness: Float = 0.5f,
    val distance: Float = 0.35f,
    val angle: Float = 90f,
)

@Serializable
data class EdgeSpec(
    /** Feathering radius in pixels at working resolution (0..6). */
    val feather: Float = 0.8f,
    /** Negative = shrink, positive = grow (pixels, -6..6). */
    val shift: Float = 0f,
    /** Removes colour fringes from the old background. */
    val decontaminate: Boolean = true,
)

@Serializable
data class Adjust(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
) {
    val isIdentity get() = brightness == 0f && contrast == 0f && saturation == 0f && warmth == 0f
}

@Serializable
enum class CanvasFormat(val label: String, val ratio: Float?) {
    ORIGINAL("Original", null),
    PORTRAIT_4_5("4:5", 0.8f),
    PORTRAIT_3_4("3:4", 0.75f),
    SQUARE("1:1", 1f),
    STORY_9_16("9:16", 9f / 16f),
    LANDSCAPE_4_3("4:3", 4f / 3f),
}

@Serializable
enum class OverlayStyle { PLAIN, SHADOW, PILL, OUTLINE, BURST, CIRCLE, RIBBON }

@Serializable
enum class OverlayFont(val label: String) {
    MODERN("Modern"), BOLD("Fett"), ELEGANT("Elegant"), SCRIPT("Schreibschrift"),
    PLAYFUL("Verspielt"), CONDENSED("Schmal"), MONO("Mono")
}

@Serializable
data class Overlay(
    val id: Long,
    val text: String,
    val x: Float = 0.5f,
    val y: Float = 0.85f,
    /** Text size as fraction of canvas width. */
    val size: Float = 0.07f,
    val rotation: Float = 0f,
    val color: Int = 0xFF1C1B1F.toInt(),
    val accent: Int = 0xFFFFFFFF.toInt(),
    val style: OverlayStyle = OverlayStyle.PLAIN,
    val font: OverlayFont = OverlayFont.MODERN,
)

@Serializable
data class EditParams(
    val removeBg: Boolean = true,
    val background: Background = Background(),
    val shadow: ShadowSpec = ShadowSpec(),
    val edge: EdgeSpec = EdgeSpec(),
    val adjust: Adjust = Adjust(),
    val format: CanvasFormat = CanvasFormat.PORTRAIT_4_5,
    val padding: Float = 0.10f,
    val subjectScale: Float = 1f,
    val subjectOffsetX: Float = 0f,
    val subjectOffsetY: Float = 0f,
    val subjectRotation: Float = 0f,
    val overlays: List<Overlay> = emptyList(),
) {
    /** In "source" placement the photo keeps its geometry (no auto centring). */
    val sourcePlacement: Boolean get() = !removeBg || background.type == BgType.BLUR
}
