package cn.walkpaint

/** Unknown ground must conceal the basemap, not merely tint its roads and labels.
 * Use muted RGB colours for a gentle appearance, never transparency outside recorded corridors.
 * Presentation only: actual explored area remains the unchanged 50 m recorded-path geometry.
 * The same opaque palette covers startup and camera motion outside the current raster.
 */
internal object FogAppearance {
    const val DEFAULT=0xFFDDE2E3.toInt()
    fun featherAlpha(color:Int,veilFraction:Float,tintAlpha:Int):Int {
        val outer=(color ushr 24).toFloat()
        val inner=minOf(tintAlpha.coerceIn(0,255).toFloat(),outer)
        return (inner+(outer-inner)*veilFraction.coerceIn(0f,1f)).toInt().coerceIn(1,255)
    }
    fun color(skin:VisualSkin,dark:Boolean=skin==VisualSkin.NIGHT):Int=if(dark) {
        if(skin==VisualSkin.DUSK) 0xFF28322B.toInt() else 0xFF252D31.toInt()
    } else when(skin) {
        VisualSkin.CITY -> DEFAULT
        VisualSkin.DUSK -> 0xFFDFE3DA.toInt()
        VisualSkin.NIGHT -> DEFAULT
    }
}
