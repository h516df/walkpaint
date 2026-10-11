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
 * With `reset` it first clears the table so every run starts from exactly the same scene; the
 * database it clears belongs to the throwaway test AVD, never to a person. The route sits in a
 * densely mapped, anonymous part of Paris so the veil is judged over real streets.
 */
internal object FogTrailProbe {
    const val TRIP_PREFIX = "fogperf-fixture-v2"
    const val BASE_LAT = 48.8566
    const val BASE_LON = 2.3522
    private const val STEP_M = 1.4
    private const val ACCURACY = 6.0

    fun run(instrumentation: Instrumentation, reset: Boolean): String {
        val context = instrumentation.targetContext
        FootprintDatabase(context).use { db ->
            val writable = db.writableDatabase
            if (reset) {
                writable.beginTransaction()
                try {
                    listOf("road_matches", "points", "trips", "coverage", "roads").forEach {
                        writable.execSQL("DELETE FROM " + it)
                    }
                    writable.setTransactionSuccessful()
                } finally {
                    writable.endTransaction()
                }
            }
            val now = System.currentTimeMillis()
            val trips = fixtures(now)
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
            val kept = stored(writable)
            check(written == kept) { "fixture rows missing: wrote $written, stored $kept" }
            check(kept >= 3000) { "fixture is too small to prove anything: $kept points" }
            val end = trips.last().points.last()
            return String.format(Locale.ROOT,
                "FOG_TRAIL_PROBE_PASS seeded points=%d trips=%d end_lat=%.7f end_lon=%.7f",
                kept, trips.size, end[0], end[1])
        }
    }

    private fun stored(writable: android.database.sqlite.SQLiteDatabase): Int =
        writable.rawQuery(
            "SELECT COUNT(*) FROM points p JOIN trips t ON t.id=p.trip_id WHERE t.id LIKE ?",
            arrayOf(TRIP_PREFIX + "%")).use { it.moveToFirst(); it.getInt(0) }

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
        )
    }

    /** Walks a leg at a walking pace, one fix per second, in metres from the start point. */
    private class Walk(private var time: Long) {
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
            val lat = BASE_LAT + y / 111194.9
            val lon = BASE_LON + (x + lateral) /
                (111194.9 * kotlin.math.cos(Math.toRadians(BASE_LAT)))
            return doubleArrayOf(lat, lon, time.toDouble())
        }
    }
}
