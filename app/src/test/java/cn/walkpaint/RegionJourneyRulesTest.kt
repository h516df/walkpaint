package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

class RegionJourneyRulesTest {
    @Test fun synchronousSuccessWithoutExceptionIsAcceptedButEmptyOrFailedResultsAreNot() {
        assertTrue(RegionJourneyRules.querySucceeded(null,1));assertTrue(RegionJourneyRules.querySucceeded(1000,1))
        assertFalse(RegionJourneyRules.querySucceeded(null,0));assertFalse(RegionJourneyRules.querySucceeded(1000,0))
        assertFalse(RegionJourneyRules.querySucceeded(1802,1));assertFalse(RegionJourneyRules.querySucceeded(1001,1))
    }
    private val start=LocalDate.of(2026,1,1)
    private val province=JourneyRegion("410000","河南省","province",18)
    private val city=JourneyRegion("410100","郑州市","city",12)
    private fun place(n:Int)=JourneyPlace(JourneyRegion("4101${n.toString().padStart(2,'0')}","区县$n","district"),city,province)
    private fun sample(day:Int,n:Int=0)=JourneySample("$day-$n",TrackPoint(34.7,113.6+n*.01,start.plusDays(day.toLong()).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli(),5f),start.plusDays(day.toLong()))
    @Test fun duplicatePointsTracksAndSameDayDoNotEarnExtraDays() {
        val samples=listOf(sample(0),sample(0,1),sample(1))
        val result=RegionJourneyRules.visits(samples,samples.associate{it.key to place(1)})
        assertEquals(3,result.size);assertTrue(result.all{it.days.size==2})
        assertEquals(1,result.first{it.region.level=="city"}.children.size)
        assertTrue(result.all{it.stage=="初临"})
    }
    @Test fun oneDayDrivingCannotUnlockLongTermBadgesOrCoverage() {
        val samples=(1..50).map{sample(0,it)}
        val visits=RegionJourneyRules.visits(samples,samples.mapIndexed{n,s->s.key to place(n)}.toMap())
        assertTrue(RegionJourneyRules.badges(visits).none{it.reached})
        assertTrue(RegionJourneyRules.coverageBadges(visits.first{it.region.level=="district"},100.0).none{it.reached})
    }
    @Test fun sevenActualDaysAndThreeDistinctCountiesUnlockOnlyFirstCountyCollection() {
        val samples=(0..6).map{sample(it)}
        val visits=RegionJourneyRules.visits(samples,samples.mapIndexed{n,s->s.key to place(n%3)}.toMap())
        val unlocked=RegionJourneyRules.badges(visits).filter{it.reached}
        assertEquals(listOf("region_district_3"),unlocked.map{it.id})
        val saved=GrowthRules.unlocked(emptyMap(),unlocked,123)
        assertEquals(saved,GrowthRules.unlocked(saved,unlocked,999))
    }
    @Test fun unknownBoundaryNeverUnlocksAreaEvenAfterOneHundredDays() {
        val visit=RegionVisit(place(1).county,(0..100).map{start.plusDays(it.toLong())}.toSet(),emptySet())
        assertTrue(RegionJourneyRules.coverageBadges(visit,null).none{it.reached})
        assertEquals(3,RegionJourneyRules.coverageBadges(visit,5.0).count{it.reached})
        assertEquals(1,RegionJourneyRules.coverageBadges(visit,.5).count{it.reached})
    }
    @Test fun parentStagesNeedBothDaysAndActuallyVisitedChildren() {
        val days=(0..100).map{start.plusDays(it.toLong())}.toSet()
        assertEquals("初临",RegionVisit(city,days,setOf("410101")).stage)
        assertEquals("寻迹",RegionVisit(city,days,setOf("410101","410102")).stage)
        assertEquals("深游",RegionVisit(city,days,(1..5).map{"$it"}.toSet()).stage)
    }
    @Test fun unknownHierarchySizeDoesNotUnlockParentStage() {
        val days=(0..100).map{start.plusDays(it.toLong())}.toSet()
        assertEquals("初临",RegionVisit(city.copy(childCount=0),days,(1..20).map{"$it"}.toSet()).stage)
    }
    @Test fun municipalityDoesNotBecomeDeeplyExploredFromOneCountyAlone() {
        val days=(0..100).map{start.plusDays(it.toLong())}.toSet()
        val beijing=JourneyRegion("110000","北京市","province",1,16)
        assertEquals("初临",RegionVisit(beijing,days,setOf("110100"),1,0,1).stage)
        assertEquals("深游",RegionVisit(beijing,days,setOf("110100"),1,0,5).stage)
        assertEquals("长游",RegionVisit(beijing,days,setOf("110100"),1,0,10).stage)
    }
    @Test fun municipalityAndDirectAdministeredCountyUseActualHierarchy() {
        val municipality=JSONObject("""{"code":"110000","name":"北京市","level":"province","children":[{"code":"110100","name":"北京市","level":"city","children":[{"code":"110101","name":"东城区","level":"district"}]}]}""")
        val result=RegionJourneyStore.placeInTree(municipality,"110101")!!
        assertEquals("110100",result.city!!.code);assertEquals("110000",result.province.code)
        val direct=JSONObject("""{"code":"410000","name":"河南省","level":"province","children":[{"code":"419001","name":"济源市","level":"district"}]}""")
        val county=RegionJourneyStore.placeInTree(direct,"419001")!!
        assertNull(county.city);assertEquals("419001",county.county.code)
        assertNull(RegionJourneyStore.placeInTree(direct,"410101"))
        assertEquals(county,RegionJourneyStore.decodePlace(RegionJourneyStore.encodePlace(county)))
    }
    @Test fun demosAndUnresolvedSamplesNeverBecomeRegionsAndDeletedTracksAreExcluded() {
        val p=sample(0).point
        assertTrue(RegionJourneyRules.samples(TrackState(listOf(Track("demo",p.time,true,listOf(p))))).isEmpty())
        val real=Track("real",p.time,false,listOf(p,p))
        assertEquals(1,RegionJourneyRules.samples(TrackState(listOf(real,real))).size)
        assertTrue(RegionJourneyRules.visits(listOf(sample(0)),emptyMap()).isEmpty())
        assertTrue(RegionJourneyRules.visits(emptyList(),mapOf(sample(0).key to place(1))).isEmpty())
    }
    @Test fun segmentEndpointsAndNewRecordedDaysSurviveSampling() {
        val a=sample(0).point;val b=a.copy(lon=a.lon+.0001,segment=1);val c=sample(1).point
        assertEquals(3,RegionJourneyRules.samples(TrackState(listOf(Track("a",a.time,false,listOf(a,b,c))))).size)
    }
}
