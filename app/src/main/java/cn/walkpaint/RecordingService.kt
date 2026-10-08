package cn.walkpaint

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.*
import android.os.*
import java.util.UUID

class RecordingService : Service(), LocationListener {
    private val app get() = application as WalkPaintApp
    private lateinit var locations: LocationManager
    private var started = false
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        locations = getSystemService(LocationManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("recording", "步行记录", NotificationManager.IMPORTANCE_LOW)
        )
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { finishRecording("本次足迹已保存"); return START_NOT_STICKY }
        if (started) return START_NOT_STICKY
        try {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                finishRecording("请允许精确位置后开始记录"); return START_NOT_STICKY
            }
            if (!locations.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                finishRecording("系统定位已关闭，请打开定位后重试"); return START_NOT_STICKY
            }
            val notification = notification("正在等待准确 GPS，锁屏后继续记录")
            if (Build.VERSION.SDK_INT >= 29) startForeground(7, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(7, notification)
            val track = Track(UUID.randomUUID().toString(), System.currentTimeMillis(), false)
            app.store.update { it.copy(tracks = it.tracks + track, activeId = track.id) }
            started = true
            app.recordingMessage = "正在记录 · 等待 GPS，请到开阔处"
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 0f, this, Looper.getMainLooper())
        } catch (e: Exception) {
            finishRecording(if (e is SecurityException) "定位权限不可用，记录已停止" else "记录无法启动，请检查定位或存储空间")
        }
        return START_NOT_STICKY
    }
    override fun onLocationChanged(location: Location) {
        if (!started) return
        val age = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000_000.0
        if (age !in 0.0..20.0 || !location.hasAccuracy()) return
        val snapshot = app.store.snapshot()
        val track = snapshot.tracks.find { it.id == snapshot.activeId } ?: return
        val previous = track.points.lastOrNull()
        val raw = TrackPoint(location.latitude, location.longitude, location.time, location.accuracy)
        if (!Geo.accept(previous, raw)) {
            app.recordingMessage = "正在记录 · GPS 精度不足或异常点已忽略"
            return
        }
        val point = raw.copy(segment = (previous?.segment ?: 0) + if (previous != null && raw.time - previous.time > 60_000) 1 else 0)
        try {
            app.store.update { s -> s.copy(tracks = s.tracks.map { if (it.id == s.activeId) it.copy(points = it.points + point) else it }) }
            app.recordingMessage = "正在记录 · 精度 ${location.accuracy.toInt()} 米 · 每点及时保存"
            getSystemService(NotificationManager::class.java).notify(7, notification("已走 %.2f 公里 · 锁屏继续记录".format(Geo.distance(track.points + point) / 1000)))
        } catch (e: Exception) { finishRecording("保存失败，已停止记录；此前足迹仍保留") }
    }
    override fun onProviderDisabled(provider: String) { app.recordingMessage = "正在记录 · 定位已关闭，等待重新开启" }
    override fun onProviderEnabled(provider: String) { app.recordingMessage = "正在记录 · 等待 GPS" }
    @Deprecated("Android legacy callback")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, RecordingService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, "recording").setSmallIcon(R.drawable.ic_footprint).setContentTitle("足迹涂色 · 正在记录")
            .setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "结束记录", stop).build()).build()
    }
    private fun finishRecording(message: String) {
        started = false
        runCatching { locations.removeUpdates(this) }
        runCatching { app.store.update { it.copy(activeId = null, tracks = it.tracks.filter { t -> t.demo || t.points.isNotEmpty() }) } }
        app.recordingMessage = message
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        runCatching { locations.removeUpdates(this) }
        if (started) runCatching { app.store.update { it.copy(activeId = null) } }
        super.onDestroy()
    }
}
