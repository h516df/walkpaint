package cn.walkpaint

import android.app.Instrumentation
import java.util.Locale

/**
 * Seeds the anonymous high-density fixture the fog and trail acceptance runs against.
 *
 * Injecting fixes through the emulator's GPS console takes one second per fix, so a history of a
 * few thousand points would take an hour and would still be a different history every run. This
 * writes the same deterministic route straight into the app's own database instead: a street
 * walked four times with different lateral offsets, a second street walked twice, and a loop.
 *
 * With `reset` it replaces only this probe's own fixture rows. All unrelated trips and roads
 * are preserved. The caller guards the dedicated AVD; China exercises the SDK coordinate shift,
 * while the historical Paris fixture covers the unchanged coordinates outside that area.
 */
internal object FogTrailProbe {
    const val TRIP_PREFIX = "fogperf-fixture-v2"
    const val BASE_LAT = 48.8566
    const val BASE_LON = 2.3522
    /** About twelve kilometres east of the first cluster: far outside any single eight kilometre frame. */
    const val FAR_LAT = 48.8566
    const val FAR_LON = 2.5162
    private const val DAY = 24 * 60 * 60 * 1000L
    private const val STEP_M = 1.4
    private const val ACCURACY = 6.0

    fun run(instrumentation: Instrumentation, reset: Boolean, region: String = "paris"): String {
        require(region == "paris" || region == "china")
        val prefix = if (region == "china") TRIP_PREFIX + "-cn" else TRIP_PREFIX
        val context = instrumentation.targetContext
        FootprintDatabase(context).use { db ->
            val writable = db.writableDatabase
            if (reset) {
                writable.beginTransaction()
                try {
                    // Only replace this probe's own rows. Preserve every unrelated trip and road.
                    writable.execSQL("DELETE FROM road_matches WHERE point_id IN (SELECT p.id FROM points p JOIN trips t ON t.id=p.trip_id WHERE t.id LIKE ?)", arrayOf(prefix + "%"))
                    writable.execSQL("DELETE FROM points WHERE trip_id IN (SELECT id FROM trips WHERE id LIKE ?)", arrayOf(prefix + "%"))
                    writable.execSQL("DELETE FROM trips WHERE id LIKE ?", arrayOf(prefix + "%"))
                    writable.setTransactionSuccessful()
                } finally {
                    writable.endTransaction()
                }
            }
            val now = System.currentTimeMillis()
            val trips = fixtures(now).map { trip ->
                if (region != "china") trip else trip.copy(
                    id = trip.id.replace(TRIP_PREFIX, prefix),
                    points = trip.points.map { point ->
                        doubleArrayOf(point[0] - BASE_LAT + 39.9075,
                            point[1] - BASE_LON + 116.3913, point[2])
                    })
            }
            var written = 0
            writable.beginTransaction()
            try {
                trips.forEach { trip ->
                    writable.execSQL("INSERT OR IGNORE INTO trips(id,started,demo) VALUES(?,?,0)",
                        arrayOf<Any>(trip.id, trip.start))
                    trip.points.forEach { point ->
                        writable.execSQL(
                            "INSERT INTO points(trip_id,lat,lon,time,accuracy,segment) VALUES(?,?,?,?,?,0)",
                            arrayOf<Any>(trip.id, point[0], point[1], point[2].toLong(), ACCURACY))
                        written++
                    }
                }
                writable.setTransactionSuccessful()
            } finally {
                writable.endTransaction()
            }
            val kept = stored(writable, prefix)
            check(written == kept) { "fixture rows missing: wrote $written, stored $kept" }
            check(kept >= 3000) { "fixture is too small to prove anything: $kept points" }
            // The anchor is the end of the near cluster: "定位我" must stay there while the map is
            // panned to the far cluster, which is what the large-history check needs.
            val near = trips.first { it.id.endsWith("-e") }.points.last()
            val far = trips.first { it.id.endsWith("-f") }.points.last()
            return String.format(Locale.ROOT,
                "FOG_TRAIL_PROBE_PASS seeded points=%d trips=%d end_lat=%.7f end_lon=%.7f " +
                    "far_lat=%.7f far_lon=%.7f",
                kept, trips.size, near[0], near[1], far[0], far[1])
        }
    }

    private fun stored(writable: android.database.sqlite.SQLiteDatabase, prefix: String): Int =
        writable.rawQuery(
            "SELECT COUNT(*) FROM points p JOIN trips t ON t.id=p.trip_id WHERE t.id LIKE ?",
            arrayOf(prefix + "%")).use { it.moveToFirst(); it.getInt(0) }

    private data class FixtureTrip(val id: String, val start: Long, val points: List<DoubleArray>)

    private fun fixtures(now: Long): List<FixtureTrip> {
        val minute = 60_000L
        // The last trip ends now, so the map's follow camera lands on the street that was walked
        // four times: that is the view a repeat has to look right in.
        return listOf(
            FixtureTrip(TRIP_PREFIX + "-a", now - 88 * minute, Walk(now - 88 * minute).apply {
                add()
                leg(north = 500.0, east = 0.0, lateralM = 0.0)
                leg(north = -500.0, east = 0.0, lateralM = 7.0)
            }.points),
            FixtureTrip(TRIP_PREFIX + "-b", now - 70 * minute, Walk(now - 70 * minute).apply {
                add()
                leg(north = 500.0, east = 0.0, lateralM = -6.0)
                leg(north = -500.0, east = 0.0, lateralM = -6.0)
            }.points),
            FixtureTrip(TRIP_PREFIX + "-c", now - 45 * minute, Walk(now - 45 * minute).apply {
                add()
                leg(north = 0.0, east = 400.0)
                leg(north = 400.0, east = 0.0)
                leg(north = 0.0, east = -400.0)
                leg(north = -400.0, east = 0.0)
            }.points),
            FixtureTrip(TRIP_PREFIX + "-d", now - 20 * minute, Walk(now - 20 * minute).apply {
                add()
                leg(north = 500.0, east = 0.0, lateralM = 250.0)
                leg(north = -500.0, east = 0.0, lateralM = 258.0)
            }.points),
            FixtureTrip(TRIP_PREFIX + "-e", now - 6 * minute, Walk(now - 6 * minute).apply {
                add()
                leg(north = 300.0, east = 0.0, lateralM = 9.0)
            }.points),
            // A second cluster about twelve kilometres east, two days old. It is the ground the map
            // has to keep painting when the person pans back to it, and the unknown ground between
            // the two clusters is what the veil has to keep covering.
            FixtureTrip(TRIP_PREFIX + "-f", now - 2 * DAY - 30 * minute, Walk(now - 2 * DAY - 30 * minute, FAR_LAT, FAR_LON).apply {
                add()
                leg(north = 600.0, east = 0.0, lateralM = 0.0)
                leg(north = -600.0, east = 0.0, lateralM = 8.0)
            }.points),
            FixtureTrip(TRIP_PREFIX + "-g", now - 2 * DAY - 10 * minute, Walk(now - 2 * DAY - 10 * minute, FAR_LAT, FAR_LON).apply {
                add()
                leg(north = 0.0, east = 400.0)
                leg(north = 300.0, east = 0.0)
            }.points),
        )
    }

    /** Walks a leg at a walking pace, one fix per second, in metres from the start point. */
    private class Walk(private var time: Long, private val originLat: Double = BASE_LAT,
                       private val originLon: Double = BASE_LON) {
        private var x = 0.0
        private var y = 0.0
        private var lateral = 0.0
        val points = mutableListOf<DoubleArray>()

        fun add() {
            points.add(at(0.0, 0.0))
        }

        fun leg(north: Double, east: Double, lateralM: Double = lateral) {
            lateral = lateralM
            val steps = maxOf(1, Math.round(kotlin.math.hypot(north, east) / STEP_M).toInt())
            repeat(steps) {
                x += east / steps
                y += north / steps
                time += 1000
                points.add(at(x, y))
            }
        }

        private fun at(x: Double, y: Double): DoubleArray {
            val lat = originLat + y / 111194.9
            val lon = originLon + (x + lateral) /
                (111194.9 * kotlin.math.cos(Math.toRadians(originLat)))
            return doubleArrayOf(lat, lon, time.toDouble())
        }
    }
}
