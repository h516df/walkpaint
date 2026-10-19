package cn.walkpaint

/** Permission recovery is user initiated. Locating never implies enabling recording. */
internal object LocationPermissionRecovery {
    enum class Purpose { LOCATE, RECORD }
    enum class Route { REQUEST, SETTINGS }
    enum class Return { NONE, LOCATE, RECORD }
    fun route(requestedBefore:Boolean,showRationale:Boolean):Route =
        if(requestedBefore && !showRationale) Route.SETTINGS else Route.REQUEST
    fun returned(waiting:Boolean,granted:Boolean,purpose:Purpose):Return = when {
        !waiting || !granted -> Return.NONE
        purpose==Purpose.LOCATE -> Return.LOCATE
        else -> Return.RECORD
    }
    fun restore(value:String?):Purpose = Purpose.entries.firstOrNull {it.name==value} ?: Purpose.RECORD
}
