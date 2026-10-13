package cn.walkpaint

import kotlin.math.ln
import kotlin.math.tan
import kotlin.math.PI

/** Display coordinates only: a map's vertical scale is Mercator, not linear latitude.
 * Keeps wide-view native route geometry aligned without projecting every point each frame. */
internal object MapRenderCoordinates {
    fun northing(lat:Double)=ln(tan(PI/4+Math.toRadians(lat.coerceIn(-85.05112878,85.05112878))/2))
    fun y(lat:Double,south:Double,north:Double,height:Int):Float {
        val top=northing(north);val bottom=northing(south)
        return ((top-northing(lat))/(top-bottom).coerceAtLeast(1e-12)*height).toFloat()
    }
}
