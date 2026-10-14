package cn.walkpaint

import android.app.Instrumentation
import android.location.Location
import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Guarded by the host's dedicated Google AVD script. Fixed public Paris fixture only. */
internal object DirectTrailProbe {
    fun run(instrumentation:Instrumentation):String {
        val context=instrumentation.targetContext
        val now=System.currentTimeMillis()
        fun fix(metres:Double,age:Long,speed:Float)=Location("gps").apply {
            latitude=48.8566+metres/111195.0;longitude=2.3522
            time=now-age;accuracy=5f;this.speed=speed;speedAccuracyMetersPerSecond=0.5f
        }
        val points=listOf(fix(0.0,110000,0f),fix(15.0,100000,1.5f),fix(85.0,90000,7f),
            fix(235.0,85000,30f),fix(400.0,80000,33f),fix(1400.0,10000,15f),fix(1500.0,0,10f))
        val temp=File(context.cacheDir,"direct-trail-db").apply { mkdirs() }
        val namespace="direct-trail-${java.util.UUID.randomUUID()}"
        val preferences=mutableSetOf<String>()
        val isolated=object:ContextWrapper(context) {
            override fun getFilesDir()=File(temp,"files").apply { mkdirs() }
            override fun getSharedPreferences(name:String,mode:Int):android.content.SharedPreferences {
                val key="$namespace-$name";preferences.add(key)
                return context.getSharedPreferences(key,mode)
            }
            override fun getDatabasePath(name:String)=File(temp,name)
            override fun openOrCreateDatabase(name:String,mode:Int,f:SQLiteDatabase.CursorFactory?) =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),f)
            override fun openOrCreateDatabase(name:String,mode:Int,f:SQLiteDatabase.CursorFactory?,h:android.database.DatabaseErrorHandler?) =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path,f,h)
        }
        try {
            FootprintDatabase(isolated).use { db ->
                check(db.roadSnapshot().roads.isEmpty())
                points.forEach { check(db.addPosition(it)) { "isolated_position_rejected" } }
                val state=db.snapshot()
                check(state.realPointCount==7)
                check(state.realTracks.flatMap { it.points }.groupBy { it.segment }.values.map { it.size }==listOf(5,2)) { "raw_gap_segments" }
                check(RecordedTrail.paths(state.realTracks).map{it.size}==listOf(2,2)) { "simplified_gap_segments" }
                check(state.totalMeters in 499.0..501.0)
                check(db.roadSnapshot().matched==0)
                // Atomic batches run only in this isolated SQLite database.
                fun observation(m:Double,age:Long)=TrackPoint(48.8566+m/111195.0,2.3522,now-age,5f)
                val batch=TrailIngress.Batch(listOf(observation(1600.0,-1000),observation(1610.0,-11000),observation(1620.0,-21000)),true)
                check(db.addPositions(batch)==3)
                check(db.snapshot().realPointCount==10)
                check(RecordedTrail.paths(db.snapshot().realTracks).map { it.size }==listOf(2,2,2)) { "batch_segment_is_separate" }
                // Abort the second row, prove neither the first row nor a trip survives.
                db.writableDatabase.execSQL("CREATE TEMP TRIGGER reject_batch BEFORE INSERT ON points WHEN NEW.lat>${48.8566+1645/111195.0} BEGIN SELECT RAISE(ABORT,'test_atomic'); END")
                val failed=TrailIngress.Batch(listOf(observation(1640.0,-31000),observation(1650.0,-41000)),true)
                check(db.addPositions(failed)==0)
                check(db.snapshot().realPointCount==10)
                db.writableDatabase.execSQL("DROP TRIGGER reject_batch")
                check(db.addPositions(failed)==2)
                check(db.lastError==null && db.snapshot().realPointCount==12)
            }
            return "DIRECT_TRAIL_NO_ROADS_DB_PASS walking_cycling_driving_gap=7 atomic_batch_rollback_recovery=PASS global_db_and_backups=untouched"
        } finally {
            preferences.forEach { key -> context.getSharedPreferences(key,Context.MODE_PRIVATE).edit().clear().commit();context.deleteSharedPreferences(key) }
            temp.deleteRecursively()
        }
    }
}
