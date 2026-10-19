package cn.walkpaint

/**
 * 历史回放：把选中那天的原始轨迹按时间切成一段段，播放时只画到进度位置。
 *
 * 纯函数，不碰数据库、不改当天选择；调用方负责把行程按开始时间排序。进度按“一共多少个点”折算，
 * 长走和短走都走完同样的时间，但线永远画到该到的位置。一个点画不出线，所以最少先给两个点。
 */
internal fun replaySlices(trips: List<List<TrackPoint>>, progress: Float): List<List<TrackPoint>> {
    val total = trips.sumOf { it.size }
    if (total < 2) return emptyList()
    var budget = kotlin.math.ceil(total * progress.coerceIn(0f, 1f)).toInt().coerceIn(2, total)
    val drawn = ArrayList<List<TrackPoint>>()
    for (trip in trips) {
        if (budget <= 0) break
        if (trip.size <= budget) { drawn.add(trip); budget -= trip.size }
        else { drawn.add(trip.subList(0, budget)); budget = 0 }
    }
    return drawn
}
