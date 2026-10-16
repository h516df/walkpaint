package cn.walkpaint

import java.time.LocalDate
import java.util.Locale

/** Read-only presentation of saved points. Never joins trips or counts time across a GPS gap. */
internal data class TrailSummary(val meters: Double, val seconds: Long, val points: Int) {
    val duration: String get() = if (seconds >= 3600) "%d:%02d:%02d".format(Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%02d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60)
    companion object {
        fun of(tracks: List<Track>): TrailSummary {
            val real = tracks.filterNot { it.demo }
            return TrailSummary(real.sumOf { Geo.distance(it.points) }, real.sumOf { track ->
                track.points.zipWithNext().sumOf { (a, b) ->
                    if (RecordedTrail.connected(a, b)) (b.time - a.time).coerceAtLeast(0) else 0L
                }
            } / 1000, real.sumOf { it.points.size })
        }
    }
}

internal enum class FootprintRange(val label: String) {
    ALL("全部"), WEEK("本周"), MONTH("本月");
    fun includes(key: String, today: LocalDate): Boolean {
        val day = runCatching { LocalDate.parse(key) }.getOrNull() ?: return false
        return when (this) {
            ALL -> true
            WEEK -> day >= today.minusDays((today.dayOfWeek.value - 1).toLong()) && day <= today
            MONTH -> day.year == today.year && day.month == today.month && day <= today
        }
    }
}
