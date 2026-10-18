package cn.walkpaint

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileNavigationTest {
    @Test fun directBackupReturnsToProfile() = assertEquals("我的",profileBackDestination("备份与恢复","我的"))
    @Test fun nestedBackupReturnsToSettings() = assertEquals("更多设置",profileBackDestination("备份与恢复","更多设置"))
    @Test fun settingsReturnToProfile() = assertEquals("我的",profileBackDestination("更多设置","我的"))
    @Test fun privacyKeepsItsSettingsParent() = assertEquals("更多设置",profileBackDestination("隐私与权限","我的"))
    @Test fun unknownRestoredParentFallsBackToProfile() = assertEquals("我的",profileBackDestination("备份与恢复","invalid"))
}
