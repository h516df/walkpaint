package cn.walkpaint

import java.time.Instant
import java.time.YearMonth
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class FootprintMonthsTest {
    @Test fun historicalMonthContainsEveryDayAndDoesNotMutateOriginalRecords() {
        val tracks=(1..8).map {day->
            val time=Instant.parse("2026-09-${day.toString().padStart(2,'0')}T12:00:00Z").toEpochMilli()
            Track("synthetic-$day",time,false,listOf(TrackPoint(0.0,0.0,time,5f)))
        }
        val days=DailyFootprints.build(TrackState(tracks),TimeZone.getTimeZone("UTC"))
        assertEquals(8,FootprintMonths.days(days,"2026-09").size)
        assertTrue(FootprintMonths.days(days,"2026-10").isEmpty())
        assertEquals(listOf("2026-10","2026-09"),FootprintMonths.available(days,YearMonth.of(2026,10)))
        assertEquals(8,tracks.size)
        assertEquals("synthetic-8",days.first().tracks.single().id)
    }
    @Test fun yearBoundaryAndLeapDayUseSavedLocalDayNotTripStartDate() {
        val time=Instant.parse("2024-02-28T23:59:55Z").toEpochMilli()
        val track=Track("synthetic",time,false,listOf(TrackPoint(0.0,0.0,time,5f),TrackPoint(0.0,0.0001,time+10000,5f)))
        val days=DailyFootprints.build(TrackState(listOf(track)),TimeZone.getTimeZone("UTC"))
        assertEquals(listOf("2024-02-29","2024-02-28"),FootprintMonths.days(days,"2024-02").map {it.key})
        assertEquals(listOf("2025-01","2024-02"),FootprintMonths.available(days,YearMonth.of(2025,1)))
    }
}
