package cn.walkpaint

/** Stable preference IDs. Loading the default never changes the person's saved trail colour. */
internal enum class VisualSkin(val id:String,val label:String,val description:String,val paint:Int) {
    CITY("city","简洁浅色","中性灰雾，蓝绿足迹",0xFF386F7A.toInt()),
    DUSK("dusk","自然浅色","灰绿雾面，松绿足迹",0xFF496F54.toInt()),
    NIGHT("night","夜间深色","炭灰雾面，青蓝足迹",0xFF8ABFC8.toInt());
    companion object { fun stored(id:String?)=entries.firstOrNull { it.id==id } ?: CITY }
}
