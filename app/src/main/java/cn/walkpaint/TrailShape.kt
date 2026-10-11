package cn.walkpaint

/**
 * Makes a recorded path draw as a line rather than as a tangle.
 *
 * Standing in one place, a phone still reports a few metres of drift every two seconds, and drawing
 * every fix faithfully turns that drift into a knot of thread. Simplifying the shape for drawing
 * removes the back-and-forth within a few metres while keeping the route recognisable. The stored
 * points are never touched: only what is handed to the renderer.
 */
internal object TrailShape {
    /** Below this, a detour is drift rather than walking. */
    const val TOLERANCE_M = 4.0

    fun simplify(points: List<TrackPoint>, toleranceM: Double = TOLERANCE_M): List<TrackPoint> {
        if (points.size <= 2 || toleranceM <= 0.0) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to points.size - 1)
        while (stack.isNotEmpty()) {
            val (first, last) = stack.removeLast()
            if (last <= first + 1) continue
            var worst = -1.0
            var worstAt = first
            for (index in first + 1 until last) {
                val distance = perpendicularMeters(points[first], points[last], points[index])
                if (distance > worst) { worst = distance; worstAt = index }
            }
            if (worst > toleranceM) {
                keep[worstAt] = true
                stack.addLast(first to worstAt)
                stack.addLast(worstAt to last)
            }
        }
        return points.filterIndexed { index, _ -> keep[index] }
    }

    /** Distance from [point] to the segment [a]..[b], in metres, on a local flat approximation. */
    private fun perpendicularMeters(a: TrackPoint, b: TrackPoint, point: TrackPoint): Double {
        val metresPerLat = 111194.9
        val metresPerLon = metresPerLat * kotlin.math.cos(Math.toRadians(a.lat)).coerceAtLeast(0.05)
        val ax = 0.0; val ay = 0.0
        val bx = (b.lon - a.lon) * metresPerLon; val by = (b.lat - a.lat) * metresPerLat
        val px = (point.lon - a.lon) * metresPerLon; val py = (point.lat - a.lat) * metresPerLat
        val dx = bx - ax; val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 1e-9) return kotlin.math.hypot(px, py)
        val t = ((px - ax) * dx + (py - ay) * dy) / lengthSquared
        val clamped = t.coerceIn(0.0, 1.0)
        return kotlin.math.hypot(px - (ax + clamped * dx), py - (ay + clamped * dy))
    }
}

/** One period's worth of a person's own history, in the terms they would say it themselves. */
internal data class Recap(
    val period: String,
    val meters: Double,
    val days: Int,
    val bestDay: String,
    val bestDayMeters: Double,
    val longestStreak: Int,
    val firstDay: String,
)

internal object RecapMaker {
    /** Summarises the days inside one `yyyy-MM` month or one `yyyy` year. */
    fun of(days: List<DailyFootprint>, period: String): Recap? {
        val inside = days.filter { it.key.startsWith(period) }
        if (inside.isEmpty()) return null
        val ordered = inside.sortedBy { it.key }
        val best = ordered.maxByOrNull { it.meters } ?: return null
        return Recap(
            period = period,
            meters = ordered.sumOf { it.meters },
            days = ordered.size,
            bestDay = best.key,
            bestDayMeters = best.meters,
            longestStreak = longestStreak(ordered.map { it.key }),
            firstDay = ordered.first().key,
        )
    }

    /** The longest run of consecutive days anywhere in the list, not just the current one. */
    internal fun longestStreak(keys: List<String>): Int {
        val dates = keys.mapNotNull { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            .distinct().sorted()
        if (dates.isEmpty()) return 0
        var best = 1
        var run = 1
        for (index in 1 until dates.size) {
            run = if (dates[index] == dates[index - 1].plusDays(1)) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }
}
