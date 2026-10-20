package cn.walkpaint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The veil's contract, restated for v0.39: unknown ground has an opaque themed veil, only a walked corridor
 * is cleared, and the frame is always wider than the view so its border can never be mistaken for
 * ground that has been explored.
 */
class TrailPaintTest {

    private fun point(lat: Double, lon: Double) = TrackPoint(lat, lon, 0L, 5f)
    /** Roughly [metres] of latitude at this scale. */
    private fun north(base: Double, metres: Double) = base + metres / 111194.9

    @Test fun theClearedBandIsAWideOneSoAnOffsetPassStaysInsideIt() {
        // A second pass over a street carries its own offset. As long as the offset is smaller than
        // the cleared band, the second pass repaints ground that is already clear.
        assertTrue("half band ${TrailPaint.CORRIDOR_M} m", TrailPaint.CORRIDOR_M >= 25.0)
    }

    @Test fun theEdgeOfTheBandIsASoftButNarrowRamp() {
        // Narrow on purpose: the real explored radius must stay unchanged, so the ramp must
        // not turn into a translucent skirt tens of metres wide.
        assertTrue("feather ${TrailPaint.FEATHER_M} m", TrailPaint.FEATHER_M in 1.0..8.0)
    }

    @Test fun unknownGroundConcealsBasemapDetails() {
        val alpha = TrailPaint.FOG_COLOR ushr 24
        assertEquals("unvisited basemap must not bleed through", 255, alpha)
        assertEquals(FogAppearance.DEFAULT, TrailPaint.FOG_COLOR)
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

    @Test fun beforeTheMapReportsAViewTheFrameIsTooWideToShowItsEdge() {
        // An opaque veil whose border fell inside the screen would read as explored ground.
        val near = point(31.0, 121.0)
        val frame = TrailPaint.frameOf(listOf(near, point(35.0, 118.0)), anchor = near)!!
        assertTrue("lat span ${frame.latSpan}", frame.latSpan >= TrailPaint.FALLBACK_SPAN_DEG)
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
        val near = TrailPaint.frameFor(ViewBounds(31.0, 121.0, 31.004, 121.004), listOf(point(31.0, 121.0)))!!
        val far = TrailPaint.frameFor(ViewBounds(31.0, 121.0, 31.05, 121.05), listOf(point(31.0, 121.0)))!!
        assertTrue(TrailPainter.pixelsPerMeter(near) > TrailPainter.pixelsPerMeter(far))
        // The band has to stay drawable even when the view covers a whole city.
        assertTrue("band was ${TrailPaint.CORRIDOR_M * TrailPainter.pixelsPerMeter(far)} px",
            TrailPaint.CORRIDOR_M * TrailPainter.pixelsPerMeter(far) > 1.0)
    }

    @Test fun aPointOnTheFrameEdgeCountsAsInsideAndOneBeyondDoesNot() {
        val frame = TrailPaint.frameOf(listOf(point(31.0, 121.0), point(31.01, 121.02)))!!
        assertTrue(frame.contains(frame.latMin, frame.lonMin))
        assertTrue(frame.contains(frame.latMax, frame.lonMax))
        assertTrue(!frame.contains(north(frame.latMax, 500.0), frame.lonMax))
    }

    @Test fun theFrameCoversWhatTheMapIsShowing() {
        val view = ViewBounds(31.0, 121.0, 31.01, 121.02)
        val frame = TrailPaint.frameFor(view, listOf(point(31.0, 121.0)))!!
        assertTrue(frame.contains(view.latMin, view.lonMin))
        assertTrue(frame.contains(view.latMax, view.lonMax))
        // With room to spare, so a pan does not outrun the veil immediately.
        assertTrue("frame is only ${frame.latSpan} against a view of ${view.latSpan}",
            frame.latSpan > view.latSpan)
    }

    @Test fun theBorderOfTheFrameIsAlwaysOutsideTheView() {
        // The veil no longer fades out at the border. Instead the border must simply never be on
        // screen, whatever the view is.
        val view = ViewBounds(31.0, 121.0, 31.20, 121.20)
        val frame = TrailPaint.frameFor(view, listOf(point(31.0, 121.0)))!!
        val marginLat = (frame.latSpan - view.latSpan) / 2
        val marginLon = (frame.lonSpan - view.lonSpan) / 2
        assertTrue("margin lat $marginLat", marginLat > 0.0)
        assertTrue("margin lon $marginLon", marginLon > 0.0)
    }

    @Test fun groundTwelveKilometresAwayIsInsideAFrameCentredOnThatGround() {
        // The regression this exists for: with the frame pinned to where the person is, old ground
        // far away fell outside the image and simply was not painted at all.
        val near = point(48.8566, 2.3522)
        val far = point(48.8566, 2.5162)
        val separation = Geo.meters(near, far)
        assertTrue("clusters are only $separation m apart", separation > 11_000.0)
        val atFar = ViewBounds(48.85, 2.50, 48.86, 2.53)
        val frame = TrailPaint.frameFor(atFar, listOf(near, far))!!
        assertTrue("the far ground fell outside the frame", frame.contains(far.lat, far.lon))
    }
}
