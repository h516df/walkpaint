package cn.walkpaint

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.cos
import kotlin.math.round

/** One small explicit request. Never uploads tracks, retries, or downloads in the background. */
internal object NearbyRoads {
    const val ENDPOINT = "https://overpass-api.de/api/interpreter"
    data class Area(val south: Double, val west: Double, val north: Double, val east: Double) {
        val bbox get() = listOf(south, west, north, east).joinToString(",") { String.format(Locale.ROOT, "%.5f", it) }
    }
    fun area(lat: Double, lon: Double): Area {
        require(lat.isFinite() && lon.isFinite() && lat in -84.0..84.0 && lon in -179.9..179.9)
        // Quantize the centre to about 100 metres; disclose the region rather than a GPS fix.
        val y = round(lat * 1000) / 1000
        val x = round(lon * 1000) / 1000
        val dy = 600.0 / 111195.0
        val dx = dy / cos(Math.toRadians(y))
        return Area(y-dy, x-dx, y+dy, x+dx)
    }
    fun usableFix(ageNanos: Long, accuracy: Float): Boolean =
        ageNanos in 0L..300_000_000_000L && accuracy in 1f..200f
    fun query(area: Area): String = "[out:json][timeout:25][maxsize:16777216];" +
        "way[\"highway\"~\"^(residential|living_street|service|unclassified|tertiary|secondary|primary|trunk|motorway|footway|pedestrian|path|cycleway|track|steps|road|primary_link|secondary_link|tertiary_link|trunk_link|motorway_link)$\"](${area.bbox});out meta geom;"

    fun decode(text: String): String {
        require(text.toByteArray(Charsets.UTF_8).size <= RoadPackCodec.MAX_BYTES) { "道路响应过大，请改用小区域道路文件" }
        val root = JSONObject(text)
        require(!root.has("remark")) { "道路服务未完整返回，请稍后重试" }
        val elements = root.getJSONArray("elements")
        require(elements.length() in 1..2000) { "附近暂无道路或区域过密，请改用道路文件" }
        val features = JSONArray()
        for (i in 0 until elements.length()) {
            val way = elements.getJSONObject(i)
            require(way.getString("type") == "way") { "道路服务返回格式异常" }
            val geometry = way.getJSONArray("geometry")
            require(geometry.length() == way.getJSONArray("nodes").length()) { "道路几何不完整，已取消导入" }
            val coords = JSONArray()
            for (j in 0 until geometry.length()) {
                val point = geometry.getJSONObject(j)
                coords.put(JSONArray().put(point.getDouble("lon")).put(point.getDouble("lat")))
            }
            features.put(JSONObject().put("type", "Feature")
                .put("properties", JSONObject().put("osm_way_id", way.get("id"))
                    .put("osm_version", way.get("version")).put("highway", way.getJSONObject("tags").getString("highway")))
                .put("geometry", JSONObject().put("type", "LineString").put("coordinates", coords)))
        }
        val pack = JSONObject().put("type", "FeatureCollection")
            .put("walkpaint", JSONObject().put("version", 1).put("coordinates", "WGS84")
                .put("license", "ODbL-1.0").put("attribution", "© OpenStreetMap contributors")
                .put("name", "附近道路").put("source", ENDPOINT))
            .put("features", features).toString()
        RoadPackCodec.decode(pack) // Reuse the full validation and identity rules before any write.
        return pack
    }
}

internal class RoadDownload(private val openConnection: () -> HttpURLConnection = {
    URI(NearbyRoads.ENDPOINT).toURL().openConnection() as HttpURLConnection
}) {
    @Volatile private var cancelled = false
    val isCancelled get() = cancelled
    @Volatile private var connection: HttpURLConnection? = null
    fun cancel() { cancelled = true; connection?.disconnect() }
    fun fetch(area: NearbyRoads.Area): String {
        check(!cancelled) { "下载已取消" }
        val conn = openConnection()
        connection = conn
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 35000
            conn.instanceFollowRedirects = false // No implicit sharing with another host.
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            conn.setRequestProperty("User-Agent", "WalkPaint/0.6 (personal Android road coverage)")
            val body = "data=" + URLEncoder.encode(NearbyRoads.query(area), "UTF-8")
            conn.setFixedLengthStreamingMode(body.toByteArray(Charsets.UTF_8).size)
            check(!cancelled) { "下载已取消" }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            check(status == 200) { if (status == 429 || status == 504) "道路服务繁忙，请稍后重试" else "道路服务暂不可用（HTTP $status）" }
            val output = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buffer = ByteArray(16384)
                val deadline = System.nanoTime() + 40_000_000_000L
                while (true) {
                    check(!cancelled && System.nanoTime() < deadline) { "下载取消或超时，请重试" }
                    val n = input.read(buffer)
                    if (n < 0) break
                    require(output.size() + n <= RoadPackCodec.MAX_BYTES) { "道路响应超过8MB" }
                    output.write(buffer, 0, n)
                }
            }
            check(!cancelled) { "下载已取消" }
            return NearbyRoads.decode(output.toString("UTF-8"))
        } finally { conn.disconnect(); connection = null }
    }
}
