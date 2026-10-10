package cn.walkpaint

import org.junit.Assert.assertEquals
import org.junit.Test

class ExplorationTest {

    private fun point(lat: Double, lon: Double, time: Long) = TrackPoint(lat, lon, time, 5f)
    private fun state(vararg points: TrackPoint) = TrackState(listOf(Track("t", points.first().time, false, points.toList())))

    @Test fun onePointLightsOneCell() {
        val e = Explorer.of(state(point(31.0, 121.0, 0L)), todayStartMs = 1L)
        assertEquals(1, e.cellsVisited)
    }

    @Test fun threePointsInThreeCellsLightThree() {
        // Spaced clear of a cell boundary, so floating point cannot decide the answer.
        val e = Explorer.of(state(
            point(31.0, 121.0, 0L),
            point(31.0, 121.0075, 1L),
            point(31.0, 121.015, 2L),
        ), todayStartMs = 10L)
        assertEquals(3, e.cellsVisited)
    }

    @Test fun returningToTheSameCellDoesNotLightItTwice() {
        val e = Explorer.of(state(
            point(31.0, 121.0, 0L),
            point(31.0, 121.0, 1L),
            point(31.0, 121.0, 2L),
        ), todayStartMs = 10L)
        assertEquals(1, e.cellsVisited)
    }

    @Test fun aLongTripDoesNotBreakTheReading() {
        // The old percentage collapsed to zero here. The count must simply be the two cells walked.
        val e = Explorer.of(state(
            point(31.0, 121.0, 0L),
            point(51.75, -1.25, 1L),
        ), todayStartMs = 10L)
        assertEquals(2, e.cellsVisited)
        assertEquals(0, e.newToday)
    }

    @Test fun onlyCellsNeverVisitedBeforeCountAsNewToday() {
        val e = Explorer.of(state(
            point(31.0, 121.0, 0L),             // visited before today
            point(31.0, 121.0, 100L),           // today, but the same cell
            point(31.0, 121.0075, 101L),        // today, and a cell never seen before
        ), todayStartMs = 50L)
        assertEquals(2, e.cellsVisited)
        assertEquals(1, e.newToday)
    }

    @Test fun nothingWalkedEarlierStillCountsNothingAsNewToday() {
        val e = Explorer.of(state(point(31.0, 121.0, 0L)), todayStartMs = 50L)
        assertEquals(0, e.newToday)
    }

    @Test fun anEmptyStateIsZeroRatherThanADivision() {
        val e = Explorer.of(TrackState(emptyList()), todayStartMs = 0L)
        assertEquals(0, e.cellsVisited)
        assertEquals(0, e.newToday)
    }
}
