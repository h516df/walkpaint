package cn.walkpaint

/** Only proven interruptions belong on the map. No-fix and stationary are settings details. */
internal object MapNotice {
    fun from(status: RecordingStatus, currentSessionHasCallback: Boolean = true): String? = when(status.code) {
        "PERMISSION" -> "定位权限已失效"
        "GPS_DISABLED" -> "系统定位已关闭"
        "SERVICE_STOPPED" -> "足迹记录已中断"
        "SAVE_FAILED" -> "足迹暂时无法保存"
        "REGISTER_FAILED" -> "定位服务暂不可用"
        "CALLBACK_INTERRUPTED" -> if(currentSessionHasCallback) "未收到新定位，请检查记录" else null
        else -> null
    }
}
