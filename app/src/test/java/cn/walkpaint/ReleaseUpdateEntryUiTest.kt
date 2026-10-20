package cn.walkpaint

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** Uses the actual merged asset and profile route; never contacts the network or a phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="zh-rCN-w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ReleaseUpdateEntryUiTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test fun actualBundledNotesOpenFromProfileAndCheckActionRemainsReachable() {
        val release=CurrentRelease.decode(RuntimeEnvironment.getApplication().assets.open("release-notes.json").bufferedReader().use {it.readText()})
        assertEquals(BuildConfig.VERSION_NAME,release.version)
        assertEquals(BuildConfig.VERSION_CODE,release.code)
        var checks=0
        compose.setContent {
            WalkPaintTheme(1,skin=VisualSkin.CITY) {
                AppShell(selectedTab=2,onTab={},state=TrackState(),fix=null,
                    automatic=false,running=false,trackingStatus="等待定位",recordingTitle="等待定位",recordingHealthy=false,
                    profileNotice=null,onResolveTracking={},mapNotice=null,selectedDay=null,
                    mapRoadState=RoadState(),dailyRoads=emptyMap(),roadAreaHint="",onShowAll={},needsBackground=false,
                    onLocate={},onZoom={},onToggleTracking={},onDemo={},onTrip={},onExport={},onImport={},
                    onBackgroundSettings={},onPowerSettings={},onRevoke={},onCopyDiagnostics={},internalBackupCount=0,
                    mapProviderLabel="高德地图",onChooseAmap={},roadState=RoadState(),roadColor=VisualSkin.CITY.paint,
                    onRoadPack={},onRoadAppearance={},appearanceMode=1,onAppearanceMode={},onShowIntro={},feedbackContent={},mapContent={},
                    updatesContent={back -> UpdatesPage(release,PrivateUpdateState(),back,{checks++},{},{},{},{},{},{})})
            }
        }
        compose.onNodeWithText("检查更新").performScrollTo().performClick()
        compose.onNodeWithText("当前版本 ${release.version}").assertIsDisplayed()
        compose.onNodeWithText("检查新版").assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals(1,checks)}
        compose.onNodeWithText("本版更新说明").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(release.sections.first {it.second.isNotEmpty()}.second.first()).performScrollTo().assertIsDisplayed()
        val image=File("build/outputs/release-ui/update-entry-${release.version}.png")
        image.parentFile?.mkdirs()
        compose.onRoot().captureRoboImage(image.absolutePath)
    }
}
