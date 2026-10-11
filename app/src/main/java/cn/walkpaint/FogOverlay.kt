package cn.walkpaint

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

/** One viewport mask for both providers, so there is no geographic rectangular fog boundary.
 * The mask is rebuilt only after data/camera/size changes. No animation loop or stored GPS edits.
 * A single stroked path forms the union: repeated routes do not accumulate tinted rectangles.
 */
internal class FogOverlay(context: Context) : View(context) {
    var project: ((TrackPoint) -> PointF?)? = null
    var metersPerPixel: (() -> Double)? = null
    private var paths = emptyList<List<TrackPoint>>()
    private var location: TrackPoint? = null
    private var image: Bitmap? = null
    private var dirty = true
    private val reveal = Path()
    private val erase = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val texture = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x0C7295AC }
    private val density = resources.displayMetrics.density
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO; isClickable = false }
    fun setPaths(value: List<List<TrackPoint>>) { paths = value; cameraChanged() }
    fun setLocation(value: TrackPoint?) { location = value; invalidate() }
    fun cameraChanged() { dirty = true; postInvalidateOnAnimation() }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        image?.recycle(); image = null; dirty = true
    }
    override fun onDetachedFromWindow() {
        image?.recycle(); image = null; dirty = true; super.onDetachedFromWindow()
    }
    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        if (dirty) rebuild()
        image?.let { canvas.drawBitmap(it, null, Rect(0,0,width,height), imagePaint) }
        // Keep the current position visible even when it has not been recorded. It reveals no land.
        location?.let { point -> project?.invoke(point)?.let { p ->
            dot.color = 0x4054C9E2; canvas.drawCircle(p.x,p.y,15*density,dot)
            dot.color = Color.WHITE; canvas.drawCircle(p.x,p.y,6*density,dot)
            dot.color = 0xFF28B7D2.toInt(); canvas.drawCircle(p.x,p.y,4*density,dot)
        } }
    }
    private fun rebuild() {
        // Half-resolution mask limits allocation and the cost of feathering during gestures.
        val w = max(1,width/2); val h = max(1,height/2)
        val bitmap = image ?: Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also { image=it }
        bitmap.eraseColor(Color.TRANSPARENT)
        val target = Canvas(bitmap)
        target.drawColor(ExplorationFog.COLOR)
        // Quiet mist texture, drawn once per mask. No busy particles or continuous battery drain.
        for (y in -1..5) for (x in -1..4) {
            val cx=(x+0.4f+0.25f*sin(y*2.1f))*w/3f
            val cy=(y+0.2f+0.2f*cos(x*1.7f))*h/4f
            texture.shader=RadialGradient(cx,cy,w*0.6f,0x127295AC,Color.TRANSPARENT,Shader.TileMode.CLAMP)
            target.drawCircle(cx,cy,w*0.6f,texture)
        }
        val mpp=metersPerPixel?.invoke()?.takeIf { it.isFinite() && it>0 } ?: 100000.0
        val radius=(ExplorationFog.RADIUS_METERS/mpp/2).toFloat().coerceAtLeast(0.5f)
        val feather=(ExplorationFog.FEATHER_METERS/mpp/2).toFloat().coerceIn(0.5f,max(w,h).toFloat())
        reveal.reset()
        val margin=(radius+feather*3)*2
        val edges=HashSet<Pair<Long,Long>>()
        fun key(p:PointF):Long = (java.lang.Float.floatToIntBits(p.x).toLong() shl 32) xor
            (java.lang.Float.floatToIntBits(p.y).toLong() and 0xFFFFFFFFL)
        fun addEdge(a:PointF,b:PointF) {
            val ka=key(a);val kb=key(b)
            if(edges.add(minOf(ka,kb) to maxOf(ka,kb))) {
                reveal.moveTo(a.x/2,a.y/2);reveal.lineTo(b.x/2,b.y/2)
            }
        }
        paths.forEach { line ->
            val projected=line.map { project?.invoke(it) }
            if (projected.size==1) {
                projected.single()?.takeIf { it.x in -margin..width+margin && it.y in -margin..height+margin }?.let {
                    addEdge(it,PointF(it.x+0.02f,it.y))
                }
            } else projected.zipWithNext().forEach { (a,b) ->
                if(a!=null && b!=null && a.x.isFinite() && a.y.isFinite() && b.x.isFinite() && b.y.isFinite() &&
                    max(a.x,b.x)>=-margin && min(a.x,b.x)<=width+margin &&
                    max(a.y,b.y)>=-margin && min(a.y,b.y)<=height+margin) {
                    addEdge(a,b)
                }
            }
        }
        erase.strokeWidth=radius*2
        erase.maskFilter=BlurMaskFilter(feather,BlurMaskFilter.Blur.NORMAL)
        target.drawPath(reveal,erase)
        // Opaque inner core keeps revealed streets crisp; feather only belongs at the boundary.
        erase.maskFilter=null; erase.strokeWidth=max(0.5f,(radius-feather)*2)
        target.drawPath(reveal,erase)
        // Provider logo and attribution remain unobscured and operable beneath this passive view.
        val clear=Paint().apply { xfermode=PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        target.drawRoundRect(-12*density,h-18*density,85*density,h+12*density,6*density,6*density,clear)
        target.drawRoundRect(w-24*density,h-18*density,w+12*density,h+12*density,6*density,6*density,clear)
        dirty=false
    }
}
