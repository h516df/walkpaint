package cn.walkpaint
import android.app.Instrumentation
import android.graphics.*
/** Native Canvas pixel regression, no user database, GPS or network. */
internal object FogProbe {
    fun run(i:Instrumentation):String {
        var failure:Throwable?=null
        i.runOnMainSync {
            try {
                val view=FogOverlay(i.targetContext)
                view.layout(0,0,400,400)
                view.project={p -> PointF(p.lon.toFloat(),p.lat.toFloat())}
                view.metersPerPixel={1.0}
                val route=listOf(TrackPoint(100.0,200.0,1000,5f),TrackPoint(300.0,200.0,11000,5f))
                fun render():Bitmap=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
                view.setPaths(listOf(route));val first=render()
                check(Color.alpha(first.getPixel(200,200))==0) { "revealed core must be transparent" }
                check(Color.alpha(first.getPixel(50,200))>=240) { "unexplored land must remain dark" }
                check(Color.alpha(first.getPixel(240,200)) in 10..235) { "boundary must be feathered" }
                val firstPixels=IntArray(160000);first.getPixels(firstPixels,0,400,0,0,400,400)
                view.setPaths(listOf(route,route));val repeated=render()
                val repeatedPixels=IntArray(160000);repeated.getPixels(repeatedPixels,0,400,0,0,400,400)
                check(firstPixels.contentEquals(repeatedPixels)) { "repeated route changes reveal alpha" }
                view.setPaths(emptyList());val empty=render()
                check(Color.alpha(empty.getPixel(200,200))>=240) { "cleared date/history retained old reveal" }
                view.setPaths(listOf(listOf(route.first()),listOf(route.last())));val gap=render()
                check(Color.alpha(gap.getPixel(200,200))>=240) { "disconnected endpoints bridged" }
                view.project={p -> PointF(p.lon.toFloat()+70,p.lat.toFloat())};view.cameraChanged()
                val moved=render();check(Color.alpha(moved.getPixel(270,100))<10) { "camera projection did not move reveal" }
                listOf(first,repeated,empty,gap,moved).forEach {it.recycle()}
            } catch(t:Throwable) {failure=t}
        }
        failure?.let {throw it}
        return "FOG_NATIVE_PIXELS_PASS core_clear outside_dark feather_soft repeat_identical empty_reset gap_preserved camera_moves"
    }
}
