package cn.walkpaint

import android.app.Instrumentation
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Explicit synthetic administrative fixture; this never claims a successful provider response. */
internal object AdministrativeJourneyProbe {
    fun run(i:Instrumentation):String {
        val context=i.targetContext
        val activity=UiEntry.main(i)
        check(!AmbientTracking.running)
        val repo=(context.applicationContext as WalkPaintApp).repository
        val original=repo.snapshot();val stamp=repo.revision
        val cache=repo.javaClass.getDeclaredField("cached").apply{isAccessible=true}
        val revision=repo.javaClass.getDeclaredField("revision").apply{isAccessible=true}
        val index=File(context.filesDir,"region-journey-v1.json")
        val boundaryFile=File(context.cacheDir,"district-110101.json")
        val previousIndex=index.takeIf{it.exists()}?.readBytes()
        val previousBoundary=boundaryFile.takeIf{it.exists()}?.readBytes()
        val prefs=context.getSharedPreferences("growth_v1",0);val awards=prefs.all.filterKeys{it.startsWith("badge_area_")}
        val tracks=(0..30).map{day->
            val time=LocalDate.now().minusDays(day.toLong()).atTime(8,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            Track("administrative-fixture-$day",time,false,listOf(TrackPoint(39.915,116.405,time,5f)))
        }
        val state=TrackState(tracks)
        val place=JourneyPlace(JourneyRegion("110101","东城区","district"),JourneyRegion("110100","北京市","city",16),JourneyRegion("110000","北京市","province",1,16))
        val entries=JSONObject();RegionJourneyRules.samples(state).forEach{entries.put(it.key,RegionJourneyStore.encodePlace(place))}
        index.writeText(JSONObject().put("samples",entries).put("provinces",JSONObject()).toString())
        val center=RegionService.display(context,RegionPoint(39.915,116.405));val d=.001
        val ring=listOf(RegionPoint(center.lat-d,center.lon-d),RegionPoint(center.lat-d,center.lon+d),RegionPoint(center.lat+d,center.lon+d),RegionPoint(center.lat+d,center.lon-d))
        boundaryFile.writeText(RegionService.encode(RegionBoundary("东城区","110101",listOf(ring))).toString())
        fun nodes():List<AccessibilityNodeInfo>{i.uiAutomation.clearCache();val all=mutableListOf<AccessibilityNodeInfo>();fun walk(n:AccessibilityNodeInfo?){if(n==null)return;if(n.isVisibleToUser)all+=n;repeat(n.childCount){walk(n.getChild(it))}};walk(i.uiAutomation.rootInActiveWindow);return all}
        fun wait(text:String):AccessibilityNodeInfo{val end=SystemClock.uptimeMillis()+18000;while(SystemClock.uptimeMillis()<end){nodes().firstOrNull{it.text?.toString()==text || it.contentDescription?.toString()==text}?.let{return it};if(text=="星火")nodes().firstOrNull{it.isScrollable}?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);Thread.sleep(120)};error("Missing $text: ${nodes().mapNotNull{it.text}.take(20)}")}
        fun tap(text:String){var n:AccessibilityNodeInfo?=wait(text);while(n!=null&&!n.isClickable)n=n.parent;check(n?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true);Thread.sleep(250)}
        fun expandSheet(){wait("微光");Thread.sleep(700);val b=i.uiAutomation.takeScreenshot()!!;val x=b.width/2;val from=(b.height*.78).toInt();val to=(b.height*.3).toInt();b.recycle();android.os.ParcelFileDescriptor.AutoCloseInputStream(i.uiAutomation.executeShellCommand("input swipe $x $from $x $to 550")).use{it.readBytes()};Thread.sleep(700)}
        fun shot(name:String){i.waitForIdleSync();Thread.sleep(650);val b=i.uiAutomation.takeScreenshot()!!;File(context.getExternalFilesDir(null),"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
        try {
            synchronized(repo){cache.set(repo,state);revision.setLong(repo,stamp+1)}
            i.runOnMainSync{activity.javaClass.getDeclaredMethod("updateUi").apply{isAccessible=true}.invoke(activity)}
            tap("我的");tap("山河行记");wait("东城区");wait("深游");shot("administrative-counties")
            tap("城市");wait("北京市");wait("31 天足迹 · 到过 1 个区县");shot("administrative-cities")
            tap("省份");wait("北京市");wait("31 天足迹 · 到过 1 个区县");shot("administrative-provinces")
            tap("区县");tap("东城区");wait("点亮这片土地");wait("微光")
            val deadline=SystemClock.uptimeMillis()+10000
            while(prefs.getLong("badge_area_district_110101_2.0",0)==0L && SystemClock.uptimeMillis()<deadline)Thread.sleep(100)
            val earned=prefs.getLong("badge_area_district_110101_2.0",0);check(earned>0){"Real fixture area and recorded days did not unlock 星火"}
            check(prefs.getLong("badge_area_district_110101_5.0",0)==0L){"31-day fixture incorrectly earned 100-day milestone"}
            shot("administrative-detail")
            i.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);Thread.sleep(300)
            tap("东城区");expandSheet();wait("星火");shot("administrative-detail-expanded");check(prefs.getLong("badge_area_district_110101_2.0",0)==earned)
            // Expanded sheets first collapse on Back; cleanup below restores the fixture without requiring a second navigation.
            return "ADMINISTRATIVE_UI_PASS fixture=true days=31 county_city_province=true area_awards=2 repeated_award=false provider_verified=false"
        } finally {
            synchronized(repo){cache.set(repo,original);revision.setLong(repo,stamp+2)}
            i.runOnMainSync{activity.javaClass.getDeclaredMethod("updateUi").apply{isAccessible=true}.invoke(activity)}
            if(previousIndex==null)index.delete() else index.writeBytes(previousIndex)
            if(previousBoundary==null)boundaryFile.delete() else boundaryFile.writeBytes(previousBoundary)
            val edit=prefs.edit();prefs.all.keys.filter{it.startsWith("badge_area_")}.forEach{edit.remove(it)};awards.forEach{(k,v)->if(v is Long)edit.putLong(k,v)};edit.commit()
        }
    }
}

/** Real signed Android SDK request, isolated derived index. No source/config secrets in output. */
internal object RegionNetworkProbe {
    fun run(i:Instrumentation):String {
        UiEntry.main(i)
        val context=i.targetContext
        val index=File(context.filesDir,"region-journey-v1.json");val previous=index.takeIf{it.exists()}?.readBytes()
        try {
            index.delete()
            val store=RegionJourneyStore(context)
            val point=TrackPoint(39.915,116.405,System.currentTimeMillis(),5f)
            val sample=RegionJourneyRules.samples(TrackState(listOf(Track("network-only",point.time,false,listOf(point))))).single()
            val place=store.resolve(sample)
            val query=com.amap.api.services.district.DistrictSearchQuery().apply{setKeywords("110000");setKeywordsLevel("province");setShowBoundary(false);setSubDistrict(2)}
            val result=com.amap.api.services.district.DistrictSearch(context).apply{setQuery(query)}.searchDistrict()
            val boundary=place?.let{runCatching{RegionService.boundary(context,it.county.code,it.county.name)}.getOrNull()}
            return "REGION_NETWORK_RESULT "+JSONObject().put("allowed",RegionService.allowed(context)).put("county",place?.county?.name).put("countyCode",place?.county?.code)
                .put("city",place?.city?.name).put("province",place?.province?.name).put("boundaryVertices",boundary?.rings?.sumOf{it.size} ?: 0)
                .put("syncException",result.aMapException?.errorCode ?: JSONObject.NULL).put("syncDistrictCount",result.district.orEmpty().size).put("failure",store.lastFailure).put("phoneVerified",false)
        }finally{if(previous==null)index.delete() else index.writeBytes(previous)}
    }
}
