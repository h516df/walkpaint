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
    SQLiteOpenHelper(context, "footprints.db", null, 3) {

    /** Most recent user-visible data problem. Display only; the write gate is [fatalError]. */
    @Volatile var lastError: String? = null
        private set
    /**
     * Set only when the database itself is unusable, and permanent for this process.
     * A transient write failure must never land here: it would stop recording until the
     * user kills the app, which is exactly the recovery path this class must not require.
     */
    @Volatile private var fatalError: String? = null
    /** True while a dropped GPS fix has not yet been followed by a successful commit. */
    @Volatile private var writeFailureLatched = false
    @Volatile internal var lastRoadError: String? = null
        private set
    @Volatile var lastFilterReason: String = "NONE"
        private set
    @Volatile var revision: Long = 0
        private set
    private var cached: TrackState? = null
    internal val journal=FootprintJournal(File(context.filesDir,"footprint-journal.json"))
    @Volatile internal var backupCountCached=0
        private set
    @Synchronized internal fun refreshBackupCount():Int {
        backupCountCached=backupFiles().size
        return backupCountCached
    }
    private var roadCatalog: RoadCatalog? = null
    private val preferences = context.getSharedPreferences("footprint_settings", Context.MODE_PRIVATE)
    @Volatile internal var backupStatus:String?=preferences.getString("auto_backup_status",null)
        private set
    private val backupSchedule=BackupSchedule()
    private val backupExecutor=java.util.concurrent.Executors.newSingleThreadExecutor {r ->
        Thread(r,"WalkPaintBackup").apply {isDaemon=true}
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS trips (id TEXT PRIMARY KEY, started INTEGER NOT NULL, demo INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS points (id INTEGER PRIMARY KEY AUTOINCREMENT, trip_id TEXT NOT NULL REFERENCES trips(id) ON DELETE CASCADE, lat REAL NOT NULL, lon REAL NOT NULL, time INTEGER NOT NULL, accuracy REAL NOT NULL, segment INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_trip_time ON points(trip_id,time,id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_points_time ON points(time)")
        createRoadTables(db)
        createRecordingCursor(db,false)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Explicit migrations here; never drop user track tables.
        check(oldVersion in 1..2 && newVersion==3) {"需要显式数据库迁移：$oldVersion → $newVersion"}
        if(oldVersion==1) createRoadTables(db)
        createRecordingCursor(db,true)
    }

    /** Only successful live commits advance this cursor; imported history cannot take it over. */
    private fun createRecordingCursor(db:SQLiteDatabase,seed:Boolean) {
        db.execSQL("CREATE TABLE recording_cursor (singleton INTEGER PRIMARY KEY CHECK(singleton=1), point_id INTEGER REFERENCES points(id) ON DELETE SET NULL)")
        db.execSQL("INSERT INTO recording_cursor(singleton,point_id) VALUES(1,NULL)")
        if(seed) db.execSQL("UPDATE recording_cursor SET point_id=(SELECT p.id FROM points p JOIN trips t ON t.id=p.trip_id WHERE t.demo=0 AND p.time<=? ORDER BY p.time DESC,p.id DESC LIMIT 1) WHERE singleton=1",
            arrayOf(System.currentTimeMillis()))
    }
    private data class RecordingAnchor(val trip:String,val point:TrackPoint)
    private fun recordingAnchor(db:SQLiteDatabase):RecordingAnchor? = db.rawQuery(
        "SELECT p.trip_id,p.lat,p.lon,p.time,p.accuracy,p.segment FROM recording_cursor r JOIN points p ON p.id=r.point_id JOIN trips t ON t.id=p.trip_id WHERE r.singleton=1 AND t.demo=0",null).use {c ->
        if(c.moveToFirst()) RecordingAnchor(c.getString(0),TrackPoint(c.getDouble(1),c.getDouble(2),c.getLong(3),c.getFloat(4),c.getInt(5))) else null
    }
    private fun saveRecordingCursor(db:SQLiteDatabase,pointId:Long) {
        db.execSQL("UPDATE recording_cursor SET point_id=? WHERE singleton=1",arrayOf(pointId))
    }

    private fun createRoadTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS roads (id TEXT PRIMARY KEY, osm_id INTEGER UNIQUE NOT NULL, geometry TEXT NOT NULL, source TEXT NOT NULL, name TEXT NOT NULL, osm_version INTEGER NOT NULL, highway TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS coverage (road_id TEXT NOT NULL REFERENCES roads(id) ON DELETE CASCADE, start REAL NOT NULL, end REAL NOT NULL, PRIMARY KEY(road_id,start), CHECK(start>=0 AND end>start))")
        db.execSQL("CREATE TABLE IF NOT EXISTS road_matches (point_id INTEGER PRIMARY KEY REFERENCES points(id) ON DELETE CASCADE, matched INTEGER NOT NULL)")
    }

    private fun catalog(): RoadCatalog = roadCatalog ?: RoadCatalog(readRoads()).also { roadCatalog = it }
    private fun readRoads(): List<RoadSegment> = readableDatabase.rawQuery("SELECT id,geometry,osm_version,highway FROM roads ORDER BY id",null).use { c ->
        buildList { while(c.moveToNext()) {
            val coordinates = org.json.JSONArray(c.getString(1))
            add(RoadSegment(c.getString(0),(0 until coordinates.length()).map { i ->
                val p = coordinates.getJSONArray(i); TrackPoint(p.getDouble(1),p.getDouble(0),0,5f)
            },c.getInt(2),c.getString(3)))
        } }
    }
    private fun intervals(db: SQLiteDatabase, roadId: String? = null): List<PaintedInterval> =
        db.rawQuery("SELECT road_id,start,end FROM coverage" + if(roadId == null) " ORDER BY road_id,start" else " WHERE road_id=? ORDER BY start",
            roadId?.let { arrayOf(it) }).use { c -> buildList {
            while(c.moveToNext()) add(PaintedInterval(c.getString(0),c.getDouble(1),c.getDouble(2)))
        } }
    private fun saveInterval(db: SQLiteDatabase, interval: PaintedInterval) {
        val merged = RoadCoverage(intervals(db,interval.roadId) + interval).snapshot()
        db.delete("coverage","road_id=?",arrayOf(interval.roadId))
        merged.forEach { v -> db.insertOrThrow("coverage",null,ContentValues().apply {
            put("road_id",v.roadId); put("start",v.from); put("end",v.to)
        }) }
    }
    private fun paintPair(db: SQLiteDatabase, pointId: Long, a: TrackPoint, b: TrackPoint) {
        val interval = catalog().traversed(a,b)
        check(db.insertWithOnConflict("road_matches",null,ContentValues().apply {
            put("point_id",pointId); put("matched",if(interval == null) 0 else 1)
        },SQLiteDatabase.CONFLICT_REPLACE) != -1L) { "道路匹配写入失败" }
        if(interval != null) saveInterval(db,interval)
    }
    /** Raw point is already inserted in the outer transaction. Road work alone may be undone. */
    private fun paintPairIsolated(db: SQLiteDatabase, pointId: Long, a: TrackPoint, b: TrackPoint) {
        db.execSQL("SAVEPOINT wp_road") // Failure to create/restore a savepoint is a real DB failure.
        try {
            paintPair(db,pointId,a,b)
        } catch (_: Exception) {
            // The leading empty statement prevents old Android SQL classifiers from treating
            // ROLLBACK TO as a full transaction abort (minSdk26). SQLite still executes it normally.
            db.execSQL(";ROLLBACK TO SAVEPOINT wp_road")
            roadCatalog = null
            lastRoadError = "道路涂色暂不可用，请重新导入有效道路包；GPS 记录独立保存"
        }
        // Do not swallow rollback/release failures: the outer GPS transaction must fail honestly.
        db.execSQL("RELEASE SAVEPOINT wp_road")
    }
    private fun rebuildCoverage(db: SQLiteDatabase) {
        db.delete("road_matches",null,null)
        db.delete("coverage",null,null)
        var previous: TrackPoint? = null
        var previousTrip: String? = null
        db.rawQuery("SELECT p.id,p.trip_id,p.lat,p.lon,p.time,p.accuracy,p.segment FROM points p JOIN trips t ON t.id=p.trip_id WHERE t.demo=0 ORDER BY p.trip_id,p.time,p.id",null).use { c ->
            while(c.moveToNext()) {
                val trip = c.getString(1)
                val point = TrackPoint(c.getDouble(2),c.getDouble(3),c.getLong(4),c.getFloat(5),c.getInt(6))
                if(previousTrip == trip && previous != null) paintPair(db,c.getLong(0),previous!!,point)
                previous = point; previousTrip = trip
            }
        }
    }
    @Synchronized internal fun roadSnapshot(): RoadState {
        try {
            val counts = readableDatabase.rawQuery("SELECT COUNT(*),COALESCE(SUM(matched),0) FROM road_matches",null).use {
                it.moveToFirst(); it.getInt(0) to it.getInt(1)
            }
            return RoadState(catalog().roads,intervals(readableDatabase),counts.first,counts.first-counts.second,counts.second,lastRoadError)
        } catch (_: Exception) {
            roadCatalog = null
            lastRoadError = "道路涂色暂不可用，请重新导入有效道路包；GPS 记录独立保存"
            return RoadState(error=lastRoadError)
        }
    }
    /** Reconstruct a day's confirmed intervals without changing global coverage or raw rows. */
    @Synchronized internal fun roadSnapshotForDay(day: String): RoadState {
        val format=java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.ROOT).apply { isLenient=false }
        val start=format.parse(day)!!.time
        val calendar=java.util.Calendar.getInstance().apply { timeInMillis=start; add(java.util.Calendar.DAY_OF_MONTH,1) }
        try {
            val catalog=catalog()
            val covered=RoadCoverage()
            var previous: TrackPoint?=null;var previousTrip: String?=null
            var pairs=0;var matched=0
            readableDatabase.rawQuery("SELECT p.id,p.trip_id,p.lat,p.lon,p.time,p.accuracy,p.segment,COALESCE(m.matched,0) FROM points p JOIN trips t ON t.id=p.trip_id LEFT JOIN road_matches m ON m.point_id=p.id WHERE t.demo=0 AND p.time>=? AND p.time<? ORDER BY p.trip_id,p.time,p.id",arrayOf(start.toString(),calendar.timeInMillis.toString())).use { c ->
                while(c.moveToNext()) {
                    val trip=c.getString(1)
                    val point=TrackPoint(c.getDouble(2),c.getDouble(3),c.getLong(4),c.getFloat(5),c.getInt(6))
                    if(previousTrip==trip && previous!=null) {
                        pairs++
                        if(c.getInt(7)==1) catalog.traversed(previous!!,point)?.let { covered.add(it); matched++ }
                    }
                    previous=point;previousTrip=trip
                }
            }
            return RoadState(catalog.roads,covered.snapshot(),pairs,pairs-matched,matched,lastRoadError)
        } catch(_:Exception) {
            return RoadState(error="当天道路显示暂不可用，原足迹与累计覆盖保留")
        }
    }

    private fun putPack(db: SQLiteDatabase, pack: RoadPack) {
        pack.roads.forEach { r ->
            val osmId = r.stableId.split(":")[2].toLong()
            val old = db.rawQuery("SELECT id FROM roads WHERE osm_id=?",arrayOf(osmId.toString())).use {
                if(it.moveToFirst()) it.getString(0) else null
            }
            if(old != null && old != r.stableId) db.delete("roads","id=?",arrayOf(old))
            check(db.insertWithOnConflict("roads",null,ContentValues().apply {
                put("id",r.stableId); put("osm_id",osmId); put("geometry",RoadPackCodec.coordinates(r.points).toString())
                put("source",pack.source); put("name",pack.name)
                put("osm_version",r.osmVersion); put("highway",r.highway)
            },SQLiteDatabase.CONFLICT_REPLACE) != -1L) { "道路目录写入失败" }
        }
    }
    @Synchronized internal fun importRoadPack(text: String): Int {
        ensureWritable()
        val pack = RoadPackCodec.decode(text) // Validate the ENTIRE file before changing anything.
        val db = writableDatabase
        db.beginTransaction()
        try {
            putPack(db,pack)
            roadCatalog = null
            require(catalog().roads.size <= 5000) { "累计道路超过5000条，请使用较小区域包" }
            rebuildCoverage(db)
            db.setTransactionSuccessful()
        } finally { db.endTransaction(); roadCatalog = null }
        lastRoadError = null
        revision++
        return pack.roads.size
    }

    init {
        try {
            writableDatabase // Initialize before testing the legacy archive.
            migrateLegacyJson()
        } catch (e: Exception) {
            fatalError = "数据库初始化失败：请先备份原文件，不会自动清除足迹"
            lastError = fatalError
        }
    }

    @Synchronized
    private fun migrateLegacyJson() {
        if (preferences.getBoolean("v1_migration_finished", false)) return
        val legacy = File(context.filesDir, "tracks.json")
        if (legacy.exists()) {
            val source = try { TrackCodec.decode(legacy.readText(Charsets.UTF_8)) }
            catch (e: Exception) {
                // Deliberately NOT fatal: the database is writable, only the old archive could not be
                // read. Blocking new recording would not recover it, it would only lose new walks too.
                // The warning stays visible because it is not a latched write failure.
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
        // Only an unusable database blocks new attempts. A previous transient failure must not,
        // or free space would never be noticed and the user would have to kill the app.
        if (fatalError != null) return false
        val wallClock = System.currentTimeMillis()
        if (!Geo.plausibleTimestamp(location.time, wallClock)) { lastFilterReason = "STALE_FIX"; return false }
        val now = TrackPoint(location.latitude, location.longitude, location.time, location.accuracy)
        // One indexed last-point query, rather than loading all history for every GPS fix.
        var currentId: String? = null
        val previous = recordingAnchor(readableDatabase)?.also {currentId=it.trip}?.point
        val gap = if (previous == null) Long.MAX_VALUE else now.time - previous.time
        val newTrip = currentId == null || previous == null || gap > 25 * 60_000L ||
            localDay(now.time) != localDay(previous.time)
        val reportedSpeed = if(location.hasSpeed() &&
            (!location.hasSpeedAccuracy() || location.speedAccuracyMetersPerSecond in 0f..3f)) location.speed.toDouble() else null
        val rejection = Geo.rejectionReason(if (newTrip) null else previous, now, confidentlyMoving,reportedSpeed)
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
            val pointId = db.insertOrThrow("points", null, ContentValues().apply {
                put("trip_id", id)
                put("lat", now.lat); put("lon", now.lon)
                put("time", now.time); put("accuracy", now.accuracy)
                put("segment", stored.segment)
            })
            if (!newTrip && previous != null) paintPairIsolated(db,pointId,previous,stored)
            saveRecordingCursor(db,pointId)
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            writeFailureLatched = true
            lastError = "足迹写入失败。请检查存储空间并尽快备份"
            return false
        } finally { db.endTransaction() }
        // This fix was dropped and honestly reported, so nothing was pretended. A later successful
        // commit proves storage recovered: clear the warning and keep recording without a restart.
        if (writeFailureLatched) { writeFailureLatched = false; lastError = null }
        cached = cached?.let { state ->
            if (newTrip) state.copy(tracks = state.tracks + Track(id, now.time, false, listOf(stored)))
            else state.copy(tracks = state.tracks.map { if (it.id == id) it.copy(points = it.points + stored) else it })
        }
        revision++
        maybeDailyBackup(now.time)
        return true
    }

    /** One bounded observation batch and live-cursor advance in the same transaction. */
    @Synchronized internal fun addPositions(batch: TrailIngress.Batch): Int {
        if (fatalError != null) return 0
        val input = batch.points
        val wall = System.currentTimeMillis()
        if (input.size !in 2..TrailIngress.MAX_POINTS || !RecordedTrail.visible(input) ||
            input.any { !Geo.plausibleTimestamp(it.time,wall) || Geo.rejectionReason(null,it)!=null } ||
            input.zipWithNext().any { (a,b) -> !RecordedTrail.connected(a,b) }) {
            lastFilterReason="INVALID_BATCH"; return 0
        }
        val db=writableDatabase
        var tripId: String?=null
        val previous=recordingAnchor(db)?.also {tripId=it.trip}?.point
        // The carried anchor may already be committed. Never duplicate it or accept overlap.
        val carriedAnchor=previous!=null && input.first().time==previous.time &&
            input.first().lat==previous.lat && input.first().lon==previous.lon
        val incoming=if(carriedAnchor) input.drop(1) else input
        if(incoming.isEmpty()) return 0
        if(previous!=null && incoming.first().time<=previous.time) {
            lastFilterReason="NON_INCREASING_TIME";return 0
        }
        var prior=previous
        var id=tripId
        val stored=ArrayList<Pair<String,TrackPoint>>()
        var savedPointId=0L
        db.beginTransaction()
        try {
            incoming.forEachIndexed { index,p ->
                val old=prior
                val newTrip=id==null || old==null || p.time-old.time>25*60_000L || localDay(p.time)!=localDay(old.time)
                if(newTrip) {
                    id=UUID.randomUUID().toString()
                    db.insertOrThrow("trips",null,ContentValues().apply { put("id",id);put("started",p.time);put("demo",0) })
                }
                val separated = index==0 && (batch.newSegment || !carriedAnchor || old==null || p.time-old.time>RecordedTrail.MAX_GAP_MS)
                val next=p.copy(segment=if(newTrip) 0 else old!!.segment + if(separated) 1 else 0)
                val pointId=db.insertOrThrow("points",null,ContentValues().apply {
                    put("trip_id",id);put("lat",next.lat);put("lon",next.lon);put("time",next.time)
                    put("accuracy",next.accuracy);put("segment",next.segment)
                })
                savedPointId=pointId
                if(!newTrip && !separated && old!=null) paintPairIsolated(db,pointId,old,next)
                stored.add(id!! to next); prior=next
            }
            saveRecordingCursor(db,savedPointId)
            db.setTransactionSuccessful()
        } catch (_:Exception) {
            writeFailureLatched=true;lastError="足迹写入失败。请检查存储空间并尽快备份";return 0
        } finally { db.endTransaction() }
        if(writeFailureLatched) { writeFailureLatched=false;lastError=null }
        cached=null;revision++
        maybeDailyBackup(stored.last().second.time)
        return stored.size
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

    private fun backupSnapshot():BackupSnapshot {
        val memos=journal.snapshot()
        return synchronized(this) {
            val state=snapshot()
            val roads=catalog().roads.associateBy {it.stableId}
            val groups=linkedMapOf<Pair<String,String>,MutableList<RoadSegment>>()
            readableDatabase.rawQuery("SELECT id,name,source FROM roads ORDER BY id",null).use {c ->
                while(c.moveToNext()) roads[c.getString(0)]?.let {road ->
                    groups.getOrPut(c.getString(1) to c.getString(2)) {mutableListOf()}.add(road)
                }
            }
            val packs=groups.flatMap {(name,items) -> items.chunked(2000).map {RoadPack(name.first,name.second,it)}}
            BackupSnapshot(state,packs,memos)
        }
    }
    fun exportBackup():String {
        return java.io.StringWriter().also {exportBackup(it)}.toString()
    }
    fun exportBackup(output:java.io.Writer) {backupSnapshot().writeTo(output)}

    /** Merge, never overwrite existing trips or a running auto-tracking session. */
    @Synchronized internal fun importBackup(text: String): RestoreResult {
        ensureWritable()
        require(text.length <= 80 * 1024 * 1024) { "备份文件过大" }
        val imported = TrackCodec.decode(text)
        val root = org.json.JSONObject(text)
        // Validate annotations before committing any imported GPS observations.
        val memos=if(root.has("journal")) JournalCodec.decode(root.getJSONObject("journal")) else null
        val packs = root.optJSONArray("roadPacks")?.let { a ->
            require(root.getInt("roadBackupVersion") == 1 && a.length() <= 5000)
            (0 until a.length()).map { RoadPackCodec.decode(a.getJSONObject(it).toString()) }
        }.orEmpty()
        require(imported.tracks.size <= 50000) { "足迹数量异常" }
        require(imported.tracks.sumOf { it.points.size.toLong() } <= 3_000_000) { "定位点数量过大" }
        val plan=BackupMerge.plan(snapshot(),imported,System.currentTimeMillis())
        val db = writableDatabase
        db.beginTransaction()
        try {
            plan.additions.forEach {insertTrip(db,it)}
            packs.forEach { putPack(db,it) }
            roadCatalog = null
            require(catalog().roads.size <= 5000) { "累计道路超过5000条" }
            rebuildCoverage(db)
            db.setTransactionSuccessful()
        } finally { db.endTransaction(); roadCatalog = null }
        cached = null
        revision++
        val journalPending=if(memos==null) false else try {journal.mergeMissing(memos);false} catch(_:Exception) {true}
        return plan.result.copy(journalPending=journalPending)
    }

    @Synchronized fun backupFiles(): List<File> =
        File(context.filesDir, "auto-backups").listFiles()?.filter { it.name.endsWith(".json") }
            ?.sortedByDescending { it.name } ?: emptyList()

    private fun localDay(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(timestamp))

    /** Enqueue only: a committed GPS batch never waits for JSON serialization or file I/O. */
    private fun maybeDailyBackup(timestamp: Long) {
        if (fatalError != null) return
        if(!backupSchedule.start(timestamp,android.os.SystemClock.elapsedRealtime(),
                preferences.getLong("last_auto_backup_time",0L))) return
        try {
            backupExecutor.execute {
                try {writeAutomaticBackup(timestamp)}
                catch(_:BackupLimitException) {rememberBackupStatus("自动备份达到容量预算，请导出备份；原快照保留")}
                catch(_:Exception) {rememberBackupStatus("自动备份未完成，原快照保留；足迹仍在本机")}
                finally {backupSchedule.finished()}
            }
        } catch(_:java.util.concurrent.RejectedExecutionException) {backupSchedule.finished()}
    }
    private class BackupLimitException:java.io.IOException()
    private fun writeAutomaticBackup(timestamp:Long) {
        backupStatus="正在更新本机自动备份"
        val directory=File(context.filesDir,"auto-backups")
        check(directory.isDirectory || directory.mkdirs())
        val file=File(directory,"footprints-${localDay(timestamp)}.json")
        val files=directory.listFiles()?.filter {it.isFile && it.name.endsWith(".json")}
            ?: error("Backup directory unavailable")
        val budget=BackupBudget.decide(files.size,files.sumOf {it.length()},file.length(),file.exists(),directory.usableSpace)
        budget.problem?.let {rememberBackupStatus(it);return}
        // All SQLite reads and immutable references are captured together; encoding is outside
        // this monitor, so the recording executor can commit while the backup streams to disk.
        val snapshot=backupSnapshot()
        val atomic=AtomicFile(file)
        val stream=atomic.startWrite()
        try {
            val bounded=object:java.io.FilterOutputStream(stream) {
                private var written=0L
                override fun write(b:Int) {if(written>=budget.maxBytes) throw BackupLimitException();out.write(b);written++}
                override fun write(b:ByteArray,offset:Int,length:Int) {
                    if(length>budget.maxBytes-written) throw BackupLimitException()
                    out.write(b,offset,length);written+=length
                }
            }
            snapshot.writeTo(java.io.OutputStreamWriter(bounded,Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch(e:Exception) {atomic.failWrite(stream);throw e}
        val completed=System.currentTimeMillis()
        preferences.edit().putLong("last_auto_backup_time",completed).apply()
        backupCountCached=files.size+if(file in files) 0 else 1
        rememberBackupStatus("自动备份已更新 · "+SimpleDateFormat("MM-dd HH:mm",Locale.ROOT).format(Date(completed)))
    }
    private fun rememberBackupStatus(text:String) {
        backupStatus=text
        preferences.edit().putString("auto_backup_status",text).apply()
    }
    override fun close() {
        backupSchedule.close()
        backupExecutor.shutdown()
        check(backupExecutor.awaitTermination(30,java.util.concurrent.TimeUnit.SECONDS)) {"Backup is still finishing"}
        super.close()
    }

    private fun ensureWritable() { check(fatalError == null) { fatalError!! } }
}

object FootprintInsights {
    fun traveledDays(state: TrackState): Int = state.dayCount
    fun exploredCells(state: TrackState): Int = state.cellCount

    fun lastVisited(state: TrackState, limit: Int = 10): List<Track> =
        state.realTracks.sortedByDescending { it.points.lastOrNull()?.time ?: it.started }.take(limit)
}
