package cn.walkpaint

import android.content.Context
import android.util.AtomicFile
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.ServiceSettings
import com.amap.api.services.district.DistrictItem
import com.amap.api.services.district.DistrictSearch
import com.amap.api.services.district.DistrictSearchQuery
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Rebuildable index only. Original tracks/backup database are never written by this component. */
internal class RegionJourneyStore(private val context:Context) {
    private val file=AtomicFile(File(context.filesDir,"region-journey-v1.json"))
    private val places=linkedMapOf<String,JourneyPlace>()
    private val trees=mutableMapOf<String,JSONObject>()
    private val boundaries=mutableMapOf<String,RegionBoundary>()
    private val boundaryAttempts=mutableSetOf<String>()
    private var requests=0
    private var dirty=false
    var deferred=false;private set
    var lastFailure:String?=null;private set
    init {
        runCatching{val bytes=file.readFully();require(bytes.size<=16_000_000);val root=JSONObject(String(bytes,Charsets.UTF_8))
            val entries=root.getJSONObject("samples")
            entries.keys().forEach{key->places[key]=decodePlace(entries.getJSONObject(key))}
            val catalog=root.optJSONObject("provinces") ?: JSONObject()
            catalog.keys().forEach{key->trees[key]=catalog.getJSONObject(key)}
        }
        places.values.map{it.county.code}.distinct().forEach{code->runCatching {
            val cache=File(context.cacheDir,"district-$code.json")
            if(cache.length() in 1..4_000_000 && System.currentTimeMillis()-cache.lastModified()<7*86400_000L)
                boundaries[code]=RegionService.decode(JSONObject(cache.readText()))
        }}
    }
    fun snapshot():Map<String,JourneyPlace> = places.toMap()
    fun contains(key:String)=key in places
    fun prune(keys:Set<String>) {if(places.keys.retainAll(keys))dirty=true}
    fun flush(){if(dirty)try{save();dirty=false}catch(_:java.io.IOException){lastFailure="地区暂存未完成，稍后会重新整理"}}
    private fun permitRequest():Boolean {
        if(requests>=40){deferred=true;lastFailure="历史足迹会在下次打开时继续整理";return false}
        requests++;return true
    }
    private fun save() {
        val data=JSONObject().put("samples",JSONObject(places.mapValues{encodePlace(it.value)})).put("provinces",JSONObject(trees)).toString().toByteArray()
        val stream=file.startWrite()
        try{stream.write(data);file.finishWrite(stream)}catch(e:Exception){file.failWrite(stream);throw e}
    }
    /** Cached polygons only attribute a point when it is actually inside; no nearby-cell guessing. */
    fun resolve(sample:JourneySample):JourneyPlace? {
        places[sample.key]?.let{return it}
        if(!RegionService.allowed(context)){lastFailure="联网识别需先同意高德地图服务";return null}
        lastFailure=null
        return try {
            val raw=RegionPoint(sample.point.lat,sample.point.lon)
            val displayed=RegionService.display(context,raw)
            val known=boundaries.values.firstOrNull{b->b.rings.any{RegionCoverage.contains(it,displayed)}}
            val knownPlace=known?.let{b->places.values.firstOrNull{it.county.code==b.code}}
            val place=knownPlace ?: lookup(raw)
            if(place!=null){places[sample.key]=place;dirty=true}
            else if(lastFailure==null)lastFailure="地区信息暂不可用，联网后会继续整理"
            place
        }catch(e:com.amap.api.services.core.AMapException){lastFailure="地区服务暂不可用（${e.errorCode}）";null}
        catch(_:Exception){lastFailure="地区连接暂不可用，稍后重试";null}
    }
    private fun lookup(raw:RegionPoint):JourneyPlace? {
        ServiceSettings.updatePrivacyShow(context,true,true);ServiceSettings.updatePrivacyAgree(context,true)
        ServiceSettings.getInstance().setApiKey(BuildConfig.AMAP_KEY)
        if(!permitRequest())return null
        val address=GeocodeSearch(context).getFromLocation(RegeocodeQuery(LatLonPoint(raw.lat,raw.lon),200f,GeocodeSearch.GPS)) ?: return null
        val countyCode=address.adCode?.takeIf{it.matches(Regex("[0-9]{6}"))} ?: return null
        val provinceName=address.province?.takeIf{it.isNotBlank()} ?: return null
        var tree=trees[provinceName]
        if(tree==null || System.currentTimeMillis()-tree.optLong("updated")>30*86400_000L) {
            if(!RegionService.allowed(context))return null
            val query=DistrictSearchQuery().apply{setKeywords(provinceName);setKeywordsLevel("province");setShowBoundary(false);setSubDistrict(2)}
            if(!permitRequest())return null
            val result=DistrictSearch(context).apply{setQuery(query)}.searchDistrict()
            if(!RegionJourneyRules.querySucceeded(result.aMapException?.errorCode,result.district.orEmpty().size)){lastFailure="地区信息暂不可用，稍后重试";return null}
            val province=result.district.orEmpty().firstOrNull{it.level=="province" && it.name==provinceName} ?: return null
            tree=encodeTree(province).put("updated",System.currentTimeMillis());trees[provinceName]=tree;dirty=true
        }
        return placeInTree(tree ?: return null,countyCode)?.also{place->
            if(boundaryAttempts.add(place.county.code) && permitRequest()) {
                runCatching{RegionService.boundary(context,place.county.code,place.county.name)?.let{boundaries[it.code]=it}}
            }
        }
    }
    companion object {
        val indexMutex=kotlinx.coroutines.sync.Mutex()
        private fun encodeTree(item:DistrictItem):JSONObject = JSONObject().put("code",item.adcode).put("name",item.name).put("level",item.level)
            .put("children",JSONArray(item.subDistrict.orEmpty().map(::encodeTree)))
        /** Municipalities and directly administered counties are taken from the returned tree,
         * never synthesized by slicing codes or counting the same province as a city. */
        internal fun placeInTree(tree:JSONObject,countyCode:String):JourneyPlace? {
            fun node(o:JSONObject):JourneyRegion {
                val children=o.optJSONArray("children") ?: JSONArray()
                var counties=0
                for(i in 0 until children.length()) {
                    val child=children.getJSONObject(i)
                    if(child.optString("level")=="district")counties++
                    else {val descendants=child.optJSONArray("children") ?: JSONArray();for(j in 0 until descendants.length())if(descendants.getJSONObject(j).optString("level")=="district")counties++}
                }
                return JourneyRegion(o.getString("code"),o.getString("name"),o.getString("level"),children.length(),counties)
            }
            val province=node(tree);if(province.level!="province")return null
            val first=tree.optJSONArray("children") ?: return null
            for(i in 0 until first.length()){
                val child=first.getJSONObject(i);val region=node(child)
                if(region.code==countyCode && region.level=="district")return JourneyPlace(region,null,province)
                if(region.level!="city")continue
                val counties=child.optJSONArray("children") ?: continue
                for(j in 0 until counties.length()) {val county=node(counties.getJSONObject(j))
                    if(county.code==countyCode && county.level=="district")return JourneyPlace(county,region,province)
                }
            }
            return null
        }
        internal fun encodePlace(place:JourneyPlace):JSONObject {
            fun encode(r:JourneyRegion)=JSONObject().put("code",r.code).put("name",r.name).put("level",r.level).put("childCount",r.childCount).put("countyCount",r.countyCount)
            return JSONObject().put("county",encode(place.county)).put("city",place.city?.let(::encode)).put("province",encode(place.province))
        }
        internal fun decodePlace(o:JSONObject):JourneyPlace {
            fun decode(r:JSONObject)=JourneyRegion(r.getString("code"),r.getString("name"),r.getString("level"),r.optInt("childCount"),r.optInt("countyCount"))
            return JourneyPlace(decode(o.getJSONObject("county")),o.optJSONObject("city")?.let(::decode),decode(o.getJSONObject("province"))).also{place->
                require(place.county.level=="district" && place.province.level=="province" && (place.city==null || place.city.level=="city"))
                require(place.regions.all{it.code.matches(Regex("[0-9]{6}")) && it.name.isNotBlank() && it.childCount>=0})
            }
        }
    }
}
