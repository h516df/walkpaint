package cn.walkpaint

import org.json.JSONArray
import org.json.JSONObject

/**
 * Paint recorded GPS points and contiguous path segments, never make up a route
 * before the first accepted movement. MapLibre coordinates remain WGS84.
 */
internal object TrackMapJson {
    fun encode(tracks: List<Track>): String {
        val features = JSONArray()
        tracks.forEach { trip ->
            trip.points.groupBy { it.segment }.values.forEach { segment ->
                if (segment.isEmpty()) return@forEach
                val first = segment.first()
                features.put(JSONObject()
                    .put("type", "Feature")
                    .put("properties", JSONObject())
                    .put("geometry", JSONObject()
                        .put("type", "Point")
                        .put("coordinates", JSONArray().put(first.lon).put(first.lat))))
                if (segment.size >= 2) {
                    // Mark the newly recorded end as well as the start. Short
                    // walks should be visible even with only two saved fixes.
                    val last = segment.last()
                    features.put(JSONObject()
                        .put("type", "Feature")
                        .put("properties", JSONObject())
                        .put("geometry", JSONObject()
                            .put("type", "Point")
                            .put("coordinates", JSONArray().put(last.lon).put(last.lat))))
                    val coordinates = JSONArray()
                    segment.forEach { coordinates.put(JSONArray().put(it.lon).put(it.lat)) }
                    features.put(JSONObject()
                        .put("type", "Feature")
                        .put("properties", JSONObject())
                        .put("geometry", JSONObject()
                            .put("type", "LineString")
                            .put("coordinates", coordinates)))
                }
            }
        }
        return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
    }
}
