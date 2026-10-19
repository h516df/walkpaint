package cn.walkpaint

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** Real Compose controls and real Android document/share UIs, using display-only GPS fixtures. */
internal object JournalJourneyProbe {
    fun run(i:Instrumentation):String {
        check(BuildConfig.DEVELOPER_BUILD && i.targetContext.packageName=="cn.walkpaint.dev")
        val context=i.targetContext
        // Instrumentation starts before Application.onCreate has necessarily returned.
        i.runOnMainSync { }
        // A software AVD can exceed UiAutomation's initial connection deadline.
        var connection:UiAutomation?=null
        val connectionDeadline=SystemClock.uptimeMillis()+180000
        while(connection==null && SystemClock.uptimeMillis()<connectionDeadline) {
            connection=runCatching {i.uiAutomation}.getOrNull()
            if(connection==null)Thread.sleep(1000)
        }
        val automation=checkNotNull(connection){"UI automation did not connect"}
        fun stage(name:String){i.sendStatus(0,Bundle().apply {putString("stream","JOURNAL_STAGE $name\n")})}
        stage("automation_connected")
        val repo=(context.applicationContext as WalkPaintApp).repository
        val journalFile=File(context.cacheDir,"journal-journey-${UUID.randomUUID()}.json")
        val fixture=FootprintJournal(journalFile)
        val field=repo.javaClass.getDeclaredField("journal").apply {isAccessible=true}
        val original=field.get(repo)
        val shared=AtomicReference<Intent?>()
        val monitor=object:Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent:Intent):Instrumentation.ActivityResult? {
                if(intent.action==Intent.ACTION_CHOOSER) shared.set(intent.getParcelableExtra(Intent.EXTRA_INTENT))
                return null
            }
        }
        fun nodes():List<AccessibilityNodeInfo> {
            automation.clearCache();val result=mutableListOf<AccessibilityNodeInfo>()
            fun walk(node:AccessibilityNodeInfo?) {if(node==null)return;if(node.isVisibleToUser)result+=node;repeat(node.childCount){walk(node.getChild(it))}}
            walk(automation.rootInActiveWindow);return result
        }
        fun find(text:String)=nodes().firstOrNull {it.text?.toString()==text || it.contentDescription?.toString()==text}
        fun await(description:String,block:()->Boolean) {
            val until=SystemClock.uptimeMillis()+180000
            while(SystemClock.uptimeMillis()<until) {if(block())return;Thread.sleep(250)}
            error("Missing $description: "+nodes().mapNotNull {it.text}.take(30))
        }
        fun wait(text:String) {await(text){find(text)!=null}}
        fun tap(text:String,scroll:Boolean=false) {
            await(text){
                if(find(text)!=null)true else {if(scroll)nodes().firstOrNull {it.isScrollable}?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);false}
            };var node=find(text)
            while(node!=null && !node.isClickable)node=node.parent
            check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true) {"Not clickable: $text"}
            Thread.sleep(300)
        }
        fun top() {repeat(6){nodes().firstOrNull {it.isScrollable}?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);Thread.sleep(150)}}
        fun setField(index:Int,value:String) {
            await("editable field $index"){nodes().count {it.isEditable}>index}
            val node=nodes().filter {it.isEditable}[index]
            check(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)
            }))
            Thread.sleep(300)
        }
        val suffix=if(context.resources.configuration.fontScale>1.1f)"-large" else ""
        fun shot(name:String) {
            i.waitForIdleSync();Thread.sleep(500)
            val image=automation.takeScreenshot() ?: error("Screenshot unavailable")
            File(context.getExternalFilesDir(null),"experience-$name$suffix.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
            image.recycle()
        }
        var activity:MainActivity?=null
        var savedPath:String?=null
        i.addMonitor(monitor)
        field.set(repo,fixture)
        try {
            return MapJourneyFixture.withFixture(i,startupTimeoutSeconds=180,compact=true) {screen->
                activity=screen
                stage("fixture_ready")
                await("welcome or footprints"){find("暂不开始")!=null || find("足迹")!=null}
                if(find("暂不开始")!=null){shot("welcome");tap("暂不开始")}
                val before=DataPreservationProbe.run(i)
                val day=java.time.LocalDate.now().toString()
                tap("足迹");tap(day,scroll=true);stage("day_opened")
                tap("写备注",scroll=true);wait("记录这一天")
                setField(0,"河畔散步");setField(1,"合成验收备注");setField(2,"散步，周末")
                tap("保存");wait("河畔散步")
                tap("收藏这天");wait("已收藏")
                check(fixture.snapshot()[day]==DayMemo("河畔散步","合成验收备注",listOf("散步","周末"),true))
                check(FootprintJournal(journalFile).snapshot()==fixture.snapshot()) {"Memo was not durable"}
                stage("memo_persisted");top();shot("detail")
                tap("制作路线明信片",scroll=true);wait("$day 的路线明信片预览");shot("card-paper")
                tap("夜色");wait("$day 的路线明信片预览");shot("card-night")
                stage("postcard_preview");tap("保存图片")
                val name="walkpaint-card-${UUID.randomUUID()}.png"
                setField(0,name)
                await("system save button") {nodes().any {it.viewIdResourceName?.endsWith("action_menu_select")==true || it.text?.toString()?.equals("save",true)==true || it.text?.toString()=="保存"}}
                var saveNode=nodes().first {it.viewIdResourceName?.endsWith("action_menu_select")==true || it.text?.toString()?.equals("save",true)==true || it.text?.toString()=="保存"}
                while(!saveNode.isClickable)saveNode=saveNode.parent ?: error("No save action")
                check(saveNode.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                wait("图片已保存")
                fun shell(command:String)=android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use {it.readText()}
                val path=shell("find /sdcard/Download /sdcard/Documents -name $name -type f 2>/dev/null").lineSequence().firstOrNull {it.endsWith(name)} ?: error("Saved image missing")
                check(path.matches(Regex("[A-Za-z0-9/._-]+")));savedPath=path
                val exported=android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("cat $path")).use {BitmapFactory.decodeStream(it)} ?: error("Invalid saved PNG")
                check(exported.width==RoutePostcard.WIDTH && exported.height==RoutePostcard.HEIGHT)
                check(exported.getPixel(5,5)==CardTheme.NIGHT.background);exported.recycle()
                stage("png_saved");tap("分享图片");await("image share intent"){shared.get()!=null}
                val intent=shared.get()!!
                check(intent.action==Intent.ACTION_SEND && intent.type=="image/png")
                check(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION!=0)
                val uri=intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM) ?: error("Missing shared image")
                check(intent.clipData?.getItemAt(0)?.uri==uri)
                val image=context.contentResolver.openInputStream(uri).use {BitmapFactory.decodeStream(it)} ?: error("Shared URI unreadable")
                check(image.width==RoutePostcard.WIDTH);image.recycle()
                await("system share chooser"){nodes().any {it.packageName?.toString()=="com.android.intentresolver"}}
                stage("chooser_ready");shot("share")
                i.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);wait("路线明信片")
                tap("返回");top();wait("当天足迹");tap("返回");top()
                tap("已收藏");wait("收藏的足迹 · 1 天");wait("河畔散步");shot("collection")
                tap("查找");setField(0,"没有这个合成内容");wait("没有找到匹配的足迹，试试其他日期或标签。")
                setField(0,"#周末");wait("河畔散步");wait("查找结果 · 1 天");shot("search")
                stage("search_verified");val encoded=org.json.JSONObject(repo.exportBackup()).getJSONObject("journal")
                check(JournalCodec.decode(encoded)[day]?.note=="合成验收备注")
                check(DataPreservationProbe.run(i)==before) {"Original SQLite rows changed"}
                "JOURNAL_JOURNEY_PASS controls=true persisted=true favorite_search=true postcard_png=true share_uri=true backup_annotations=true sqlite_unchanged=true fontScale=${context.resources.configuration.fontScale}"
            }
        } catch(e:Throwable) {
            runCatching {shot("failure")}
            throw e
        } finally {
            i.removeMonitor(monitor)
            field.set(repo,original)
            i.runOnMainSync {activity?.finish()}
            journalFile.delete()
            savedPath?.let {automation.executeShellCommand("rm $it").close()}
        }
    }
}
