package cn.walkpaint

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** Native interaction with synthetic routes; no network, map SDK, or private device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=WalkPaintApp::class,qualifiers="zh-rCN-w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuityUiTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val month=YearMonth.now().minusMonths(2)
    private val state=TrackState((1..8).map {day->
        val time=month.atDay(day).atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        Track("synthetic-$day",time,false,listOf(TrackPoint(0.0,0.0,time,5f),TrackPoint(0.0,0.0001,time+10000,5f)))
    })
    private fun shot(name:String) {
        val path=File("build/outputs/continuity-ui/$name.png");path.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(path.absolutePath)
    }
    @Composable private fun shell() {
        var tab by rememberSaveable {mutableIntStateOf(1)}
        WalkPaintTheme(1) {
            AppShell(selectedTab=tab,onTab={tab=it},state=state,fix=null,
                automatic=false,running=false,trackingStatus="等待定位",recordingTitle="等待定位",recordingHealthy=false,
                profileNotice=null,onResolveTracking={},mapNotice=null,selectedDay=null,
                mapRoadState=RoadState(),dailyRoads=emptyMap(),roadAreaHint="",onShowAll={},needsBackground=false,
                onLocate={},onZoom={},onToggleTracking={},onDemo={},onTrip={tab=0},onExport={},onImport={},
                onBackgroundSettings={},onPowerSettings={},onRevoke={},onCopyDiagnostics={},internalBackupCount=0,
                mapProviderLabel="合成地图",onChooseAmap={},roadState=RoadState(),roadColor=VisualSkin.CITY.paint,
                onRoadPack={},onRoadAppearance={},appearanceMode=1,onAppearanceMode={},onShowIntro={},feedbackContent={},
                mapContent={Text("合成地图占位")},updatesContent={})
        }
    }
    private fun pickHistoryMonth() {
        compose.onNodeWithContentDescription("选择月份").performClick()
        compose.onNodeWithText("${month.year}年${month.monthValue}月").performClick()
    }
    @Test fun mapAndTabRoundTripRetainsDetailAndOriginalListScroll() {
        compose.setContent {shell()}
        pickHistoryMonth()
        compose.onNodeWithTag("footprint-history").performScrollToNode(hasText(month.atDay(1).toString()))
        compose.onNodeWithText(month.atDay(1).toString()).performClick()
        compose.onNodeWithContentDescription("更早足迹").assertIsNotEnabled()
        compose.onNodeWithContentDescription("更晚足迹").performClick()
        compose.onNodeWithText(month.atDay(2).toString()).assertIsDisplayed()
        compose.onNodeWithContentDescription("更早足迹").performClick()
        compose.onNodeWithText("在地图查看这天的足迹").performScrollTo().performClick()
        compose.onNodeWithText("足迹").performClick()
        compose.onNodeWithText(month.atDay(1).toString()).performScrollTo().assertIsDisplayed()
        compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithText(month.atDay(1).toString()).assertIsDisplayed()
        shot("returned-list-position")
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("足迹").performClick()
        compose.onNodeWithText(month.atDay(1).toString()).assertIsDisplayed()
    }
    @Test fun searchMonthAndListSurviveOtherTabAndSavedStateRestore() {
        val restoration=StateRestorationTester(compose)
        restoration.setContent {shell()}
        pickHistoryMonth()
        compose.onNodeWithText("查找").performClick()
        compose.onNodeWithText("日期、标题、备注或标签").performTextInput(month.atDay(3).toString())
        compose.waitUntil(10000) {compose.onAllNodesWithText("全部足迹中的结果 · 1 天").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("我的").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("足迹").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains(month.atDay(3).toString())
        compose.waitUntil(10000) {compose.onAllNodesWithText("全部足迹中的结果 · 1 天").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("footprint-history").performScrollToIndex(0)
        compose.onNodeWithText("收起查找").performClick()
        compose.onNodeWithText("${month.year}年${month.monthValue}月 ▾").assertIsDisplayed()
    }
    @Test @Config(qualifiers="zh-rCN-w320dp-h640dp-xhdpi") fun emptyMonthJumpsToLatestAndDayButtonsFitLargeText() {
        RuntimeEnvironment.setFontScale(1.5f)
        compose.setContent {WalkPaintTheme(1) {Surface {DesignTripsPage(state,VisualSkin.CITY.paint,{}, {})}}}
        compose.onNodeWithTag("footprint-history").performScrollToNode(hasText("查看最近足迹"))
        compose.onNodeWithText("查看最近足迹").performClick()
        compose.onNodeWithText(month.atDay(8).toString()).assertIsDisplayed()
        compose.onNodeWithContentDescription("更晚足迹").assertIsNotEnabled()
        compose.onNodeWithContentDescription("更早足迹").performClick()
        compose.onNodeWithText(month.atDay(7).toString()).assertIsDisplayed()
        shot("day-navigation-320-font150")
    }
    @Test @Config(qualifiers="zh-rCN-w320dp-h640dp-xhdpi") fun retainedDownloadOffersExplicitResumeAndCancel() {
        RuntimeEnvironment.setFontScale(1.5f)
        val release=CurrentRelease("0.70",78,listOf("说明" to listOf("合成更新说明")))
        val candidate=UpdateCandidate(CurrentRelease("0.71",79,emptyList()),UpdateAsset(1,4L*1024*1024,"a".repeat(64),"WalkPaint-v0.71-arm64.apk"))
        var resumes=0;var cancels=0
        var ui by mutableStateOf(PrivateUpdateState(message="网络暂时不可用",candidate=candidate,resumableBytes=3L*1024*1024))
        compose.setContent {WalkPaintTheme(1) {Surface {UpdatesPage(release,ui,{}, {},{resumes++;ui=ui.copy(busy=true,progress=75)}, {},
            {cancels++;ui=ui.copy(busy=false,progress=null,resumableBytes=0)}, {}, {}, {})}}}
        compose.onNodeWithText("继续下载 · 75%").performScrollTo().assertIsDisplayed()
        shot("resume-download-320-font150")
        compose.onNodeWithText("继续下载 · 75%").performClick()
        compose.onNodeWithText("取消").performScrollTo().performClick()
        assertEquals(1,resumes);assertEquals(1,cancels)
        compose.onNodeWithText("下载新版 · 4.0 MB").performScrollTo().assertIsDisplayed()
    }
    @Test fun coldUpdaterKeepsPartialWithoutNetworkAndExplicitCancelClearsIt() {
        val app=RuntimeEnvironment.getApplication() as WalkPaintApp
        val candidate=UpdateCandidate(CurrentRelease("0.71",79,emptyList()),UpdateAsset(123,100,"a".repeat(64),"WalkPaint-v0.71-arm64.apk"))
        val cache=PendingUpdateCache(File(app.cacheDir,"updates"),BuildConfig.VERSION_CODE)
        cache.prepare(candidate);cache.partial.writeBytes(ByteArray(75))
        val updater=PrivateUpdater(app)
        try {
            assertTrue(updater.snapshot().message.contains("有未完成的下载"))
            assertFalse(updater.snapshot().busy);assertNull(updater.snapshot().candidate);assertNull(updater.snapshot().ready)
            assertEquals(75L,cache.bytes(candidate))
            updater.cancel()
            assertFalse(cache.partial.exists());assertFalse(cache.hasPartial())
        } finally {updater.close()}
    }
}
