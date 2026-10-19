package cn.walkpaint

/** SDK readiness is not evidence that remote map tiles have been downloaded. */
internal object ExplorationReadiness {
    fun message(viewReady:Boolean, automatic:Boolean, hasFix:Boolean):String = when {
        !viewReady -> "地图正在初始化。淡雾保留地理轮廓，走过后才算探索。"
        !automatic -> "还没有足迹。开始记录，走过的地方就会揭开迷雾。"
        !hasFix -> "正在等待可靠定位，请允许精确定位并到室外稍等。"
        else -> "等待第一段足迹保存。淡雾不代表底图加载成功或失败。"
    }
}

/** A noisy compass must not repeatedly spin the camera back to north. No GPS course fallback. */
internal class HeadingContinuity {
    private var lastReliable=0f
    fun resolve(heading:Float?):Float {
        if(heading!=null && heading.isFinite()) lastReliable=HeadingFilter.normalize(heading)
        return lastReliable
    }
}

/** An explicit locate's zoom survives SDK loading; a manual gesture cancels the intent. */
internal class LocateZoomRequest {
    var pending=false
        private set
    fun request() {pending=true}
    fun cancel() {pending=false}
    fun delivered(accepted:Boolean) {if(accepted) pending=false}
}
