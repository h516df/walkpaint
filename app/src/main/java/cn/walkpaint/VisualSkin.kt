package cn.walkpaint

/** Stable preference IDs. Loading the default never changes the person's saved trail colour. */
internal enum class VisualSkin(val id:String,val label:String,val description:String,val paint:Int) {
    CITY("city","城市冰蓝","清爽城市，沿河出发",0xFF3D7BFF.toInt()),
    DUSK("dusk","暮色湖岸","山湖暮色，温柔紫调",0xFFA17BEA.toInt()),
    NIGHT("night","午夜城市","深色街景，暖金足迹",0xFFE3BB70.toInt());
    companion object { fun stored(id:String?)=entries.firstOrNull { it.id==id } ?: CITY }
}
