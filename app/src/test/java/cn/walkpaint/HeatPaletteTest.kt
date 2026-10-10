package cn.walkpaint

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The footprint calendar is the one screen a person looks at to feel progress, so its shades have
 * to be readable, not just pretty. Every assertion below is a WCAG number, not a screenshot opinion.
 */
class HeatPaletteTest {

    @Test
    fun everyRecordedLevelHasAReadableLabel() {
        for (level in 1..HeatPalette.MAX_LEVEL) {
            val ratio = HeatPalette.contrast(HeatPalette.fillOver(level), HeatPalette.textOn(level))
            assertTrue(
                "level $level label contrast is ${"%.2f".format(ratio)}, below ${HeatPalette.MIN_TEXT_CONTRAST}",
                ratio >= HeatPalette.MIN_TEXT_CONTRAST,
            )
        }
    }

    @Test
    fun labelIsTheBestOfTheCandidates() {
        for (level in 1..HeatPalette.MAX_LEVEL) {
            val fill = HeatPalette.fillOver(level)
            val chosen = HeatPalette.textOn(level)
            val best = HeatPalette.textCandidates.maxBy { HeatPalette.contrast(fill, it) }
            assertTrue(
                "level $level picked $chosen but $best reads better",
                HeatPalette.contrast(fill, chosen) >= HeatPalette.contrast(fill, best) - 1e-9,
            )
        }
    }

    @Test
    fun emptyDayLabelIsReadable() {
        val ratio = HeatPalette.contrast(HeatPalette.emptyFill, HeatPalette.emptyText)
        assertTrue(
            "empty day label contrast is ${"%.2f".format(ratio)}, below ${HeatPalette.MIN_TEXT_CONTRAST}",
            ratio >= HeatPalette.MIN_TEXT_CONTRAST,
        )
    }

    @Test
    fun faintIsNotUsableAsADayLabel() {
        // Kept as a written reason: the faint token measures about 2.5:1 on the empty cell, which is
        // why it is banned from carrying the date. If this ever starts passing, revisit the token.
        val ratio = HeatPalette.contrast(HeatPalette.emptyFill, Faint)
        assertTrue("Faint on the empty cell now reads ${"%.2f".format(ratio)}, re-check the palette", ratio < HeatPalette.MIN_TEXT_CONTRAST)
    }

    @Test
    fun deeperLevelsAreDarker() {
        val luminances = (1..HeatPalette.MAX_LEVEL).map { HeatPalette.luminance(HeatPalette.fillOver(it)) }
        for (i in 1 until luminances.size) {
            assertTrue("level ${i + 1} is not darker than level $i", luminances[i] < luminances[i - 1])
        }
    }

    @Test
    fun compositedFillIsOpaque() {
        for (level in 1..HeatPalette.MAX_LEVEL) {
            val fill = HeatPalette.fillOver(level)
            assertTrue("level $level is still translucent", fill.alpha == 1f)
        }
        assertTrue("empty cell must be opaque", HeatPalette.emptyFill.alpha == 1f)
    }

    @Test
    fun whiteIsOnlyUsedWhereItActuallyWins() {
        // Guards against re-introducing the old "level >= 3 means white" rule.
        for (level in 1..HeatPalette.MAX_LEVEL) {
            val ratio = HeatPalette.contrast(HeatPalette.fillOver(level), Color.White)
            if (HeatPalette.textOn(level) == Color.White) {
                assertTrue("white on level $level is unreadable at ${"%.2f".format(ratio)}", ratio >= HeatPalette.MIN_TEXT_CONTRAST)
            }
        }
    }
}
