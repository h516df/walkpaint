package cn.walkpaint

internal data class RestoreResult(val addedTrips:Int,val addedPoints:Int,val duplicates:Int,
                                  val conflicts:Int,val skippedPoints:Int) {
    fun message():String = "已恢复 $addedTrips 条新足迹，补入 $addedPoints 个点；重复 $duplicates 个" +
        if(conflicts+skippedPoints>0) "。冲突 $conflicts 个，另有 $skippedPoints 个关联点未合并，原记录保留" else "，原记录保留"
}

/** A restore adds observations. Existing rows and segment labels are never rewritten. */
internal object BackupMerge {
    data class Plan(val additions:List<Track>,val result:RestoreResult)
    private fun same(a:TrackPoint,b:TrackPoint)=
        a.time==b.time && a.lat==b.lat && a.lon==b.lon && a.accuracy==b.accuracy

    fun plan(existing:TrackState,incoming:TrackState,now:Long):Plan {
        incoming.tracks.filterNot {it.demo}.forEach {t ->
            require(t.id.isNotBlank() && t.id.length<=200 && t.started in 0..now+60_000) {"备份行程或时间无效，未恢复"}
            require(t.points.all {p -> p.time in 1..now+60_000 && p.segment>=0 &&
                p.lat.isFinite() && p.lat in -90.0..90.0 && p.lon.isFinite() && p.lon in -180.0..180.0 &&
                p.accuracy.isFinite() && p.accuracy>=0f}) {"备份含无效位置、未来时间或分段，未恢复"}
        }
        val tracks=existing.tracks.associateBy {it.id}.toMutableMap()
        val additions=linkedMapOf<String,Track>()
        var trips=0;var added=0;var duplicates=0;var conflicts=0;var skipped=0
        for(source in incoming.tracks.filterNot {it.demo}) {
            val old=tracks[source.id]
            if(old?.demo==true) {conflicts+=source.points.size;continue}
            val byTime=old?.points?.groupBy {it.time}?.mapValues {it.value.toMutableList()}?.toMutableMap()
                ?: mutableMapOf()
            var highest=old?.points?.maxOfOrNull {it.segment} ?: -1
            val appended=mutableListOf<TrackPoint>()
            val runs=mutableListOf<MutableList<TrackPoint>>()
            for(p in source.points.sortedBy {it.time}) {
                val run=runs.lastOrNull()
                if(run==null || !RecordedTrail.connected(run.last(),p)) runs+=mutableListOf(p)
                else run+=p
            }
            for(run in runs) {
                val matching=run.mapNotNull {p -> byTime[p.time]?.firstOrNull {same(it,p)}}
                duplicates+=matching.size
                val clashing=run.filter {p -> byTime[p.time]?.none {same(it,p)}==true}
                val missing=run.filter {it.time !in byTime}
                if(clashing.isNotEmpty()) {
                    conflicts+=clashing.size;skipped+=missing.size;continue
                }
                if(missing.isEmpty()) continue
                val anchors=matching.map {it.segment}.distinct()
                if(anchors.size>1) {conflicts+=missing.size;continue}
                val segment=when {
                    anchors.size==1 -> anchors.single()
                    old==null -> run.first().segment
                    else -> {require(highest<Int.MAX_VALUE) {"备份分段数量异常，未恢复"};highest+1}
                }
                highest=maxOf(highest,segment)
                missing.forEach {p ->
                    val stored=p.copy(segment=segment)
                    appended+=stored;byTime.getOrPut(p.time) {mutableListOf()}.add(stored)
                }
            }
            if(old==null || appended.isNotEmpty()) {
                if(old==null) trips++
                val previous=additions[source.id]
                additions[source.id]=Track(source.id,old?.started ?: source.started,false,
                    previous?.points.orEmpty()+appended)
                tracks[source.id]=Track(source.id,old?.started ?: source.started,false,
                    (old?.points.orEmpty()+appended).sortedBy {it.time})
                added+=appended.size
            }
        }
        return Plan(additions.values.toList(),RestoreResult(trips,added,duplicates,conflicts,skipped))
    }
}
