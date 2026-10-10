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

internal object YearHeatmap {
    /** 53 columns covers a rolling year with the current week last. */
    const val WEEKS = 53

    /**
     * Builds the grid ending with the week that contains [today]. Columns always start on Monday,
     * so cells line up under each other, and the newest week is the rightmost one. A rolling year
     * rather than a calendar year, so the picture never resets to an empty page in January.
     */
    fun build(today: LocalDate, metersByDay: Map<String, Double>): List<HeatWeek> {
        val levels = bandThresholds(metersByDay)
        val lastMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val firstMonday = lastMonday.minusWeeks((WEEKS - 1).toLong())
        return (0 until WEEKS).map { week ->
            val weekStart = firstMonday.plusWeeks(week.toLong())
            val cells = (0 until 7).map { day ->
                val date = weekStart.plusDays(day.toLong())
                val meters = metersByDay[date.toString()] ?: 0.0
                val level = when {
                    date.isAfter(today) -> -1
                    meters <= 0.0 -> 0
                    else -> band(levels, meters)
                }
                HeatCell(date, meters, level)
            }
            HeatWeek(cells, monthLabel(cells))
        }
    }

    /** The 1st of a month falling inside a week labels that column. */
    private fun monthLabel(cells: List<HeatCell>): String? =
        cells.firstOrNull { it.date.dayOfMonth == 1 }?.let { "${it.date.monthValue}月" }

    /** Quartile cut points of the days that actually carry a record. */
    private fun bandThresholds(metersByDay: Map<String, Double>): List<Double> {
        val recorded = metersByDay.values.filter { it > 0.0 }.sorted()
        if (recorded.isEmpty()) return emptyList()
        return listOf(0.25, 0.5, 0.75).map { recorded[((recorded.size - 1) * it).toInt()] }
    }

    private fun band(thresholds: List<Double>, meters: Double): Int {
        if (thresholds.isEmpty()) return 4
        val index = thresholds.indexOfFirst { meters <= it }
        return if (index < 0) 4 else index + 1
    }
}
