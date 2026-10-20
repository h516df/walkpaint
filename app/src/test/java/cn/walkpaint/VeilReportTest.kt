package cn.walkpaint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The veil report is the only thing that can tell "my image was translucent" from "the map drew
 * over my image", because both look the same to a person holding the phone. These tests pin the
 * distinction, and pin the wording that a person reads in the export.
 */
class VeilReportTest {

    private fun pixel(alpha: Int, rgb: Int) = (alpha shl 24) or rgb

    @Test fun opaqueBlackIsReportedAsBothHidingAndBlack() {
        val stats = VeilReport.of(IntArray(100) { 0xFF000000.toInt() })
        assertEquals(1.0, stats.hiddenShare, 0.0001)
        assertEquals(1.0, stats.blackShare, 0.0001)
        assertEquals(0.0, stats.openShare, 0.0001)
    }

    @Test fun themedFogReportsConcealmentWithoutClaimingExploration() {
        val stats=VeilReport.of(IntArray(100){TrailPaint.FOG_COLOR})
        assertEquals(0.0,stats.openShare,0.0001)
        assertEquals(1.0,stats.hiddenShare,0.0001)
        assertEquals(0.0,stats.blackShare,0.0001)
        assertTrue(VeilReport.image(stats,1).contains("不代表已探索"))
    }
    @Test fun anOpaqueVeilThatIsNotBlackHidesButIsNotTheAgreedColour() {
        // Exactly the 0.39 veil: alpha 255 and navy. It hides perfectly well, and it is still not
        // what the person asked for, so a report that only counted "opaque" would have missed it.
        val navy = pixel(255, 0x0A1428)
        val stats = VeilReport.of(IntArray(100) { navy })
        assertEquals(1.0, stats.hiddenShare, 0.0001)
        assertEquals("an opaque veil in the wrong colour must not count as black",
            0.0, stats.blackShare, 0.0001)
    }

    @Test fun aThinVeilIsReportedAsGroundShowingThrough() {
        // The person's own words: the fog was thin enough to read the roads through it.
        val stats = VeilReport.of(IntArray(100) { pixel(120, 0x0A1428) })
        assertEquals("a veil under alpha 128 is not hiding anything", 1.0, stats.openShare, 0.0001)
        assertEquals(0.0, stats.hiddenShare, 0.0001)
    }

    @Test fun theRampBetweenClearedGroundAndVeilIsCountedSeparately() {
        val image = IntArray(4)
        image[0] = 0xFF000000.toInt()          // untouched veil: hides, and is black
        image[1] = pixel(200, 0)                 // the ramp: the basemap shows through faintly
        image[2] = pixel(40, 0x334455)           // walked ground, carrying the person's colour
        image[3] = pixel(0, 0)                   // fully cleared: the map itself
        val stats = VeilReport.of(image)
        assertEquals(1, stats.hidden)
        assertEquals(1, stats.black)
        assertEquals(1, stats.partial)
        assertEquals(2, stats.open)
    }

    @Test fun anOpaquePixelOfTheWrongColourStillCountsAsHiding() {
        // A fully cleared corridor tinted opaquely would hide the map just as well as fog does, so
        // "hidden" is about alpha alone; only the colour separates it from the agreed veil.
        val stats = VeilReport.of(IntArray(2) { pixel(255, 0x334455) })
        assertEquals(2, stats.hidden)
        assertEquals("opaque but not black must not be counted as the agreed veil", 0, stats.black)
    }

    @Test fun anEmptyImageDoesNotDivideByZero() {
        val stats = VeilReport.of(IntArray(0))
        assertEquals(0, stats.pixels)
        assertEquals(0.0, stats.hiddenShare, 0.0)
        assertTrue(VeilReport.image(stats, -1).contains("0像素"))
    }

    @Test fun theFrameDrawnBeforeTheMapReportsAViewCannotShowACorridor() {
        // This is the launch state the person described as "no fog until I zoom in", and it is
        // arithmetic rather than opinion: a 60 degree frame puts thirty metres under one pixel.
        val walk = listOf(TrackPoint(31.2300, 121.4700, 0L, 8f), TrackPoint(31.2306, 121.4707, 1000L, 8f))
        val wide = TrailPaint.frameFor(null, walk, walk[0])!!
        val line = VeilReport.frame(wide, lineByMap = true)
        assertTrue("the corridor must be reported as unrepresentable", line.contains("画不出走廊"))
        assertTrue("the map has to draw the line in this state", line.contains("地图画行走线=是"))
        // 60 degrees of latitude is roughly 6,600 km, so thirty metres is far under one pixel.
        assertTrue(line, line.contains("每米=0.0"))
        assertTrue("this frame is too coarse and must not inflate the corridor",
            TrailPaint.CORRIDOR_M * TrailPainter.pixelsPerMeter(wide) < 2.0)
        assertFalse(line.contains("2像素下限"))
        assertTrue(line.contains("走廊=0.0像素"))
    }

    @Test fun aStreetLevelFrameShowsTheCorridorItself() {
        val view = ViewBounds(31.2280, 121.4680, 31.2330, 121.4740)
        val walk = listOf(TrackPoint(31.2300, 121.4700, 0L, 8f), TrackPoint(31.2310, 121.4710, 1000L, 8f))
        val frame = TrailPaint.frameFor(view, walk)!!
        val line = VeilReport.frame(frame, lineByMap = false)
        assertFalse("at street level the corridor exists in the image", line.contains("画不出走廊"))
        assertTrue(line, line.contains("地图画行走线=否"))
        assertEquals("每米 should be a usable fraction at street level", true,
            TrailPainter.pixelsPerMeter(frame) > 0.05)
    }

    @Test fun aMissingFrameSaysSoInPlainLanguage() {
        assertTrue(VeilReport.frame(null, false).contains("尚未生成"))
    }

    @Test fun whenThePhoneCannotRunAmapTheExportSaysWhy() {
        val line = VeilReport.engineChoice(supported = false, firstAbi = "x86_64",
            keyConfigured = true, consent = true, active = false)
        assertTrue(line, line.contains("没有用高德"))
        assertTrue(line, line.contains("x86_64"))
        assertTrue("the ARM-only reason must be stated, not left to be guessed",
            line.contains("高德只提供 ARM 库"))
    }

    @Test fun whenAmapIsRunningTheExportSaysWhatTurnedItOn() {
        val line = VeilReport.engineChoice(supported = true, firstAbi = "arm64-v8a",
            keyConfigured = true, consent = true, active = true)
        assertTrue(line.contains("正在用高德"))
        assertTrue(line.contains("高德支持本机=是"))
        assertTrue(line.contains("高德Key=已配置"))
        assertTrue(line.contains("高德隐私同意=是"))
    }

    @Test fun aMissingKeyOrConsentIsReportedWithoutClaimingAmapIsRunning() {
        val line = VeilReport.engineChoice(supported = true, firstAbi = "arm64-v8a",
            keyConfigured = false, consent = false, active = false)
        assertTrue(line.contains("没有用高德"))
        assertTrue(line.contains("高德Key=未配置"))
        assertTrue(line.contains("高德隐私同意=否"))
        assertTrue("only the fact that a key exists is exported, never the key",
            !line.contains("高德Key=已配置"))
    }
}
