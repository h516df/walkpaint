package cn.walkpaint

import android.app.Instrumentation
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import org.json.JSONObject

/** Read-only UI acceptance facts. No coordinates, accounts or preference mutations. */
internal object UiStateProbe {
    fun run(i: Instrumentation): String {
        val context = i.targetContext
        val days = mutableMapOf<String, Int>()
        SQLiteDatabase.openDatabase(context.getDatabasePath("footprints.db").path, null,
            SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT p.time FROM points p JOIN trips t ON p.trip_id=t.id WHERE t.demo=0", null).use { c ->
                while (c.moveToNext()) {
                    val key = DailyFootprints.key(c.getLong(0))
                    days[key] = (days[key] ?: 0) + 1
                }
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        File(context.applicationInfo.sourceDir).inputStream().use { input ->
            val block = ByteArray(1024 * 1024)
            while (true) { val count = input.read(block); if (count < 0) break; digest.update(block, 0, count) }
        }
        val result = JSONObject().put("days", JSONObject(days)).put("today", LocalDate.now().toString())
            .put("automatic_enabled", AmbientTracking.enabled(context))
            .put("appearance_mode", context.getSharedPreferences("ui_appearance", Context.MODE_PRIVATE).getInt("mode", 0))
            .put("skin", context.getSharedPreferences("ui_appearance", Context.MODE_PRIVATE).getString("skin","city"))
            .put("road_color", RoadAppearance(context).color)
            .put("installed_apk_sha256", digest.digest().joinToString("") { "%02x".format(it) })
        return "UI_STATE_PASS: $result"
    }
}
