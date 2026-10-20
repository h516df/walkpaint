package cn.walkpaint

/** Screen-space selection only: no fog, track, arrival or persistence changes. */
internal data class ExplorationHint(val point:ExplorationLandmark,val x:Float,val y:Float,val saved:Boolean)
internal object ExplorationHints {
    fun select(points:List<ExplorationHint>,width:Float,height:Float,density:Float,zoom:Float):List<ExplorationHint> {
        val selected=mutableListOf<ExplorationHint>()
        val spacing=64f*density
        val limit=if(zoom<13f) 3 else if(zoom<15f) 5 else 8
        points.distinctBy {it.point.id}.forEach {p ->
            if(selected.size<limit && p.x.isFinite() && p.y.isFinite() &&
                p.x in 24f*density..width-24f*density && p.y in 48f*density..height-72f*density &&
                selected.none {kotlin.math.hypot(it.x-p.x,it.y-p.y)<spacing}) selected+=p
        }
        return selected
    }
    fun hit(points:List<ExplorationHint>,x:Float,y:Float,density:Float):ExplorationLandmark? =
        points.minByOrNull {kotlin.math.hypot(it.x-x,it.y-y)}
            ?.takeIf {kotlin.math.hypot(it.x-x,it.y-y)<=24f*density}?.point
}

/** Cache validity is based on reliable source coordinates, never the panned camera. */
internal object NearbyRefreshPolicy {
    const val INTERVAL_MS=60_000L
    const val CACHE_MS=5*60_000L
    fun near(a:RegionPoint,b:RegionPoint)=Geo.meters(TrackPoint(a.lat,a.lon,0,1f),TrackPoint(b.lat,b.lon,0,1f))<250.0
}
