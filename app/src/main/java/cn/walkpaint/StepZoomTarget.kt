package cn.walkpaint

/** +/- requests accumulate against the requested destination while the SDK is animating.
 * A canceled animation is an intermediate frame, not the user's new destination.
 */
internal class StepZoomTarget {
    var target: Float? = null
        private set
    fun request(current: Float, delta: Int, min: Float, max: Float): Float {
        val next=((target ?: current)+delta).coerceIn(min,max)
        target=next
        return next
    }
    fun settled(current: Float) {
        if(target?.let { kotlin.math.abs(it-current)<.01f }==true) target=null
    }
    fun reset() { target=null }
}
