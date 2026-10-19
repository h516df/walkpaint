package cn.walkpaint

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk=[34],application=Application::class,qualifiers="zh-rCN-w320dp-h900dp-xhdpi")
class LocationPermissionDialogRenderTest {
    @get:Rule val compose=createComposeRule()
    @Test fun locateLight()=capture("locate-light",1,LocationPermissionRecovery.Purpose.LOCATE)
    @Test fun locateDark()=capture("locate-dark",2,LocationPermissionRecovery.Purpose.LOCATE)
    @Test fun recordLight()=capture("record-light",1,LocationPermissionRecovery.Purpose.RECORD)
    @Test fun recordDark()=capture("record-dark",2,LocationPermissionRecovery.Purpose.RECORD)
    private fun capture(name:String,mode:Int,purpose:LocationPermissionRecovery.Purpose) {
        RuntimeEnvironment.setFontScale(1.5f)
        var settings=0;var dismiss=0
        compose.setContent {WalkPaintTheme(mode=mode) {
            LocationPermissionSettingsDialog(purpose,{dismiss++},{settings++})
        }}
        compose.waitForIdle()
        val out=File("build/outputs/location-permission-dialog/$name.png");out.parentFile.mkdirs()
        compose.onRoot().captureRoboImage(out.absolutePath)
        compose.onNodeWithText("去设置").performClick()
        compose.onNodeWithText("稍后").performClick()
        assertEquals(1,settings);assertEquals(1,dismiss)
    }
}
