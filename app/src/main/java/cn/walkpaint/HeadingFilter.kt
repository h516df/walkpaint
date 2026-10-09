package cn.walkpaint

import kotlin.math.*

/** Circular smoothing, bounded turn rate and 5 Hz output. Ephemeral, never persisted. */
internal class HeadingFilter {
    private var filtered: Float? = null
    private var lastSampleAt = 0L
    private var lastOutputAt = 0L
    private var sent = 0f
    fun reset() { filtered=null;lastSampleAt=0;lastOutputAt=0 }
    fun sample(raw: Float, at: Long, reliable: Boolean = true): Float? {
        if (!reliable || !raw.isFinite()) { reset();return null }
        val previous=filtered
        if(previous!=null && at<=lastSampleAt) return null
        val next=if(previous==null) normalize(raw) else {
            val dt=(at-lastSampleAt).toFloat()
            val step=(delta(previous,raw)*(1f-exp(-dt/250f))).coerceIn(-dt*.18f,dt*.18f)
            normalize(previous+step)
        }
        filtered=next;lastSampleAt=at
        if(previous!=null && (at-lastOutputAt<200 || abs(delta(sent,next))<2f)) return null
        lastOutputAt=at;sent=next
        return next
    }
    companion object {
        fun normalize(a:Float)=((a%360f)+360f)%360f
        fun delta(from:Float,to:Float)=((normalize(to)-normalize(from)+540f)%360f)-180f
    }
}
