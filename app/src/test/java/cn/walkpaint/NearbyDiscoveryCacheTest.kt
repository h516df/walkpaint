package cn.walkpaint

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NearbyDiscoveryCacheTest {
    private val center=RegionPoint(0.0,0.0)
    private val result=listOf(NearbyCandidate(ExplorationLandmark("park","公开夹具公园",0.0,0.0),"公园",0))
    @Test fun repeatOpenAndJitterReuseFiveMinuteCache()=runBlocking {
        var now=0L;var calls=0;val cache=NearbyDiscoveryCache {now}
        repeat(20) {assertEquals(1,cache.get(center) {calls++;result}.size)}
        now=61_000;cache.get(RegionPoint(.0001,0.0)) {calls++;result}
        assertEquals(1,calls)
        now=300_000;cache.get(center) {calls++;result};assertEquals(2,calls)
    }
    @Test fun movedLocationCannotShowOldCandidatesOrBypassRateLimit()=runBlocking {
        var now=0L;var calls=0;val cache=NearbyDiscoveryCache {now}
        cache.get(center) {calls++;result}
        try {cache.get(RegionPoint(.01,0.0)) {calls++;result};fail("new location must wait, not pretend no places exist")}
        catch(e:NearbyRefreshDeferred) {assertTrue(NearbyExplorationService.message(e).contains("最多一分钟"))}
        assertEquals(1,calls)
        now=60_000;cache.get(RegionPoint(.01,0.0)) {calls++;emptyList()};assertEquals(2,calls)
    }
    @Test fun permissionAndNetworkErrorsRemainVisibleAndThrottled()=runBlocking {
        var now=0L;var calls=0;val cache=NearbyDiscoveryCache {now}
        repeat(10) {
            try {cache.get(center) {calls++;throw IllegalStateException("permission")};fail()}
            catch(e:IllegalStateException) {assertEquals("permission",e.message)}
        }
        assertEquals(1,calls)
        now=60_000;assertEquals(result,cache.get(center) {calls++;result});assertEquals(2,calls)
    }
    @Test fun cancelledConsumerGetsNoLateResultAndConcurrentOpenDoesNotDuplicateSdkCall()=runBlocking {
        val cache=NearbyDiscoveryCache {0L};val entered=CountDownLatch(1);val release=CountDownLatch(1)
        var delivered=false;var duplicate=false
        val first=launch(Dispatchers.Default) {
            cache.get(center) {entered.countDown();check(release.await(5,TimeUnit.SECONDS));result};delivered=true
        }
        check(entered.await(5,TimeUnit.SECONDS));first.cancel()
        val second=async {cache.get(center) {duplicate=true;result}}
        release.countDown();first.join();assertEquals(result,second.await())
        assertFalse(delivered);assertFalse(duplicate)
    }
}
