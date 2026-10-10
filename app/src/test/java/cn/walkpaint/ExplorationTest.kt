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

    // ---- lit cells merged into drawable strips ----
    // Offsets sit in the middle of their cell, so floating point cannot move a point across a
    // boundary and change the answer.

    @Test fun adjacentCellsOnARowBecomeOneStrip() {
        val rects = Explorer.rectangles(state(
            point(31.002, 121.002, 0L),
            point(31.002, 121.007, 1L),
            point(31.002, 121.012, 2L),
        ), limit = 500)
        assertEquals(1, rects.size)
        assertEquals(3, rects.single().cells)
    }

    @Test fun aGapSplitsTheRowIntoTwoStrips() {
        val rects = Explorer.rectangles(state(
            point(31.002, 121.002, 0L),
            point(31.002, 121.012, 1L),
        ), limit = 500)
        assertEquals(2, rects.size)
        assertEquals(listOf(1, 1), rects.map { it.cells })
    }

    @Test fun eachRowIsItsOwnStrip() {
        val rects = Explorer.rectangles(state(
            point(31.002, 121.002, 0L),
            point(31.007, 121.002, 1L),
        ), limit = 500)
        assertEquals(2, rects.size)
        assertEquals(2, rects.map { it.row }.distinct().size)
    }

    @Test fun overlappingVisitsDoNotMakeDuplicateStrips() {
        val rects = Explorer.rectangles(state(
            point(31.002, 121.002, 0L),
            point(31.002, 121.007, 1L),
            point(31.002, 121.002, 2L),
        ), limit = 500)
        assertEquals(1, rects.size)
        assertEquals(2, rects.single().cells)
    }

    @Test fun theLimitKeepsTheLargestStripsSoTheCostStaysBounded() {
        // One long strip and four single cells: with a limit of two, the long one must survive.
        val points = mutableListOf(
            point(31.002, 121.002, 0L),
            point(31.002, 121.007, 1L),
            point(31.002, 121.012, 2L),
        )
        listOf(121.1, 121.2, 121.3, 121.4).forEachIndexed { index, lon ->
            points += point(31.502, lon, 10L + index)
        }
        val rects = Explorer.rectangles(state(*points.toTypedArray()), limit = 2)
        assertEquals(2, rects.size)
        assertEquals(3, rects.first().cells)
    }

    @Test fun aStripCoversTheWholeCellItRepresents() {
        val rect = Explorer.rectangles(state(point(31.002, 121.002, 0L)), limit = 500).single()
        assertEquals(Explorer.CELL_DEGREES, rect.latRange().endInclusive - rect.latRange().start, 1e-9)
        assertEquals(Explorer.CELL_DEGREES, rect.lonRange().endInclusive - rect.lonRange().start, 1e-9)
    }

    @Test fun anEmptyTrackHasNoStrips() {
        assertEquals(0, Explorer.rectangles(TrackState(emptyList()), limit = 500).size)
    }

    // ---- streak ----

    @Test fun aDayOnItsOwnIsARunOfOne() {
        assertEquals(1, Explorer.streak(listOf("2026-10-07"), todayKey = "2026-10-07"))
    }

    @Test fun consecutiveDaysCountUp() {
        assertEquals(3, Explorer.streak(
            listOf("2026-10-05", "2026-10-06", "2026-10-07"), todayKey = "2026-10-07"))
    }

    @Test fun aDayThatHasNotFinishedYetDoesNotBreakTheRun() {
        // Nothing walked today still leaves yesterday's run alive until the day is over.
        assertEquals(2, Explorer.streak(
            listOf("2026-10-05", "2026-10-06"), todayKey = "2026-10-07"))
    }

    @Test fun aMissedDayEndsTheRun() {
        // Nothing yesterday and nothing today: whatever happened earlier is history, not a streak.
        assertEquals(0, Explorer.streak(
            listOf("2026-10-01", "2026-10-02", "2026-10-03"), todayKey = "2026-10-07"))
    }

    @Test fun aRunEndingTodayIgnoresOlderIsolatedDays() {
        assertEquals(2, Explorer.streak(
            listOf("2026-09-30", "2026-10-06", "2026-10-07"), todayKey = "2026-10-07"))
    }

    @Test fun nothingRecordedIsAStreakOfZero() {
        assertEquals(0, Explorer.streak(emptyList(), todayKey = "2026-10-07"))
    }

    @Test fun anUnparseableDayKeyIsIgnoredRatherThanCrashing() {
        assertEquals(1, Explorer.streak(listOf("not-a-date", "2026-10-07"), todayKey = "2026-10-07"))
    }
}
