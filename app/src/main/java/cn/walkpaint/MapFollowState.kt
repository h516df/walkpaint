package cn.walkpaint

/** Display-only interaction state. No GPS recording or road matching decisions. */
internal class MapFollowState {
    enum class Mode { FOLLOWING, BROWSING, HISTORY }
    var mode = Mode.FOLLOWING
        private set
    var visible = false
    val following get() = visible && mode == Mode.FOLLOWING
    fun gesture() { if (mode != Mode.HISTORY) mode = Mode.BROWSING }
    fun history() { mode = Mode.HISTORY }
    fun locate() { mode = Mode.FOLLOWING }
    fun accepts(q: MapFixQuality): Boolean = q.accuracy.isFinite() &&
        if (q.provider == "gps") q.ageMillis in 0..45_000 && q.accuracy in 1f..35f
        else q.provider == "network" && q.ageMillis in 0..30_000 && q.accuracy in 1f..80f
}

internal object CameraFixPolicy {
    fun plausible(previous: TrackPoint?, next: TrackPoint): Boolean {
        if (previous == null) return true
        val seconds = (next.time - previous.time) / 1000.0
        if (seconds < 0) return false
        // Camera-only jump rejection, independent of the stricter recording/matcher filters.
        return Geo.meters(previous,next) <= maxOf(100.0,seconds*50.0) + previous.accuracy + next.accuracy
    }
}
