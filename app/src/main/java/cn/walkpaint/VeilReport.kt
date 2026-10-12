package cn.walkpaint

import java.util.Locale

/**
 * What the veil image actually contains, counted rather than assumed.
 *
 * The person's complaint was that the fog was thin enough to read the roads through it. That is a
 * question about pixels, and it can only be answered on the device that is showing them: one painter
 * feeds both map engines, but each engine composites the image its own way. Counting the image here
 * separates "the veil I handed the map was translucent" from "the map drew something over an opaque
 * veil". Those are different bugs with different fixes, and from the outside they look identical.
 *
 * Nothing here touches the map engine, so the arithmetic, the layout and the wording are all
 * settled by unit tests instead of by another walk in the street.
 */
internal data class VeilStats(
    val pixels: Int,
    /** alpha 250..255: genuinely hides whatever is under it. */
    val hidden: Int,
    /** alpha 255 *and* rgb 0: hides, and is the black the effect was specified as. */
    val black: Int,
    /** alpha 128..249: the basemap shows through faintly, which is how a road becomes readable. */
    val partial: Int,
    /** alpha below 128: cleared ground, the band the person has walked. */
    val open: Int,
) {
    val hiddenShare: Double get() = share(hidden)
    val blackShare: Double get() = share(black)
    val partialShare: Double get() = share(partial)
    val openShare: Double get() = share(open)

    private fun share(count: Int) = if (pixels == 0) 0.0 else count.toDouble() / pixels
}

internal object VeilReport {
    /** Buckets one ARGB image. Linear, and only ever called when the person asks for an export. */
    fun of(argb: IntArray): VeilStats {
        var hidden = 0
        var black = 0
        var partial = 0
        var open = 0
        for (pixel in argb) {
            val alpha = (pixel ushr 24) and 0xFF
            when {
                alpha >= 250 -> {
                    hidden++
                    if (alpha == 255 && (pixel and 0x00FFFFFF) == 0) black++
                }
                alpha >= 128 -> partial++
                else -> open++
            }
        }
        return VeilStats(argb.size, hidden, black, partial, open)
    }

    fun percent(value: Double) = String.format(Locale.ROOT, "%.1f%%", value * 100)

    /**
     * The image, described as the thing that either hides the map or does not.
     *
     * `其中纯黑` next to `不透光` is deliberate: a veil at alpha 255 in navy hides just as well but
     * is not what was asked for, and a single "opaque" number could not tell the two apart.
     */
    fun image(stats: VeilStats, revision: Int): String =
        "雾位图=${stats.pixels}像素 不透光=${percent(stats.hiddenShare)} " +
            "其中纯黑=${percent(stats.blackShare)} 半透=${percent(stats.partialShare)} " +
            "透出地面=${percent(stats.openShare)} 版本=$revision"

    /**
     * The frame, and what it is able to show.
     *
     * [TrailPainter.pixelsPerMeter] is the veil image's own resolution, and it is the fact that
     * decides whether the cleared corridor exists inside the image at all. On the wide frame drawn
     * before the map has reported a view, thirty metres is a small fraction of one pixel: nothing
     * can be revealed in that image, and the walked line has to be drawn by the map instead. That is
     * why the person saw the fog arrive only after zooming in, and it is a number, not a hunch.
     */
    fun frame(frame: PaintFrame?, lineByMap: Boolean): String {
        if (frame == null) return "雾帧=尚未生成（还没有定位，或地图引擎还没报告视图）"
        val perMetre = TrailPainter.pixelsPerMeter(frame)
        val raw = TrailPaint.CORRIDOR_M * perMetre
        val drawn = raw
        val floor = if (!TrailPaint.corridorVisible(frame)) "（这一帧太宽，画不出走廊；雾层保持不透光，路线用线条显示）" else ""
        return String.format(Locale.ROOT,
            "雾帧=%dx%d像素 覆盖纬度%.5f° 经度%.5f° 每米=%.3f像素 走廊=%.1f像素%s 地图画行走线=%s",
            frame.widthPx, frame.heightPx, frame.latSpan, frame.lonSpan, perMetre, drawn, floor,
            if (lineByMap) "是" else "否")
    }

    /**
     * Why this engine, and which switch turned it on.
     *
     * The most common reason for the phone and the record disagreeing is that the phone was never
     * running the engine the record was written against. `高德只提供 ARM 库` is not a defect in the
     * app. An ARM64 artifact may use a declared native bridge; the actual engine is read back.
     */
    fun engineChoice(supported: Boolean, firstAbi: String, keyConfigured: Boolean,
                     consent: Boolean, active: Boolean): String = buildString {
        append("引擎判定=")
        append(if (active) "正在用高德" else "没有用高德")
        append(" 本机首选ABI=").append(firstAbi.ifBlank { "未知" })
        append(" 高德支持本机=").append(if (supported) "是" else "否（高德只提供 ARM 库）")
        append(" 高德Key=").append(if (keyConfigured) "已配置" else "未配置")
        append(" 高德隐私同意=").append(if (consent) "是" else "否")
    }
}
