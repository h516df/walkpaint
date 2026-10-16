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
    val realPointCount by lazy { realTracks.sumOf { it.points.size } }
    // A snapshot is immutable; only the user's current calendar timezone can invalidate this view.
    @Volatile private var dailyCache: Pair<java.util.TimeZone,List<DailyFootprint>>? = null
    internal fun dailyFootprints(zone: java.util.TimeZone): List<DailyFootprint> {
        dailyCache?.takeIf { it.first == zone }?.let { return it.second }
        return synchronized(this) {
            dailyCache?.takeIf { it.first == zone }?.second ?: DailyFootprints.build(this,zone).also {
                dailyCache = (zone.clone() as java.util.TimeZone) to it
            }
        }
    }
    val dayCount get() = DailyFootprints.days(this).size
    val cellCount by lazy { realTracks.flatMap { it.points }.map { floor(it.lat / 0.005).toInt() to floor(it.lon / 0.005).toInt() }.toSet().size }
}

object Geo {
    fun plausibleTimestamp(fixTime: Long, now: Long): Boolean =
        fixTime in (now - 120_000L)..(now + 60_000L)

    // Android's monotonic location age is more reliable than a provider wall-clock.
    // Recover the timestamp only for a genuinely fresh fix; never revive stale GPS.
    fun normalizedFixTime(providerTime: Long, receivedAt: Long, ageMillis: Long): Long? {
        if (ageMillis !in 0L..30_000L) return null
        return if (plausibleTimestamp(providerTime, receivedAt)) providerTime
               else receivedAt - ageMillis
    }

    fun meters(a: TrackPoint, b: TrackPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 6371000.0 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
    // Points in this list have already been accepted into persistent storage.
    // Do not reapply the stricter no-motion filter when counting recorded distance:
    // a short but genuine accepted step would otherwise be drawn but report 0 m.
    fun distance(points: List<TrackPoint>): Double = points.zipWithNext().sumOf { (a, b) ->
        val distance = meters(a, b)
        if (RecordedTrail.connected(a,b) && distance >= 3.0) distance else 0.0
    }
    // Speed or three consistent recent GPS fixes may lower the minimum movement
    // threshold. With no motion evidence, retain the strict drift rejection.
    fun accept(previous: TrackPoint?, next: TrackPoint, confidentlyMoving: Boolean = false): Boolean =
        rejectionReason(previous, next, confidentlyMoving) == null

    fun rejectionReason(previous: TrackPoint?, next: TrackPoint, confidentlyMoving: Boolean = false,
                        reportedSpeed: Double? = null): String? {
        if (!next.lat.isFinite() || !next.lon.isFinite() || next.lat !in -90.0..90.0 ||
            next.lon !in -180.0..180.0) return "INVALID_COORDINATE"
        if (!next.accuracy.isFinite() || next.accuracy !in 1f..35f) return "UNRELIABLE_ACCURACY"
        if (previous == null) return null
        val seconds = (next.time - previous.time) / 1000.0
        if (seconds <= 0) return "NON_INCREASING_TIME"
        val distance = meters(previous, next)
        val maxAccuracy = max(previous.accuracy.toDouble(), next.accuracy.toDouble())
        val minimumMove = if (confidentlyMoving) max(3.0, maxAccuracy * 0.55)
                          else max(4.0, maxAccuracy * 1.25)
        if (distance < minimumMove) return "STATIONARY_OR_SMALL_MOVE"
        // A gap changes continuity, never whether a stationary fix counts as movement.
        if (seconds * 1000 > RecordedTrail.MAX_GAP_MS) return null
        val speed=distance/seconds
        if (speed>RecordedTrail.MAX_SPEED_MPS) return "JUMP_OR_TOO_FAST"
        // Fast travel needs precise fixes and, when the receiver measures its own speed, a
        // displacement that agrees with it. A GPS jump shows up as travel far faster than the
        // speed the chip is actually measuring, so refusing only that direction keeps genuine
        // cycling and driving while still dropping a jump that no real vehicle could have made.
        if(speed>RecordedTrail.FAST_TRAVEL_MPS) {
            if(maxAccuracy>20.0) return "UNRELIABLE_ACCURACY"
            if(reportedSpeed!=null) {
                if(!reportedSpeed.isFinite() || reportedSpeed !in 0.0..RecordedTrail.MAX_SPEED_MPS) return "JUMP_OR_TOO_FAST"
                if(speed-reportedSpeed>max(4.0,maxAccuracy*2/seconds)) return "JUMP_OR_TOO_FAST"
            }
        }
        return null
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
