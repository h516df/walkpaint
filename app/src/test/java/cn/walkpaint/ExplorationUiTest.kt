package cn.walkpaint

import android.app.Application
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** Real Compose and AndroidView rendering, synthetic map surface; NOT AMap/device acceptance. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="zh-rCN-w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ExplorationUiTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun shot(name:String) {compose.waitForIdle();val file=File("build/outputs/exploration-ui/$name.png");file.parentFile?.mkdirs();compose.onRoot().captureRoboImage(file.absolutePath)}
    @Composable private fun shell(skin:VisualSkin) {
        var tab by remember {mutableIntStateOf(0)}
        WalkPaintTheme(if(skin==VisualSkin.NIGHT)2 else 1,skin=skin) {
            AppShell(selectedTab=tab,onTab={tab=it},state=TrackState(),fix=null,
                automatic=false,running=false,trackingStatus="等待定位",recordingTitle="等待定位",recordingHealthy=false,
                profileNotice=null,onResolveTracking={},mapNotice="正在等待有效定位，已有足迹会保留",selectedDay=null,
                mapRoadState=RoadState(),dailyRoads=emptyMap(),roadAreaHint="",onShowAll={},needsBackground=false,
                onLocate={},onZoom={},onToggleTracking={},onDemo={},onTrip={},onExport={},onImport={},
                onBackgroundSettings={},onPowerSettings={},onRevoke={},onCopyDiagnostics={},internalBackupCount=0,
                mapProviderLabel="合成地图验收",onChooseAmap={},roadState=RoadState(intervals=listOf(PaintedInterval("fixture",0.0,1.0))),roadColor=skin.paint,
                onRoadPack={},onRoadAppearance={},appearanceMode=1,onAppearanceMode={},onShowIntro={},feedbackContent={},updatesContent={},
                mapContent={AndroidView(factory={context -> FrameLayout(context).apply {
                    setBackgroundColor(FogAppearance.color(skin))
                    addView(TextView(context).apply {text="合成地图版权位置 · 非真实地图";textSize=10f;setTextColor(if(skin==VisualSkin.NIGHT) android.graphics.Color.WHITE else android.graphics.Color.BLACK)},
                        FrameLayout.LayoutParams(-2,-2,android.view.Gravity.BOTTOM or android.view.Gravity.LEFT))
                }},modifier=Modifier.fillMaxSize())})
        }
    }
    @Test fun navigationAndCreditHaveSeparateSpace() {
        compose.setContent {shell(VisualSkin.CITY)}
        val credit=compose.onNodeWithText("© OpenStreetMap contributors · ODbL 1.0").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val nav=compose.onNodeWithText("探索").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Attribution must end above navigation",credit.bottom<nav.top)
        shot("map-city-360x800")
        compose.onNodeWithText("我的").performClick();compose.onNodeWithText("探索").performClick()
        compose.onNodeWithText("© OpenStreetMap contributors · ODbL 1.0").assertIsDisplayed()
    }
    @Test @Config(qualifiers="zh-rCN-w320dp-h640dp-xhdpi") fun smallScreenLargeFontNavigation() {
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent {shell(VisualSkin.CITY)}
        compose.onNodeWithText("探索").assertIsDisplayed();compose.onNodeWithText("足迹").assertIsDisplayed();compose.onNodeWithText("我的").assertIsDisplayed()
        val locate=compose.onNodeWithContentDescription("定位我").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val credit=compose.onNodeWithText("© OpenStreetMap contributors · ODbL 1.0").fetchSemanticsNode().boundsInRoot
        assertTrue("Copyright must not overlap locate",locate.bottom<=credit.top)
        val explore=compose.onNodeWithText("探索点").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val zoom=compose.onNodeWithContentDescription("放大地图").fetchSemanticsNode().boundsInRoot
        assertTrue("Discovery must not overlap zoom",explore.right<=zoom.left)
        shot("map-city-320x640-font200")
    }
    private fun inset(bottom:Int) {
        compose.runOnIdle {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(compose.activity.window,false)
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView,
                androidx.core.view.WindowInsetsCompat.Builder()
                    .setInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars(),androidx.core.graphics.Insets.of(0,0,0,bottom))
                    .setVisible(androidx.core.view.WindowInsetsCompat.Type.navigationBars(),true).build())
        }
        compose.waitForIdle()
    }
    @Test fun gestureAndThreeButtonInsetsReserveFooterSpace() {
        compose.setContent {shell(VisualSkin.CITY)}
        inset(48)
        val gesture=compose.onNodeWithText("探索").fetchSemanticsNode().boundsInRoot
        shot("map-gesture-inset")
        inset(96)
        val buttons=compose.onNodeWithText("探索").fetchSemanticsNode().boundsInRoot
        assertTrue("Larger system inset moves navigation up",buttons.bottom<gesture.bottom)
        compose.onNodeWithText("© OpenStreetMap contributors · ODbL 1.0").assertIsDisplayed()
        shot("map-three-button-inset")
    }
    @Test fun nightMapSurface() {compose.setContent {shell(VisualSkin.NIGHT)};shot("map-night")}
    @Test fun themePickerUsesHonestPreview() {compose.setContent {WalkPaintTheme(1) {SkinPicker(VisualSkin.CITY,{}, {})}};shot("theme-picker")}
    @Test @Config(qualifiers="zh-rCN-w320dp-h640dp-xhdpi") fun largeThemePickerRemainsScrollable() {
        RuntimeEnvironment.setFontScale(2f)
        var choice:VisualSkin?=null
        compose.setContent {WalkPaintTheme(1) {SkinPicker(VisualSkin.CITY,{choice=it}, {})}}
        compose.onNodeWithText("完成").assertIsDisplayed()
        compose.onNodeWithText("夜间深色").performScrollTo().assertIsDisplayed()
        shot("theme-picker-font200")
        compose.onNodeWithText("夜间深色").performClick();assertEquals(VisualSkin.NIGHT,choice)
    }
    @Test fun discoversAutomaticallyAndRetryDoesNotRecordArrival() {
        var calls=0;var selected:ExplorationLandmark?=null
        val fixture=NearbyCandidate(ExplorationLandmark("public-test","合成公园",0.001,0.0),"公园",111)
        compose.setContent {WalkPaintTheme(1) {LandmarkList(emptyList(),null,true,RegionPoint(0.0,0.0),false,
            {selected=it},{},load={_,_->calls++;if(calls==1) error("offline") else listOf(fixture)})}}
        compose.waitUntil(10000) {compose.onAllNodesWithText("重试").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("重试").performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithText("合成公园").fetchSemanticsNodes().isNotEmpty()}
        shot("nearby-results-synthetic")
        compose.onNodeWithText("合成公园").performClick()
        assertEquals(2,calls);assertNotNull(selected);assertNull(selected!!.visitedAt)
    }
    @Test fun loadingAndEmptyStateAreDistinct() {
        val gate=CompletableDeferred<List<NearbyCandidate>>()
        compose.setContent {WalkPaintTheme(1) {LandmarkList(emptyList(),null,true,RegionPoint(0.0,0.0),true,{}, {},load={_,_->gate.await()})}}
        compose.onNodeWithText("正在寻找附近值得探索的地方…").assertIsDisplayed()
        compose.runOnIdle {gate.complete(emptyList())}
        compose.waitUntil(10000) {compose.onAllNodesWithText("重新查找").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("最近足迹周围 2 公里 · 高德地点").assertIsDisplayed();shot("nearby-empty")
    }
    @Test fun firstLocationStartsOnceAndSubsequentFixesDoNotStorm() {
        var center by mutableStateOf<RegionPoint?>(null)
        val calls=java.util.concurrent.atomic.AtomicInteger(0)
        val usedCenter=java.util.concurrent.atomic.AtomicReference<RegionPoint>()
        compose.setContent {WalkPaintTheme(1) {LandmarkList(emptyList(),null,true,center,false,{}, {},load={_,p->usedCenter.set(p);calls.incrementAndGet();emptyList()})}}
        compose.onNodeWithText("正在等待定位，获得位置后会自动发现附近地点；不会猜测你的位置。").assertIsDisplayed()
        assertEquals(0,calls.get())
        compose.runOnIdle {center=RegionPoint(0.0,0.0)}
        compose.waitUntil(10000) {compose.onAllNodesWithText("重新查找").fetchSemanticsNodes().isNotEmpty()}
        compose.runOnIdle {center=RegionPoint(0.001,0.0)}
        compose.waitForIdle();assertEquals(1,calls.get())
        compose.onNodeWithText("重新查找").performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithText("重新查找").fetchSemanticsNodes().isNotEmpty() && calls.get()>=2}
        assertEquals(2,calls.get());assertEquals(RegionPoint(0.001,0.0),usedCenter.get())
    }
    @Test fun dismissWhileLoadingDoesNotReopenOrSaveAnything() {
        var open by mutableStateOf(true)
        var selected=false
        val gate=CompletableDeferred<List<NearbyCandidate>>()
        compose.setContent {WalkPaintTheme(1) {if(open) LandmarkList(emptyList(),null,true,RegionPoint(0.0,0.0),false,
            {selected=true},{open=false},load={_,_->gate.await()})}}
        compose.onNodeWithText("正在寻找附近值得探索的地方…").assertIsDisplayed()
        compose.onNodeWithText("完成").performClick()
        compose.runOnIdle {gate.complete(listOf(NearbyCandidate(ExplorationLandmark("test","不应出现",0.0,0.0),"",0)))}
        compose.onNodeWithText("附近探索点").assertDoesNotExist()
        compose.onNodeWithText("不应出现").assertDoesNotExist();assertFalse(selected)
    }
    @Test fun noMapConsentNeverQueries() {
        var calls=0
        compose.setContent {WalkPaintTheme(1) {LandmarkList(emptyList(),null,false,RegionPoint(0.0,0.0),false,{}, {},load={_,_->calls++;emptyList()})}}
        compose.onNodeWithText("开启高德地图后可自动发现附近地点。已收藏地点仍保留。").assertIsDisplayed()
        assertEquals(0,calls)
    }
    @Test fun mapDiscoveryWaitsForFixAndIgnoresJitter() {
        var center by mutableStateOf<RegionPoint?>(null)
        var calls=0;var shown=emptyList<NearbyCandidate>()
        val fixture=NearbyCandidate(ExplorationLandmark("park","合成公园",0.0,0.0),"公园",0)
        compose.setContent {NearbyMapHints(center,{shown=it},{},load={_,_->calls++;listOf(fixture)})}
        compose.waitForIdle();assertEquals(0,calls)
        compose.runOnIdle {center=RegionPoint(0.0,0.0)}
        compose.waitForIdle()
        compose.waitUntil(10000) {shown.isNotEmpty()}
        compose.runOnIdle {center=RegionPoint(.00001,0.0)}
        compose.waitForIdle();assertEquals(1,calls)
        compose.runOnIdle {center=null}
        compose.waitForIdle()
        compose.waitUntil(10000) {shown.isEmpty()}
    }
    @Test fun mapDepartureCancelsLateResults() {
        var open by mutableStateOf(true);var shown=emptyList<NearbyCandidate>()
        val gate=CompletableDeferred<List<NearbyCandidate>>()
        compose.setContent {if(open) NearbyMapHints(RegionPoint(0.0,0.0),{shown=it},{},load={_,_->gate.await()})}
        compose.waitForIdle()
        compose.runOnIdle {open=false}
        compose.waitForIdle()
        compose.runOnIdle {gate.complete(listOf(NearbyCandidate(ExplorationLandmark("late","迟到结果",0.0,0.0),"",0)))}
        compose.waitForIdle();assertTrue(shown.isEmpty())
    }
    @Test @Config(qualifiers="zh-rCN-w320dp-h640dp-xhdpi") fun longPointDetailLargeTextCanSaveWithoutArrival() {
        RuntimeEnvironment.setFontScale(2f)
        val point=ExplorationLandmark("fixture","合成的长名称文化公园与城市历史展览场馆，仅作界面验收",.001,0.0)
        var saved=false
        compose.setContent {WalkPaintTheme(1) {LandmarkDetail(point,false,null,{saved=true},{},{},RegionPoint(0.0,0.0))}}
        compose.onNodeWithText("探索点详情").assertIsDisplayed()
        compose.onNodeWithText("收藏探索点").assertIsDisplayed()
        shot("hint-detail-font200")
        compose.onNodeWithText("收藏探索点").performClick();assertTrue(saved);assertNull(point.visitedAt)
    }

    @Test fun actualHintPainterOverOpaqueFogOpensDetailAndKeepsVisitsSeparate() {
        var selected by mutableStateOf<ExplorationLandmark?>(null)
        var saved=false
        val candidate=ExplorationLandmark("public-park","合成公园",0.001,0.0)
        compose.setContent {WalkPaintTheme(1) {
            AndroidView(factory={context -> object:android.view.View(context) {
                private val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                private val d=context.resources.displayMetrics.density
                private fun hints()=ExplorationHints.select(listOf(
                    ExplorationHint(candidate,160*d,260*d,false),
                    ExplorationHint(ExplorationLandmark("saved","合成收藏",0.002,0.0),240*d,360*d,true)),
                    width.toFloat(),height.toFloat(),d,17f)
                override fun onDraw(canvas:android.graphics.Canvas) {
                    canvas.drawColor(android.graphics.Color.BLACK)
                    canvas.drawColor(FogAppearance.DEFAULT)
                    ExplorationHintPainter.draw(canvas,hints(),d,false,paint)
                }
                override fun onTouchEvent(event:android.view.MotionEvent):Boolean {
                    if(event.action==android.view.MotionEvent.ACTION_UP) selected=ExplorationHints.hit(hints(),event.x,event.y,d)
                    return true
                }
            }},modifier=Modifier.fillMaxSize().testTag("hint-map"))
            selected?.let {LandmarkDetail(it,saved,null,{saved=true;selected=null},{},{selected=null},RegionPoint(0.0,0.0))}
        }}
        shot("hints-over-opaque-fog-synthetic")
        val density=compose.activity.resources.displayMetrics.density
        compose.onNodeWithTag("hint-map").performTouchInput {click(androidx.compose.ui.geometry.Offset(160*density,260*density))}
        compose.onNodeWithText("探索点详情").assertIsDisplayed()
        compose.onNodeWithText("推荐地点 · 尚未收藏").assertIsDisplayed()
        compose.onNodeWithText("距你约 100 米 · 直线距离").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick();assertFalse(saved);assertNull(candidate.visitedAt)
        compose.onNodeWithTag("hint-map").performTouchInput {click(androidx.compose.ui.geometry.Offset(160*density,260*density))}
        compose.onNodeWithText("收藏探索点").performClick();assertTrue(saved);assertNull(candidate.visitedAt)
    }

}
