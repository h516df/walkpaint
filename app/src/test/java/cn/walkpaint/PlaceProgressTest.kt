package cn.walkpaint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceProgressTest {

    private fun point(lat: Double, lon: Double, time: Long) = TrackPoint(lat, lon, time, 5f)
    private fun state(vararg points: TrackPoint) = TrackState(listOf(Track("t", points.first().time, false, points.toList())))

    @Test fun nothingWalkedIsZeroRatherThanADivision() {
        val share = PlaceProgress.of(TrackState(emptyList()), 31.0, 121.0)
        assertEquals(0, share.percent)
        assertEquals(0, share.litCells)
    }

    @Test fun aTwoKilometreDiscHoldsAFewDozensOfCells() {
        // The denominator has to be small enough that one walk moves the number.
        val share = PlaceProgress.of(TrackState(emptyList()), 31.0, 121.0)
        assertTrue("was ${share.totalCells}", share.totalCells in 25..90)
    }

    @Test fun aSmallerRadiusIsASmallerDenominator() {
        val near = PlaceProgress.of(TrackState(emptyList()), 31.0, 121.0, radiusKm = 1)
        val far = PlaceProgress.of(TrackState(emptyList()), 31.0, 121.0, radiusKm = 2)
        assertTrue("1 km: ${near.totalCells}, 2 km: ${far.totalCells}", near.totalCells < far.totalCells)
    }

    @Test fun walkingOneCellOfTheDiscShowsAsSomePercent() {
        val share = PlaceProgress.of(state(point(31.0, 121.0, 0L)), 31.0, 121.0)
        assertEquals(1, share.litCells)
        assertTrue("one lit cell must not read as zero, was ${share.percent}", share.percent >= 1)
    }

    @Test fun walkingMoreOfTheDiscRaisesTheShare() {
        // Two points a cell apart light two cells, so the share can only go up.
        val one = PlaceProgress.of(state(point(31.0, 121.0, 0L)), 31.0, 121.0)
        val two = PlaceProgress.of(state(
            point(31.0, 121.0, 0L),
            point(31.0, 121.0075, 1L),
        ), 31.0, 121.0)
        assertTrue("${two.litCells} cells must beat ${one.litCells}", two.litCells > one.litCells)
        assertTrue(two.percent >= one.percent)
    }

    @Test fun aCellOutsideTheDiscDoesNotCount() {
        // Five kilometres away: far outside a two kilometre disc.
        val share = PlaceProgress.of(state(point(31.05, 121.0, 0L)), 31.0, 121.0)
        assertEquals(0, share.litCells)
    }

    @Test fun anInvalidCentreIsZeroRatherThanACrash() {
        val share = PlaceProgress.of(state(point(31.0, 121.0, 0L)), Double.NaN, 121.0)
        assertEquals(0, share.percent)
        assertEquals(0, share.totalCells)
    }
}
