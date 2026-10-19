package cn.walkpaint

import kotlin.math.cos

internal data class CardPoint(val x:Double,val y:Double)
internal data class RouteOutline(val paths:List<List<CardPoint>>,val width:Double,val height:Double) {
    fun fit(width:Double,height:Double):List<List<CardPoint>> {
        require(width>0 && height>0)
        val scale=minOf(width*.84/this.width.coerceAtLeast(1e-7),height*.84/this.height.coerceAtLeast(1e-7))
        return paths.map {path->path.map {p->CardPoint(width/2+(p.x-this.width/2)*scale,height/2+(p.y-this.height/2)*scale)}}
    }
    companion object {
        fun from(tracks:List<Track>):RouteOutline {
            val source=RecordedTrail.exploredPaths(tracks)
            val first=source.firstOrNull()?.firstOrNull() ?: return RouteOutline(emptyList(),0.0,0.0)
            val factor=cos(Math.toRadians(first.lat)).coerceAtLeast(.01)
            val paths=source.map {path->path.map {p->
                // Keep a route crossing the date line local instead of spanning the whole world.
                CardPoint(((p.lon-first.lon+540.0)%360.0-180.0)*factor,first.lat-p.lat)
            }}
            val points=paths.flatten()
            val minX=points.minOf {it.x};val minY=points.minOf {it.y}
            return RouteOutline(paths.map {path->path.map {CardPoint(it.x-minX,it.y-minY)}},
                points.maxOf {it.x}-minX,points.maxOf {it.y}-minY)
        }
    }
}
