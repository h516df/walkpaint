package cn.walkpaint

import android.app.Instrumentation
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import java.io.File
import java.util.UUID

/** Isolated databases only. Never clears or replaces the installed footprint database. */
class RoadAcceptanceInstrumentation : Instrumentation() {
    override fun onStart() {
        val result = Bundle()
        val root=File(targetContext.cacheDir,"road-test-${UUID.randomUUID()}").apply { mkdirs() }
        val databases=mutableListOf<FootprintDatabase>()
        val prefs=mutableSetOf<String>()
        var resultCode=Activity.RESULT_CANCELED
        try {
            fun testContext(name:String): Context = object: ContextWrapper(targetContext) {
                override fun getDatabasePath(n:String)=File(root,"$name-$n")
                override fun getFilesDir()=File(root,"$name-files").apply { mkdirs() }
                override fun getSharedPreferences(n:String,mode:Int): android.content.SharedPreferences {
                    val key="${root.name}-$name-$n"; prefs+=key
                    return super.getSharedPreferences(key,mode)
                }
                override fun openOrCreateDatabase(n:String,mode:Int,f:SQLiteDatabase.CursorFactory?) =
                    SQLiteDatabase.openOrCreateDatabase(getDatabasePath(n),f)
                override fun openOrCreateDatabase(n:String,mode:Int,f:SQLiteDatabase.CursorFactory?,h:android.database.DatabaseErrorHandler?) =
                    SQLiteDatabase.openOrCreateDatabase(getDatabasePath(n).path,f,h)
            }
            val ctx=testContext("migration")
            // A schema-v1 database with fixed, clearly isolated test rows.
            SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath("footprints.db"),null).use { old ->
                old.execSQL("CREATE TABLE trips(id TEXT PRIMARY KEY,started INTEGER NOT NULL,demo INTEGER NOT NULL DEFAULT 0)")
                old.execSQL("CREATE TABLE points(id INTEGER PRIMARY KEY AUTOINCREMENT,trip_id TEXT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,lat REAL NOT NULL,lon REAL NOT NULL,time INTEGER NOT NULL,accuracy REAL NOT NULL,segment INTEGER NOT NULL DEFAULT 0)")
                old.execSQL("INSERT INTO trips VALUES('migration-test',1000,0)")
                old.execSQL("INSERT INTO points VALUES(17,'migration-test',51.7534867,-1.2554942,1000,5,0)")
                old.execSQL("INSERT INTO points VALUES(18,'migration-test',51.753529,-1.2550655,21000,5,0)")
                old.version=1
            }
            val repo=FootprintDatabase(ctx).also { databases+=it }
            check(repo.lastError==null)
            check(repo.readableDatabase.version==2)
            check(repo.snapshot().realPointCount==2)
            check(repo.readableDatabase.rawQuery("SELECT id FROM points ORDER BY id",null).use {
                it.moveToFirst() && it.getInt(0)==17 && it.moveToNext() && it.getInt(0)==18 })
            val fixture=context.assets.open("oxford-roads.geojson").bufferedReader().use { it.readText() }
            check(repo.importRoadPack(fixture)==6)
            val before=repo.roadSnapshot()
            check(before.meters>20 && before.matched==1)
            repo.importRoadPack(fixture)
            check(kotlin.math.abs(repo.roadSnapshot().meters-before.meters)<0.01)
            // Malformed import is atomic; valid history/coverage remain intact.
            try { repo.importRoadPack(fixture.replace("ODbL-1.0","invalid")); error("accepted bad pack") }
            catch(_:IllegalArgumentException) { }
            check(repo.snapshot().realPointCount==2 && repo.roadSnapshot().meters==before.meters)
            val backup=repo.exportBackup()
            val restored=FootprintDatabase(testContext("restore")).also { databases+=it }
            check(restored.importBackup(backup)==1)
            check(restored.snapshot()==repo.snapshot())
            check(restored.roadSnapshot().roads.map { it.stableId }==before.roads.map { it.stableId })
            check(kotlin.math.abs(restored.roadSnapshot().meters-before.meters)<0.01)
            check(restored.importBackup(backup)==0)
            check(restored.snapshot().realPointCount==2)
            val duplicated=org.json.JSONObject(backup)
            val trips=duplicated.getJSONArray("tracks")
            trips.put(trips.getJSONObject(0))
            val duplicateRestore=FootprintDatabase(testContext("duplicate-restore")).also { databases+=it }
            check(duplicateRestore.importBackup(duplicated.toString())==1)
            check(duplicateRestore.snapshot().realPointCount==2)
            // Regression: corrupt ONLY a dedicated test road database, never installed history.
            val roadContext=testContext("road-reliability")
            var logging=FootprintDatabase(roadContext).also { databases+=it }
            logging.importRoadPack(fixture)
            val road=RoadPackCodec.decode(fixture).roads.first { it.stableId.startsWith("osm:way:1675518:") }
            val fixBase=System.currentTimeMillis()-90_000L
            fun fix(offset:Double,step:Int): android.location.Location {
                val p=RoadCoverage(listOf(PaintedInterval(road.stableId,0.0,offset))).highlighted(road).single().last()
                return android.location.Location("gps").apply {
                    latitude=p.lat; longitude=p.lon; accuracy=5f; time=fixBase+step*10_000L
                }
            }
            check(logging.addPosition(fix(18.0,0)))
            check(logging.addPosition(fix(32.0,1)))
            val goodIntervals=logging.roadSnapshot().intervals
            val goodMatches=logging.roadSnapshot().matched
            check(goodIntervals.isNotEmpty())
            logging.writableDatabase.execSQL("UPDATE roads SET geometry='malformed-json' WHERE id=?",arrayOf(road.stableId))
            logging.close()
            logging=FootprintDatabase(roadContext).also { databases+=it } // Evict the cached catalog.
            check(logging.addPosition(fix(46.0,2))) { "road catalog failure discarded accepted GPS" }
            check(logging.lastError==null && logging.snapshot().realPointCount==3)
            check(logging.addPosition(fix(60.0,3))) { "road failure permanently stopped GPS logging" }
            check(logging.lastError==null && logging.snapshot().realPointCount==4)
            check(logging.roadSnapshot().error!=null) { "road error was hidden from UI or snapshot failed" }
            check(logging.readableDatabase.rawQuery("SELECT COUNT(*) FROM road_matches",null).use { it.moveToFirst() && it.getInt(0)==goodMatches })
            check(logging.readableDatabase.rawQuery("SELECT start,end FROM coverage",null).use { c ->
                c.moveToFirst() && c.getDouble(0)==goodIntervals.single().from && c.getDouble(1)==goodIntervals.single().to && !c.moveToNext()
            })
            logging.importRoadPack(fixture)
            val recovered=logging.roadSnapshot()
            check(recovered.error==null && logging.lastRoadError==null)
            check(recovered.meters>40 && recovered.matched==3) { "valid reimport did not recover missed coverage" }
            // Fail AFTER the match row is inserted and old coverage has been deleted.
            logging.writableDatabase.execSQL("CREATE TRIGGER road_failure BEFORE INSERT ON coverage BEGIN SELECT RAISE(ABORT,'road-only-test'); END")
            check(logging.addPosition(fix(74.0,4))) { "road coverage write failure discarded GPS" }
            check(logging.lastError==null && logging.snapshot().realPointCount==5)
            check(logging.roadSnapshot().intervals==recovered.intervals && logging.roadSnapshot().matched==recovered.matched) { "partial road writes escaped rollback" }
            check(logging.addPosition(fix(60.0,5))) { "further GPS fix did not persist after road write failure" }
            check(logging.snapshot().realPointCount==6 && logging.roadSnapshot().intervals==recovered.intervals)
            logging.writableDatabase.execSQL("DROP TRIGGER road_failure")
            logging.importRoadPack(fixture)
            check(logging.roadSnapshot().meters>55 && logging.roadSnapshot().matched==5)
            check(logging.lastRoadError==null)
            // Real raw-point failure MUST remain fatal and must not pretend to record.
            logging.writableDatabase.execSQL("CREATE TRIGGER gps_failure BEFORE INSERT ON points BEGIN SELECT RAISE(ABORT,'gps-write-test'); END")
            val finalCoverage=logging.roadSnapshot().intervals
            check(!logging.addPosition(fix(46.0,6)) && logging.lastError!=null)
            check(logging.snapshot().realPointCount==6 && logging.roadSnapshot().intervals==finalCoverage)
            result.putString("road_regression","accepted GPS survives catalog and partial coverage failures; valid reimport recovers; raw GPS write failure remains fatal")
            databases.forEach { db -> check(db.readableDatabase.rawQuery("PRAGMA integrity_check",null).use { it.moveToFirst() && it.getString(0)=="ok" }) }
            result.putString("stream","V05_ISOLATED_DATABASE_PASS: v1 rows and IDs preserved; road reimport atomic; backup roundtrip deduplicated; ${result.getString("road_regression")}\n")
            resultCode=Activity.RESULT_OK
        } catch(e:Throwable) {
            result.putString("stream","V05_ISOLATED_DATABASE_FAIL: ${e.stackTraceToString()}\n")
        } finally {
            databases.forEach { runCatching { it.close() } }
            prefs.forEach {
                // Flush earlier apply() work before deleting the isolated preference.
                targetContext.getSharedPreferences(it,Context.MODE_PRIVATE).edit().clear().commit()
                targetContext.deleteSharedPreferences(it)
            }
            // Only this instrumentation's UUID-named cache files, never user history.
            if(!root.deleteRecursively()) {
                resultCode=Activity.RESULT_CANCELED
                result.putString("stream","V05_ISOLATED_DATABASE_FAIL: isolated cache cleanup failed\n")
            }
        }
        // finish() can terminate the instrumented process immediately; cleanup must precede it.
        finish(resultCode,result)
    }
    override fun onCreate(arguments:Bundle?) { super.onCreate(arguments); start() }
}
