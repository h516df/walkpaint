package cn.walkpaint

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import android.util.AtomicFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Durable GPS history. Database is deliberately independent of both map SDKs and UI.
 * Future schema upgrades belong in onUpgrade; old v0.1 tracks.json is migrated once.
 */
class FootprintDatabase(private val context: Context) :
    SQLiteOpenHelper(context, "footprints.db", null, 1) {

    @Volatile var lastError: String? = null
        private set
    @Volatile var lastFilterReason: String = "NONE"
        private set
    @Volatile var revision: Long = 0
        private set
    private var cached: TrackState? = null
    private val preferences = context.getSharedPreferences("footprint_settings", Context.MODE_PRIVATE)

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS trips (id TEXT PRIMARY KEY, started INTEGER NOT NULL, demo INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS points (id INTEGER PRIMARY KEY AUTOINCREMENT, trip_id TEXT NOT NULL REFERENCES trips(id) ON DELETE CASCADE, lat REAL NOT NULL, lon REAL NOT NULL, time INTEGER NOT NULL, accuracy REAL NOT NULL, segment INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_trip_time ON points(trip_id,time,id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_points_time ON points(time)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Explicit migrations here; never drop user track tables.
        if (oldVersion != newVersion) throw IllegalStateException("需要显式数据库迁移：$oldVersion → $newVersion")
    }

    init {
        try {
            writableDatabase // Initialize before testing the legacy archive.
            migrateLegacyJson()
        } catch (e: Exception) {
            lastError = "数据库初始化失败：请先备份原文件，不会自动清除足迹"
        }
    }

    @Synchronized
    private fun migrateLegacyJson() {
        if (preferences.getBoolean("v1_migration_finished", false)) return
        val legacy = File(context.filesDir, "tracks.json")
        if (legacy.exists()) {
            val source = try { TrackCodec.decode(legacy.readText(Charsets.UTF_8)) }
            catch (e: Exception) {
                lastError = "旧版足迹无法读取。原始文件已保留，请勿清除应用数据"
                return
            }
            val db = writableDatabase
            db.beginTransaction()
            try {
                for (trip in source.tracks) {
                    val exists = db.rawQuery("SELECT 1 FROM trips WHERE id=? LIMIT 1", arrayOf(trip.id))
                        .use { it.moveToFirst() }
                    if (!exists) insertTrip(db, trip)
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            // Keep tracks.json as a recovery copy; never delete the original.
        }
        preferences.edit().putBoolean("v1_migration_finished", true).apply()
        cached = null
    }

    private fun insertTrip(db: SQLiteDatabase, trip: Track) {
        val row = ContentValues().apply {
            put("id", trip.id)
            put("started", trip.started)
            put("demo", if (trip.demo) 1 else 0)
        }
        db.insertWithOnConflict("trips", null, row, SQLiteDatabase.CONFLICT_IGNORE)
        for (point in trip.points) {
            val cell = ContentValues().apply {
                put("trip_id", trip.id)
                put("lat", point.lat)
                put("lon", point.lon)
                put("time", point.time)
                put("accuracy", point.accuracy)
                put("segment", point.segment)
            }
            db.insertOrThrow("points", null, cell)
        }
    }

    @Synchronized fun snapshot(): TrackState {
        cached?.let { return it }
        val tracks = mutableListOf<Track>()
        val db = readableDatabase
        db.rawQuery("SELECT id,started,demo FROM trips ORDER BY started,id", null).use { trips ->
            while (trips.moveToNext()) {
                val id = trips.getString(0)
                val points = ArrayList<TrackPoint>()
                db.rawQuery(
                    "SELECT lat,lon,time,accuracy,segment FROM points WHERE trip_id=? ORDER BY time,id",
                    arrayOf(id)
                ).use { locations ->
                    while (locations.moveToNext()) points += TrackPoint(
                        locations.getDouble(0), locations.getDouble(1), locations.getLong(2),
                        locations.getFloat(3), locations.getInt(4)
                    )
                }
                tracks += Track(id, trips.getLong(1), trips.getInt(2) != 0, points)
            }
        }
        val state = TrackState(tracks, null)
        cached = state
        return state
    }

    @Synchronized fun addPosition(location: Location, confidentlyMoving: Boolean = false): Boolean {
        if (lastError != null) return false
        val wallClock = System.currentTimeMillis()
        if (!Geo.plausibleTimestamp(location.time, wallClock)) { lastFilterReason = "STALE_FIX"; return false }
        val now = TrackPoint(location.latitude, location.longitude, location.time, location.accuracy)
        // One indexed last-point query, rather than loading all history for every GPS fix.
        var currentId: String? = null
        val previous = readableDatabase.rawQuery(
            "SELECT p.lat,p.lon,p.time,p.accuracy,p.segment,p.trip_id FROM points p JOIN trips t ON t.id=p.trip_id WHERE t.demo=0 ORDER BY p.time DESC,p.id DESC LIMIT 1", null
        ).use { c -> if (c.moveToFirst()) {
            currentId = c.getString(5)
            TrackPoint(c.getDouble(0), c.getDouble(1), c.getLong(2), c.getFloat(3), c.getInt(4))
        } else null }
        val gap = if (previous == null) Long.MAX_VALUE else now.time - previous.time
        val newTrip = currentId == null || previous == null || gap > 25 * 60_000L ||
            localDay(now.time) != localDay(previous.time)
        val rejection = Geo.rejectionReason(if (newTrip) null else previous, now, confidentlyMoving)
        if (rejection != null) { lastFilterReason = rejection; return false }

        val db = writableDatabase
        val id = if (newTrip) UUID.randomUUID().toString() else currentId!!
        val stored = now.copy(segment = if (newTrip) 0 else previous!!.segment + if (gap > 45_000L) 1 else 0)
        db.beginTransaction()
        try {
            if (newTrip) {
                db.insertOrThrow("trips", null, ContentValues().apply {
                    put("id", id); put("started", now.time); put("demo", 0)
                })
            }
            db.insertOrThrow("points", null, ContentValues().apply {
                put("trip_id", id)
                put("lat", now.lat); put("lon", now.lon)
                put("time", now.time); put("accuracy", now.accuracy)
                put("segment", stored.segment)
            })
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            lastError = "足迹写入失败。请检查存储空间并尽快备份"
            return false
        } finally { db.endTransaction() }
        cached = cached?.let { state ->
            if (newTrip) state.copy(tracks = state.tracks + Track(id, now.time, false, listOf(stored)))
            else state.copy(tracks = state.tracks.map { if (it.id == id) it.copy(points = it.points + stored) else it })
        }
        revision++
        maybeDailyBackup(now.time)
        return true
    }

    @Synchronized fun setDemo(enabled: Boolean) {
        ensureWritable()
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("trips", "demo=1", null)
            if (enabled) {
                val now = System.currentTimeMillis()
                val positions = listOf(
                    0.0000 to 0.0000, 0.0009 to 0.0011, 0.0018 to 0.0022,
                    0.0026 to 0.0019, 0.0031 to 0.0006, 0.0022 to -0.0004,
                    0.0014 to -0.0010, 0.0005 to -0.0004, 0.0000 to 0.0000
                )
                insertTrip(db, Track("demo-preview", now, true, positions.mapIndexed { i, p ->
                    TrackPoint(31.2304 + p.first, 121.4737 + p.second, now + i * 10_000L, 5f)
                }))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        cached = null
        revision++
    }

    @Synchronized fun exportBackup(): String = TrackCodec.encode(snapshot())

    /** Merge, never overwrite existing trips or a running auto-tracking session. */
    @Synchronized fun importBackup(text: String): Int {
        ensureWritable()
        require(text.length <= 80 * 1024 * 1024) { "备份文件过大" }
        val imported = TrackCodec.decode(text)
        require(imported.tracks.size <= 50000) { "足迹数量异常" }
        require(imported.tracks.sumOf { it.points.size.toLong() } <= 3_000_000) { "定位点数量过大" }
        val originalIds = snapshot().tracks.map { it.id }.toHashSet()
        var added = 0
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (t in imported.tracks) {
                if (t.id in originalIds || t.demo) continue
                require(t.points.all { p ->
                    p.lat in -90.0..90.0 && p.lon in -180.0..180.0 &&
                        p.lat.isFinite() && p.lon.isFinite() && p.accuracy.isFinite()
                }) { "备份中的经纬度无效" }
                insertTrip(db, t)
                added++
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        cached = null
        revision++
        return added
    }

    @Synchronized fun backupFiles(): List<File> =
        File(context.filesDir, "auto-backups").listFiles()?.filter { it.name.endsWith(".json") }
            ?.sortedByDescending { it.name } ?: emptyList()

    private fun localDay(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(timestamp))

    /** A local rolling daily snapshot; separate off-device backups remain user initiated. */
    private fun maybeDailyBackup(timestamp: Long) {
        if (lastError != null) return
        val date = localDay(timestamp)
        val previousBackup = preferences.getLong("last_auto_backup_time", 0L)
        if (previousBackup > 0 && timestamp - previousBackup < 30 * 60_000L) return
        try {
            val directory = File(context.filesDir, "auto-backups")
            if (!directory.exists()) directory.mkdirs()
            val atomic = AtomicFile(File(directory, "footprints-$date.json"))
            val stream = atomic.startWrite()
            try {
                stream.write(exportBackup().toByteArray(Charsets.UTF_8))
                atomic.finishWrite(stream)
            } catch (e: Exception) {
                atomic.failWrite(stream)
                throw e
            }
            preferences.edit().putLong("last_auto_backup_time", timestamp).apply()
        } catch (_: Exception) { /* Primary SQLite remains authoritative. */ }
    }

    private fun ensureWritable() { check(lastError == null) { lastError!! } }
}

object FootprintInsights {
    fun traveledDays(state: TrackState): Int = state.dayCount
    fun exploredCells(state: TrackState): Int = state.cellCount

    fun lastVisited(state: TrackState, limit: Int = 10): List<Track> =
        state.realTracks.sortedByDescending { it.points.lastOrNull()?.time ?: it.started }.take(limit)
}
