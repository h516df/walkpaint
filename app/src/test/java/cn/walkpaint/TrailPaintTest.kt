package cn.walkpaint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailPaintTest {

    private fun point(lat: Double, lon: Double) = TrackPoint(lat, lon, 0L, 5f)
    /** Roughly [metres] of latitude at this scale. */
    private fun north(base: Double, metres: Double) = base + metres / 111194.9

    @Test fun theClearedBandIsAWideOneSoAnOffsetPassStaysInsideIt() {
        // A second pass over a street carries its own offset. As long as the offset is smaller than
        // the cleared band, the second pass repaints ground that is already clear.
        assertTrue("half band ${TrailPaint.CORRIDOR_M} m", TrailPaint.CORRIDOR_M >= 12.0)
    }

    @Test fun theEdgeOfTheBandIsSoftRatherThanStepped() {
        assertTrue(TrailPaint.FEATHER_M >= 6.0)
    }

    @Test fun theVeilIsDarkEnoughToReadAsFogAndLightEnoughToSeeStreets() {
        val alpha = TrailPaint.FOG_COLOR ushr 24
        assertTrue("alpha $alpha", alpha in 0x80..0xE8)
    }

    @Test fun theFrameItselfFadesOutSoItNeverReadsAsARectangle() {
        assertTrue(TrailPaint.EDGE_FADE in 0.05..0.30)
    }

    @Test fun nothingRecordedMeansNothingToPaint() {
        assertNull(TrailPaint.frameOf(emptyList()))
    }

    @Test fun oneShortWalkStillGetsAUsableFrame() {
        val frame = TrailPaint.frameOf(listOf(point(31.0, 121.0)))!!
        assertTrue("lat span ${frame.latSpan}", frame.latSpan >= TrailPaint.MIN_SPAN_DEG)
        assertTrue(frame.widthPx >= 64 && frame.heightPx >= 64)
    }

    @Test fun theImageIsNeverLargerThanTheCap() {
        val frame = TrailPaint.frameOf(listOf(point(31.0, 121.0), point(35.0, 118.0)))!!
        assertTrue("${frame.widthPx}x${frame.heightPx}", frame.widthPx <= TrailPaint.MAX_PX)
        assertTrue("${frame.widthPx}x${frame.heightPx}", frame.heightPx <= TrailPaint.MAX_PX)
    }

    @Test fun aFrameStretchedOverTensOfKilometresIsClampedAroundWhereYouAre() {
        val near = point(31.0, 121.0)
        val frame = TrailPaint.frameOf(listOf(near, point(35.0, 118.0)), anchor = near)!!
        assertTrue(frame.latSpan * 111194.9 < TrailPaint.MAX_SPAN_METERS * 1.4)
        assertTrue("the anchor fell outside its own frame", frame.contains(near.lat, near.lon))
    }

    @Test fun northIsUpAndWestIsLeft() {
        val frame = TrailPaint.frameOf(listOf(point(31.0, 121.0), point(31.01, 121.02)))!!
        assertEquals(0f, frame.pixelY(frame.latMax), 0.001f)
        assertEquals(frame.heightPx.toFloat(), frame.pixelY(frame.latMin), 0.001f)
        assertEquals(0f, frame.pixelX(frame.lonMin), 0.001f)
        assertEquals(frame.widthPx.toFloat(), frame.pixelX(frame.lonMax), 0.001f)
    }

    @Test fun aMetreIsMorePixelsWideInASmallerFrame() {
        val near = TrailPaint.frameOf(listOf(point(31.0, 121.0), point(31.004, 121.0)))!!
        val far = TrailPaint.frameOf(listOf(point(31.0, 121.0), point(31.05, 121.0)))!!
        assertTrue(TrailPainter.pixelsPerMeter(near) > TrailPainter.pixelsPerMeter(far))
        // The band has to stay drawable even when the frame covers a whole city.
        assertTrue("band was ${TrailPaint.CORRIDOR_M * TrailPainter.pixelsPerMeter(far)} px",
            TrailPaint.CORRIDOR_M * TrailPainter.pixelsPerMeter(far) > 1.0)
    }

    @Test fun aPointOnTheFrameEdgeCountsAsInsideAndOneBeyondDoesNot() {
        val frame = TrailPaint.frameOf(listOf(point(31.0, 121.0), point(31.01, 121.02)))!!
        assertTrue(frame.contains(frame.latMin, frame.lonMin))
        assertTrue(frame.contains(frame.latMax, frame.lonMax))
        assertTrue(!frame.contains(north(frame.latMax, 500.0), frame.lonMax))
    }
}
