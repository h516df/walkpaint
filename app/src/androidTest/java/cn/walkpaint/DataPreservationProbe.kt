package cn.walkpaint

import android.app.Instrumentation
import android.database.sqlite.SQLiteDatabase
import java.security.MessageDigest
import org.json.JSONObject

/** Read-only fingerprints for coverage-install acceptance. Never exports GPS rows. */
internal object DataPreservationProbe {
    fun run(i: Instrumentation): String {
        val result=JSONObject()
        SQLiteDatabase.openDatabase(i.targetContext.getDatabasePath("footprints.db").path,null,SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA integrity_check",null).use { check(it.moveToFirst() && it.getString(0)=="ok") }
            for(table in listOf("trips","points","roads","road_matches","coverage")) {
                val digest=MessageDigest.getInstance("SHA-256")
                var count=0
                db.rawQuery("SELECT * FROM $table ORDER BY rowid",null).use { cursor ->
                    while(cursor.moveToNext()) {
                        count++
                        for(column in 0 until cursor.columnCount) {
                            val value=if(cursor.isNull(column)) "N" else "V"+cursor.getString(column)
                            val bytes=value.toByteArray(Charsets.UTF_8)
                            digest.update((bytes.size.toString()+":").toByteArray(Charsets.UTF_8));digest.update(bytes)
                        }
                    }
                }
                result.put(table,JSONObject().put("count",count).put("sha256",digest.digest().joinToString(""){"%02x".format(it)}))
            }
        }
        return "DATA_PRESERVATION_PASS: $result"
    }
}
