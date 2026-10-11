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
    private var networkProbe=false
    private var catalogProbe=false
    private var fixtureProbe=false
    private var cameraProbe=false
    private var roadColorProbe=false
    private var privateUpdateProbe=false
    private var directTrailProbe=false
    private var fogTrailProbe=false
    private var fogTrailReset=false
    private var updateLogoutProbe=false
    private var candidateSha:String?=null
    override fun onStart() {
        val result = Bundle()
        if(fogTrailProbe) {
            try { result.putString("stream",FogTrailProbe.run(this,fogTrailReset));finish(Activity.RESULT_OK,result) }
            catch(e:Throwable) { result.putString("stream","FOG_TRAIL_PROBE_FAIL: ${e.javaClass.simpleName}: ${e.message}");finish(Activity.RESULT_CANCELED,result) }
            return
        }
        if(directTrailProbe) {
            try { result.putString("stream",DirectTrailProbe.run(this));finish(Activity.RESULT_OK,result) }
            catch(e:Throwable) { result.putString("stream","DIRECT_TRAIL_DB_FAIL: ${e.javaClass.simpleName}: ${e.message}");finish(Activity.RESULT_CANCELED,result) }
            return
        }
        if(updateLogoutProbe) {
            try {
                check(targetContext.getSharedPreferences("private_update_authorization",Context.MODE_PRIVATE).all.isEmpty())
                check(!java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias("walkpaint-update-v1"))
                check(File(targetContext.cacheDir,"updates").listFiles().isNullOrEmpty())
                result.putString("stream","PRIVATE_UPDATE_REAL_LOGOUT_PASS: preferences empty, device key removed, update cache empty; no credentials exported")
                finish(Activity.RESULT_OK,result)
            } catch(_:Throwable) {
                result.putString("stream","PRIVATE_UPDATE_REAL_LOGOUT_FAIL")
                finish(Activity.RESULT_CANCELED,result)
            }
            return
        }
        candidateSha?.let { sha ->
            try { result.putString("stream",PrivateUpdateProbe.candidate(this,sha));finish(Activity.RESULT_OK,result) }
            catch(e:Throwable) { result.putString("stream","PRIVATE_UPDATE_NEW_APK_FAIL: ${e.javaClass.simpleName}");finish(Activity.RESULT_CANCELED,result) }
            return
        }
        if(privateUpdateProbe) {
            try { result.putString("stream",PrivateUpdateProbe.run(this));finish(Activity.RESULT_OK,result) }
            catch(e:Throwable) { result.putString("stream","PRIVATE_UPDATE_ANDROID_BOUNDARIES_FAIL: ${e.javaClass.simpleName}: ${e.message}");finish(Activity.RESULT_CANCELED,result) }
            return
        }
        if(roadColorProbe) {
            try { result.putString("stream",RoadColorProbe(this).run());finish(Activity.RESULT_OK,result) }
            catch(e:Throwable) { result.putString("stream","ROAD_COLOR_SDK_FAIL: ${e.javaClass.simpleName}: ${e.message}");finish(Activity.RESULT_CANCELED,result) }
            return
        }
        if(cameraProbe) {
            try { CameraFollowProbe(this).run();result.putString("stream","PUBLIC_CAMERA_BEARING_ZOOM_SENSOR_LIFETIME_PASS");finish(Activity.RESULT_OK,result) }
            catch(e:Throwable) { result.putString("stream","PUBLIC_CAMERA_PROBE_FAIL: ${e.javaClass.simpleName}: ${e.message}");finish(Activity.RESULT_CANCELED,result) }
            return
        }
        if(fixtureProbe) {
            // Only fixed public fixture sections; no real-GPS-derived lookup or database writes.
            try {
                FootprintDatabase(targetContext).use { repo ->
                    val state=repo.roadSnapshot()
                    val catalog=RoadCatalog(state.roads)
                    val fixture=context.assets.open("oxford-roads.geojson").bufferedReader().use { RoadPackCodec.decode(it.readText()) }
                    for ((index,road) in fixture.roads.withIndex()) {
                        for (start in 10..(road.length.toInt()-40) step 5) {
                            if(state.intervals.any { it.roadId==road.stableId && it.to>start && it.from<start+30 }) continue
                            val points=listOf(0.0,10.0,20.0,30.0).mapIndexed { i,d ->
                                RoadCoverage(listOf(PaintedInterval(road.stableId,0.0,start+d))).highlighted(road).single().last().copy(time=1000L+i*10000,accuracy=5f)
                            }
                            if(points.zipWithNext().all { (a,b) -> catalog.traversed(a,b)?.roadId==road.stableId }) {
                                result.putString("stream","PUBLIC_UNPAINTED_FIXTURE_PASS fixture=$index start=$start")
                                finish(Activity.RESULT_OK,result)
                                return
                            }
                        }
                    }
                    error("No unpainted unambiguous public fixture remains")
                }
            } catch(e:Exception) { result.putString("stream","PUBLIC_FIXTURE_PROBE_FAIL_${e.javaClass.simpleName}");finish(Activity.RESULT_CANCELED,result) }
            return
        }
        if(catalogProbe) {
            try {
                val repo=FootprintDatabase(targetContext)
                val roads=repo.roadSnapshot().roads
                val catalog=RoadCatalog(roads)
                val fixture=context.assets.open("oxford-roads.geojson").bufferedReader().use { RoadPackCodec.decode(it.readText()) }
                val road=fixture.roads.first { it.stableId.startsWith("osm:way:1675518:") }
                val points=listOf(18.0,32.0,46.0,60.0,94.0).mapIndexed { i,d ->
                    RoadCoverage(listOf(PaintedInterval(road.stableId,0.0,d))).highlighted(road).single().last().copy(time=1000L+i*10000,accuracy=5f)
                }
                val near=points.map { catalog.near(it).size }
                val match=points.map { RoadMatcher.match(it,catalog.near(it))!=null }
                val pairs=points.zipWithNext().map { (a,b) -> catalog.traversed(a,b)!=null }
                repo.close()
                result.putString("stream","CATALOG_READ_ONLY: roads=${roads.size}; near=$near; match=$match; pairs=$pairs")
                finish(Activity.RESULT_OK,result)
            } catch(e:Exception) { result.putString("stream","CATALOG_READ_ONLY_FAILURE_${e.javaClass.simpleName}");finish(Activity.RESULT_CANCELED,result) }
            return
        }
        if(networkProbe) {
            val lines=mutableListOf<String>()
            try {
                val pack=RoadDownload().fetch(NearbyRoads.area(51.754,-1.255))
                lines += "production_fetch=PASS:${RoadPackCodec.decode(pack).roads.size}"
            } catch(e:Exception) { lines += "production_fetch="+e.javaClass.simpleName+":"+(e.message?.take(200) ?: "") }
            for(host in listOf("https://overpass-api.de/api/status","https://overpass.private.coffee/api/status","https://api.openstreetmap.org/api/0.6/capabilities.json")) {
                val c=java.net.URI(host).toURL().openConnection() as java.net.HttpURLConnection
                try {
                    c.connectTimeout=10000; c.readTimeout=10000; c.instanceFollowRedirects=false
                    lines += host+" HTTP "+c.responseCode
                } catch(e:Exception) { lines += host+" "+e.javaClass.simpleName+":"+(e.message?.take(200) ?: "") }
                finally { c.disconnect() }
            }
            result.putString("stream",lines.joinToString("\n"))
            finish(Activity.RESULT_OK,result)
            return
        }
        val root=File(targetContext.cacheDir,"road-test-${UUID.randomUUID()}").apply { mkdirs() }
        val databases=mutableListOf<FootprintDatabase>()
        val prefs=mutableSetOf<String>()
        var resultCode=Activity.RESULT_CANCELED
        try {
            for (turn in listOf(0f,90f,180f,359f)) {
                val radians=Math.toRadians(turn.toDouble())/2
                val vector=floatArrayOf(0f,0f,-kotlin.math.sin(radians).toFloat(),kotlin.math.cos(radians).toFloat())
                for(rotation in 0..3) {
                    val actual=ScreenHeading.angle(vector,rotation)!!
                    val expected=HeadingFilter.normalize(turn+rotation*90f)
                    check(kotlin.math.abs(HeadingFilter.delta(expected,actual))<.02f) { "SCREEN_HEADING: public=$turn rotation=$rotation expected=$expected actual=$actual" }
                }
            }
            // Real Android main Handler: a transient provider exception must leave a next tick.
            val latch=java.util.concurrent.CountDownLatch(1)
            val attempts=java.util.concurrent.atomic.AtomicInteger()
            val failures=java.util.concurrent.atomic.AtomicInteger()
            val handler=android.os.Handler(android.os.Looper.getMainLooper())
            val loop=HealthCheckLoop({attempts.get()<2}, {
                if(attempts.incrementAndGet()==1) throw SecurityException("isolated provider probe")
                latch.countDown()
            }, {failures.incrementAndGet()}, {handler.postDelayed(it,20)})
            handler.post(loop)
            check(latch.await(5,java.util.concurrent.TimeUnit.SECONDS))
            check(attempts.get()==2 && failures.get()==1)
            handler.removeCallbacks(loop)
            // No preferences or live service fault injection; isolated stage evidence.
            val stages=DiagnosticBuffer(mapOf("gps_count" to 136L,"saved_count" to 43L))
            stages.record("start","ISOLATED_SESSION",1000)
            stages.satellites(10,0,2000)
            check(stages.long("session_gps_count")==0L && stages.long("session_saved_count")==0L)
            stages.record("gps","GPS_CALLBACK",3000)
            stages.record("valid_gps","VALID_GPS_RECEIVED",3001)
            stages.record("saved","SQLITE_SAVED",3002)
            check(stages.long("session_gps_count")==1L && stages.long("session_valid_gps_count")==1L && stages.long("session_saved_count")==1L)
            stages.record("start","ISOLATED_RESTART",4000)
            check(stages.long("session_gps_count")==0L && stages.long("gnss_at")==0L)
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
            val rawBefore=repo.snapshot()
            val day=DailyFootprints.key(1000)
            val dayRoad=repo.roadSnapshotForDay(day)
            check(kotlin.math.abs(dayRoad.meters-before.meters)<0.01)
            check(repo.snapshot()==rawBefore && repo.roadSnapshot().meters==before.meters)
            val emptyDay=repo.roadSnapshotForDay(DailyFootprints.key(System.currentTimeMillis()))
            check(emptyDay.meters==0.0)
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
            // A raw-point failure must stay honest: no false success, the fix is not stored, and the
            // error is visible. It must NOT be process-permanent, or a transient fault (disk full,
            // one-off SQLite error) would end recording until the user kills and restarts the app.
            logging.writableDatabase.execSQL("CREATE TRIGGER gps_failure BEFORE INSERT ON points BEGIN SELECT RAISE(ABORT,'gps-write-test'); END")
            val finalCoverage=logging.roadSnapshot().intervals
            check(!logging.addPosition(fix(46.0,6)) && logging.lastError!=null)
            check(logging.snapshot().realPointCount==6 && logging.roadSnapshot().intervals==finalCoverage)
            // Attempts keep failing honestly for as long as the fault persists.
            check(!logging.addPosition(fix(46.0,7))) { "a failing write reported success" }
            check(logging.lastError!=null && logging.snapshot().realPointCount==6)
            // Once the fault clears, recording resumes and the stale error clears with it.
            logging.writableDatabase.execSQL("DROP TRIGGER gps_failure")
            check(logging.addPosition(fix(32.0,8))) { "recording did not resume after the write fault cleared" }
            check(logging.lastError==null) { "stale write error left the UI reporting a dead recorder" }
            check(logging.snapshot().realPointCount==7)
            result.putString("road_regression","accepted GPS survives catalog and partial coverage failures; valid reimport recovers; raw GPS write failure stays honest and recovers without a restart")
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
    override fun onCreate(arguments:Bundle?) { fogTrailReset=arguments?.getString("fogTrailReset")=="true";fogTrailProbe=arguments?.getString("fogTrailProbe")=="true";directTrailProbe=arguments?.getString("directTrailProbe")=="true"; updateLogoutProbe=arguments?.getString("updateLogoutProbe")=="true";candidateSha=arguments?.getString("candidateSha");privateUpdateProbe=arguments?.getString("privateUpdateProbe")=="true";roadColorProbe=arguments?.getString("roadColorProbe")=="true"; cameraProbe=arguments?.getString("cameraProbe")=="true"; fixtureProbe=arguments?.getString("fixtureProbe")=="true"; catalogProbe=arguments?.getString("catalogProbe")=="true"; networkProbe=arguments?.getString("networkProbe")=="true"; super.onCreate(arguments); start() }
}
