package cn.walkpaint

import android.accessibilityservice.AccessibilityService
import android.app.Instrumentation
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File

/** Real native interaction on the isolated developer package, with no map consent or GPS grants. */
internal object LandscapeUiProbe {
    fun run(i:Instrumentation):String {
        check(BuildConfig.DEVELOPER_BUILD && i.targetContext.packageName=="cn.walkpaint.dev") {
            "Landscape acceptance is restricted to the isolated developer package"
        }
        check(!AmbientTracking.running) { "Do not share an active recorder" }
        val originalMode=i.targetContext.getSharedPreferences("ui_appearance",0).getInt("mode",0)
        UiEntry.main(i)
        fun nodes():List<AccessibilityNodeInfo> {
            i.uiAutomation.clearCache()
            val all=mutableListOf<AccessibilityNodeInfo>()
            fun visit(node:AccessibilityNodeInfo?) {
                if(node==null)return
                if(node.isVisibleToUser)all+=node
                repeat(node.childCount){visit(node.getChild(it))}
            }
            visit(i.uiAutomation.rootInActiveWindow)
            return all
        }
        fun find(label:String)=nodes().filter{it.text?.toString()==label || it.contentDescription?.toString()==label}
            .let{matches->matches.firstOrNull{it.isClickable} ?: matches.firstOrNull()}
        fun wait(label:String,scroll:Boolean=false):AccessibilityNodeInfo {
            val until=SystemClock.elapsedRealtime()+25000
            while(SystemClock.elapsedRealtime()<until) {
                find(label)?.let{return it}
                if(scroll)nodes().firstOrNull{it.isScrollable}?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                Thread.sleep(200)
            }
            error("Missing native control '$label': ${nodes().mapNotNull{it.text}.take(20)}")
        }
        fun tap(label:String,scroll:Boolean=false) {
            var node:AccessibilityNodeInfo?=wait(label,scroll)
            while(node!=null && !node.isClickable)node=node.parent
            check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true){"Click failed: $label"}
            Thread.sleep(350)
        }
        fun back(){check(i.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK));Thread.sleep(350)}
        fun shot(name:String) {
            i.waitForIdleSync();Thread.sleep(800)
            val bitmap=checkNotNull(i.uiAutomation.takeScreenshot())
            File(i.targetContext.getExternalFilesDir(null),"landscape-$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
            }
            bitmap.recycle()
        }
        // Dismiss onboarding with the actual opt-out control, never granting location or map consent.
        if(find("走过的路，\n在地图上慢慢涂满。")!=null)tap("先看看，暂不记录",true)
        tap("我的")
        wait("山河行记");shot("profile")
        repeat(2) {
            tap("外观与配色",true);wait("足迹颜色");back();wait("山河行记")
        }
        tap("备份与数据",true);wait("恢复备份");back();wait("山河行记")
        tap("更多设置",true);tap("隐私与权限");wait("撤回自动记录授权")
        tap("撤回自动记录授权");wait("撤回自动记录授权？");tap("取消")
        back();wait("首次使用引导");back();wait("山河行记")
        tap("山河行记");wait("山河成就");shot("journey")
        tap("说明");wait("行记说明");tap("知道了");back();wait("山河行记")
        try {
            tap("外观与配色",true);tap("外观：深色");back();wait("山河行记");shot("dark")
            tap("外观与配色",true);tap("外观：浅色");back();wait("山河行记");shot("light")
        } finally {
            tap("外观与配色",true)
            tap("外观："+when(originalMode){1->"浅色";2->"深色";else->"跟随系统"})
            back()
        }
        repeat(2){tap("足迹");wait("足迹日历",true);tap("我的");wait("山河行记")}
        tap("探索");wait("附近探索");shot("explore")
        tap("附近探索");wait("探索范围与说明",true);back()
        tap("我的");wait("山河行记")
        check(!AmbientTracking.running){"UI-only acceptance unexpectedly started recording"}
        return "LANDSCAPE_UI_PASS: native rendering, repeated navigation, cancel/back, light/dark, no recording; map network and physical device not verified"
    }
}
