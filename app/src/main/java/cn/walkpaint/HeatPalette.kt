package cn.walkpaint

import androidx.compose.ui.graphics.Color

/**
 * The footprint calendar shades, and the rule for what colour sits on top of them.
 *
 * This lives outside the screens so a unit test can assert the numbers instead of a reviewer having
 * to eyeball a phone. The shade a level produces is composited over the card colour first, because a
 * translucent pigment over white is exactly what the eye sees.
 *
 * The previous version hard-coded white for levels 3 and 4 and the faint token for an empty day,
 * which measured 2.30:1, 3.10:1 and 2.49:1. Body text needs 4.5:1, so both pairings are now chosen
 * by measurement rather than by eye.
 */
internal object HeatPalette {
    /** WCAG 2.2 AA minimum contrast for normal text. */
    const val MIN_TEXT_CONTRAST = 4.5

    /** Recorded days are banded 1..4; 0 is an empty day and -1 is a day that has not happened. */
    const val MAX_LEVEL = 4

    /** Label candidates. The winner is whichever reads better on the shade, never a hard-coded white. */
    val textCandidates = listOf(Ink, Color.White)

    /** A day with no record: the faintest cell, still carrying its date. */
    val emptyFill: Color = Line

    /** The date drawn inside [emptyFill]. Faint measured 2.49:1 here, so it is Muted at 4.59:1. */
    val emptyText: Color = Muted

    /** Opacity of the pigment for a recorded level. Level 1 is a light wash, level 4 is deep. */
    fun alpha(level: Int): Float = 0.22f + 0.195f * (level - 1)

    /** The shade as it appears on [background], with the pigment alpha already composited. */
    fun fillOver(level: Int, background: Color = Canvas): Color {
        require(level >= 1) { "level 0 is the empty cell and has no pigment" }
        val a = alpha(level).coerceIn(0f, 1f)
        return Color(
            red = Pigment.red * a + background.red * (1f - a),
            green = Pigment.green * a + background.green * (1f - a),
            blue = Pigment.blue * a + background.blue * (1f - a),
            alpha = 1f,
        )
    }

    /** The label colour for a recorded level: whichever candidate actually reads better. */
    fun textOn(level: Int): Color = textCandidates.maxBy { contrast(fillOver(level), it) }

    /** WCAG relative luminance. */
    fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    /** WCAG contrast ratio between two opaque colours. */
    fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
