package cn.walkpaint

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that decides whether the veil image may show a corridor at all.
 *
 * This is the root cause of the reported defect, so it is pinned as arithmetic rather than as a
 * screenshot. The frame is capped at a few hundred pixels, so a wide frame is a coarse one: at the
 * sixty degree frame drawn before the engine reports a view, thirty metres of corridor is 0.002 px.
 * The stroke used to have a two pixel floor, which turned "thinner than a pixel" into a two pixel
 * line — and two pixels of a sixty degree frame is over thirty kilometres of ground the person had
 * never walked, reported as explored. On the street-level first screen that strip covers the whole
 * display, which is the person's own words: "there was no black fog over the map when I opened the
 * app, and the fog only appeared after I zoomed in".
 *
 * The invariant: the image shows a corridor of the true ground width, or none. When it shows none,
 * the map draws the walked route as a line, so the record survives and unknown ground stays black.
 */
class CorridorRevealTest {

    private val walk = listOf(
        TrackPoint(31.2300, 121.4700, 0L, 8f),
        TrackPoint(31.2306, 121.4707, 1_000L, 8f),
    )

    @Test fun theLaunchFrameCannotShowACorridorSoItMustNotDrawOne() {
        // No viewport yet: the frame covers everything the app would accept, which makes it far too
        // coarse to contain a thirty metre corridor. This is the state the first screen is drawn in.
        val launch = TrailPaint.frameFor(null, walk, walk[0])!!
        assertTrue("the launch frame is still the wide one", launch.latSpan > 1.0)
        assertFalse("a sub-pixel corridor must not be widened into a visible one",
            TrailPaint.corridorVisible(launch))
    }

    @Test fun aStreetLevelFrameShowsTheCorridorItself() {
        val view = ViewBounds(31.2280, 121.4680, 31.2330, 121.4740)
        val frame = TrailPaint.frameFor(view, walk)!!
        assertTrue("a street view must show the cleared band in the image",
            TrailPaint.corridorVisible(frame))
        // And the band it shows is the real one, not a widened stand-in.
        val pixels = TrailPaint.CORRIDOR_M * TrailPainter.pixelsPerMeter(frame)
        assertTrue("corridor drawn as $pixels px", pixels >= TrailPaint.NETWORK_BELOW_PX)
    }

    @Test fun theDecisionIsExactlyAtTwoPixelsOfCorridor() {
        // perMetre = 2 px / 30 m puts the corridor exactly on the threshold. It is constructed
        // directly because the point is the boundary rule, not the frame-building code.
        val perMetre = TrailPaint.NETWORK_BELOW_PX / TrailPaint.CORRIDOR_M
        val span = 512.0 / (perMetre * 111194.9)
        val atThreshold = PaintFrame(0.0, 0.0, span, span, 512, 512)
        assertTrue(TrailPaint.corridorVisible(atThreshold))
        val halfTheSpan = PaintFrame(0.0, 0.0, span * 2, span * 2, 512, 512)
        assertFalse("half the resolution is half the corridor", TrailPaint.corridorVisible(halfTheSpan))
    }

    @Test fun anUnwalkedDatasetStillGetsAFrameSoTheVeilCanBeDrawnBlack() {
        // A brand-new person has unknown ground in front of them; showing them the whole basemap is
        // the same defect as the launch frame. The frame therefore exists even with nothing walked,
        // so the painter can fill it opaque. (That the image really comes out opaque is asserted
        // against real pixels in FogPixelProbe, which can rasterise.)
        val view = ViewBounds(31.2280, 121.4680, 31.2330, 121.4740)
        val frame = TrailPaint.frameFor(view, emptyList(), walk[0])!!
        assertTrue("the frame must cover the view it is drawn for",
            frame.latMin < view.latMin && frame.latMax > view.latMax)
        assertTrue(frame.lonMin < view.lonMin && frame.lonMax > view.lonMax)
    }
}
