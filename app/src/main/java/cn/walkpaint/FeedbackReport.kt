package cn.walkpaint

internal data class FeedbackFacts(
    val version:String, val build:Int, val android:Int, val brand:String, val model:String,
    val enabled:Boolean,val running:Boolean,val fine:Boolean,val background:Boolean,val notifications:Boolean,
    val points:Int,val roads:Int,val matched:Int,val unconfirmed:Int,val paintedMetres:Long,
    val currentConfirmable:Int,val noCandidates:Int,val roadReadFailed:Boolean,
    val diagnostics:Map<String,Any?>,val history:List<ProblemEntry>,val collected:Boolean,
    val historySaveFailed:Boolean=false,
    val directTrail:Boolean=false,val trailLines:Int=0,val trailMetres:Long=0
)
internal object FeedbackReport {
    val categories=listOf("定位","道路涂色","地图显示","权限和后台","闪退","其他")
    fun age(at:Long,now:Long)=if(at<=0) "无记录" else "${(now-at).coerceAtLeast(0)/60_000} 分钟前"
    private fun device(value:String)=value.filter { it.isLetterOrDigit() || it in " _-" }.take(60)
    fun build(f:FeedbackFacts,category:String,description:String,now:Long):String {
        fun flag(v:Boolean)=if(v) "是" else "否"
        fun count(key:String)=(f.diagnostics[key] as? Number)?.toLong()?.coerceAtLeast(0) ?: 0L
        fun recent(key:String)=age(count(key),now)
        val topic=category.takeIf { it in categories } ?: "其他"
        val exitCode=(f.diagnostics["process_exit_reason"] as? String)?.let {
            Regex("ANDROID_REASON_([0-9]{1,2})").matchEntire(it)?.groupValues?.get(1)
        } ?: "无可用原因码"
        val clue=when {
            f.enabled && f.running && count("session_gps_count")==0L -> "本次服务尚未收到定位回调，运行中不等于已有足迹"
            f.points==0 -> "尚无保存的定位点"
            f.directTrail -> "已保存的轨迹直接在地图上涂色，不依赖道路包；请结合GPS、保存与地图服务状态检查"
            f.roadReadFailed -> "道路统计暂不可用，原足迹保留"
            f.roads==0 -> "未准备道路数据"
            f.currentConfirmable>0 && f.paintedMetres==0L -> "当前复算可确认，但累计涂色为空，需要进一步检查"
            f.matched==0 && f.noCandidates>0 -> "当前道路目录存在无候选的区间"
            else -> "请结合下方计数与近期线索检查，报告不会自动判定所有故障"
        }
        return listOf(
            "足迹涂色 · 问题反馈", "版本 ${f.version} (${f.build}) · Android ${f.android}",
            "设备 ${device(f.brand)} ${device(f.model)}", "问题类型：$topic",
            "你填写的描述（会随报告分享）：\n${description.take(1000).ifBlank { "未填写" }}",
            "当前状态：记录开关 ${flag(f.enabled)} / 服务运行 ${flag(f.running)}",
            "授权：精确位置 ${flag(f.fine)} / 后台位置 ${flag(f.background)} / 通知 ${flag(f.notifications)}",
            "最近定位回调 ${recent("gps_callback_at")} / 有效定位 ${recent("valid_gps_at")} / 保存 ${recent("saved_at")}",
            "本次服务：回调 ${count("session_gps_count")} / 有效 ${count("session_valid_gps_count")} / 保存 ${count("session_saved_count")} / 过滤 ${count("session_filtered_count")}",
            "等待移动确认 ${count("session_pending_count")} 次暂缓决定；可能随后随批入库，不是丢点统计",
            GpsFilterDiagnostics.session(f.diagnostics),
            GpsFilterDiagnostics.recent(f.history, f.collected, f.historySaveFailed, now),
            "系统退出原因码：$exitCode（仅系统线索，不推断厂商限制）",
            if(f.directTrail) "直接轨迹计数：已保存定位 ${f.points} 点 / 连续轨迹 ${f.trailLines} 段 / 累计记录 ${f.trailMetres} 米；不是去重道路里程，也不表示地图像素已验收"
                else "计数：定位点 ${f.points} / 道路 ${f.roads} / 已确认 ${f.matched} / 未确认 ${f.unconfirmed} / 涂色 ${f.paintedMetres} 米",
            if(f.directTrail) "绘制来源：本机已保存GPS；不需要准备地图或下载道路包，旧道路匹配不控制本次显示"
                else "当前目录只读复算（最近最多2000段）：可确认 ${f.currentConfirmable} / 无候选 ${f.noCandidates}；不等于历史确认结果",clue,
            "近期线索（本机最多7天，按原因合并）：${if(f.collected) "收集中" else "已关闭收集"}${if(f.historySaveFailed) "，本机保存失败" else ""}",
            f.history.take(30).joinToString("\n") { "${age(it.last,now)} · ${it.code.label} · ${it.count} 次 · ${if(it.code.problem) "已观察，是否恢复请看当前状态" else "操作记录"}" }.ifBlank { "暂无线索。未发现线索不表示已发现所有故障。" },
            "自动信息仅含版本、非唯一设备型号、权限、相对时间、固定原因与计数；不含坐标、路线、道路名称编号、截图或原始日志。你填写的描述会原样出现，请检查后再分享。"
        ).joinToString("\n\n")
    }
}
