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
        val isolated=object:ContextWrapper(context) {
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
                check(RecordedTrail.paths(state.realTracks).map{it.size}==listOf(5,2))
                check(state.totalMeters in 499.0..501.0)
                check(db.roadSnapshot().matched==0)
            }
            var added=0
            FootprintDatabase(context).use { db ->
                val before=db.snapshot().realPointCount
                // Idempotent after interrupted host UI runs; never rewind GPS time or delete rows.
                val existing=db.snapshot().realTracks.flatMap { it.points }
                val present=points.all { fix -> existing.any {
                    kotlin.math.abs(it.lat-fix.latitude)<0.00000001 && kotlin.math.abs(it.lon-fix.longitude)<0.00000001
                } }
                if(!present) {
                    points.forEach { check(db.addPosition(it)) { "public_fixture_rejected" } }
                    added=7
                }
                check(db.snapshot().realPointCount==before+added)
            }
            return "DIRECT_TRAIL_NO_ROADS_DB_PASS walking_cycling_driving_gap=7 added=$added"
        } finally { temp.deleteRecursively() }
    }
}
