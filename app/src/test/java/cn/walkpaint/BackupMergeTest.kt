package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test

class BackupMergeTest {
    private val points=(0..4).map {TrackPoint(48.8566+it*.00004,2.3522,1000+it*2000L,8f)}
    private fun state(p:List<TrackPoint>,id:String="trip")=TrackState(listOf(Track(id,1000,false,p)))
    @Test fun laterSnapshotAddsOnlyMissingPointsAndPreservesOriginalRows() {
        val p=BackupMerge.plan(state(points.take(2)),state(points),20_000)
        assertEquals(3,p.result.addedPoints);assertEquals(0,p.result.addedTrips)
        assertEquals(points.drop(2),p.additions.single().points)
        assertEquals(2,p.result.duplicates)
    }
    @Test fun olderAndRepeatedSnapshotsAreIdempotent() {
        val p=BackupMerge.plan(state(points),state(points.take(2)),20_000)
        assertEquals(0,p.result.addedPoints);assertTrue(p.additions.isEmpty())
    }
    @Test fun partialOverlapRestoresMissingInteriorPoints() {
        val p=BackupMerge.plan(state(listOf(points[0],points[2],points[4])),state(points),20_000)
        assertEquals(listOf(points[1],points[3]),p.additions.single().points)
    }
    @Test fun unanchoredImportCannotJoinAnExistingSegmentByReusingItsLabel() {
        val p=BackupMerge.plan(state(points.take(2)),state(points.drop(3)),20_000)
        assertTrue(p.additions.single().points.all {it.segment==1})
        val stored=state(points.take(2)+p.additions.single().points)
        assertEquals(0,BackupMerge.plan(stored,state(points.drop(3)),20_000).result.addedPoints)
    }
    @Test fun conflictingPositionPreservesExistingRunAndReportsAssociatedSkippedPoints() {
        val incoming=listOf(points[0],points[1].copy(lon=2.4),points[2])
        val p=BackupMerge.plan(state(points.take(2)),state(incoming),20_000)
        assertEquals(1,p.result.conflicts)
        // The large jump also breaks source continuity. No existing row is replaced.
        assertTrue(p.additions.flatMap {it.points}.none {it.time==points[1].time})
    }
    @Test fun twoConflictingExistingSegmentAnchorsAreNotJoined() {
        val old=listOf(points[0],points[2].copy(segment=1))
        val p=BackupMerge.plan(state(old),state(points.take(3)),20_000)
        assertEquals(1,p.result.conflicts);assertTrue(p.additions.isEmpty())
    }
    @Test fun duplicateTripInsideAFileDoesNotDuplicatePoints() {
        val t=state(points).tracks.single()
        val p=BackupMerge.plan(TrackState(),TrackState(listOf(t,t)),20_000)
        assertEquals(1,p.result.addedTrips);assertEquals(5,p.result.addedPoints)
        assertEquals(5,p.result.duplicates)
    }
    @Test fun invalidFuturePointRejectsEntirePlan() {
        try {BackupMerge.plan(state(points),state(listOf(points[0].copy(time=100_000))),20_000);fail()}
        catch(_:IllegalArgumentException) {}
    }
    @Test fun invalidSegmentRejectsEntirePlan() {
        try {BackupMerge.plan(TrackState(),state(listOf(points[0].copy(segment=-1))),20_000);fail()}
        catch(_:IllegalArgumentException) {}
    }
    @Test fun demoDataDoesNotBecomePersonalHistory() {
        val t=state(points).tracks.single().copy(demo=true)
        assertEquals(0,BackupMerge.plan(TrackState(),TrackState(listOf(t)),20_000).result.addedPoints)
    }
}
