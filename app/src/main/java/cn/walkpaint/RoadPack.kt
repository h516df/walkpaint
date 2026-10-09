package cn.walkpaint

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.math.*

/** Only locally selected, attributed OSM data; never map tile features or tile IDs. */
internal data class RoadPack(val name: String, val source: String, val roads: List<RoadSegment>)
internal object RoadPackCodec {
    const val MAX_BYTES = 8 * 1024 * 1024
    private val highways = setOf("residential", "living_street", "service", "unclassified", "tertiary",
        "secondary", "primary", "trunk", "motorway", "footway", "pedestrian", "path", "cycleway",
        "track", "steps", "road", "primary_link", "secondary_link", "tertiary_link", "trunk_link", "motorway_link")
    fun decode(text: String): RoadPack {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "道路包超过8MB" }
        val root = JSONObject(text)
        require(root.getString("type") == "FeatureCollection" && !root.has("crs")) { "必须是 WGS84 GeoJSON" }
        val meta = root.getJSONObject("walkpaint")
        require(meta.getInt("version") == 1 && meta.getString("coordinates") == "WGS84" &&
            meta.getString("license") == "ODbL-1.0" &&
            meta.getString("attribution") == "© OpenStreetMap contributors") { "道路包缺少 WGS84 / ODbL 许可声明" }
        val source = meta.getString("source")
        require(source.startsWith("https://") && source.length <= 500 &&
            !source.contains("tiles", true) && !source.contains("openfreemap", true)) { "不能导入公共地图瓦片" }
        val name = meta.getString("name")
        require(name.isNotBlank() && name.length <= 100)
        val features = root.getJSONArray("features")
        require(features.length() in 1..2000) { "道路包需要1至2000条完整 OSM way" }
        var vertices = 0
        val ids = hashSetOf<Long>()
        val roads = (0 until features.length()).map { i ->
            val f = features.getJSONObject(i)
            require(f.getString("type") == "Feature")
            val p = f.getJSONObject("properties")
            require(p.get("osm_way_id").toString().matches(Regex("[1-9][0-9]{0,18}")) &&
                p.get("osm_version").toString().matches(Regex("[1-9][0-9]{0,8}"))) { "OSM ID / 版本必须是正整数" }
            val id = p.getLong("osm_way_id")
            require(id > 0 && ids.add(id) && p.getString("highway") in highways) { "OSM way ID 重复或不是道路" }
            require(p.getInt("osm_version") > 0) { "缺少 OSM way 版本" }
            val g = f.getJSONObject("geometry")
            require(g.getString("type") == "LineString") { "需要完整 LineString，不能是瓦片裁剪段" }
            val c = g.getJSONArray("coordinates")
            vertices += c.length()
            require(c.length() in 2..10000 && vertices <= 100000) { "道路几何过大" }
            val points = (0 until c.length()).map { j ->
                val xy = c.getJSONArray(j)
                require(xy.length() == 2)
                TrackPoint(xy.getDouble(1), xy.getDouble(0), 0, 5f).also {
                    require(it.lat.isFinite() && it.lon.isFinite() && it.lat in -85.0..85.0 && it.lon in -180.0..180.0)
                }
            }
            require(points.zipWithNext().all { (a,b) -> Geo.meters(a,b) in 0.01..2000.0 }) { "重复坐标或道路边超过2公里" }
            // Identity is OSM way + canonical FULL geometry revision, never a hosted tile ID.
            val canonical = canonical(points)
            val hash = MessageDigest.getInstance("SHA-256").digest(coordinates(canonical).toString().toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            RoadSegment("osm:way:$id:g:$hash", canonical,p.getInt("osm_version"),p.getString("highway"))
        }
        return RoadPack(name, source, roads)
    }
    private fun canonical(points: List<TrackPoint>): List<TrackPoint> {
        val reversed = points.reversed()
        for (i in points.indices) {
            val c = compareValuesBy(points[i], reversed[i], { it.lat }, { it.lon })
            if (c != 0) return if (c < 0) points else reversed
        }
        return points
    }
    fun coordinates(points: List<TrackPoint>) = JSONArray().apply {
        points.forEach { put(JSONArray().put(it.lon).put(it.lat)) }
    }
    fun encode(pack: RoadPack): String = JSONObject().put("type", "FeatureCollection")
        .put("walkpaint", JSONObject().put("version",1).put("coordinates","WGS84")
            .put("license","ODbL-1.0").put("attribution","© OpenStreetMap contributors")
            .put("name",pack.name).put("source",pack.source))
        .put("features", JSONArray().apply { pack.roads.forEach { r ->
            put(JSONObject().put("type","Feature").put("properties",JSONObject()
                .put("osm_way_id",r.stableId.split(":")[2].toLong()).put("osm_version",r.osmVersion).put("highway",r.highway))
                .put("geometry",JSONObject().put("type","LineString").put("coordinates",coordinates(r.points))))
        } }).toString()
}

/** Bounded local grid. No network and no search over all vertices per recorded fix. */
internal class RoadCatalog(val roads: List<RoadSegment>) {
    private val cells = mutableMapOf<Pair<Int,Int>, MutableSet<Int>>()
    private fun cell(v: Double) = floor(v / 0.002).toInt()
    init {
        require(roads.sumOf { it.points.size.toLong() } <= 200000) { "累计道路顶点超过20万" }
        var entries = 0
        roads.forEachIndexed { index,r -> r.points.zipWithNext().forEach { (a,b) ->
        for (y in cell(min(a.lat,b.lat))..cell(max(a.lat,b.lat)))
            for (x in cell(min(a.lon,b.lon))..cell(max(a.lon,b.lon))) {
                require(++entries <= 500000) { "道路包索引过大，请缩小区域" }
                cells.getOrPut(y to x) { hashSetOf() }.add(index)
            }
    } } }
    fun near(p: TrackPoint): List<RoadSegment> {
        val result = hashSetOf<Int>()
        // Longitude cells narrow toward the poles. Include a physical 30m buffer.
        val dy = 30.0 / 111195.0
        val dx = dy / cos(Math.toRadians(p.lat)).coerceAtLeast(0.08)
        for(y in cell(p.lat-dy)..cell(p.lat+dy)) for(x in cell(p.lon-dx)..cell(p.lon+dx))
            result.addAll(cells[y to x].orEmpty())
        return result.map { roads[it] }
    }
    fun traversed(a: TrackPoint, b: TrackPoint): PaintedInterval? {
        if(a.segment != b.segment || b.time-a.time !in 1L..45000L) return null
        val start=RoadMatcher.match(a,near(a)) ?: return null
        val end=RoadMatcher.match(b,near(b)) ?: return null
        if(start.road.stableId != end.road.stableId || abs(start.along-end.along) !in 3.0..120.0) return null
        val interval=PaintedInterval(start.road.stableId,min(start.along,end.along),max(start.along,end.along))
        val candidates=mutableMapOf<String,RoadSegment>()
        // Collect ALL nearby roads along the proposed curved section, including high-latitude
        // crossings outside endpoint grid cells; an endpoint-only query is unsafe.
        val line=RoadCoverage(listOf(interval)).highlighted(start.road).single()
        for((p,q) in line.zipWithNext()) {
            val steps=ceil(Geo.meters(p,q)/5).toInt().coerceAtLeast(1)
            for(i in 0..steps) {
                val t=i.toDouble()/steps
                near(p.copy(lat=p.lat+(q.lat-p.lat)*t,lon=p.lon+(q.lon-p.lon)*t)).forEach {
                    candidates[it.stableId]=it
                }
            }
        }
        return RoadMatcher.traversed(a,b,candidates.values.toList())
    }
}

internal data class RoadState(val roads: List<RoadSegment> = emptyList(),
    val intervals: List<PaintedInterval> = emptyList(), val pairs: Int = 0, val unmapped: Int = 0,
    val matched: Int = 0, val error: String? = null) {
    val coverage = RoadCoverage(intervals)
    val meters get() = coverage.meters
    val totalMeters get() = roads.sumOf { it.length }
    val summary get() = if(error != null) error
        else if (roads.isEmpty()) "未导入道路包 · 不会把 GPS 折线当道路"
        else "累计涂色 %.0f 米 / 已导入 %.0f 米 · %d 条道路\n%d 段未确认（断档、路口、精度或包外），%d 段已确认".format(
            meters,totalMeters,roads.size,unmapped,matched)
    fun geoJson(painted: Boolean): String {
        val features = JSONArray()
        roads.forEach { r ->
            val lines = if (painted) coverage.highlighted(r) else listOf(r.points)
            lines.forEach { points -> features.put(JSONObject().put("type","Feature")
                .put("properties", JSONObject()).put("geometry",JSONObject().put("type","LineString")
                    .put("coordinates",RoadPackCodec.coordinates(points)))) }
        }
        return JSONObject().put("type","FeatureCollection").put("features",features).toString()
    }
}
