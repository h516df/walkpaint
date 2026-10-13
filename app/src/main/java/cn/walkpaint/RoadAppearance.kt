package cn.walkpaint

import android.content.Context

internal class RoadAppearance(context:Context) {
    private val prefs=context.getSharedPreferences("road_appearance",Context.MODE_PRIVATE)
    val color get()=RoadPaintColor.stored(runCatching { prefs.getString("explored_color",null) }.getOrNull())
    fun save(color:Int) { prefs.edit().putString("explored_color",RoadPaintColor.hex(color)).apply() }
}
