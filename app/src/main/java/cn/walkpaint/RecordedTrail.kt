package cn.walkpaint

/** Shared display geometry for AMap and the fallback. No road catalog or map download. */
internal object RecordedTrail {
    const val MAX_GAP_MS=45_000L
    const val MAX_SPEED_MPS=60.0
    fun connected(a:TrackPoint,b:TrackPoint):Boolean {
        val gap=b.time-a.time
        return a.segment==b.segment && gap in 1..MAX_GAP_MS &&
            Geo.meters(a,b)/(gap/1000.0)<=MAX_SPEED_MPS
    }
    fun paths(tracks:List<Track>):List<List<TrackPoint>> = buildList {
        tracks.filterNot { it.demo }.forEach { trip ->
            var path=mutableListOf<TrackPoint>()
            trip.points.forEach { p ->
                if(!p.lat.isFinite() || !p.lon.isFinite() || p.lat !in -90.0..90.0 || p.lon !in -180.0..180.0) {
                    if(path.isNotEmpty()) add(path.toList())
                    path=mutableListOf()
                } else {
                    if(path.isNotEmpty() && !connected(path.last(),p)) { add(path.toList());path=mutableListOf() }
                    path.add(p)
                }
            }
            if(path.isNotEmpty()) add(path.toList())
        }
    }
}
