package cn.walkpaint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    // ---- month page ----

    @Test fun aMonthIsLaidOutAsARealCalendarPage() {
        // 2026-10-01 is a Thursday, and columns start on Monday, so the first three slots are blank.
        val grid = YearHeatmap.monthGrid(2026, 10, wednesday, emptyMap())
        assertEquals(2026, grid.year)
        assertEquals(10, grid.month)
        assertEquals(5, grid.weeks.size)
        assertNull(grid.weeks[0][0])
        assertNull(grid.weeks[0][2])
        assertEquals(1, grid.weeks[0][3]!!.date.dayOfMonth)
        assertEquals("2026 年 10 月", grid.label)
        // Every non-null cell must actually belong to this month.
        assertTrue(grid.weeks.flatten().filterNotNull().all { it.date.monthValue == 10 })
    }

    @Test fun aMonthMarksDaysAfterTodayAsNotYet() {
        val grid = YearHeatmap.monthGrid(2026, 10, wednesday, emptyMap())
        val cells = grid.weeks.flatten().filterNotNull()
        assertTrue(cells.filter { it.date.isAfter(wednesday) }.all { it.level == -1 })
        assertEquals(0, cells.first { it.date == wednesday }.level)
    }

    @Test fun monthAndYearAgreeOnWhatADarkDayMeans() {
        val meters = mapOf(
            "2026-10-01" to 1000.0, "2026-10-02" to 2000.0,
            "2026-10-03" to 3000.0, "2026-10-05" to 4000.0,
        )
        val month = YearHeatmap.monthGrid(2026, 10, wednesday, meters).weeks.flatten().filterNotNull()
        val byDate = month.associateBy { it.date.toString() }
        assertEquals(1, byDate.getValue("2026-10-01").level)
        assertEquals(4, byDate.getValue("2026-10-05").level)
        // The same day must be the same shade in the year grid.
        val year = YearHeatmap.build(wednesday, meters).flattenCells().associateBy { it.date.toString() }
        assertEquals(year.getValue("2026-10-05").level, byDate.getValue("2026-10-05").level)
    }

    // ---- day strip ----

    @Test fun theDayStripPutsDistanceInTheHourItHappened() {
        val zone = java.time.ZoneId.systemDefault()
        fun at(hour: Int, minute: Int) =
            java.time.LocalDate.of(2026, 10, 7).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        val start = 31.0
        // Ten seconds apart: inside the gap that still counts as one continuous walk.
        val track = Track("day", at(9, 30), false, listOf(
            TrackPoint(start, 121.0, at(9, 30), 5f),
            TrackPoint(start + 100.0 / 111194.9, 121.0, at(9, 30) + 10_000L, 5f),
        ))
        val hours = YearHeatmap.hourStrip(listOf(track))
        assertEquals(24, hours.size)
        assertTrue("the walk happened in the 9 o'clock hour", hours[9] in 99.0..101.0)
        assertTrue("no other hour may claim it", hours.filterIndexed { i, _ -> i != 9 }.all { it == 0.0 })
    }

    @Test fun theDayStripIgnoresAJumpTheSameWayTotalsDo() {
        val zone = java.time.ZoneId.systemDefault()
        fun at(hour: Int) =
            java.time.LocalDate.of(2026, 10, 7).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        // 3 km in one second is a jump, not a walk, and must not appear as travelled distance.
        val track = Track("jump", at(9), false, listOf(
            TrackPoint(31.0, 121.0, at(9), 5f),
            TrackPoint(31.027, 121.0, at(9) + 1_000L, 5f),
        ))
        assertTrue(YearHeatmap.hourStrip(listOf(track)).all { it == 0.0 })
    }

    private fun List<HeatWeek>.flattenCells(): List<HeatCell> = flatMap { it.cells }
}
