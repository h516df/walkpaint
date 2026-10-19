package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test

class TrackingSetupTest {
    @Test fun preciseLocationAlwaysComesBeforeBackgroundAndNotifications() {
        assertEquals(TrackingSetup.Step.LOCATION, TrackingSetup.next(36, false, false))
    }
    @Test fun backgroundPermissionIsSeparateFromForeground() {
        assertEquals(TrackingSetup.Step.BACKGROUND, TrackingSetup.next(36, true, false))
        assertEquals(TrackingSetup.Step.START, TrackingSetup.next(36, true, true))
    }
    @Test fun decliningBackgroundStillAllowsForegroundServiceWithoutRePrompt() {
        assertEquals(TrackingSetup.Step.START,
            TrackingSetup.next(36, true, false, backgroundDeferred=true))
        assertEquals(TrackingSetup.Step.START,
            TrackingSetup.next(36, true, false, backgroundDeferred=true))
    }
    @Test fun oldAndroidDoesNotRequestUnsupportedPermissions() {
        assertEquals(TrackingSetup.Step.START, TrackingSetup.next(28, true, false))
        assertEquals(TrackingSetup.Step.BACKGROUND, TrackingSetup.next(29, true, false))
        assertEquals(TrackingSetup.Step.START, TrackingSetup.next(32, true, true))
    }
    @Test fun existingPermissionsStartDirectly() {
        assertEquals(TrackingSetup.Step.START, TrackingSetup.next(36, true, true))
    }
    @Test fun healthyRecordingDoesNotProduceStatusParagraph() {
        assertNull(TrackingSetup.notice("SAVED",true))
        assertNull(TrackingSetup.notice("WAITING_MOVEMENT",true))
    }
    @Test fun optionalBackgroundPermissionDoesNotMakeWorkingRecordingLookBroken() {
        assertNull(TrackingSetup.notice("SAVED",false))
        assertNull(TrackingSetup.notice("WAITING_MOVEMENT",false))
        assertNull(TrackingSetup.notice("SAVED",true))
    }
    @Test fun firstRunWaitsOnlyForPreciseLocationAndThenStartsWithoutSettingsDetour() {
        assertEquals(TrackingSetup.Step.LOCATION,
            TrackingSetup.next(36,false,false,backgroundDeferred=true))
        assertEquals(TrackingSetup.Step.START,
            TrackingSetup.next(36,true,false,backgroundDeferred=true))
    }
    @Test fun gpsProblemsStayVisibleWithoutOptionalBackgroundAccess() {
        assertEquals("很久没收到定位了",TrackingSetup.notice("CALLBACK_INTERRUPTED",false))
        assertEquals("正在等待可靠定位",TrackingSetup.notice("INVALID_FIX",false))
    }
    @Test fun failuresTakePriorityOverPermissionTipsAndUsePlainLanguage() {
        assertEquals("足迹暂时无法保存",TrackingSetup.notice("SAVE_FAILED",false))
        assertEquals("需要允许精确定位",TrackingSetup.notice("PERMISSION",false))
        assertEquals("很久没收到定位了",TrackingSetup.notice("CALLBACK_INTERRUPTED",true))
        assertEquals("正在等待可靠定位",TrackingSetup.notice("STARTING",true))
    }
    @Test fun hidingNotificationsDoesNotHideActualGpsFailure() {
        assertEquals("很久没收到定位了",TrackingSetup.notice("CALLBACK_INTERRUPTED",true))
        assertEquals("正在等待可靠定位",TrackingSetup.notice("INVALID_FIX",true))
        assertEquals("当前无法记录",TrackingSetup.notice("REGISTER_FAILED",true))
    }
}

