package cn.walkpaint

import android.graphics.Bitmap
import android.graphics.Matrix

/** Gate native POIs using the actual displayed fog pixels, not another exploration geometry. */
internal object FogPoiVisibility {
    fun visible(image:Bitmap?,transform:Matrix,x:Float,y:Float,frameMatches:Boolean):Boolean {
        if(!frameMatches || image==null || image.isRecycled || !x.isFinite() || !y.isFinite()) return false
        val inverse=Matrix()
        if(!transform.invert(inverse)) return false
        val pixel=floatArrayOf(x,y);inverse.mapPoints(pixel)
        if(!pixel.all {it.isFinite()}) return false
        val px=kotlin.math.floor(pixel[0].toDouble()).toInt();val py=kotlin.math.floor(pixel[1].toDouble()).toInt()
        // Require all neighboring samples to be fully revealed. Filtered feather edges cannot
        // expose an invisible POI, and an unavailable/outside raster fails closed.
        if(px<1 || py<1 || px>=image.width-1 || py>=image.height-1) return false
        return (py-1..py+1).all {row -> (px-1..px+1).all {col ->
            (image.getPixel(col,row) ushr 24)<=TrailPaint.TINT_ALPHA
        }}
    }
}
