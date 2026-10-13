package cn.walkpaint

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Presentation only. Original trip IDs/rows and gap boundaries are never rewritten. */
internal data class DailyFootprint(val key: String, val tracks: List<Track>) {
    val points get() = tracks.sumOf { it.points.size }
    val meters get() = tracks.sumOf { Geo.distance(it.points) }
    val segments get() = tracks.sumOf { it.points.map { p -> p.segment }.distinct().size }
    fun focusTrack(): Track {
        var segment=0
        val points=tracks.sortedBy { it.points.first().time }.flatMap { trip ->
            trip.points.groupBy { it.segment }.values.flatMap { original ->
                val number=segment++
                original.map { it.copy(segment=number) }
            }
        }
        return Track("day:$key",points.minOf { it.time },false,points)
    }
}
internal object DailyFootprints {
    fun key(at: Long) = SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(Date(at))
    fun days(state: TrackState): List<DailyFootprint> {
        val groups=linkedMapOf<String,MutableList<Track>>()
        state.realTracks.forEach { trip ->
            trip.points.sortedBy { it.time }.groupBy { key(it.time) }.forEach { (day,points) ->
                groups.getOrPut(day) { mutableListOf() }.add(trip.copy(started=points.first().time,points=points))
            }
        }
        return groups.entries.sortedByDescending { it.key }.map { DailyFootprint(it.key,it.value) }
    }
    fun select(state: TrackState, day: String?): TrackState =
        if(day==null) state else TrackState(days(state).firstOrNull { it.key==day }?.tracks.orEmpty())
}
