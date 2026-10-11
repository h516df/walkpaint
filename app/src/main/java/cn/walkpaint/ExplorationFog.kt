package cn.walkpaint

/** Display only. Persisted GPS, recording policy and progress calculations are untouched. */
internal object ExplorationFog {
    const val RADIUS_METERS = 45.0
    const val FEATHER_METERS = 18.0
    const val COLOR = 0xF2101E2B.toInt()
    fun paths(state: TrackState): List<List<TrackPoint>> = RecordedTrail.paths(state.realTracks)
}
