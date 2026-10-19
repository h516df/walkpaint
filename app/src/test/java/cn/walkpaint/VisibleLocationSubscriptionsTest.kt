package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test

class VisibleLocationSubscriptionsTest {
    private val all=setOf("gps","network")
    @Test fun existingDisabledProvidersAreSubscribedWithoutAnEnabledGate() {
        val requested=mutableListOf<VisibleLocationSubscriptions.Request>()
        VisibleLocationSubscriptions().refresh(true,true,all,requested::add,{})
        assertEquals(listOf("gps","network"),requested.map {it.provider})
        assertEquals(listOf(2_000L,20_000L),requested.map {it.intervalMillis})
        assertEquals(listOf(0f,8f),requested.map {it.distanceMeters})
    }
    @Test fun repeatedForegroundCallsDoNotRegisterAgain() {
        val subscriptions=VisibleLocationSubscriptions();val calls=mutableListOf<String>()
        repeat(4){subscriptions.refresh(true,true,all,{calls+=it.provider},{fail("unexpected removal")})}
        assertEquals(listOf("gps","network"),calls)
    }
    @Test fun failingGpsDoesNotBlockNetworkAndOnlyFailureRetries() {
        val subscriptions=VisibleLocationSubscriptions();val calls=mutableListOf<String>()
        repeat(2){subscriptions.refresh(true,true,all,{calls+=it.provider;if(it.provider=="gps")error("GPS unavailable")},{})}
        assertEquals(listOf("gps","network","gps"),calls)
    }
    @Test fun pauseRemovesOnceAndResumeRegistersAgain() {
        val subscriptions=VisibleLocationSubscriptions();var calls=0;var removals=0
        subscriptions.refresh(true,true,all,{calls++},{removals++})
        subscriptions.stop {removals++};subscriptions.stop {removals++}
        assertEquals(1,removals)
        subscriptions.refresh(true,true,all,{calls++},{removals++})
        assertEquals(4,calls)
    }
    @Test fun deniedOrBackgroundNeverRegistersAndRevocationRemoves() {
        val subscriptions=VisibleLocationSubscriptions();var calls=0;var removals=0
        subscriptions.refresh(true,false,all,{calls++},{removals++})
        subscriptions.refresh(false,true,all,{calls++},{removals++})
        assertEquals(0,calls)
        subscriptions.refresh(true,true,all,{calls++},{removals++})
        subscriptions.refresh(true,false,all,{calls++},{removals++})
        assertEquals(2,calls);assertEquals(1,removals)
        subscriptions.refresh(true,true,all,{calls++},{removals++})
        subscriptions.refresh(false,true,all,{calls++},{removals++})
        assertEquals(4,calls);assertEquals(2,removals)
    }
    @Test fun providerAdditionAndRemovalAreReconciled() {
        val subscriptions=VisibleLocationSubscriptions();val calls=mutableListOf<String>();var removals=0
        subscriptions.refresh(true,true,setOf("gps"),{calls+=it.provider},{removals++})
        subscriptions.refresh(true,true,all,{calls+=it.provider},{removals++})
        assertEquals(listOf("gps","network"),calls);assertEquals(0,removals)
        subscriptions.refresh(true,true,setOf("network"),{calls+=it.provider},{removals++})
        assertEquals(listOf("gps","network","network"),calls);assertEquals(1,removals)
    }
    @Test fun failedRegistrationStillGetsLifecycleCleanupAndRemovalFailureIsContained() {
        val subscriptions=VisibleLocationSubscriptions();var removals=0;var calls=0
        subscriptions.refresh(true,true,all,{throw SecurityException("permission changed")},{})
        subscriptions.stop {removals++;throw SecurityException("permission revoked")}
        subscriptions.stop {removals++}
        assertEquals(1,removals)
        subscriptions.refresh(true,true,all,{calls++},{})
        assertEquals(2,calls)
    }
    @Test fun absentProvidersDoNotCreateSubscriptions() {
        var called=false
        VisibleLocationSubscriptions().refresh(true,true,emptySet(),{called=true},{called=true})
        assertFalse(called)
    }
}
