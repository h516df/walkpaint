package cn.walkpaint

import org.json.JSONArray
import org.json.JSONObject

/**
 * Paint one selected day's walking as lines, and never as a bead of points.
 *
 * The layer used to carry a point feature at the start and end of every run, which the renderer
 * drew as a row of circles sitting on the line: the person called it a string of beads. Those
 * points are gone. The default map draws no line at all and lets the veil paint the walked ground,
 * so a long history cannot pile up as a braid of overlapping passes; this is only the route of the
 * one day someone asked to look at, with repeated ground collapsed by [TrailShape.dedupe].
 * MapLibre coordinates remain WGS84.
 */
internal object TrackMapJson {
    fun encode(tracks: List<Track>): String =
        encodePaths(TrailShape.dedupe(RecordedTrail.visiblePaths(tracks)))

    fun encodePaths(paths: List<List<TrackPoint>>): String {
        val features = JSONArray()
        paths.forEach { run ->
            if (!RecordedTrail.visible(run)) return@forEach
            val coordinates = JSONArray()
            run.forEach { coordinates.put(JSONArray().put(it.lon).put(it.lat)) }
            features.put(JSONObject()
                .put("type", "Feature")
                .put("properties", JSONObject())
                .put("geometry", JSONObject()
                    .put("type", "LineString")
                    .put("coordinates", coordinates)))
        }
        return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
    }
}
