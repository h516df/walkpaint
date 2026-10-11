package cn.walkpaint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailPaintTest {

    private fun point(lat: Double, lon: Double) = TrackPoint(lat, lon, 0L, 5f)

    @Test fun nothingRecordedMeansNothingToPaint() {
        assertNull(TrailPaint.frameOf(emptyList()))
    }

    @Test fun oneShortWalkStillGetsAUsableFrame() {
        // A single point would otherwise produce a zero-size frame and a zero-size image.
        val frame = TrailPaint.frameOf(listOf(point(31.0, 121.0)))!!
        assertTrue("lat span ${frame.latSpan}", frame.latSpan >= TrailPaint.MIN_SPAN_DEG)
        assertTrue("lon span ${frame.lonSpan}", frame.lonSpan >= TrailPaint.MIN_SPAN_DEG)
        assertTrue(frame.widthPx >= 64 && frame.heightPx >= 64)
    }

    @Test fun theImageIsNeverLargerThanTheCap() {
        // A whole year of trips across a province must not allocate a giant bitmap.
        val frame = TrailPaint.frameOf(listOf(point(31.0, 121.0), point(35.0, 118.0)))!!
        assertTrue("${frame.widthPx}x${frame.heightPx}", frame.widthPx <= TrailPaint.MAX_PX)
        assertTrue("${frame.widthPx}x${frame.heightPx}", frame.heightPx <= TrailPaint.MAX_PX)
    }

    @Test fun theFrameCoversEveryPointWithAMargin() {
        val points = listOf(point(31.0, 121.0), point(31.01, 121.02))
        val frame = TrailPaint.frameOf(points)!!
        points.forEach {
            assertTrue("point outside the frame", frame.contains(it.lat, it.lon))
        }
        assertTrue("no margin above", frame.latMax > 31.01)
        assertTrue("no margin below", frame.latMin < 31.0)
    }

    @Test fun northIsUpAndWestIsLeft() {
        val frame = TrailPaint.frameOf(listOf(point(31.0, 121.0), point(31.01, 121.02)))!!
        assertEquals(0f, frame.pixelY(frame.latMax), 0.001f)
        assertEquals(frame.heightPx.toFloat(), frame.pixelY(frame.latMin), 0.001f)
        assertEquals(0f, frame.pixelX(frame.lonMin), 0.001f)
        assertEquals(frame.widthPx.toFloat(), frame.pixelX(frame.lonMax), 0.001f)
        // A point further north must be higher on the image, which means a smaller y.
        assertTrue(frame.pixelY(31.009) < frame.pixelY(31.001))
    }

    @Test fun aTallTracksFrameIsTallerThanItIsWide() {
        val frame = TrailPaint.frameOf(listOf(point(31.0, 121.0), point(31.02, 121.001)))!!
        assertTrue("${frame.widthPx}x${frame.heightPx}", frame.heightPx > frame.widthPx)
    }

    @Test fun aWideTracksFrameIsWiderThanItIsTall() {
        val frame = TrailPaint.frameOf(listOf(point(31.0, 121.0), point(31.001, 121.02)))!!
        assertTrue("${frame.widthPx}x${frame.heightPx}", frame.widthPx > frame.heightPx)
    }

    @Test fun nonFinitePointsAreIgnoredRatherThanBreakingTheFrame() {
        val frame = TrailPaint.frameOf(listOf(point(31.0, 121.0), point(Double.NaN, 121.0)))!!
        assertTrue(frame.contains(31.0, 121.0))
    }
}
