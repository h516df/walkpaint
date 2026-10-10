package cn.walkpaint

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * One day cell in the rolling-year heatmap.
 *
 * [level] is -1 for a day that has not happened yet, 0 for a day with no record, and 1..4 for a
 * recorded day banded against the person's own busiest days. Banding against the person's own data
 * rather than a fixed distance keeps the picture readable whether they walk 500 m or 20 km a day.
 */
internal data class HeatCell(val date: LocalDate, val meters: Double, val level: Int)

/** One column: a Monday-to-Sunday week, labelled when a month starts inside it. */
internal data class HeatWeek(val cells: List<HeatCell>, val monthLabel: String?)

/** A calendar month: Monday-first weeks, with null where a slot belongs to another month. */
internal data class MonthGrid(val year: Int, val month: Int, val weeks: List<List<HeatCell?>>) {
    val label get() = "$year 年 $month 月"
}

/**
 * The colour meaning, shared by every level of the calendar so a shade means the same thing whether
 * you are looking at a year, a month or a day. Levels band against the person's own recorded days.
 */
internal class HeatBands(private val thresholds: List<Double>) {
    fun level(meters: Double): Int = when {
        meters <= 0.0 -> 0
        thresholds.isEmpty() -> 4
        else -> thresholds.indexOfFirst { meters <= it }.let { if (it < 0) 4 else it + 1 }
    }

    companion object {
        fun of(metersByDay: Map<String, Double>): HeatBands {
            val recorded = metersByDay.values.filter { it > 0.0 }.sorted()
            if (recorded.isEmpty()) return HeatBands(emptyList())
            return HeatBands(listOf(0.25, 0.5, 0.75).map { recorded[((recorded.size - 1) * it).toInt()] })
        }
    }
}

internal object YearHeatmap {
    /** 53 columns covers a rolling year with the current week last. */
    const val WEEKS = 53

    /**
     * Builds the grid ending with the week that contains [today]. Columns always start on Monday,
     * so cells line up under each other, and the newest week is the rightmost one. A rolling year
     * rather than a calendar year, so the picture never resets to an empty page in January.
     */
    fun build(today: LocalDate, metersByDay: Map<String, Double>): List<HeatWeek> {
        val bands = HeatBands.of(metersByDay)
        val lastMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val firstMonday = lastMonday.minusWeeks((WEEKS - 1).toLong())
        return (0 until WEEKS).map { week ->
            val weekStart = firstMonday.plusWeeks(week.toLong())
            val cells = (0 until 7).map { day ->
                val date = weekStart.plusDays(day.toLong())
                val meters = metersByDay[date.toString()] ?: 0.0
                val level = when {
                    date.isAfter(today) -> -1
                    else -> bands.level(meters)
                }
                HeatCell(date, meters, level)
            }
            HeatWeek(cells, monthLabel(cells))
        }
    }

    /**
     * One month as a real calendar page. A year grid answers "did I keep it up", but it cannot show
     * which particular day was skipped this week; a month page can, so the year is the overview and
     * the month is where you actually read your habits.
     */
    fun monthGrid(year: Int, month: Int, today: LocalDate, metersByDay: Map<String, Double>): MonthGrid {
        val bands = HeatBands.of(metersByDay)
        val first = LocalDate.of(year, month, 1)
        val lastSunday = first.withDayOfMonth(first.lengthOfMonth())
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val weeks = mutableListOf<List<HeatCell?>>()
        var weekStart = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        while (!weekStart.isAfter(lastSunday)) {
            weeks += (0 until 7).map { offset ->
                val date = weekStart.plusDays(offset.toLong())
                when {
                    date.year != year || date.monthValue != month -> null
                    date.isAfter(today) -> HeatCell(date, 0.0, -1)
                    else -> {
                        val meters = metersByDay[date.toString()] ?: 0.0
                        HeatCell(date, meters, bands.level(meters))
                    }
                }
            }
            weekStart = weekStart.plusWeeks(1)
        }
        return MonthGrid(year, month, weeks)
    }

    /**
     * Distance travelled in each hour of the supplied day, for the day strip. Answers the one thing
     * neither a year nor a month can: when during the day did I actually walk.
     */
    fun hourStrip(tracks: List<Track>): List<Double> {
        val hours = DoubleArray(24)
        val zone = java.time.ZoneId.systemDefault()
        tracks.forEach { trip ->
            trip.points.sortedBy { it.time }.zipWithNext().forEach { (a, b) ->
                val meters = Geo.distance(listOf(a, b))
                if (meters > 0.0) {
                    val hour = java.time.Instant.ofEpochMilli(a.time).atZone(zone).hour
                    hours[hour] += meters
                }
            }
        }
        return hours.toList()
    }

    /** The 1st of a month falling inside a week labels that column. */
    private fun monthLabel(cells: List<HeatCell>): String? =
        cells.firstOrNull { it.date.dayOfMonth == 1 }?.let { "${it.date.monthValue}月" }
}
