package cn.walkpaint

/** One raw GPS fix reduced to what a motion decision needs. */
internal data class MotionFix(val lat: Double, val lon: Double, val time: Long, val accuracy: Float)

/**
 * Decides whether the receiver is genuinely travelling. That matters because the store keeps a
 * much smaller displacement once travel is established, and the strict drift gate otherwise
 * rejects almost every step of a walk.
 *
 * Walking is the hard case: at 1.4 m/s a two-second fix moves 2.8 m, so a three-fix window
 * covers about 5.6 m. Any rule that demands more than that silently discards every walk, which
 * is exactly what the previous version did (it required more than 7 m between two fixes and
 * never fired for a pedestrian).
 */
internal object MovementCue {
    /** At or below this the receiver is treated as at rest; real receivers report small noise. */
    const val MIN_TRAVEL_MPS = 0.35
    /** Above this the displacement is large enough that the strict gate already accepts it. */
    const val FAST_TRAVEL_MPS = 12.0
    /** Accuracy accepted for the geometric cue, in metres. */
    const val MAX_CUE_ACCURACY = 15f
    /** Span covered by the three fixes used by the geometric cue. */
    const val MAX_CUE_WINDOW_MS = 30_000L
    /** Smallest meaningful step between two consecutive fixes. */
    const val MIN_STEP_M = 2.0
    /** Largest heading change still read as one continuous direction of travel. */
    const val MAX_TURN_DEG = 50.0
    /**
     * Net displacement must be most of the path walked. A real walk goes somewhere; a stationary
     * receiver wanders, so the path is long while the net displacement stays short.
     */
    const val MIN_STRAIGHTNESS = 0.75

    /** Provider Doppler speed is the most reliable motion cue when the receiver reports one. */
    fun fromProviderSpeed(hasSpeed: Boolean, speedMps: Float, hasSpeedAccuracy: Boolean, speedAccuracyMps: Float): Boolean {
        if (!hasSpeed || !speedMps.isFinite()) return false
        if (hasSpeedAccuracy && speedAccuracyMps.isFinite() && speedAccuracyMps >= 1.3f) return false
        return speedMps >= MIN_TRAVEL_MPS && speedMps <= FAST_TRAVEL_MPS
    }

    /** Three consecutive fixes that agree on one direction and speed. */
    fun fromGeometry(a: MotionFix, b: MotionFix, c: MotionFix): Boolean {
        val worst = maxOf(a.accuracy, b.accuracy, c.accuracy)
        if (!worst.isFinite() || worst > MAX_CUE_ACCURACY) return false
        if (c.time <= b.time || b.time <= a.time) return false
        if (c.time - a.time > MAX_CUE_WINDOW_MS) return false
        val seconds = (c.time - a.time) / 1000.0
        if (seconds <= 0.0) return false
        val ab = meters(a, b)
        val bc = meters(b, c)
        val net = meters(a, c)
        if (ab < MIN_STEP_M || bc < MIN_STEP_M) return false
        val speed = net / seconds
        if (speed < MIN_TRAVEL_MPS || speed > FAST_TRAVEL_MPS) return false
        if (net <= maxOf(3.5, worst * 0.5)) return false
        val path = ab + bc
        if (path <= 0.0 || net / path < MIN_STRAIGHTNESS) return false
        return turnDegrees(a, b, c) <= MAX_TURN_DEG
    }

    private fun meters(a: MotionFix, b: MotionFix) = Geo.meters(
        TrackPoint(a.lat, a.lon, a.time, a.accuracy),
        TrackPoint(b.lat, b.lon, b.time, b.accuracy),
    )

    private fun turnDegrees(a: MotionFix, b: MotionFix, c: MotionFix): Double {
        val first = bearing(a.lat, a.lon, b.lat, b.lon)
        val second = bearing(b.lat, b.lon, c.lat, c.lon)
        val delta = ((second - first + 540.0) % 360.0) - 180.0
        return kotlin.math.abs(delta)
    }

    private fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = kotlin.math.sin(dLon) * kotlin.math.cos(p2)
        val x = kotlin.math.cos(p1) * kotlin.math.sin(p2) -
            kotlin.math.sin(p1) * kotlin.math.cos(p2) * kotlin.math.cos(dLon)
        return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360.0) % 360.0
    }
}
