package cn.walkpaint

/** The road list is catalog order, not the user's location or most recent walk. */
internal object RoadMapFocus {
    fun choose(state: TrackState, current: TrackPoint?, roads: RoadState): TrackPoint? {
        if(current != null) return current
        state.realTracks.mapNotNull { it.points.lastOrNull() }.maxByOrNull { it.time }?.let { return it }
        val painted=roads.intervals.firstOrNull()?.roadId
        val road=roads.roads.firstOrNull { it.stableId==painted } ?: roads.roads.firstOrNull() ?: return null
        val line=roads.coverage.highlighted(road).firstOrNull() ?: road.points
        return line.getOrNull(line.size/2)
    }
}
