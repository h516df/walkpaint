package cn.walkpaint

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONObject
import org.json.JSONArray

/** User-selected map POI, in source WGS84 coordinates. Never an inferred recommendation. */
internal data class ExplorationLandmark(val id:String,val name:String,val lat:Double,val lon:Double,
    val visitedAt:Long?=null,val deleted:Boolean=false)

internal object LandmarkCodec {
    const val MAX_ENTRIES=500
    const val MAX_BYTES=512*1024
    fun validate(p:ExplorationLandmark) {
        require(p.id.isNotBlank() && p.id.length<=200 && p.name.length<=120)
        require(p.deleted || p.name.isNotBlank())
        require(p.lat.isFinite() && p.lat in -90.0..90.0 && p.lon.isFinite() && p.lon in -180.0..180.0)
        require(p.visitedAt==null || p.visitedAt>0)
    }
    fun encode(items:Map<String,ExplorationLandmark>):JSONObject {
        require(items.size<=MAX_ENTRIES && items.all {(id,point)->id==point.id})
        val array=JSONArray()
        items.values.forEach {p->validate(p);array.put(JSONObject().put("id",p.id).put("name",p.name)
            .put("lat",p.lat).put("lon",p.lon).put("visitedAt",p.visitedAt ?: JSONObject.NULL).put("deleted",p.deleted))}
        return JSONObject().put("version",1).put("coordinateSystem","WGS84").put("items",array)
    }
    fun decode(root:JSONObject):Map<String,ExplorationLandmark> {
        require(root.getInt("version")==1);require(root.getString("coordinateSystem")=="WGS84");val a=root.getJSONArray("items");require(a.length()<=MAX_ENTRIES)
        return buildMap { for(n in 0 until a.length()) {
            val o=a.getJSONObject(n);val p=ExplorationLandmark(o.getString("id"),o.getString("name"),o.getDouble("lat"),o.getDouble("lon"),
                if(o.isNull("visitedAt")) null else o.getLong("visitedAt"),o.optBoolean("deleted",false))
            validate(p);require(p.id !in this);put(p.id,p)
        } }
    }
}

/** Atomic independent metadata; failed reads never replace the original with an empty file. */
internal class LandmarkStore(private val file:File) {
    private var cache:Map<String,ExplorationLandmark>?=null
    @Synchronized fun snapshot():Map<String,ExplorationLandmark> {
        cache?.let {return it.toMap()}
        val data=if(file.exists()) {require(file.length()<=LandmarkCodec.MAX_BYTES);LandmarkCodec.decode(JSONObject(file.readText()))} else emptyMap()
        cache=data;return data.toMap()
    }
    @Synchronized fun save(point:ExplorationLandmark) {
        LandmarkCodec.validate(point);val old=snapshot()
        persist(old+(point.id to point.copy(visitedAt=old[point.id]?.visitedAt ?: point.visitedAt)))
    }
    @Synchronized fun remove(id:String) {persist(snapshot()+(id to ExplorationLandmark(id,"",0.0,0.0,deleted=true)))}
    @Synchronized fun visit(id:String,at:Long) {
        val old=snapshot();val point=old[id] ?: return
        if(!point.deleted && point.visitedAt==null) persist(old+(id to point.copy(visitedAt=at)))
    }
    @Synchronized fun mergeMissing(incoming:Map<String,ExplorationLandmark>) {
        val old=snapshot();val next=incoming+old
        if(next!=old) persist(next)
    }
    private fun persist(next:Map<String,ExplorationLandmark>) {
        val bytes=LandmarkCodec.encode(next).toString().toByteArray(Charsets.UTF_8)
        require(bytes.size<=LandmarkCodec.MAX_BYTES)
        val dir=file.absoluteFile.parentFile!!;check(dir.isDirectory || dir.mkdirs())
        val temp=File(dir,file.name+".tmp")
        try {
            FileOutputStream(temp).use {it.write(bytes);it.fd.sync()}
            Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        } finally {temp.delete()}
        cache=next.toMap()
    }
}

/** Two fresh, accurate, non-mock GPS observations, >=10s apart and <=60s gap. Local only. */
internal class LandmarkArrival {
    private val first=mutableMapOf<String,Long>()
    fun reset() {first.clear()}
    fun sample(points:Collection<ExplorationLandmark>,lat:Double,lon:Double,accuracy:Float,
               elapsedMillis:Long,ageMillis:Long,isGps:Boolean,isMock:Boolean,enabled:Boolean):List<String> {
        if(elapsedMillis<0 || !enabled || !isGps || isMock || ageMillis !in 0..15_000 || !accuracy.isFinite() || accuracy !in 1f..25f ||
            !lat.isFinite() || lat !in -90.0..90.0 || !lon.isFinite() || lon !in -180.0..180.0) {reset();return emptyList()}
        val result=mutableListOf<String>()
        val active=points.filter {!it.deleted && it.visitedAt==null}
        first.keys.retainAll(active.map {it.id}.toSet())
        active.forEach {p->
            val distance=Geo.meters(TrackPoint(lat,lon,0,accuracy),TrackPoint(p.lat,p.lon,0,1f))
            if(distance+accuracy>50.0) first.remove(p.id)
            else {
                val start=first[p.id]
                if(start==null || elapsedMillis<start || elapsedMillis-start>60_000) first[p.id]=elapsedMillis
                else if(elapsedMillis-start>=10_000) {result+=p.id;first.remove(p.id)}
            }
        }
        return result
    }
}
