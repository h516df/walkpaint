package cn.walkpaint

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface

internal object ScreenHeading {
    fun axes(rotation:Int)=when(rotation) {
        Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
        Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
        Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
    }
    fun angle(vector:FloatArray,rotation:Int):Float? {
        if(vector.size<3 || vector.any { !it.isFinite() }) return null
        val matrix=FloatArray(9);val remapped=FloatArray(9);val angles=FloatArray(3)
        SensorManager.getRotationMatrixFromVector(matrix,vector)
        val (x,y)=axes(rotation)
        if(!SensorManager.remapCoordinateSystem(matrix,x,y,remapped)) return null
        SensorManager.getOrientation(remapped,angles)
        return HeadingFilter.normalize(Math.toDegrees(angles[0].toDouble()).toFloat())
    }
}

/** Only active on the visible following map; no game-vector drift or background sensor. */
internal class HeadingSensor(context:Context,private val rotation:()->Int,
                             private val heading:(Float?)->Unit):SensorEventListener {
    private val manager=context.getSystemService(SensorManager::class.java)
    private val sensor=manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: manager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    private val filter=HeadingFilter()
    var listening=false
        private set
    fun setActive(active:Boolean) {
        if(!active) { stop();return }
        if(listening) return
        filter.reset()
        listening=sensor?.let { manager.registerListener(this,it,SensorManager.SENSOR_DELAY_UI) } ?: false
        if(!listening) heading(null) // Stable north-up fallback on unsupported devices.
    }
    fun stop() {
        if(listening) manager.unregisterListener(this)
        listening=false;filter.reset()
    }
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int) {
        if(listening && accuracy<SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) {
            filter.reset();heading(null)
        }
    }
    override fun onSensorChanged(event:SensorEvent) {
        if(!listening) return
        val trustworthy=event.accuracy>=SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM &&
            (event.values.size<5 || event.values[4]<0 || event.values[4]<=Math.toRadians(25.0))
        if(!trustworthy) { filter.reset();heading(null);return }
        val angle=ScreenHeading.angle(event.values,rotation()) ?: return
        filter.sample(angle,event.timestamp/1_000_000L)?.let(heading)
    }
}
