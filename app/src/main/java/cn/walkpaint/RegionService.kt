package cn.walkpaint

import android.content.Context
import com.amap.api.maps.CoordinateConverter
import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.ServiceSettings
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.district.DistrictSearch
import com.amap.api.services.district.DistrictSearchQuery
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal object RegionService {
    fun allowed(context:Context)=BuildConfig.AMAP_KEY.isNotBlank() && context.getSharedPreferences("ui_onboarding",0).getBoolean("amap_sdk_consent",false)
    fun display(context:Context,p:RegionPoint):RegionPoint {
        val result=CoordinateConverter(context).from(CoordinateConverter.CoordType.GPS).coord(LatLng(p.lat,p.lon)).convert()
        return RegionPoint(result.latitude,result.longitude)
    }
    /** Called off the UI thread. Consent checked before each network operation. */
    fun resolve(context:Context,raw:RegionPoint):RegionBoundary? {
        if(!allowed(context))return null
        val point=display(context,raw)
        val file=File(context.cacheDir,"exploration-district.json")
        val cached=runCatching{if(file.length() !in 1..4_000_000) null else decode(JSONObject(file.readText()))}.getOrNull()
        if(cached!=null && System.currentTimeMillis()-file.lastModified()<7*86400_000L && cached.rings.any{RegionCoverage.contains(it,point)})return cached
        ServiceSettings.updatePrivacyShow(context,true,true);ServiceSettings.updatePrivacyAgree(context,true)
        ServiceSettings.getInstance().setApiKey(BuildConfig.AMAP_KEY)
        val address=GeocodeSearch(context).getFromLocation(RegeocodeQuery(LatLonPoint(raw.lat,raw.lon),200f,GeocodeSearch.GPS)) ?: return null
        val code=address.adCode?.takeIf{it.isNotBlank()} ?: return null
        if(!allowed(context))return null
        val boundary=boundary(context,code,address.district ?: code) ?: return null
        if(!boundary.rings.any{RegionCoverage.contains(it,point)})return null
        runCatching{file.writeText(encode(boundary).toString())}
        return boundary
    }

    fun boundary(context:Context,code:String,name:String):RegionBoundary? {
        if(!allowed(context) || !code.matches(Regex("[0-9]{6}")))return null
        val cache=File(context.cacheDir,"district-$code.json")
        runCatching{if(cache.length() in 1..4_000_000 && System.currentTimeMillis()-cache.lastModified()<7*86400_000L)decode(JSONObject(cache.readText())) else null}.getOrNull()?.let{return it}
        ServiceSettings.updatePrivacyShow(context,true,true);ServiceSettings.updatePrivacyAgree(context,true)
        ServiceSettings.getInstance().setApiKey(BuildConfig.AMAP_KEY)
        val query=DistrictSearchQuery().apply{setKeywords(code);setKeywordsLevel("district");setShowBoundary(true);setSubDistrict(0)}
        val result=DistrictSearch(context).apply{setQuery(query)}.searchDistrict()
        if(!RegionJourneyRules.querySucceeded(result.aMapException?.errorCode,result.district.orEmpty().size))return null
        val item=result.district?.firstOrNull{it.adcode==code} ?: return null
        val rings=item.districtBoundary().orEmpty().flatMap{it.split('|')}.map{part->part.split(';').mapNotNull{pair->
            val numbers=pair.split(',');if(numbers.size!=2)null else {
                val lon=numbers[0].toDoubleOrNull();val lat=numbers[1].toDoubleOrNull()
                if(lat!=null && lon!=null && lat in -85.0..85.0 && lon in -180.0..180.0)RegionPoint(lat,lon) else null
            }
        }}.filter{it.size>=3}
        if(rings.isEmpty() || rings.sumOf{it.size}>100_000)return null
        val boundary=RegionBoundary(item.name ?: name,code,rings)
        runCatching{cache.writeText(encode(boundary).toString())}
        return boundary
    }

    internal fun encode(value:RegionBoundary)=JSONObject().put("name",value.name).put("code",value.code)
        .put("rings",JSONArray(value.rings.map{ring->JSONArray(ring.map{p->JSONArray(listOf(p.lat,p.lon))})}))
    internal fun decode(o:JSONObject):RegionBoundary {
        val rings=o.getJSONArray("rings")
        return RegionBoundary(o.getString("name"),o.getString("code"),(0 until rings.length()).map{i->val r=rings.getJSONArray(i)
            (0 until r.length()).map{j->val p=r.getJSONArray(j);RegionPoint(p.getDouble(0),p.getDouble(1))}
        })
    }
}
