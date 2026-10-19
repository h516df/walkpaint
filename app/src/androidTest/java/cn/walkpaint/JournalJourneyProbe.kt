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
    fun run(i:Instrumentation,recreatePicker:Boolean=false):String {
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
            if(android.os.Build.VERSION.SDK_INT>=34) automation.clearCache()
            val result=mutableListOf<AccessibilityNodeInfo>()
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
        var savedUri:android.net.Uri?=null
        val documentResult=AtomicReference<android.net.Uri?>()
        var documentObserver:AutoCloseable?=null
        i.addMonitor(monitor)
        field.set(repo,fixture)
        try {
            return MapJourneyFixture.withFixture(i,startupTimeoutSeconds=600,compact=true,freshTask=true) {screen->
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
                i.runOnMainSync {documentObserver=DocumentResultCapture.install(screen,documentResult)}
                stage("postcard_preview");tap("保存图片")
                val name="walkpaint-card-${UUID.randomUUID()}.png"
                setField(0,name)
                if(recreatePicker) {
                    var stopped=false
                    await("document picker has backgrounded main") {
                        i.runOnMainSync {stopped=screen.lifecycle.currentState==androidx.lifecycle.Lifecycle.State.CREATED}
                        stopped
                    }
                    i.runOnMainSync {screen.recreate()}
                }
                fun saveAction():AccessibilityNodeInfo? {
                    val current=nodes()
                    if(current.any {it.className?.toString()?.endsWith("ProgressBar")==true}) return null
                    var node=current.firstOrNull {it.viewIdResourceName?.endsWith("action_menu_select")==true || it.text?.toString()?.equals("save",true)==true || it.text?.toString()=="保存"}
                    while(node!=null && !node.isClickable)node=node.parent
                    return node?.takeIf {it.isEnabled}
                }
                await("enabled system save action") {find("图片已保存")!=null || saveAction()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true}
                // A11y may acknowledge a click while DocumentsUI is still loading its provider.
                // Retry only its visible, enabled save control; an acknowledged action is not a save.
                var retrySaveAt=SystemClock.uptimeMillis()+5000
                await("图片已保存") {
                    if(find("图片已保存")!=null) true else {
                        if(SystemClock.uptimeMillis()>=retrySaveAt) {
                            saveAction()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            retrySaveAt=SystemClock.uptimeMillis()+5000
                        }
                        false
                    }
                }
                if(recreatePicker){await("activity recreated during picker"){screen.isDestroyed};stage("picker_recreation_verified")}
                val saved=checkNotNull(documentResult.get()){"Document result was not observed"}
                val actualName=context.contentResolver.query(saved,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use {cursor->
                    if(cursor.moveToFirst())cursor.getString(0) else null
                }
                // DocumentsProvider may add a collision suffix; keep the unique request stem bound.
                val providerName=Regex(Regex.escape(name.removeSuffix(".png"))+" \\([1-9][0-9]*\\)\\.png")
                check(actualName==name || actualName?.matches(providerName)==true){"Unexpected document name: $actualName"};savedUri=saved
                val exported=context.contentResolver.openInputStream(saved).use {BitmapFactory.decodeStream(it)} ?: error("Invalid saved PNG")
                check(exported.width==RoutePostcard.WIDTH && exported.height==RoutePostcard.HEIGHT){"Unexpected saved PNG dimensions"}
                check(exported.getPixel(5,5)==CardTheme.NIGHT.background){"Saved PNG was not the selected night theme"};exported.recycle()
                stage("png_saved");tap("分享图片");await("image share intent"){shared.get()!=null}
                val intent=shared.get()!!
                check(intent.action==Intent.ACTION_SEND && intent.type=="image/png")
                check(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION!=0)
                val uri=intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM) ?: error("Missing shared image")
                check(intent.clipData?.getItemAt(0)?.uri==uri)
                val image=context.contentResolver.openInputStream(uri).use {BitmapFactory.decodeStream(it)} ?: error("Shared URI unreadable")
                check(image.width==RoutePostcard.WIDTH);image.recycle()
                await("system share chooser") {
                    val packages=nodes().map {it.packageName?.toString()}
                    packages.contains("com.android.intentresolver") ||
                        (android.os.Build.VERSION.SDK_INT<33 && packages.contains("android") &&
                            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                                automation.executeShellCommand("dumpsys activity activities")
                            ).bufferedReader().use {it.readText().contains("com.android.internal.app.ChooserActivity")})
                }
                stage("chooser_ready");shot("share")
                i.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);wait("路线明信片")
                tap("返回");top();wait("当天足迹");tap("返回");top()
                tap("已收藏");wait("收藏的足迹 · 1 天");wait("河畔散步");shot("collection")
                tap("查找");setField(0,"没有这个合成内容");wait("没有找到匹配的足迹，试试其他日期或标签。")
                setField(0,"#周末");wait("河畔散步");wait("查找结果 · 1 天");shot("search")
                stage("search_verified");val encoded=org.json.JSONObject(repo.exportBackup()).getJSONObject("journal")
                check(JournalCodec.decode(encoded)[day]?.note=="合成验收备注")
                check(DataPreservationProbe.run(i)==before) {"Original SQLite rows changed"}
                "JOURNAL_JOURNEY_PASS controls=true persisted=true favorite_search=true postcard_png=true share_uri=true backup_annotations=true sqlite_unchanged=true fontScale=${context.resources.configuration.fontScale} picker_recreation=$recreatePicker"
            }
        } catch(e:Throwable) {
            runCatching {shot("failure")}
            throw e
        } finally {
            i.runOnMainSync {documentObserver?.close()}
            i.removeMonitor(monitor)
            field.set(repo,original)
            i.runOnMainSync {if(activity?.isDestroyed==false)activity?.finish()}
            journalFile.delete()
            savedUri?.let {runCatching {android.provider.DocumentsContract.deleteDocument(context.contentResolver,it)}}
        }
    }
}
