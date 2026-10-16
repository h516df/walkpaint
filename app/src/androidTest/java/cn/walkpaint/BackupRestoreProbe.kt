package cn.walkpaint

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import java.io.File
import java.util.UUID

/** Uses its own database, files and preferences. No installed history is used as a fixture. */
internal object BackupRestoreProbe {
    fun run(i:Instrumentation):String {
        val root=File(i.targetContext.cacheDir,"restore-test-${UUID.randomUUID()}").apply {mkdirs()}
        val preferences=mutableSetOf<String>()
        val databases=mutableListOf<FootprintDatabase>()
        fun context(name:String)=object:ContextWrapper(i.targetContext) {
            override fun getDatabasePath(n:String)=File(root,"$name-$n")
            override fun getFilesDir()=File(root,"$name-files").apply {mkdirs()}
            override fun openOrCreateDatabase(n:String,mode:Int,f:android.database.sqlite.SQLiteDatabase.CursorFactory?) =
                android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(getDatabasePath(n),f)
            override fun openOrCreateDatabase(n:String,mode:Int,f:android.database.sqlite.SQLiteDatabase.CursorFactory?,h:android.database.DatabaseErrorHandler?) =
                android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(getDatabasePath(n).path,f,h)
            override fun getSharedPreferences(n:String,mode:Int):android.content.SharedPreferences {
                val key="${root.name}-$name-$n";preferences+=key
                return baseContext.getSharedPreferences(key,mode)
            }
        }
        fun db(name:String)=FootprintDatabase(context(name)).also {databases+=it}
        fun point(n:Int,at:Long)=TrackPoint(48.8566+n*.00004,2.3522,at,8f)
        fun backup(points:List<TrackPoint>,id:String="same-trip")=TrackCodec.encode(
            TrackState(listOf(Track(id,points.first().time,false,points))))
        val failures=mutableListOf<String>()
        fun scenario(name:String,body:()->Unit) {try {body()} catch(e:Throwable) {failures+="$name: ${e.message}"}}
        try {
            val past=System.currentTimeMillis()-86_400_000
            val points=(0..3).map {point(it,past+it*2000)}
            scenario("old_then_new") {
                val repo=db("merge")
                repo.importBackup(backup(points.take(2)))
                repo.importBackup(backup(points))
                check(repo.snapshot().realPointCount==4) {"later backup points were skipped"}
                repo.importBackup(backup(points));repo.importBackup(backup(points.take(2)))
                check(repo.snapshot().realPointCount==4) {"restore is not idempotent"}
            }
            scenario("restore_during_recording_and_reopen") {
                val c=context("live");var repo=FootprintDatabase(c).also {databases+=it}
                val now=System.currentTimeMillis()
                val a=point(0,now-12_000);val b=point(1,now-10_000)
                check(repo.addPositions(TrailIngress.Batch(listOf(a,b),true))==2)
                // A valid historical import is newer than a batch already queued by the service.
                repo.importBackup(backup(listOf(point(100,now-2000)),"restored-history"))
                val next=point(2,now-8000)
                check(repo.addPositions(TrailIngress.Batch(listOf(b,next),false))==1) {"import changed the live recording anchor"}
                repo.close();repo=FootprintDatabase(c).also {databases+=it}
                check(repo.addPositions(TrailIngress.Batch(listOf(next,point(3,now-6000)),false))==1) {"recording anchor did not survive reopen"}
                check(repo.snapshot().realTracks.size==2)
            }
            scenario("future_time_rejected_atomically") {
                val repo=db("future")
                repo.importBackup(backup(points.take(2)))
                val before=repo.snapshot()
                val future=point(20,System.currentTimeMillis()+86_400_000)
                var rejected=false
                try {repo.importBackup(backup(listOf(future),"future"))} catch(_:IllegalArgumentException) {rejected=true}
                check(rejected && repo.snapshot()==before) {"future import accepted or original data changed"}
            }
            check(failures.isEmpty()) {failures.joinToString("; ")}
            return "BACKUP_RESTORE_PASS old_new_idempotent=true recording_during_restore_and_reopen=true future_atomic_rejection=true isolated=true"
        } finally {
            databases.forEach {runCatching {it.close()}}
            preferences.forEach {i.targetContext.deleteSharedPreferences(it)}
            root.deleteRecursively()
        }
    }
}
