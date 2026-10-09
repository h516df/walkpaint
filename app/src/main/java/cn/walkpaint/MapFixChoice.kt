package cn.walkpaint
internal data class MapFixQuality(val provider: String?, val ageMillis: Long, val accuracy: Float)
/** Display-only priority. No change to recording filters, road matching or stored coordinates. */
internal object MapFixChoice {
    fun choose(fixes: List<MapFixQuality>): Int? = fixes.indices.filter {
        val p=fixes[it]
        p.ageMillis in 0..120_000 && p.accuracy.isFinite() && p.accuracy in 1f..200f
    }.minWithOrNull(compareBy<Int> {
        val p=fixes[it]
        if(p.provider=="gps" && p.ageMillis<=45_000 && p.accuracy<=35f) 0 else 1
    }.thenBy { fixes[it].ageMillis }.thenBy { fixes[it].accuracy })
}
