package cn.walkpaint

/** Only proven interruptions belong on the map. No-fix and stationary are settings details. */
internal object MapNotice {
    fun from(status: RecordingStatus, currentSessionHasCallback: Boolean = true, hasExploration:Boolean = true): String? = when(status.code) {
        "PAUSED" -> if(!hasExploration) "记录已暂停，开启记录后才能驱散迷雾" else null
        "STARTING", "CALLBACK_INTERRUPTED" -> if(!hasExploration) "正在等待有效定位，定位后会显露周围地图" else null
        "INVALID_FIX" -> if(!hasExploration) "定位信号较弱，请到开阔处等待定位" else null
        "PERMISSION" -> "定位权限已失效"
        "GPS_DISABLED" -> "系统定位已关闭"
        "SERVICE_STOPPED" -> "足迹记录已中断"
        "SAVE_FAILED" -> "足迹暂时无法保存"
        "REGISTER_FAILED" -> "定位服务暂不可用"
        // A missing fix is not proof the background stopped. Keep the actual age/recovery and
        // satellite evidence in 我的/诊断 instead of interrupting the exploration canvas.
        else -> null
    }
}
