package cn.walkpaint

import android.util.JsonWriter
import java.io.Writer
import org.json.JSONObject
import org.json.JSONArray

/** Immutable database snapshot. Encoding runs after the repository monitor is released. */
internal data class BackupSnapshot(val state:TrackState,val packs:List<RoadPack>,val journal:Map<String,DayMemo> = emptyMap(),
    val landmarks:Map<String,ExplorationLandmark> = emptyMap()) {
    fun writeTo(output:Writer) {
        val json=JsonWriter(output)
        json.beginObject().name("version").value(1).name("activeId")
        if(state.activeId==null) json.nullValue() else json.value(state.activeId)
        json.name("tracks").beginArray()
        state.tracks.forEach {t ->
            json.beginObject().name("id").value(t.id).name("started").value(t.started)
                .name("demo").value(t.demo).name("points").beginArray()
            t.points.forEach {p ->
                json.beginArray().value(p.lat).value(p.lon).value(p.time)
                    .value(p.accuracy.toDouble()).value(p.segment.toLong()).endArray()
            }
            json.endArray().endObject()
        }
        json.endArray().name("roadBackupVersion").value(1).name("roadPacks").beginArray()
        packs.forEach {writeValue(json,JSONObject(RoadPackCodec.encode(it)))}
        json.endArray().name("journal")
        writeValue(json,JournalCodec.encode(journal))
        json.name("landmarks");writeValue(json,LandmarkCodec.encode(landmarks))
        json.endObject()
        json.flush() // AtomicFile owns the underlying stream and its final fsync/close.
    }
    private fun writeValue(json:JsonWriter,value:Any?) {
        when(value) {
            null,JSONObject.NULL -> json.nullValue()
            is JSONObject -> {json.beginObject();value.keys().forEach {key ->json.name(key);writeValue(json,value.get(key))};json.endObject()}
            is JSONArray -> {json.beginArray();for(n in 0 until value.length()) writeValue(json,value.get(n));json.endArray()}
            is Number -> json.value(value)
            is Boolean -> json.value(value)
            else -> json.value(value.toString())
        }
    }
}
