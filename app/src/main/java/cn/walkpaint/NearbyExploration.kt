package cn.walkpaint

import android.content.Context
import com.amap.api.maps.CoordinateConverter
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.ServiceSettings
import com.amap.api.services.poisearch.PoiSearchV2

/** Ephemeral discovery results are not saved visits and never participate in fog geometry. */
internal data class NearbyCandidate(val point:ExplorationLandmark,val category:String,val meters:Int)
internal object NearbyCandidates {
    const val RADIUS_METERS=2000
    fun select(center:RegionPoint,items:List<Pair<ExplorationLandmark,String>>):List<NearbyCandidate> = items
        .filter { (p,_) -> runCatching {LandmarkCodec.validate(p)}.isSuccess && !p.deleted }
        .filter { (p,_) -> Geo.meters(TrackPoint(center.lat,center.lon,0,1f),TrackPoint(p.lat,p.lon,0,1f)) <= RADIUS_METERS }
        .distinctBy {it.first.id}
        .map { (p,category) -> NearbyCandidate(p.copy(visitedAt=null),category,
            Geo.meters(TrackPoint(center.lat,center.lon,0,1f),TrackPoint(p.lat,p.lon,0,1f)).toInt()) }
        .filter {it.meters in 0..RADIUS_METERS}.sortedBy {it.meters}.take(20)
    fun failure(code:Int?):String=when(code) {
        1004,1005,1010 -> "高德地点服务访问量暂时受限（代码 $code），请稍后重试。"
        1001,1002,1003,1006,1007,1008,1009,1011,1012,1013 ->
            "高德地点服务的应用配置或权限未通过（代码 $code）。请反馈给开发者；已收藏地点不受影响。"
        else -> "附近地点加载失败，请检查网络后重试" + (code?.let {"（代码 $it）"} ?: "。")
    }
}
internal object NearbyExplorationService {
    private val cache=NearbyDiscoveryCache {android.os.SystemClock.elapsedRealtime()}
    suspend fun nearby(context:Context,center:RegionPoint):List<NearbyCandidate> {
        check(RegionService.allowed(context)) {"地图授权已撤回"}
        return cache.get(center) {search(context,center)}.also {
            check(RegionService.allowed(context)) {"地图授权已撤回"}
        }
    }
    /** Existing AMap consent/key only. No web-service key, remote proxy, or new provider. */
    fun search(context:Context,center:RegionPoint):List<NearbyCandidate> {
        check(RegionService.allowed(context)) {"地图授权已撤回"}
        ServiceSettings.updatePrivacyShow(context,true,true)
        ServiceSettings.updatePrivacyAgree(context,true)
        ServiceSettings.getInstance().setApiKey(BuildConfig.AMAP_KEY)
        val display=RegionService.display(context,center)
        // Scenic parks and cultural destinations; not homes, shops or invented landmarks.
        val query=PoiSearchV2.Query("","110000|140100|140200|140300").apply {
            pageSize=20;pageNum=1;setDistanceSort(true)
        }
        val search=PoiSearchV2(context,query).apply {
            bound=PoiSearchV2.SearchBound(LatLonPoint(display.lat,display.lon),NearbyCandidates.RADIUS_METERS,true)
        }
        check(RegionService.allowed(context)) {"地图授权已撤回"}
        val result=search.searchPOI() ?: error("高德未返回地点结果")
        check(RegionService.allowed(context)) {"地图授权已撤回"}
        return NearbyCandidates.select(center,result.pois.orEmpty().mapNotNull {p ->
            val coordinate=p.latLonPoint ?: return@mapNotNull null
            val id=p.poiId?.takeIf {it.isNotBlank()} ?: return@mapNotNull null
            val name=p.title?.takeIf {it.isNotBlank()} ?: return@mapNotNull null
            val source=if(CoordinateConverter.isAMapDataAvailable(coordinate.latitude,coordinate.longitude))
                CoordinateInverse.toSource(coordinate.latitude,coordinate.longitude) {lat,lon ->
                    val converted=RegionService.display(context,RegionPoint(lat,lon));converted.lat to converted.lon
                } else coordinate.latitude to coordinate.longitude
            ExplorationLandmark(id.take(200),name.take(120),source.first,source.second) to p.typeDes.orEmpty()
        })
    }
    fun message(error:Throwable):String=if(error is NearbyRefreshDeferred) error.message!!
        else NearbyCandidates.failure((error as? AMapException)?.errorCode)
}
