package cn.walkpaint

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.*

data class TrackPoint(val lat: Double, val lon: Double, val time: Long, val accuracy: Float, val segment: Int = 0)
data class Track(val id: String, val started: Long, val demo: Boolean, val points: List<TrackPoint> = emptyList())
data class TrackState(val tracks: List<Track> = emptyList(), val activeId: String? = null) {
    val realTracks by lazy { tracks.filter { !it.demo && it.points.isNotEmpty() } }
    val totalMeters by lazy { realTracks.sumOf { Geo.distance(it.points) } }
    val dayCount by lazy { realTracks.flatMap { it.points }.map { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date(it.time)) }.toSet().size }
    val cellCount by lazy { realTracks.flatMap { it.points }.map { floor(it.lat / 0.005).toInt() to floor(it.lon / 0.005).toInt() }.toSet().size }
}

object Geo {
    fun plausibleTimestamp(fixTime: Long, now: Long): Boolean =
        fixTime in (now - 120_000L)..(now + 60_000L)

    fun meters(a: TrackPoint, b: TrackPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 6371000.0 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
    fun distance(points: List<TrackPoint>) = points.zipWithNext().sumOf { (a, b) -> if (a.segment == b.segment && accept(a, b)) meters(a, b) else 0.0 }
    fun accept(previous: TrackPoint?, next: TrackPoint): Boolean {
        if (!next.lat.isFinite() || !next.lon.isFinite() || next.lat !in -90.0..90.0 || next.lon !in -180.0..180.0 || !next.accuracy.isFinite() || next.accuracy !in 0f..35f) return false
        if (previous == null) return true
        val seconds = (next.time - previous.time) / 1000.0
        if (seconds <= 0) return false
        val distance = meters(previous, next)
        // Avoid coloring stationary GPS drift as genuine movement.
        val minimumMove = max(4.0, max(previous.accuracy.toDouble(), next.accuracy.toDouble()) * 1.25)
        return distance >= minimumMove && distance <= max(60.0, seconds * 8.0) && distance / seconds <= 8.0
    }
}

object TrackCodec {
    fun encode(state: TrackState): String = JSONObject().apply {
        put("version", 1)
        put("activeId", state.activeId ?: JSONObject.NULL)
        put("tracks", JSONArray().apply {
            state.tracks.forEach { track -> put(JSONObject().apply {
                put("id", track.id); put("started", track.started); put("demo", track.demo)
                put("points", JSONArray().apply { track.points.forEach { p -> put(JSONArray(listOf(p.lat, p.lon, p.time, p.accuracy, p.segment))) } })
            }) }
        })
    }.toString()
    fun decode(json: String): TrackState {
        val root = JSONObject(json)
        require(root.getInt("version") == 1) { "不支持的轨迹格式" }
        val tracks = root.getJSONArray("tracks")
        return TrackState((0 until tracks.length()).map { i ->
            val t = tracks.getJSONObject(i)
            val points = t.getJSONArray("points")
            Track(t.getString("id"), t.getLong("started"), t.getBoolean("demo"), (0 until points.length()).map { j ->
                val p = points.getJSONArray(j)
                TrackPoint(p.getDouble(0), p.getDouble(1), p.getLong(2), p.getDouble(3).toFloat(), p.getInt(4)).also {
                    require(it.lat.isFinite() && it.lat in -90.0..90.0 && it.lon.isFinite() && it.lon in -180.0..180.0 && it.accuracy.isFinite())
                }
            })
        }, if (root.isNull("activeId")) null else root.getString("activeId"))
    }
}

class TrackStore(private val file: File) {
    var loadError: String? = null
        private set
    private var state = try {
        if (file.exists()) TrackCodec.decode(file.readText()) else TrackState()
    } catch (e: Exception) {
        loadError = "历史数据读取失败，已保留原文件；停止写入以免覆盖"
        TrackState()
    }
    @Synchronized fun snapshot() = state
    @Synchronized fun update(transform: (TrackState) -> TrackState) {
        check(loadError == null) { loadError!! }
        val next = transform(state)
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(temp).use { stream -> stream.write(TrackCodec.encode(next).toByteArray(Charsets.UTF_8)); stream.fd.sync() }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        state = next
    }
}
