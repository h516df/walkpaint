package cn.walkpaint
import android.app.Instrumentation
import android.content.Intent
/** Fixed public Paris route, only the host-guarded disposable Google AVD replica. */
internal object FogUiProbe {
    fun run(i:Instrumentation):String {
        val now=System.currentTimeMillis()-300000L
        val points=listOf(0 to 0,0 to 80,0 to 160,80 to 160,160 to 160,160 to 80,160 to 0,
            240 to 0,320 to 0,400 to 0).mapIndexed {n,(north,east) ->
            TrackPoint(48.8566+north/111195.0,2.3522+east/(111195.0*kotlin.math.cos(Math.toRadians(48.8566))),now+n*10000,5f)
        }
        FootprintDatabase(i.targetContext).use { db ->
            db.importBackup(TrackCodec.encode(TrackState(listOf(Track("fog-public-paris-v032",now,false,points)))))
        }
        val activity=i.startActivitySync(Intent(i.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Thread.sleep(2500)
        i.runOnMainSync {
            MainActivity::class.java.getDeclaredMethod("pauseMapFollowing").apply {isAccessible=true}.invoke(activity)
            MainActivity::class.java.getDeclaredMethod("focusCoordinate",Double::class.javaPrimitiveType,Double::class.javaPrimitiveType,Double::class.javaPrimitiveType)
                .apply {isAccessible=true}.invoke(activity,48.8580,2.3532,16.5)
        }
        Thread.sleep(8000)
        fun shot(name:String) {
            val image=i.uiAutomation.takeScreenshot() ?: error("no real screenshot")
            java.io.File(i.context.cacheDir,"fog-$name.png").outputStream().use {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            };image.recycle()
        }
        shot("map-public")
        // Real single-finger pan reaches the SDK through the passive fog view.
        val down=android.os.SystemClock.uptimeMillis()
        for(n in 0..10) {
            val action=when(n) {0->android.view.MotionEvent.ACTION_DOWN;10->android.view.MotionEvent.ACTION_UP;else->android.view.MotionEvent.ACTION_MOVE}
            val event=android.view.MotionEvent.obtain(down,down+n*25L,action,250f+n*12,500f+n*6,0)
            i.sendPointerSync(event);event.recycle();Thread.sleep(25)
        }
        Thread.sleep(1200);shot("pan-public")
        i.runOnMainSync {
            MainActivity::class.java.getDeclaredMethod("focusCoordinate",Double::class.javaPrimitiveType,Double::class.javaPrimitiveType,Double::class.javaPrimitiveType)
                .apply {isAccessible=true}.invoke(activity,48.8580,2.3532,17.5)
        }
        Thread.sleep(1200);shot("zoom-public")
        i.runOnMainSync {
            val map=MainActivity::class.java.getDeclaredField("mapInstance").apply {isAccessible=true}.get(activity) as org.maplibre.android.maps.MapLibreMap
            val position=org.maplibre.android.camera.CameraPosition.Builder(map.cameraPosition).bearing(45.0).build()
            map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(position))
        }
        Thread.sleep(1000);shot("rotate-public")
        return "FOG_PUBLIC_PARIS_FIXTURE_PASS preserves_previous_rows"
    }
}
