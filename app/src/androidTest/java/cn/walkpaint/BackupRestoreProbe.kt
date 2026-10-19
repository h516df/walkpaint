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
            scenario("journal_roundtrip_and_local_edits") {
                val source=db("journal-source");source.importBackup(backup(points))
                val day=DailyFootprints.key(past)
                val memo=DayMemo("合成散步","只用于验收",listOf("周末"),true)
                source.journal.update(day){memo}
                val text=source.exportBackup()
                check(TrackCodec.decode(text).realPointCount==4) {"legacy GPS decoding changed"}
                val target=db("journal-target")
                check(!target.importBackup(text).journalPending)
                check(target.journal.snapshot()[day]==memo)
                target.journal.update(day){it.copy(note="本机编辑")}
                target.importBackup(text)
                check(target.journal.snapshot()[day]?.note=="本机编辑")
                val before=target.snapshot()
                val malformed=org.json.JSONObject(text).put("journal",org.json.JSONObject().put("version",99))
                check(runCatching {target.importBackup(malformed.toString())}.isFailure)
                check(target.snapshot()==before)
            }
            scenario("journal_failure_is_reported_without_discarding_tracks") {
                val source=db("partial-source");source.importBackup(backup(points))
                source.journal.update(DailyFootprints.key(past)){DayMemo("合成标题")}
                val c=context("partial-target");val target=FootprintDatabase(c).also {databases+=it}
                val journal=File(c.filesDir,"footprint-journal.json").apply {writeText("corrupt-original")}
                val result=target.importBackup(source.exportBackup())
                check(result.journalPending && result.message().contains("行记尚未恢复"))
                check(target.snapshot().realPointCount==4 && journal.readText()=="corrupt-original")
            }
            scenario("combined_metadata_roundtrip_local_clears_and_deletions") {
                val day=DailyFootprints.key(past)
                val source=db("combined-source");source.importBackup(backup(points))
                source.journal.update(day){DayMemo("旧备注",starred=true)}
                source.landmarks.save(ExplorationLandmark("fixture-poi","合成公园",48.8566,2.3522))
                val text=source.exportBackup();val json=org.json.JSONObject(text)
                check(json.has("journal") && json.has("landmarks"))
                check(json.getJSONObject("landmarks").getString("coordinateSystem")=="WGS84")
                val target=db("combined-target");target.importBackup(text)
                check(target.journal.snapshot()[day]?.title=="旧备注")
                check(target.landmarks.snapshot()["fixture-poi"]?.name=="合成公园")
                target.journal.update(day){DayMemo()};target.landmarks.remove("fixture-poi")
                repeat(2){target.importBackup(text)}
                check(target.journal.snapshot()[day]==DayMemo())
                check(target.landmarks.snapshot()["fixture-poi"]?.deleted==true)
                check(target.snapshot().realPointCount==4)
                val before=target.snapshot()
                val malformed=org.json.JSONObject(text).put("landmarks",org.json.JSONObject().put("version",99))
                check(runCatching {target.importBackup(malformed.toString())}.isFailure && target.snapshot()==before)
            }
            scenario("metadata_failures_are_independent_and_originals_retained") {
                val source=db("both-source");source.importBackup(backup(points))
                val day=DailyFootprints.key(past)
                source.journal.update(day){DayMemo("合成标题")}
                source.landmarks.save(ExplorationLandmark("fixture-poi","合成公园",48.8566,2.3522))
                val text=source.exportBackup()
                for((badJournal,badLandmarks) in listOf(true to false,false to true,true to true)) {
                    val c=context("metadata-$badJournal-$badLandmarks")
                    val target=FootprintDatabase(c).also {databases+=it}
                    val j=File(c.filesDir,"footprint-journal.json")
                    val l=File(c.filesDir,"exploration-landmarks.json")
                    if(badJournal)j.writeText("journal-original")
                    if(badLandmarks)l.writeText("landmark-original")
                    val result=target.importBackup(text)
                    check(result.journalPending==badJournal && result.landmarksPending==badLandmarks)
                    check(result.message().contains("行记尚未恢复")==badJournal)
                    check(result.message().contains("探索点恢复未完成")==badLandmarks)
                    check(target.snapshot().realPointCount==4)
                    if(badJournal) check(j.readText()=="journal-original") else check(target.journal.snapshot()[day]?.title=="合成标题")
                    if(badLandmarks) check(l.readText()=="landmark-original") else check(target.landmarks.snapshot()["fixture-poi"]?.name=="合成公园")
                }
            }
            check(failures.isEmpty()) {failures.joinToString("; ")}
            return "BACKUP_RESTORE_PASS old_new_idempotent=true recording_during_restore_and_reopen=true future_atomic_rejection=true journal_roundtrip_local_edits_partial_failure=true combined_metadata_clears_deletions_independent_failures=true isolated=true"
        } finally {
            databases.forEach {runCatching {it.close()}}
            preferences.forEach {i.targetContext.deleteSharedPreferences(it)}
            root.deleteRecursively()
        }
    }
}
