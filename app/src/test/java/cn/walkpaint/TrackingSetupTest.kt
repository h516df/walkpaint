package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test

class TrackingSetupTest {
    @Test fun preciseLocationAlwaysComesBeforeBackgroundAndNotifications() {
        assertEquals(TrackingSetup.Step.LOCATION, TrackingSetup.next(36, false, false, false))
    }
    @Test fun backgroundPermissionIsSeparateFromForeground() {
        assertEquals(TrackingSetup.Step.BACKGROUND, TrackingSetup.next(36, true, false, false))
        assertEquals(TrackingSetup.Step.NOTIFICATIONS, TrackingSetup.next(36, true, true, false))
    }
    @Test fun decliningBackgroundStillAllowsForegroundServiceWithoutRePrompt() {
        assertEquals(TrackingSetup.Step.NOTIFICATIONS,
            TrackingSetup.next(36, true, false, false, backgroundDeferred=true))
        assertEquals(TrackingSetup.Step.START,
            TrackingSetup.next(36, true, false, false, backgroundDeferred=true, notificationAttempted=true))
    }
    @Test fun oldAndroidDoesNotRequestUnsupportedPermissions() {
        assertEquals(TrackingSetup.Step.START, TrackingSetup.next(28, true, false, false))
        assertEquals(TrackingSetup.Step.BACKGROUND, TrackingSetup.next(29, true, false, false))
        assertEquals(TrackingSetup.Step.START, TrackingSetup.next(32, true, true, false))
    }
    @Test fun existingPermissionsStartDirectly() {
        assertEquals(TrackingSetup.Step.START, TrackingSetup.next(36, true, true, true))
    }
    @Test fun healthyRecordingDoesNotProduceStatusParagraph() {
        assertNull(TrackingSetup.notice("SAVED",true,true))
        assertNull(TrackingSetup.notice("WAITING_MOVEMENT",true,true))
    }
    @Test fun missingBackgroundAndNotificationAreReportedWithoutClaimingStopped() {
        assertEquals("需要允许后台定位",TrackingSetup.notice("SAVED",false,true))
        assertEquals("记录通知未开启",TrackingSetup.notice("SAVED",true,false))
    }
    @Test fun failuresTakePriorityOverPermissionTipsAndUsePlainLanguage() {
        assertEquals("足迹暂时无法保存",TrackingSetup.notice("SAVE_FAILED",false,false))
        assertEquals("需要允许精确定位",TrackingSetup.notice("PERMISSION",false,false))
        assertEquals("暂未收到新的定位",TrackingSetup.notice("CALLBACK_INTERRUPTED",true,true))
        assertEquals("正在等待可靠定位",TrackingSetup.notice("STARTING",true,true))
    }
}
