package cn.walkpaint

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import java.io.File
import org.json.JSONObject

/** Real two-finger touch events. Host must verify the dedicated AVD before invoking. */
internal object AmapGestureProbe {
    private var transientSamples=0
    private var minimumUnknownBlack=1.0
    fun run(instrumentation: Instrumentation, direction: String): String {
        require(direction == "in" || direction == "out")
        transientSamples=0;minimumUnknownBlack=1.0
        val context = instrumentation.targetContext
        instrumentation.startActivitySync(Intent(context,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun report(): String {
            instrumentation.runOnMainSync {
                context.startActivity(Intent(context,MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("cn.walkpaint.extra.EXPORT_FIELD_REPORT",true))
            }
            Thread.sleep(1800)
            return File(context.getExternalFilesDir(null),"field-report.txt").readText()
        }
        fun zoom(text: String): Double = Regex("缩放=([0-9.]+)").find(text)?.groupValues?.get(1)?.toDouble()
            ?: error("Camera did not report zoom")
        var text = report()
        repeat(12) { if(zoom(text)<16.5) text=report() }
        check(text.contains("引擎判定=正在用高德") && text.contains("地图加载完成=true"))
        val before=zoom(text)
        check(before>=16.5) { "Street-level scene did not settle" }
        fun shot(name:String) {
            val bitmap=instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
            try { File(context.getExternalFilesDir(null),name).outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG,90,it))
            } } finally {bitmap.recycle()}
        }
        shot("v043-gesture-street.png")
        repeat(4) { pinch(instrumentation,"out");Thread.sleep(1400) }
        val wide=zoom(report())
        check(wide<before-3.0) { "Actual camera did not zoom out" }
        shot("v043-gesture-wide.png")
        repeat(4) { pinch(instrumentation,"in");Thread.sleep(1400) }
        val restored=zoom(report())
        check(restored>wide+3.0) { "Actual camera did not zoom in" }
        shot("v043-gesture-return.png")
        return "AMAP_GESTURE_PASS: "+JSONObject().put("street_zoom",before)
            .put("wide_zoom",wide).put("returned_zoom",restored)
            .put("transient_samples",transientSamples).put("minimum_unknown_black",minimumUnknownBlack).toString()
    }

    private fun pinch(instrumentation:Instrumentation,direction:String) {
        val metrics = instrumentation.targetContext.resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val y = metrics.heightPixels * 0.38f
        val begin = if (direction == "out") metrics.widthPixels * 0.28f else metrics.widthPixels * 0.07f
        val end = if (direction == "out") metrics.widthPixels * 0.07f else metrics.widthPixels * 0.28f
        val properties = Array(2) { index -> MotionEvent.PointerProperties().apply {
            id = index; toolType = MotionEvent.TOOL_TYPE_FINGER
        } }
        val down = SystemClock.uptimeMillis()
        fun event(action: Int, span: Float, count: Int) {
            val coordinates = Array(count) { index -> MotionEvent.PointerCoords().apply {
                this.x = x + (if (index == 0) -span else span)
                this.y = y; pressure = 1f; size = 1f
            } }
            val motion = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, count,
                properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { check(instrumentation.uiAutomation.injectInputEvent(motion, true)) }
            finally { motion.recycle() }
        }
        event(MotionEvent.ACTION_DOWN, begin, 1)
        event(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8), begin, 2)
        for (step in 1..30) {
            SystemClock.sleep(20)
            event(MotionEvent.ACTION_MOVE, begin + (end - begin) * step / 30f, 2)
            if(step%10==0) {
                val image=instrumentation.uiAutomation.takeScreenshot() ?: error("Transient screenshot unavailable")
                try {
                    transientSamples++
                    val sites=listOf(.08f to .18f,.90f to .18f,.08f to .84f)
                    val fractions=sites.map { (fx,fy) ->
                        val cx=(image.width*fx).toInt();val cy=(image.height*fy).toInt()
                        var black=0
                        for(dx in -4..4) for(dy in -4..4) {
                            val pixel=image.getPixel(cx+dx,cy+dy)
                            if(android.graphics.Color.red(pixel)<12 && android.graphics.Color.green(pixel)<12 && android.graphics.Color.blue(pixel)<12) black++
                        }
                        black/81.0
                    }
                    minimumUnknownBlack=minOf(minimumUnknownBlack,fractions.min())
                    File(instrumentation.targetContext.getExternalFilesDir(null),"v044-transient-$transientSamples.png").outputStream().use {
                        check(image.compress(Bitmap.CompressFormat.PNG,90,it))
                    }
                    check(fractions.all { it>.90 }) { "Unknown map exposed during $direction gesture: $fractions" }
                } finally { image.recycle() }
            }
        }
        event(MotionEvent.ACTION_POINTER_UP or (1 shl 8), end, 2)
        event(MotionEvent.ACTION_UP, end, 1)
    }
}
