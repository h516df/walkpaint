package cn.walkpaint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class YearHeatmapTest {

    private val wednesday = LocalDate.of(2026, 10, 7)

    @Test fun gridIsFiftyThreeWeeksOfSevenDays() {
        val grid = YearHeatmap.build(wednesday, emptyMap())
        assertEquals(53, grid.size)
        assertTrue(grid.all { it.cells.size == 7 })
    }

    @Test fun newestWeekIsRightmostAndColumnsStartOnMonday() {
        val grid = YearHeatmap.build(wednesday, emptyMap())
        val lastWeek = grid.last()
        assertEquals(DayOfWeek.MONDAY, lastWeek.cells.first().date.dayOfWeek)
        assertEquals(2026, lastWeek.cells.first().date.year)
        // The last column must be the week containing "today", not a future or past week.
        assertTrue(lastWeek.cells.any { it.date == wednesday })
        // And the first column must be a year earlier, so the window rolls rather than resets.
        assertTrue(grid.first().cells.first().date.isBefore(wednesday.minusDays(360)))
    }

    @Test fun daysAfterTodayAreMarkedNotYet() {
        val grid = YearHeatmap.build(wednesday, emptyMap())
        val lastWeek = grid.last()
        val future = lastWeek.cells.filter { it.date.isAfter(wednesday) }
        assertTrue("a Wednesday must leave the rest of the week pending", future.isNotEmpty())
        assertTrue(future.all { it.level == -1 })
        assertEquals(0, lastWeek.cells.first { it.date == wednesday }.level)
    }

    @Test fun recordedDaysAreBandedAgainstTheUsersOwnBusiestDays() {
        val meters = mapOf(
            "2026-10-01" to 1000.0,
            "2026-10-02" to 2000.0,
            "2026-10-03" to 3000.0,
            "2026-10-05" to 4000.0,
        )
        val grid = YearHeatmap.build(wednesday, meters)
        val byDate = grid.flattenCells().associateBy { it.date.toString() }
        assertEquals(1, byDate.getValue("2026-10-01").level)
        assertEquals(2, byDate.getValue("2026-10-02").level)
        assertEquals(3, byDate.getValue("2026-10-03").level)
        assertEquals(4, byDate.getValue("2026-10-05").level)
    }

    @Test fun aDayWithNoRecordIsLevelZero() {
        val grid = YearHeatmap.build(wednesday, mapOf("2026-10-01" to 1000.0))
        val empty = grid.flattenCells().first { it.date.toString() == "2026-10-04" }
        assertEquals(0, empty.level)
        assertEquals(0.0, empty.meters, 1e-9)
    }

    @Test fun aMonthBoundaryInsideTheWeekLabelsTheColumn() {
        val grid = YearHeatmap.build(LocalDate.of(2026, 11, 4), emptyMap())
        val labelled = grid.filter { it.monthLabel != null }
        assertTrue("a rolling year spans every month, so labels must exist", labelled.size >= 12)
        assertNotNull(labelled.firstOrNull { it.monthLabel == "11月" })
    }

    private fun List<HeatWeek>.flattenCells(): List<HeatCell> = flatMap { it.cells }
}
