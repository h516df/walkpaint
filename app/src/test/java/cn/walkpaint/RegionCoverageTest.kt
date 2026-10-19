package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test
class RegionCoverageTest {
 private val center=RegionPoint(0.0,0.0)
 @Test fun singleFixRevealsCircleAndDuplicateDoesNotIncreaseArea(){
  val one=RegionCoverage.measure(listOf(listOf(center)),center)
  val twice=RegionCoverage.measure(listOf(listOf(center,center),listOf(center)),center)
  assertEquals(one,twice);assertEquals(Math.PI*50*50,one.squareMeters,1600.0)
  assertEquals(Math.PI*2000*2000,one.totalSquareMeters,.1)
 }
 @Test fun disconnectedPointsDoNotFillGap(){val end=RegionPoint(0.0,1000/111194.9)
  val separate=RegionCoverage.measure(listOf(listOf(center),listOf(end)),center)
  val joined=RegionCoverage.measure(listOf(listOf(center,end)),center)
  assertTrue(joined.squareMeters>separate.squareMeters*4)
 }
 @Test fun polygonAreaAndClippingAreReal(){val span=100/111194.9
  val ring=listOf(RegionPoint(-span,-span),RegionPoint(-span,span),RegionPoint(span,span),RegionPoint(span,-span))
  val boundary=RegionBoundary("测试地区","fixture",listOf(ring))
  val result=RegionCoverage.measure(listOf(listOf(RegionPoint(1.0,1.0))),center,boundary)
  assertEquals(40000.0,result.totalSquareMeters,1.0);assertEquals(0.0,result.squareMeters,0.0)
  val inside=RegionCoverage.measure(listOf(listOf(center)),center,boundary)
  assertTrue(inside.percent in 15.0..25.0)
 }
 @Test fun demoTracksNeverEarnTravelBadges(){val state=TrackState(listOf(Track("sample",0,true,listOf(TrackPoint(0.0,0.0,1,5f),TrackPoint(0.0,.001,10001,5f)))))
  assertTrue(walkGrowth(state).metrics.all{it.value==0L})
 }
 @Test fun explorationStagesUnlockOnlyAtRealThresholds(){
  assertEquals(5,explorationStage(0.0).next)
  assertEquals("初光",explorationStage(4.999).title)
  assertEquals("寻光",explorationStage(5.0).title)
  assertEquals(50,explorationStage(20.0).next)
  assertEquals("流光",explorationStage(99.99).title)
  assertNull(explorationStage(100.0).next)
 }
}
