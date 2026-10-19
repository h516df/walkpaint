package cn.walkpaint

/** Presentation only: actual explored area remains the unchanged 50 m recorded-path geometry. */
internal object FogAppearance {
    const val DEFAULT=0x709FB5C6
    fun featherAlpha(color:Int,veilFraction:Float,tintAlpha:Int):Int {
        val outer=(color ushr 24).toFloat()
        val inner=minOf(tintAlpha.coerceIn(0,255).toFloat(),outer)
        return (inner+(outer-inner)*veilFraction.coerceIn(0f,1f)).toInt().coerceIn(1,255)
    }
    fun color(skin:VisualSkin):Int=when(skin) {
        VisualSkin.CITY -> DEFAULT
        VisualSkin.DUSK -> 0x709F91B8
        VisualSkin.NIGHT -> 0x80333E55.toInt()
    }
}
