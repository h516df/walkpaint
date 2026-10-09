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
            databases.forEach { db -> check(db.readableDatabase.rawQuery("PRAGMA integrity_check",null).use { it.moveToFirst() && it.getString(0)=="ok" }) }
            result.putString("stream","V05_ISOLATED_DATABASE_PASS: v1 rows and IDs preserved; road reimport atomic; backup roundtrip deduplicated\n")
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
