package cn.walkpaint

import android.app.Instrumentation

/** Anonymous display-only history. SQLite and recording settings are never written. */
internal object MapJourneyFixture {
    fun <T> withFixture(i:Instrumentation,scale:Int=1,startupTimeoutSeconds:Long=20,compact:Boolean=false,freshTask:Boolean=false,block:(MainActivity)->T):T {
        require(scale in 1..2)
        val activity=UiEntry.main(i,startupTimeoutSeconds,freshTask)
        val repo=(i.targetContext.applicationContext as WalkPaintApp).repository
        check(!AmbientTracking.running) {"Stop: fixture cannot share an active recorder"}
        val cache=repo.javaClass.getDeclaredField("cached").apply {isAccessible=true}
        val revision=repo.javaClass.getDeclaredField("revision").apply {isAccessible=true}
        val original=repo.snapshot();val stamp=repo.revision
        val today=java.time.LocalDate.now().atTime(8,0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val tracks=(if(compact) listOf(0,70,144) else (0 until 145).toList()).map {segment ->
            val count=(if(segment<10) 63 else 62)*scale
            val start=today-(if(segment<70) 86_400_000L else 0L)+(segment%70)*180_000L
            val row=if(segment==144) 34 else segment%35
            val lat=39.90+row*.00045;val lon=116.39+(segment/35)*.011
            val points=(0 until count).map {n ->TrackPoint(lat+(if(n%2==0) 0.0 else 7.2/111194.9),
                lon+n*13.03/(111194.9*kotlin.math.cos(Math.toRadians(lat))),start+n*2000L,5f,segment)}
            Track("display-only-$segment",start,false,points)
        }
        val data=TrackState(tracks)
        check(data.realPointCount==(if(compact)187 else 9000)*scale)
        check(RecordedTrail.paths(data.realTracks).size==if(compact)3 else 145)
        check(data.totalMeters in (if(compact)2000.0 else 125_000.0)*scale..(if(compact)3000.0 else 140_000.0)*scale)
        synchronized(repo) {cache.set(repo,data);revision.setLong(repo,stamp+1)}
        val update=activity.javaClass.getDeclaredMethod("updateUi").apply {isAccessible=true}
        i.runOnMainSync {update.invoke(activity)}
        try {return block(activity)} finally {
            synchronized(repo) {cache.set(repo,original);revision.setLong(repo,stamp+2)}
            i.runOnMainSync {if(!activity.isDestroyed)update.invoke(activity)}
        }
    }
}
