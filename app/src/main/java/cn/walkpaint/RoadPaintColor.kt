package cn.walkpaint

import java.util.Locale

/** Opaque display colors only. Never used for GPS, road matching or backup data. */
internal object RoadPaintColor {
    const val DEFAULT=0xFF579B8C.toInt()
    data class Preset(val name:String,val argb:Int)
    val presets=listOf(
        // Muted set, kept because it was chosen deliberately.
        Preset("青绿",DEFAULT),Preset("雾蓝",0xFF647D9E.toInt()),
        Preset("灰紫",0xFF887A9D.toInt()),Preset("石墨",0xFF657778.toInt()),Preset("赭金",0xFFA38556.toInt()),
        // High-contrast set, for reading the trail against an online basemap full of grey roads
        // and pale green land. Without these the only choices were greyish and hard to see.
        Preset("亮青",0xFF00A98F.toInt()),Preset("橘橙",0xFFE2611C.toInt()),
        Preset("品红",0xFFC2185B.toInt()),Preset("亮蓝",0xFF1565D8.toInt()))
    fun parse(text:String?):Int? {
        val digits=text?.trim()?.removePrefix("#") ?: return null
        if(!digits.matches(Regex("[0-9a-fA-F]{6}"))) return null
        return digits.toInt(16) or 0xFF000000.toInt()
    }
    fun hex(argb:Int)="#%06X".format(Locale.ROOT,argb and 0xFFFFFF)
    fun name(argb:Int)=presets.firstOrNull { it.argb==argb }?.name ?: "自定义"
    fun stored(text:String?)=parse(text) ?: DEFAULT
}
