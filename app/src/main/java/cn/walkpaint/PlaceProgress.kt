package cn.walkpaint

/** Lit share of the ground around one place. */
internal data class PlaceShare(val percent: Int, val litCells: Int, val totalCells: Int)

/**
 * How much of the ground around one place has been lit up.
 *
 * An earlier attempt divided lit cells by the whole area the tracks span. That frame is unbounded,
 * so one long trip or a single stray fix pushed the denominator into the tens of millions and the
 * reading sat at zero forever; it was measured at 62910624 cells and 0 percent on a real device.
 * A named place with a fixed radius has a denominator a person can picture instead: two kilometres
 * is about a twenty-minute walk. The radius is printed in the interface, because a percentage
 * without its denominator is not a fact.
 */
internal object PlaceProgress {
    /** About a twenty-minute walk. Larger and the number crawls; smaller and two laps fill it. */
    const val RADIUS_KM = 2

    /** Shown whenever the place name cannot be resolved, so the line never reads as broken. */
    const val UNKNOWN_PLACE = "当前位置附近"

    fun of(state: TrackState, centerLat: Double, centerLon: Double, radiusKm: Int = RADIUS_KM): PlaceShare {
        if (!centerLat.isFinite() || !centerLon.isFinite() ||
            centerLat !in -90.0..90.0 || centerLon !in -180.0..180.0) return PlaceShare(0, 0, 0)
        val meters = radiusKm * 1000.0
        val latSpan = meters / 111194.9
        val lonSpan = latSpan / kotlin.math.cos(Math.toRadians(centerLat)).coerceAtLeast(0.05)
        val lit = Explorer.visitedCells(state)
        val firstRow = kotlin.math.floor((centerLat - latSpan) / Explorer.CELL_DEGREES).toInt()
        val lastRow = kotlin.math.floor((centerLat + latSpan) / Explorer.CELL_DEGREES).toInt()
        val firstCol = kotlin.math.floor((centerLon - lonSpan) / Explorer.CELL_DEGREES).toInt()
        val lastCol = kotlin.math.floor((centerLon + lonSpan) / Explorer.CELL_DEGREES).toInt()
        var total = 0
        var litHere = 0
        for (row in firstRow..lastRow) {
            for (col in firstCol..lastCol) {
                val cellLat = (row + 0.5) * Explorer.CELL_DEGREES
                val cellLon = (col + 0.5) * Explorer.CELL_DEGREES
                if (Geo.meters(TrackPoint(centerLat, centerLon, 0L, 5f), TrackPoint(cellLat, cellLon, 0L, 5f)) > meters) continue
                total++
                if (Explorer.cellKey(row, col) in lit) litHere++
            }
        }
        if (total <= 0) return PlaceShare(0, 0, 0)
        return PlaceShare(litHere * 100 / total, litHere, total)
    }
}
