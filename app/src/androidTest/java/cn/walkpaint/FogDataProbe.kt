package cn.walkpaint
import android.app.Instrumentation
import android.database.Cursor
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
/** Row-level digest for signed release upgrade, no coordinates or rows exported. */
internal object FogDataProbe {
    fun run(i:Instrumentation):String = FootprintDatabase(i.targetContext).use { repo ->
        val result=JSONObject()
        val db=repo.readableDatabase
        db.rawQuery("PRAGMA integrity_check",null).use { c -> check(c.moveToFirst() && c.getString(0)=="ok") }
        for(table in listOf("trips","points","roads","road_matches","coverage")) {
            val rows=mutableListOf<String>()
            db.rawQuery("SELECT * FROM $table",null).use { c ->
                while(c.moveToNext()) {
                    val row=JSONArray()
                    for(n in 0 until c.columnCount) row.put(when(c.getType(n)) {
                        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                        Cursor.FIELD_TYPE_INTEGER -> c.getLong(n)
                        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(n)
                        else -> c.getString(n)
                    })
                    rows.add(row.toString())
                }
            }
            val digest=MessageDigest.getInstance("SHA-256").digest(rows.sorted().joinToString("\n").toByteArray())
                .joinToString("") {"%02x".format(it)}
            result.put(table,JSONObject().put("count",rows.size).put("sha256",digest))
        }
        "FOG_DATA_DIGEST_PASS "+result.toString()
    }
}
