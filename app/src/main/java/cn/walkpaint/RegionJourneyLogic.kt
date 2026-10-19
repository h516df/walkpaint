package cn.walkpaint

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Administrative identities come from the provider hierarchy, never from a truncated adcode. */
internal data class JourneyRegion(val code:String,val name:String,val level:String,val childCount:Int=0,val countyCount:Int=0)
internal data class JourneyPlace(val county:JourneyRegion,val city:JourneyRegion?,val province:JourneyRegion) {
    val regions get()=listOfNotNull(county,city,province)
}
internal data class JourneySample(val key:String,val point:TrackPoint,val day:LocalDate)
internal data class RegionVisit(val region:JourneyRegion,val days:Set<LocalDate>,val children:Set<String>,val cityCount:Int=0,val directCountyCount:Int=0,val visitedCounties:Int=0) {
    val municipality get()=region.level=="province" && region.childCount==1 && region.countyCount>1
    val childSummary get()=if(municipality)"$visitedCounties 个区县" else if(region.level=="city")"${children.size} 个区县" else listOfNotNull(
        "$cityCount 座城".takeIf{cityCount>0},"$directCountyCount 个直属区县".takeIf{directCountyCount>0}).joinToString("、")
    val childKind get()=if(region.level=="city" || municipality)"个区县" else if(directCountyCount==0)"座城" else "座城或直属区县"
    fun nextPlaces(target:Int)=target.coerceAtMost(if(municipality)region.countyCount else region.childCount.coerceAtLeast(1))
    val stage get()=when {
        qualifies(100,10)->"长游"
        qualifies(30,5)->"深游"
        qualifies(7,2)->"寻迹"
        else->"初临"
    }
    fun qualifies(dayTarget:Int,childTarget:Int)=days.size>=dayTarget &&
        (region.level=="district" || (region.childCount>0 && children.size>=childTarget.coerceAtMost(region.childCount))) &&
        (region.level!="province" || (region.countyCount>0 && visitedCounties>=childTarget.coerceAtMost(region.countyCount)))
    val next get()=when(stage){"初临"->7 to 2;"寻迹"->30 to 5;"深游"->100 to 10;else->null}
}
internal object RegionJourneyRules {
    // Synchronous DistrictResult constructors leave the exception field null on success.
    // An asynchronous listener's 1000 convention must not reject actual synchronous data.
    fun querySucceeded(errorCode:Int?,resultCount:Int)=(errorCode==null || errorCode==1000) && resultCount>0
    /** Samples are actual saved positions. Never interpolate visits through missing GPS segments.
     * A sample every 250m plus each day/segment endpoint bounds network work; narrow crossed
     * districts may be omitted until another saved position is resolved, never invented. */
    fun samples(state:TrackState,zone:ZoneId=ZoneId.systemDefault()):List<JourneySample> {
        val result=linkedMapOf<String,JourneySample>()
        fun add(p:TrackPoint) {
            if(!p.lat.isFinite() || !p.lon.isFinite() || p.lat !in -85.0..85.0 || p.lon !in -180.0..180.0 || p.time<=0)return
            val day=Instant.ofEpochMilli(p.time).atZone(zone).toLocalDate()
            val key="${p.lat}:${p.lon}:$day"
            result.putIfAbsent(key,JourneySample(key,p,day))
        }
        state.realTracks.forEach{track->
            var previous:TrackPoint?=null
            track.points.forEachIndexed{index,p->
                val before=previous
                if(before==null || before.segment!=p.segment || Geo.meters(before,p)>=250 ||
                    Instant.ofEpochMilli(before.time).atZone(zone).toLocalDate()!=Instant.ofEpochMilli(p.time).atZone(zone).toLocalDate()) {
                    add(p);previous=p
                }
                if(index==track.points.lastIndex || track.points[index+1].segment!=p.segment)add(p)
            }
        }
        return result.values.sortedByDescending{it.point.time}
    }
    fun visits(samples:List<JourneySample>,places:Map<String,JourneyPlace>):List<RegionVisit> {
        val regions=linkedMapOf<String,JourneyRegion>()
        val days=mutableMapOf<String,MutableSet<LocalDate>>()
        val children=mutableMapOf<String,MutableSet<String>>()
        val cities=mutableMapOf<String,MutableSet<String>>()
        val directCounties=mutableMapOf<String,MutableSet<String>>()
        val countyVisits=mutableMapOf<String,MutableSet<String>>()
        samples.forEach{sample->places[sample.key]?.let{place->
            place.regions.forEach{region->
                val key="${region.level}:${region.code}"
                regions[key]=region;days.getOrPut(key){mutableSetOf()}.add(sample.day)
                val child=when(region.level){"city"->place.county.code;"province"->place.city?.code ?: place.county.code;else->null}
                if(child!=null)children.getOrPut(key){mutableSetOf()}.add(child)
                if(region.level=="province") {
                    countyVisits.getOrPut(key){mutableSetOf()}.add(place.county.code)
                    if(place.city!=null)cities.getOrPut(key){mutableSetOf()}.add(place.city.code)
                    else directCounties.getOrPut(key){mutableSetOf()}.add(place.county.code)
                }
            }
        }}
        return regions.map{(key,region)->RegionVisit(region,days.getValue(key),children[key].orEmpty(),cities[key].orEmpty().size,directCounties[key].orEmpty().size,countyVisits[key].orEmpty().size)}
            .sortedWith(compareByDescending<RegionVisit>{it.days.maxOrNull()}.thenBy{it.region.code})
    }
    /** Distinct places and distinct recorded days are both required. A one-day drive cannot finish it. */
    fun badges(visits:List<RegionVisit>):List<GrowthBadge> = listOf("district" to listOf("街巷拾光","十境寻踪","风物成集"), "city" to listOf("城际初章","十城风物","远城来信"), "province" to listOf("山河初见","四方漫记","山河长卷")).flatMap{(level,names)->
        val regions=visits.filter{it.region.level==level}
        val days=regions.flatMap{it.days}.toSet().size
        listOf(Triple(3,7,names[0]),Triple(10,30,names[1]),Triple(30,100,names[2])).map{(count,day,title)->
            GrowthBadge("region_${level}_$count",title,"到访 $count 个${levelLabel(level)} · 探索 $day 天",
                minOf(regions.size.toLong()*day,days.toLong()*count),count.toLong()*day,0)
        }
    }
    fun levelLabel(level:String)=when(level){"district"->"区县";"city"->"城市";else->"省级地区"}
    fun coverageBadges(visit:RegionVisit,percent:Double?):List<GrowthBadge> = listOf(Triple(.5,7,"微光"),Triple(2.0,30,"星火"),Triple(5.0,100,"流光")).map{(area,day,title)->
        val target=(area*100).toLong()*day
        GrowthBadge("area_${visit.region.level}_${visit.region.code}_$area",title,"点亮 $area% · 探索 $day 天",
            minOf(((percent ?: 0.0)*100).toLong()*day,visit.days.size.toLong()*(area*100).toLong()),target,0)
    }
}
