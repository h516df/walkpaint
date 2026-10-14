package cn.walkpaint

/** A user-facing view of the existing diagnosis, with the full report retained for copying. */
internal data class DiagnosisPresentation(val title:String,val detail:String,val rows:List<DiagnosisItem>) {
    companion object {
        fun of(f:DiagnosisFacts):DiagnosisPresentation {
            val id=Diagnosis.syndromeId(f)
            val title=when(id) {
                Diagnosis.SCREEN_PAUSED -> "自动记录已暂停"
                Diagnosis.SCREEN_PERMISSION -> "需要精确定位权限"
                Diagnosis.SCREEN_PHONE_LOCATION_OFF -> "手机定位已关闭"
                Diagnosis.SCREEN_STORAGE -> "足迹暂时无法保存"
                Diagnosis.SCREEN_BACKGROUND_STOPPED -> "记录后台已停止"
                Diagnosis.SCREEN_NO_SIGNAL -> "正在等待 GPS 位置"
                Diagnosis.SCREEN_WEAK_SIGNAL -> "定位精度暂时不足"
                Diagnosis.SCREEN_WALKING_FILTERED -> "记录需要检查"
                Diagnosis.SCREEN_LONG_GAP -> "当前记录有历史断档"
                "ACQUIRING_FIRST_GPS" -> "正在搜寻首次定位"
                Diagnosis.SCREEN_UNCLEAR -> "等待确认新的移动"
                else -> "定位与记录正常"
            }
            if(id==Diagnosis.SCREEN_PAUSED) return DiagnosisPresentation(title,
                "这是你选择的状态。历史足迹仍在；回到“我的”开启自动记录后，再开始新的记录。",
                listOf(DiagnosisItem("历史足迹",Health.OK,"${f.savedCount} 个点保存在本机")))
            return DiagnosisPresentation(title,Diagnosis.verdict(f),Diagnosis.items(f).filter { it.title!="记录开关" })
        }
    }
}
