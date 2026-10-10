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

    /**
     * Hard ceiling on how many strips may be drawn over the live map. Above this only the largest
     * are kept, so a well-travelled area cannot turn the map back into the stuttering thing this
     * project just fixed.
     */
    const val MAX_STRIPS = 240

    /** Soft tint for explored ground. Kept low so the trail stays the thing you read. */
    const val EXPLORED_FILL_ALPHA = 0.13f

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

    /**
     * Consecutive days with a record, counting back from today.
     *
     * A day that has not finished yet must not break the run, or the number would drop to zero
     * every midnight and only come back once someone walked. So the run is counted from today when
     * today has a record, otherwise from yesterday, and a gap older than that means the run is over.
     * This is the one progress figure that is both honest and motivating: it needs no denominator
     * at all, and it is the thing that gets someone out of the door.
     */
    fun streak(dayKeys: Collection<String>, todayKey: String): Int {
        val days = dayKeys.mapNotNull { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }.toHashSet()
        if (days.isEmpty()) return 0
        val today = runCatching { java.time.LocalDate.parse(todayKey) }.getOrNull() ?: return 0
        var cursor = if (today in days) today else today.minusDays(1)
        if (cursor !in days) return 0
        var run = 0
        while (cursor in days) {
            run++
            cursor = cursor.minusDays(1)
        }
        return run
    }

    private fun key(row: Int, col: Int): Long = (row.toLong() shl 32) xor (col.toLong() and 0xFFFFFFFFL)

    /** One lit run of cells on a single grid row, stored as raw grid indices. */
    internal data class CellRect(val row: Int, val colStart: Int, val colEnd: Int) {
        val cells: Int get() = colEnd - colStart + 1
        fun latRange(): ClosedFloatingPointRange<Double> =
            (row * CELL_DEGREES)..((row + 1) * CELL_DEGREES)
        fun lonRange(): ClosedFloatingPointRange<Double> =
            (colStart * CELL_DEGREES)..((colEnd + 1) * CELL_DEGREES)
    }

    /**
     * The lit cells merged into one rectangle per run of adjacent cells on a row.
     *
     * Drawing one shape per cell would mean hundreds of overlays over a live map, and a map that
     * stutters is exactly the bug this project just finished fixing. Merging neighbours turns a
     * walked area into a few dozen long strips instead, so the explored ground can be shown
     * without paying for it. [limit] is a hard ceiling: above it only the largest strips are kept,
     * which keeps the cost bounded and the drawing still recognisable.
     */
    fun rectangles(state: TrackState, limit: Int): List<CellRect> {
        val rows = HashMap<Int, MutableSet<Int>>()
        state.realTracks.forEach { trip ->
            trip.points.forEach { point ->
                if (!point.lat.isFinite() || !point.lon.isFinite()) return@forEach
                val row = kotlin.math.floor(point.lat / CELL_DEGREES).toInt()
                val col = kotlin.math.floor(point.lon / CELL_DEGREES).toInt()
                rows.getOrPut(row) { mutableSetOf() }.add(col)
            }
        }
        val merged = mutableListOf<CellRect>()
        rows.forEach { (row, columns) ->
            val sorted = columns.sorted()
            var start = sorted.first()
            var previous = start
            for (index in 1 until sorted.size) {
                val col = sorted[index]
                if (col == previous + 1) {
                    previous = col
                } else {
                    merged += CellRect(row, start, previous)
                    start = col
                    previous = col
                }
            }
            merged += CellRect(row, start, previous)
        }
        if (merged.size <= limit) return merged
        return merged.sortedByDescending { it.cells }.take(limit)
    }

    /**
     * The strips as a GeoJSON polygon collection, for a renderer that can draw them in one layer.
     * Keeping this pure means the shape of what gets drawn is unit-tested rather than eyeballed.
     */
    fun stripsGeoJson(state: TrackState, limit: Int = MAX_STRIPS): String {
        val features = org.json.JSONArray()
        rectangles(state, limit).forEach { rect ->
            val lat = rect.latRange()
            val lon = rect.lonRange()
            val ring = org.json.JSONArray()
                .put(org.json.JSONArray().put(lon.start).put(lat.start))
                .put(org.json.JSONArray().put(lon.endInclusive).put(lat.start))
                .put(org.json.JSONArray().put(lon.endInclusive).put(lat.endInclusive))
                .put(org.json.JSONArray().put(lon.start).put(lat.endInclusive))
                .put(org.json.JSONArray().put(lon.start).put(lat.start))
            features.put(org.json.JSONObject()
                .put("type", "Feature")
                .put("properties", org.json.JSONObject())
                .put("geometry", org.json.JSONObject()
                    .put("type", "Polygon")
                    .put("coordinates", org.json.JSONArray().put(ring))))
        }
        return org.json.JSONObject().put("type", "FeatureCollection").put("features", features).toString()
    }
}
