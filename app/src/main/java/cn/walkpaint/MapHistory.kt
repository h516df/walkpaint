package cn.walkpaint

/** One context supplies the trail, fog and anchor: today's fix cannot leak into a past day. */
internal object MapHistory {
    fun anchor(state:TrackState,day:String?):TrackPoint? = if(day==null) null else
        state.realTracks.asSequence().flatMap {it.points.asSequence()}.maxByOrNull {it.time}
}
