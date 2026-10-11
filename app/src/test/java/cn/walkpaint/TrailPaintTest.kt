package cn.walkpaint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailPaintTest {

    private fun point(lat: Double, lon: Double) = TrackPoint(lat, lon, 0L, 5f)
    /** Roughly [metres] of latitude at this scale. */
    private fun north(base: Double, metres: Double) = base + metres / 111194.9

    @Test fun aBlockIsAboutAHundredMetresAcross() {
        val metres = TrailPaint.CELL_DEGREES * 111194.9
        assertTrue("block is $metres m", metres in 100.0..125.0)
    }

    @Test fun aBlockIsSeveralTimesWiderThanTheGpsError() {
        // This is the whole point: a twenty metre wobble has to stay inside one block, or the
        // explored edge would blur and grow every time the same street is walked again.
        val metres = TrailPaint.CELL_DEGREES * 111194.9
        assertTrue("block is only $metres m against a 20 m error", metres >= 80.0)
    }

    @Test fun twoPointsWithinOneBlockShareTheSameBlock() {
        val a = TrailPaint.cellKey(TrailPaint.cellRow(31.0000), TrailPaint.cellCol(121.0000))
        val b = TrailPaint.cellKey(TrailPaint.cellRow(north(31.0, 50.0)), TrailPaint.cellCol(121.0000))
        assertEquals(a, b)
    }

    @Test fun aPointPastTheBlockEdgeIsANewBlock() {
        val a = TrailPaint.cellKey(TrailPaint.cellRow(31.0000), TrailPaint.cellCol(121.0000))
        val b = TrailPaint.cellKey(TrailPaint.cellRow(north(31.0, 130.0)), TrailPaint.cellCol(121.0000))
        assertNotEquals(a, b)
    }

    @Test fun blocksOnEitherSideOfTheDateLineDoNotCollide() {
        // Packing row and column into one long must not let negative columns alias positive rows.
        assertNotEquals(
            TrailPaint.cellKey(1, -1),
            TrailPaint.cellKey(0, 4294967295L.toInt()),
        )
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
        assertTrue(frame.latSpan * 111194.9 < TrailPaint.MAX_SPAN_METERS * 1.3)
        assertTrue("the anchor fell outside its own frame", frame.contains(near.lat, near.lon))
    }

    @Test fun northIsUpAndWestIsLeft() {
        val frame = TrailPaint.frameOf(listOf(point(31.0, 121.0), point(31.01, 121.02)))!!
        assertEquals(0f, frame.pixelY(frame.latMax), 0.001f)
        assertEquals(frame.heightPx.toFloat(), frame.pixelY(frame.latMin), 0.001f)
        assertEquals(0f, frame.pixelX(frame.lonMin), 0.001f)
        assertEquals(frame.widthPx.toFloat(), frame.pixelX(frame.lonMax), 0.001f)
    }

    @Test fun aBlockIsDrawnBiggerWhenTheFrameIsSmaller() {
        val near = TrailPaint.frameOf(listOf(point(31.0, 121.0), point(31.004, 121.0)))!!
        val far = TrailPaint.frameOf(listOf(point(31.0, 121.0), point(31.05, 121.0)))!!
        val nearPx = TrailPaint.CELL_DEGREES * 111194.9 * TrailPainter.pixelsPerMeter(near)
        val farPx = TrailPaint.CELL_DEGREES * 111194.9 * TrailPainter.pixelsPerMeter(far)
        assertTrue("near $nearPx px, far $farPx px", nearPx > farPx)
        assertTrue("a block must stay visible, was $farPx px", farPx > 1.0)
    }
}
