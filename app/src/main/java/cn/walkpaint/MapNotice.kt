package cn.walkpaint

/** Only proven interruptions belong on the map. No-fix and stationary are settings details. */
internal object MapNotice {
    fun from(status: RecordingStatus, currentSessionHasCallback: Boolean = true): String? = when(status.code) {
        "PERMISSION" -> "定位权限已失效"
        "GPS_DISABLED" -> "系统定位已关闭"
        "SERVICE_STOPPED" -> "足迹记录已中断"
        "SAVE_FAILED" -> "足迹暂时无法保存"
        "REGISTER_FAILED" -> "定位服务暂不可用"
        // A missing fix is not proof the background stopped. Keep the actual age/recovery and
        // satellite evidence in 我的/诊断 instead of interrupting the exploration canvas.
        "CALLBACK_INTERRUPTED" -> null
        else -> null
    }
}
