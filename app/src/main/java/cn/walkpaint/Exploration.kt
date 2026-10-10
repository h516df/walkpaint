package cn.walkpaint

/** What the app actually knows about explored ground: cells lit, and cells lit for the first time today. */
internal data class Exploration(val cellsVisited: Int, val newToday: Int)

/**
 * Counts the ground the person has lit up, using only their own tracks.
 *
 * There is deliberately no percentage here. The first version of this divided lit cells by the
 * area the tracks span, and the on-device run showed why that is the wrong metric: the frame is
 * unbounded, so a single long trip, or any stray far-away fix, turns the denominator into millions
 * of cells and pins the reading at zero forever. It would have been a number that never moves,
 * which is worse than no number at all, and it would have been a claim about places the app has no
 * evidence about. So the app reports the two things it can stand behind: how many cells are lit,
 * and how many were lit for the first time today.
 */
internal object Explorer {
    /** The same grid the area count already uses: about 550 m north to south. */
    const val CELL_DEGREES = 0.005

    fun of(state: TrackState, todayStartMs: Long): Exploration {
        val visited = HashSet<Long>()
        val visitedBeforeToday = HashSet<Long>()
        state.realTracks.forEach { trip ->
            trip.points.forEach { point ->
                if (!point.lat.isFinite() || !point.lon.isFinite()) return@forEach
                val row = kotlin.math.floor(point.lat / CELL_DEGREES).toInt()
                val col = kotlin.math.floor(point.lon / CELL_DEGREES).toInt()
                visited.add(key(row, col))
                if (point.time < todayStartMs) visitedBeforeToday.add(key(row, col))
            }
        }
        if (visited.isEmpty()) return Exploration(0, 0)
        return Exploration(visited.size, visited.count { it !in visitedBeforeToday })
    }

    private fun key(row: Int, col: Int): Long = (row.toLong() shl 32) xor (col.toLong() and 0xFFFFFFFFL)
}
