package cn.walkpaint

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import java.io.File
import java.io.StringWriter
import java.io.Writer
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Real files/SQLite, all namespaced. Slow-output test exercises the public export boundary. */
internal object BackupWorkerProbe {
    fun run(i:Instrumentation):String {
        val root=File(i.targetContext.cacheDir,"backup-worker-${UUID.randomUUID()}").apply {mkdirs()}
        val prefs=mutableSetOf<String>();val databases=mutableListOf<FootprintDatabase>()
        fun context(name:String)=object:ContextWrapper(i.targetContext) {
            override fun getDatabasePath(n:String)=File(root,"$name-$n")
            override fun getFilesDir()=File(root,"$name-files").apply {mkdirs()}
            override fun openOrCreateDatabase(n:String,mode:Int,f:SQLiteDatabase.CursorFactory?)=SQLiteDatabase.openOrCreateDatabase(getDatabasePath(n),f)
            override fun openOrCreateDatabase(n:String,mode:Int,f:SQLiteDatabase.CursorFactory?,h:android.database.DatabaseErrorHandler?)=SQLiteDatabase.openOrCreateDatabase(getDatabasePath(n).path,f,h)
            override fun getSharedPreferences(n:String,mode:Int):android.content.SharedPreferences {
                val key="${root.name}-$name-$n";prefs+=key;return baseContext.getSharedPreferences(key,mode)
            }
        }
        fun db(c:Context)=FootprintDatabase(c).also {databases+=it}
        val now=System.currentTimeMillis()
        fun p(n:Int)=TrackPoint(48.8566+n*.00004,2.3522,now-12_000+n*2000,8f)
        fun await(repo:FootprintDatabase) {
            val end=SystemClock.elapsedRealtime()+10_000
            while((repo.backupStatus==null || repo.backupStatus=="正在更新本机自动备份") && SystemClock.elapsedRealtime()<end) SystemClock.sleep(20)
            check(repo.backupStatus!=null && repo.backupStatus!="正在更新本机自动备份") {"Backup did not finish"}
        }
        try {
            val normal=context("normal");val repo=db(normal)
            check(repo.addPositions(TrailIngress.Batch(listOf(p(0),p(1)),true))==2)
            await(repo)
            val backup=repo.backupFiles().single()
            check(TrackCodec.decode(backup.readText()).realPointCount==2)
            val restored=db(context("roundtrip"))
            check(restored.importBackup(backup.readText()).addedPoints==2)
            check(restored.snapshot()==repo.snapshot())
            val started=CountDownLatch(1);val release=CountDownLatch(1)
            val pool=Executors.newFixedThreadPool(2)
            val captured=StringWriter()
            val output=object:Writer() {
                var first=true
                override fun write(b:CharArray,o:Int,n:Int) {
                    if(first) {first=false;started.countDown();check(release.await(10,TimeUnit.SECONDS))}
                    captured.write(b,o,n)
                }
                override fun flush()=captured.flush()
                override fun close() {}
            }
            try {
                val exporting=pool.submit {repo.exportBackup(output)}
                check(started.await(5,TimeUnit.SECONDS))
                val writing=pool.submit<Int> {repo.addPositions(TrailIngress.Batch(listOf(p(1),p(2)),false))}
                check(writing.get(2,TimeUnit.SECONDS)==1) {"File encoding held the GPS repository lock"}
                release.countDown();exporting.get(5,TimeUnit.SECONDS)
                check(TrackCodec.decode(captured.toString()).realPointCount==2) {"Export mixed revisions"}
                check(repo.snapshot().realPointCount==3)
            } finally {release.countDown();pool.shutdown();check(pool.awaitTermination(15,TimeUnit.SECONDS))}
            val blocked=context("blocked")
            File(blocked.filesDir,"auto-backups").writeText("owned file blocking this test directory")
            val failed=db(blocked)
            check(failed.addPositions(TrailIngress.Batch(listOf(p(0),p(1)),true))==2);await(failed)
            check(failed.backupStatus!!.contains("未完成"))
            check(failed.addPositions(TrailIngress.Batch(listOf(p(1),p(2)),false))==1)
            check(failed.lastError==null && failed.snapshot().realPointCount==3)
            val budgetContext=context("budget")
            val directory=File(budgetContext.filesDir,"auto-backups").apply {mkdirs()}
            val originals=(0..30).associate {n -> File(directory,"owned-$n.json") to "sentinel-$n"}
            originals.forEach {(file,value) ->file.writeText(value)}
            val limited=db(budgetContext)
            check(limited.addPositions(TrailIngress.Batch(listOf(p(0),p(1)),true))==2);await(limited)
            check(limited.backupStatus!!.contains("预算"))
            check(limited.addPositions(TrailIngress.Batch(listOf(p(1),p(2)),false))==1)
            check(directory.listFiles()!!.size==31 && originals.all {(file,value)->file.readText()==value})
            return "BACKUP_WORKER_PASS streamed_roundtrip=true slow_output_does_not_block_live_commit=true consistent_snapshot=true failure_preserves_recording=true budget_preserves_all_old_files=true"
        } finally {
            databases.forEach {it.close()}
            prefs.forEach {i.targetContext.deleteSharedPreferences(it)}
            root.deleteRecursively()
        }
    }
}
