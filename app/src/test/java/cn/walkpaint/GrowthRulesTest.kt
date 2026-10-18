package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
class GrowthRulesTest {
 private fun badge(value:Long)=GrowthRules.badges(listOf(GrowthMetric("saved","记录",value,listOf(1,10),listOf("初见","十份"),"份")))
 @Test fun awardsAreIdempotentAndKeepOriginalDate(){val first=GrowthRules.unlocked(emptyMap(),badge(10),123);assertEquals(first,GrowthRules.unlocked(first,badge(10),999));assertEquals(123L,first["saved_1"])}
 @Test fun removingContentDoesNotRevokeEarnedBadge(){val first=GrowthRules.unlocked(emptyMap(),badge(10),123);assertEquals(first,GrowthRules.unlocked(first,badge(0),999));assertEquals(2,first.size)}
 @Test fun missedTodayKeepsYesterdayStreakButGapBreaksIt(){val today=LocalDate.of(2026,10,18);val days=setOf(today.minusDays(1),today.minusDays(2),today.minusDays(4));assertEquals(2,GrowthRules.streak(days,today));assertEquals(0,GrowthRules.streak(days,today.plusDays(1)))}
 @Test fun mondayAndYearBoundaryResetWeek(){assertEquals(LocalDate.of(2025,12,29),GrowthRules.weekStart(LocalDate.of(2026,1,1)));assertEquals(LocalDate.of(2026,1,5),GrowthRules.weekStart(LocalDate.of(2026,1,5)))}
 @Test fun progressIsBoundedAndLevelsAdvanceOnlyWithEarnedPoints(){assertEquals(0f,badge(-2).first().progress);assertEquals(1f,badge(100).first().progress);assertEquals(1,GrowthRules.level(99));assertEquals(2,GrowthRules.level(100))}
}
