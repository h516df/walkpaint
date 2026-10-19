package cn.walkpaint

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationPermissionRecoveryTest {
    @Test fun firstRequestDoesNotMistakeAbsentRationaleForPermanentDenial() {
        assertEquals(LocationPermissionRecovery.Route.REQUEST,LocationPermissionRecovery.route(false,false))
    }
    @Test fun ordinaryDenialCanRequestAgain() {
        assertEquals(LocationPermissionRecovery.Route.REQUEST,LocationPermissionRecovery.route(true,true))
    }
    @Test fun permanentDenialUsesSettings() {
        assertEquals(LocationPermissionRecovery.Route.SETTINGS,LocationPermissionRecovery.route(true,false))
    }
    @Test fun locatingDoesNotEnableRecordingAfterSettings() {
        assertEquals(LocationPermissionRecovery.Return.LOCATE,LocationPermissionRecovery.returned(true,true,LocationPermissionRecovery.Purpose.LOCATE))
    }
    @Test fun recordingIntentRemainsDistinct() {
        assertEquals(LocationPermissionRecovery.Return.RECORD,LocationPermissionRecovery.returned(true,true,LocationPermissionRecovery.Purpose.RECORD))
    }
    @Test fun deniedOrUnrequestedReturnDoesNothing() {
        for(p in LocationPermissionRecovery.Purpose.entries) {
            assertEquals(LocationPermissionRecovery.Return.NONE,LocationPermissionRecovery.returned(true,false,p))
            assertEquals(LocationPermissionRecovery.Return.NONE,LocationPermissionRecovery.returned(false,true,p))
        }
    }
    @Test fun purposeSurvivesSavedStateRoundTrip() {
        for(p in LocationPermissionRecovery.Purpose.entries) assertEquals(p,LocationPermissionRecovery.restore(p.name))
        assertEquals(LocationPermissionRecovery.Purpose.RECORD,LocationPermissionRecovery.restore(null))
    }
}
