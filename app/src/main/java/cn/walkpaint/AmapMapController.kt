package cn.walkpaint

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import android.location.LocationManager
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.CoordinateConverter
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds

/**
 * Optional official AMap renderer. Native GPS data ALWAYS stays WGS84 in SQLite/backup.
 * Conversion is performed exactly once at the display boundary.
 * Do not call create() until the user has explicitly accepted the AMap SDK privacy notice.
 */
internal class AmapMapController(private val context: Context, private val onGesture:()->Unit,
                                 private val onReady:()->Unit, private val onCamera:()->Unit,
                                 private val onCameraSettled:()->Unit) {
    private var currentView: MapView? = null
    private var map: AMap? = null
    private var latestPosition: Location? = null
    private var latestState=TrackState()
    private var initialFocusPending = true
    private val zoomSteps=StepZoomTarget()
    private fun nativeGesture() { initialFocusPending=false;pendingFocus=null;zoomSteps.reset();onGesture() }
    private var exploredColor = ExploredRoadArgb
    /** Cached route vectors are transformed with the immutable fog in one display list. */
    private var renderedPaths: List<List<TrackPoint>>?=null
    private var groundImage:Bitmap?=null
    private val fogTransform=android.graphics.Matrix()
    private val fogImagePaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val routePixels=android.graphics.Path()
    private val routeOnScreen=android.graphics.Path()
    private var routeFrame:PaintFrame?=null
    private val routePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND
    }
    private var coverageHost:android.view.View?=null
    private var coverageGuard: android.graphics.drawable.Drawable? = null
    private val guardPath = android.graphics.Path()
    private val guardPaint = Paint().apply { color = Color.BLACK }
    private var frameQueued=false
    private var cameraMovingUntil=0L
    private val choreographer by lazy { android.view.Choreographer.getInstance() }
    private val fogFrameCallback=object:android.view.Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos:Long) {
            frameQueued=false
            val view=currentView ?: return
            if(!view.isShown) return
            // Read the live SDK projection at Android's drawing cadence, including the gaps
            // between SDK camera notifications. Reuse the immutable raster and cached Path.
            coverageGuard?.invalidateSelf()
            if(gestureActive || android.os.SystemClock.uptimeMillis()<cameraMovingUntil) queueFogFrame()
        }
    }
    private fun queueFogFrame() {
        if(!frameQueued && currentView?.isShown==true) {
            frameQueued=true;choreographer.postFrameCallback(fogFrameCallback)
        }
    }
    private fun updateCoverageGuard() { coverageGuard?.invalidateSelf();queueFogFrame() }
    private fun cameraMotion() {
        cameraMovingUntil=android.os.SystemClock.uptimeMillis()+250
        updateCoverageGuard()
    }
    private fun installCoverageGuard(view: MapView) {
        val guard = object : android.graphics.drawable.Drawable() {
            override fun draw(canvas: Canvas) {
                canvas.save()
                val mapOrigin=IntArray(2);val hostOrigin=IntArray(2)
                view.getLocationOnScreen(mapOrigin);(coverageHost ?: view).getLocationOnScreen(hostOrigin)
                canvas.translate((mapOrigin[0]-hostOrigin[0]).toFloat(),(mapOrigin[1]-hostOrigin[1]).toFloat())
                val frame = groundFrame
                val image = groundImage
                val projection = map?.projection
                // Read one projection for both the hole and raster. During native camera
                // animation a second read can already describe a different frame.
                val corners = if(frame!=null && image!=null && projection!=null)
                    listOf(toAmap(frame.latMax,frame.lonMin),toAmap(frame.latMax,frame.lonMax),
                        toAmap(frame.latMin,frame.lonMax),toAmap(frame.latMin,frame.lonMin))
                        .map {projection.toScreenLocation(it)} else emptyList()
                val drawable = image!=null && corners.distinctBy {it.x to it.y}.size==4 &&
                    fogTransform.setPolyToPoly(
                        floatArrayOf(0f,0f,image.width.toFloat(),0f,image.width.toFloat(),image.height.toFloat(),0f,image.height.toFloat()),0,
                        corners.flatMap {listOf(it.x.toFloat(),it.y.toFloat())}.toFloatArray(),0,4)
                guardPath.reset()
                guardPath.fillType = android.graphics.Path.FillType.EVEN_ODD
                guardPath.addRect(0f,0f,view.width.toFloat(),view.height.toFloat(),android.graphics.Path.Direction.CW)
                if(drawable) {
                        guardPath.moveTo(corners[0].x.toFloat(),corners[0].y.toFloat())
                        corners.drop(1).forEach { guardPath.lineTo(it.x.toFloat(),it.y.toFloat()) }
                        guardPath.close()
                }
                canvas.drawPath(guardPath,guardPaint)
                if(drawable && image!=null) {
                        // Outside coverage and inside raster are recorded in one Android display
                        // list. AMap cannot render one with the other frame's bounds anymore.
                        canvas.drawBitmap(image,fogTransform,fogImagePaint)
                        routeOnScreen.reset();routePixels.transform(fogTransform,routeOnScreen)
                        // A fixed thick stroke merged close streets into solid blocks when zoomed
                        // out. Keep the real corridor width, with a one-pixel trace for wide views.
                        val corridorPixels=(TrailPaint.CORRIDOR_M/(map?.scalePerPixel?.toDouble()?.takeIf {it>0} ?: 1.0)).toFloat().coerceAtLeast(1f)
                        routePaint.color=TrailOutlineArgb;routePaint.strokeWidth=minOf(4f*density,corridorPixels)
                        canvas.drawPath(routeOnScreen,routePaint)
                        routePaint.color=(exploredColor and 0x00FFFFFF) or (0xB0 shl 24);routePaint.strokeWidth=minOf(2.5f*density,corridorPixels)
                        canvas.drawPath(routeOnScreen,routePaint)
                }
                latestPosition?.let { fix ->
                    projection?.toScreenLocation(toAmap(fix.latitude,fix.longitude))?.let { point ->
                        val marker=if(fix.provider==LocationManager.NETWORK_PROVIDER) networkDotImage else blueDotImage
                        canvas.drawBitmap(marker,point.x-marker.width/2f,point.y-marker.height/2f,fogImagePaint)
                    }
                }
                canvas.restore()
            }
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(filter: android.graphics.ColorFilter?) {}
            @Suppress("DEPRECATION") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
        coverageGuard=guard
        fun graphicsSurface(node:android.view.View):android.view.View? {
            if(node is android.opengl.GLSurfaceView || node is android.view.TextureView) return node
            if(node is android.view.ViewGroup) for(index in 0 until node.childCount)
                graphicsSurface(node.getChildAt(index))?.let {return it}
            return null
        }
        fun attach() {
            // Attach over the rendering surface, below the SDK's own logo/attribution widgets.
            // Only public Android View types are used; no SDK private names or fields.
            val host=graphicsSurface(view) ?: view
            if(host!==coverageHost) {coverageHost?.overlay?.remove(guard);coverageHost=host;host.overlay.add(guard)}
            guard.setBounds(0,0,host.width,host.height);guard.invalidateSelf()
        }
        view.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> attach() }
        attach()
    }
    private var paintedRevision = -1
    /** Kept only so an exported field report can say what the phone's own engine ended up with. */
    private var groundPixels: String? = null
    private var groundFrame: PaintFrame? = null
    private var mapLoaded = false
    /** Display coordinates are converted once per point, not once per point per redraw. */
    private val converted = HashMap<Long, LatLng>()
    private var retainedCamera: CameraPosition?=null
    private var pendingFocus: CameraPosition?=null
    private val density = context.resources.displayMetrics.density
    private val blueDotImage by lazy {
        val bitmap = Bitmap.createBitmap((32*density).toInt(),(32*density).toInt(),Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        val center=bitmap.width/2f
        paint.color=Color.argb(40,48,139,211); canvas.drawCircle(center,center,14*density,paint)
        paint.color=Color.WHITE;canvas.drawCircle(center,center,6*density,paint)
        paint.color=Color.rgb(48,139,211);canvas.drawCircle(center,center,4.5f*density,paint)
        bitmap
    }

    private var gestureActive = false
    private val networkDotImage by lazy {
        val bitmap=Bitmap.createBitmap((32*density).toInt(),(32*density).toInt(),Bitmap.Config.ARGB_8888)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(234,178,73);style=Paint.Style.STROKE;strokeWidth=2*density }
        Canvas(bitmap).drawCircle(bitmap.width/2f,bitmap.height/2f,6*density,paint)
        bitmap
    }
    fun create(state: TrackState, location: Location?, roads: RoadState, color:Int=ExploredRoadArgb): MapView {
        exploredColor=color
        check(BuildConfig.AMAP_KEY.isNotBlank()) { "高德地图 Key 尚未设置" }
        MapsInitializer.updatePrivacyShow(context.applicationContext, true, true)
        MapsInitializer.updatePrivacyAgree(context.applicationContext, true)
        MapsInitializer.setApiKey(BuildConfig.AMAP_KEY)
        val view = MapView(context)
        view.onCreate(null)
        currentView = view
        map = view.map
        groundFrame = null
        installCoverageGuard(view)
        renderedPaths=null
        groundImage=null;routeFrame=null;routePixels.reset()
        paintedRevision = -1
        groundPixels = null
        mapLoaded = false
        pendingFocus=null
        converted.clear()
        map?.uiSettings?.isZoomControlsEnabled = false
        map?.uiSettings?.isCompassEnabled = false
        map?.uiSettings?.isRotateGesturesEnabled = true
        var downX=0f;var downY=0f
        val slop=android.view.ViewConfiguration.get(context).scaledTouchSlop
        val taps=android.view.GestureDetector(context,object:android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e:android.view.MotionEvent)=true
            override fun onDoubleTap(e:android.view.MotionEvent):Boolean { nativeGesture();return false }
            override fun onSingleTapConfirmed(e:android.view.MotionEvent):Boolean {
                val fix=latestPosition ?: return false
                if(fix.provider!=LocationManager.NETWORK_PROVIDER) return false
                val p=map?.projection?.toScreenLocation(toAmap(fix.latitude,fix.longitude)) ?: return false
                if(kotlin.math.hypot(e.x-p.x,e.y-p.y)>24*density) return false
                android.widget.Toast.makeText(context,"这是网络位置，仅用于找方向；GPS 连续记录尚需看定位状态。",android.widget.Toast.LENGTH_LONG).show()
                return true
            }
        })
        map?.setOnMapTouchListener { event ->
            taps.onTouchEvent(event)
            when(event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> { downX=event.x;downY=event.y;gestureActive=true;cameraMotion() }
                android.view.MotionEvent.ACTION_POINTER_DOWN -> nativeGesture()
                android.view.MotionEvent.ACTION_MOVE -> if(kotlin.math.hypot(event.x-downX,event.y-downY)>slop) nativeGesture()
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> endGesture()
            }
        }
        map?.setOnMapLoadedListener {
            mapLoaded = true
            // Camera moves issued before the SDK finishes loading may be replaced by its default
            // camera. Restore only after loading, or leave the initial focus pending for render.
            val requested=pendingFocus
            pendingFocus=null
            (requested ?: retainedCamera)?.let {
                map?.moveCamera(CameraUpdateFactory.newCameraPosition(it))
                initialFocusPending = false
            }
            resolveInitialFocus()
            onReady()
        }
        // The veil follows the map: a pan or a zoom asks for a redraw for the new view.
        map?.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
            override fun onCameraChange(position: CameraPosition?) { cameraMotion(); onCamera() }
            override fun onCameraChangeFinish(position: CameraPosition?) {
                position?.let { zoomSteps.settled(it.zoom) };updateCoverageGuard();onCameraSettled()
            }
        })
        map?.uiSettings?.isTiltGesturesEnabled = false
        latestPosition = location
        initialFocusPending = true
        render(state,roads,emptyList(),null,null,-1)
        return view
    }

    /** Safe during gestures: bitmap, coverage and route share the same atomic Android draw. */
    fun render(state: TrackState, roads: RoadState, paths: List<List<TrackPoint>>,
               fog: Bitmap?, fogFrame: PaintFrame?, fogRevision: Int) {
        renderNow(state,roads,paths,fog,fogFrame,fogRevision)
    }

    private fun endGesture() {gestureActive=false;cameraMotion()}

    private fun renderNow(state: TrackState, roads: RoadState, paths: List<List<TrackPoint>>,
                          fog: Bitmap?, fogFrame: PaintFrame?, fogRevision: Int) {
        val a = map ?: return
        latestState=state
        // Route vectors sit above the raster in the same draw, including wide views.
        syncGround(a,fog,fogFrame,fogRevision)
        syncCanvasRoutes(paths)
        updateUser(latestPosition)
        resolveInitialFocus()
    }

    /** Loading must resolve the camera even when the raster cache has nothing new to upload. */
    private fun resolveInitialFocus() {
        if(!initialFocusPending || !mapLoaded) return
        val a=map ?: return
        val current=latestPosition?.let {TrackPoint(it.latitude,it.longitude,it.time,it.accuracy)}
        RoadMapFocus.choose(latestState,current,RoadState())?.let { point ->
            initialFocusPending=false
            a.moveCamera(CameraUpdateFactory.newLatLngZoom(toAmap(point.lat,point.lon),17f))
        }
    }

    /** Build geography only when it or the raster frame changes; camera frames transform one Path. */
    private fun syncCanvasRoutes(paths:List<List<TrackPoint>>) {
        val frame=groundFrame ?: return
        if(renderedPaths==paths && routeFrame==frame) return
        routePixels.reset()
        val southwest=toAmap(frame.latMin,frame.lonMin)
        val northeast=toAmap(frame.latMax,frame.lonMax)
        paths.forEach { path ->path.forEachIndexed { index,p ->
            val coordinate=toAmap(p.lat,p.lon)
            val x=((coordinate.longitude-southwest.longitude)/(northeast.longitude-southwest.longitude)*frame.widthPx).toFloat()
            val y=MapRenderCoordinates.y(coordinate.latitude,southwest.latitude,northeast.latitude,frame.heightPx)
            if(index==0) routePixels.moveTo(x,y) else routePixels.lineTo(x,y)
        } }
        renderedPaths=paths;routeFrame=frame
        updateCoverageGuard()
    }

    /** One immutable raster + geographic frame, consumed atomically by the existing coverage view. */
    private fun syncGround(a:AMap,fog:Bitmap?,frame:PaintFrame?,revision:Int) {
        if(fog==null || frame==null || revision<0 || revision==paintedRevision) return
        groundImage=fog;groundFrame=frame;paintedRevision=revision
        groundPixels="${fog.width}x${fog.height}"
        updateCoverageGuard()
    }

    fun setExploredColor(color:Int) {exploredColor=color;updateCoverageGuard()}

    fun updateUser(location:Location?) {
        val previous=latestPosition
        latestPosition=location
        resolveInitialFocus()
        if(previous?.latitude!=location?.latitude || previous?.longitude!=location?.longitude)
            updateCoverageGuard()
    }

    fun zoomBy(delta: Int) {
        initialFocusPending = false
        pendingFocus=null
        val a=map ?: return
        val camera=a.cameraPosition
        val target=zoomSteps.request(camera.zoom,delta,a.minZoomLevel,a.maxZoomLevel)
        cameraMotion()
        a.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.builder(camera).zoom(target).build()))
    }

    fun focus(lat: Double, lon: Double, zoom: Double) {
        initialFocusPending=false
        zoomSteps.reset()
        val a=map ?: return
        val target=CameraPosition.builder(a.cameraPosition).target(toAmap(lat,lon)).zoom(zoom.toFloat()).build()
        // The date request precedes its MapView load; it must win over the retained camera.
        pendingFocus=if(mapLoaded) null else target
        if(mapLoaded) a.animateCamera(CameraUpdateFactory.newCameraPosition(target))
    }

    /**
     * [minZoom] is supplied only when the person explicitly asked to be located: it lifts a wide
     * camera to street level. Following a moving fix leaves the camera's own zoom alone.
     */
    fun followCamera(fix: Location, bearing: Float, animate: Boolean, minZoom: Float? = null) {
        if (!mapLoaded || initialFocusPending) return
        val a=map ?: return
        zoomSteps.reset()
        val current=a.cameraPosition
        val zoom=when {
            minZoom!=null -> maxOf(current.zoom,minZoom)
            current.zoom<5f -> 18f
            else -> current.zoom
        }
        val target=CameraPosition.builder(current)
            .target(toAmap(fix.latitude,fix.longitude)).bearing(bearing)
            .zoom(zoom).build()
        val update=CameraUpdateFactory.newCameraPosition(target)
        if(animate) a.animateCamera(update,200,null) else a.moveCamera(update)
    }
    fun cancelFollowingAnimation() {
        // A delayed first data render must not undo the person's manual camera choice.
        initialFocusPending = false
        map?.stopAnimation()
    }

    fun view(): MapView? = currentView

    /** Shows a whole walked area, used when the person asks to see all of their exploration. */
    fun focusBounds(latMin: Double, lonMin: Double, latMax: Double, lonMax: Double) {
        zoomSteps.reset()
        val bounds = com.amap.api.maps.model.LatLngBounds(
            toAmap(latMin, lonMin), toAmap(latMax, lonMax))
        map?.moveCamera(com.amap.api.maps.CameraUpdateFactory.newLatLngBounds(bounds, 80))
    }

    /** One line describing what the camera is showing, for the on-demand field export. */
    fun cameraSummary(): String {
        val camera = map?.cameraPosition ?: return "高德地图尚未就绪"
        val source = fromAmap(camera.target.latitude, camera.target.longitude)
        return String.format(java.util.Locale.ROOT, "缩放=%.2f 中心=%.5f,%.5f",
            camera.zoom, source.latitude, source.longitude)
    }

    fun cameraSource(): Triple<Double, Double, Float>? {
        val camera = map?.cameraPosition ?: return null
        val source = fromAmap(camera.target.latitude, camera.target.longitude)
        return Triple(source.latitude, source.longitude, camera.zoom)
    }

    /** What the AMap view is showing, so the veil can follow it. */
    fun viewport(): ViewBounds? {
        val bounds = map?.projection?.visibleRegion?.latLngBounds ?: return null
        val southwest = fromAmap(bounds.southwest.latitude, bounds.southwest.longitude)
        val northeast = fromAmap(bounds.northeast.latitude, bounds.northeast.longitude)
        return ViewBounds(southwest.latitude, southwest.longitude,
            northeast.latitude, northeast.longitude)
    }

    private fun fromAmap(lat: Double, lon: Double): LatLng {
        if (!CoordinateConverter.isAMapDataAvailable(lat, lon)) return LatLng(lat, lon)
        val source = CoordinateInverse.toSource(lat, lon) { sourceLat, sourceLon ->
            val display = toAmap(sourceLat, sourceLon)
            display.latitude to display.longitude
        }
        return LatLng(source.first, source.second)
    }

    /**
     * What the AMap side of the veil actually ended up doing, for the on-demand field export.
     *
     * Report actual SDK readiness and the foreground raster/vector coverage separately. The
     * native SDK supplies the basemap/projection; the Android overlay supplies one coherent veil.
     */
    fun describe(): String {
        if (map == null) return "高德=视图未创建"
        val pixels = groundPixels
        val layer = when {
            groundImage != null -> "有(位图=$pixels 图层版本=$paintedRevision 边界=" +
                (groundFrame?.let { String.format(java.util.Locale.ROOT, "%.5f,%.5f~%.5f,%.5f",
                    it.latMin, it.lonMin, it.latMax, it.lonMax) } ?: "未记录") + ")"
            pixels != null -> "没有（位图尚未显示）"
            else -> "没有（还没有位图）"
        }
        return "高德=视图已建 地图加载完成=$mapLoaded 地面图层$layer " +
            "绘制=原生覆盖层整帧迷雾/路线${renderedPaths?.size ?: 0}条/位置，原生高德底图 " +
            "坐标换算缓存=${converted.size} 手势挂起=$gestureActive"
    }

    fun release(view: MapView) {
        if (view !== currentView) return
        choreographer.removeFrameCallback(fogFrameCallback);frameQueued=false;cameraMovingUntil=0L;gestureActive=false
        zoomSteps.target?.let { map?.moveCamera(CameraUpdateFactory.zoomTo(it)) }
        zoomSteps.reset()
        retainedCamera=pendingFocus ?: map?.cameraPosition
        pendingFocus=null
        renderedPaths=null
        latestState=TrackState()
        coverageGuard?.let {coverageHost?.overlay?.remove(it)}
        coverageGuard=null;coverageHost=null
        groundImage=null;routeFrame=null;routePixels.reset()
        groundPixels = null
        groundFrame = null
        mapLoaded = false
        paintedRevision = -1
        map = null
        currentView = null
        view.onPause()
        view.onDestroy()
    }

    /**
     * WGS84 to the display datum, remembered per point. The conversion used to run twice for every
     * point of every trip on every redraw, which is a large share of the dragging stutter.
     */
    private fun toAmap(lat: Double, lon: Double): LatLng {
        val key = (Math.round(lat * 1_000_000.0) shl 32) xor
            (Math.round(lon * 1_000_000.0) and 0xFFFFFFFFL)
        converted[key]?.let { return it }
        val point = LatLng(lat, lon)
        val result = if (!CoordinateConverter.isAMapDataAvailable(lat, lon)) point
        else CoordinateConverter(context)
            .from(CoordinateConverter.CoordType.GPS)
            .coord(point)
            .convert() ?: point
        if (converted.size > 20_000) converted.clear()
        converted[key] = result
        return result
    }
}
