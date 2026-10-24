package cn.walkpaint

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.core.app.ActivityOptionsCompat
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowContentResolver

/** Native Compose behavior and real ContentResolver writes, with isolated synthetic route data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=WalkPaintApp::class,qualifiers="zh-rCN-w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class FootprintHistoryUiTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val oldMonth=java.time.YearMonth.now().minusMonths(2)
    private fun fixtures():TrackState = TrackState((1..8).map {day->
        val time=oldMonth.atDay(day).atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        Track("synthetic-$day",time,false,listOf(TrackPoint(0.0,0.0,time,5f),TrackPoint(0.0,0.0001,time+10000,5f)))
    })
    private fun image(name:String) {
        val file=File("build/outputs/history-ui/$name.png");file.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(file.absolutePath)
    }
    private fun screen(state:TrackState=fixtures()) {
        compose.setContent {WalkPaintTheme(1) {Surface {DesignTripsPage(state,VisualSkin.CITY.paint,{}, {})}}}
    }
    @Test fun historicalMonthListsMoreThanFiveDaysAndRestoresAfterRecreation() {
        val restoration=StateRestorationTester(compose)
        restoration.setContent {WalkPaintTheme(1) {Surface {DesignTripsPage(fixtures(),VisualSkin.CITY.paint,{}, {})}}}
        compose.onNodeWithContentDescription("选择月份").performClick()
        compose.onNodeWithText("${oldMonth.year}年${oldMonth.monthValue}月").performClick()
        compose.onNodeWithText("8 天出发 · 89 米").assertExists()
        image("historical-month")
        compose.onNodeWithText("这个月的足迹 · 8 天").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("footprint-history").performScrollToNode(hasText(oldMonth.atDay(1).toString()))
        compose.onNodeWithText(oldMonth.atDay(1).toString()).assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("选择月份").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("${oldMonth.year}年${oldMonth.monthValue}月 ▾").assertIsDisplayed()
        compose.onNodeWithText("统计").performClick()
        compose.onNodeWithText("${oldMonth.year}年${oldMonth.monthValue.toString().padStart(2,'0')}月").performScrollTo().assertIsDisplayed()
        image("historical-statistics")
    }
    @Test @Config(qualifiers="zh-rCN-w320dp-h640dp-xhdpi") fun smallScreenLargeFontCanOpenDayAndCancelGpxWithoutLaunchingPicker() {
        RuntimeEnvironment.setFontScale(1.5f)
        screen()
        compose.onNodeWithContentDescription("选择月份").performClick()
        compose.onNodeWithText("${oldMonth.year}年${oldMonth.monthValue}月").performClick()
        compose.onNodeWithTag("footprint-history").performScrollToNode(hasText(oldMonth.atDay(8).toString()))
        compose.onNodeWithText(oldMonth.atDay(8).toString()).performClick()
        compose.onNodeWithText("导出当天路线 GPX").performScrollTo().performClick()
        compose.onNodeWithText("选择保存位置").assertIsDisplayed()
        image("gpx-disclosure-320-font150")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("导出当天路线 GPX").assertIsDisplayed()
        assertNull(shadowOf(compose.activity).nextStartedActivityForResult)
    }
    @Test fun exportWritesOnlySelectedDayAfterPickerStateRestoreAndPreservesDatabase() {
        val app=RuntimeEnvironment.getApplication() as WalkPaintApp
        app.repository.importBackup(TrackCodec.encode(fixtures()))
        val before=app.repository.exportBackup()
        val day=DailyFootprints.days(app.repository.snapshot()).first()
        val bytes=ByteArrayOutputStream()
        val uri=Uri.parse("content://synthetic-export/day.gpx")
        shadowOf(app.contentResolver).registerOutputStream(uri,bytes)
        var request=-1
        val registry=object:ActivityResultRegistry() {
            override fun <I,O> onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?) {request=requestCode}
        }
        val owner=object:ActivityResultRegistryOwner {override val activityResultRegistry=registry}
        val restoration=StateRestorationTester(compose)
        restoration.setContent {CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
            WalkPaintTheme(1) {GpxExportButton(day)}
        }}
        compose.onNodeWithText("导出当天路线 GPX").performClick()
        compose.onNodeWithText("选择保存位置").performClick()
        assertTrue(request>=0)
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {registry.dispatchResult(request,Activity.RESULT_OK,Intent().setData(uri))}
        compose.waitUntil(10000) {compose.onAllNodesWithText("GPX 已保存").fetchSemanticsNodes().isNotEmpty()}
        val doc=javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(bytes.toByteArray().inputStream())
        assertEquals(2,doc.getElementsByTagName("trkpt").length)
        assertTrue(doc.getElementsByTagName("name").item(0).textContent.contains(day.key))
        assertEquals(before,app.repository.exportBackup())
        image("gpx-saved")
    }
    @Test fun searchStillFindsOtherMonthsAndEmptyMonthCanRecover() {
        screen()
        compose.onNodeWithText("查找").performClick()
        compose.onNodeWithText("日期、标题、备注或标签").performTextInput(oldMonth.atDay(1).toString())
        compose.waitUntil(10000) {compose.onAllNodesWithText("全部足迹中的结果 · 1 天").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("footprint-history").performScrollToNode(hasText(oldMonth.atDay(1).toString()))
        compose.onNodeWithText(oldMonth.atDay(1).toString()).assertIsDisplayed()
        compose.onNodeWithText("收起查找").performScrollTo().performClick()
        compose.onNodeWithContentDescription("上个月").performClick()
        compose.onNodeWithText("这个月还没有足迹。点月份可以跳到有记录的月份。").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("回到本月").performScrollTo().performClick()
        compose.onNodeWithContentDescription("下个月").assertIsNotEnabled()
    }
}
