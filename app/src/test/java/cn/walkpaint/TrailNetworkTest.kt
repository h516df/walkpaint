package cn.walkpaint

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The line drawn over the veil. It exists so the record survives a zoom-out, where the veil's image
 * cannot resolve a thirty metre corridor — but it must not cost what the whole history costs.
 */
class TrailNetworkTest {

    private fun point(lat: Double, lon: Double, at: Long = 0L) = TrackPoint(lat, lon, at, 5f)

    private fun straight(lat: Double, lon: Double, count: Int, stepDeg: Double): List<TrackPoint> =
        (0 until count).map { point(lat, lon + it * stepDeg, it * 1000L) }

    @Test fun groundFarOutsideTheViewIsNotHandedToTheMap() {
        val near = straight(31.0, 121.0, 40, 0.00005)
        val far = straight(35.0, 118.0, 40, 0.00005)
        val frame = TrailPaint.frameFor(ViewBounds(30.99, 120.99, 31.01, 121.01), near)!!
        val line = TrailShape.network(listOf(near, far), frame)
        assertTrue("nothing was drawn", line.isNotEmpty())
        assertTrue("ground four hundred kilometres away was sent to the map",
            line.flatten().all { it.lat < 32.0 })
    }

    @Test fun whatIsOnScreenIsStillDrawn() {
        val walk = straight(31.0, 121.0, 40, 0.00005)
        val frame = TrailPaint.frameFor(ViewBounds(30.99, 120.99, 31.01, 121.01), walk)!!
        val line = TrailShape.network(listOf(walk), frame)
        assertTrue("the walked line was lost", line.sumOf { it.size } >= 2)
    }

    @Test fun aCoarseViewDoesNotCarryEveryPoint() {
        // Zoomed out, a point every few metres cannot be drawn anyway: the whole walk is a few
        // pixels long. Those points are the ones that made an update expensive.
        val walk = straight(31.0, 121.0, 400, 0.00002)
        val wide = TrailPaint.frameFor(ViewBounds(20.0, 110.0, 42.0, 132.0), walk)!!
        val line = TrailShape.network(listOf(walk), wide)
        val kept = line.sumOf { it.size }
        assertTrue("a city-wide view kept $kept of 400 points", kept < 40)
    }

    @Test fun withoutAFrameNothingIsThrownAway() {
        val walk = straight(31.0, 121.0, 40, 0.00005)
        val line = TrailShape.network(listOf(walk), null)
        assertTrue("the line disappeared without a frame", line.sumOf { it.size } >= 2)
    }
}
