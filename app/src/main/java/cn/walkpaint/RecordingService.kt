package cn.walkpaint

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import android.provider.Settings
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object AmbientTracking {
    private const val PREFERENCES = "footprint_preferences"
    private const val KEY = "automatic_enabled"

    @Volatile var running = false
    @Volatile var status = "自动足迹尚未启用"
    @Volatile var lastFix: Location? = null
    @Volatile var lastSaved: Long = 0L

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
        status = if (enabled) "自动足迹已启用，正在连接定位" else "已暂停自动足迹"
    }

    fun hasFineLocation(context: Context) =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocation(context: Context) =
        Build.VERSION.SDK_INT < 29 ||
            context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun start(context: Context) {
        if (!enabled(context) || !hasFineLocation(context)) {
            status = "需要精确定位授权，才能继续记录"
            return
        }
        try {
            context.startForegroundService(Intent(context, RecordingService::class.java).setAction("ENABLE"))
        } catch (e: Exception) {
            running = false
            status = "系统暂时不允许自动恢复，请打开应用重新启用"
        }
    }

    fun stop(context: Context) {
        setEnabled(context, false)
        try {
            context.startService(Intent(context, RecordingService::class.java).setAction("STOP"))
        } catch (_: Exception) {
            context.stopService(Intent(context, RecordingService::class.java))
        }
    }
}

/**
 * Started once by the user and stays visible via an ongoing notification.
 * Adaptive GPS: after idle, keep passive/network providers and briefly probe GPS.
 * This is not an OS bypass; force-stop, power saving, and background limits still apply.
 */
class RecordingService : Service(), LocationListener {
    private val app get() = application as WalkPaintApp
    private lateinit var locations: LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val storeExecutor = Executors.newSingleThreadExecutor()
    private val isClosing = AtomicBoolean(false)
    private var gpsEnabled = false
    private var idle = false
    private var lastMovementElapsed = 0L
    private var gpsProbeStarted = 0L
    private var lastGpsPaused = 0L

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        locations = getSystemService(LocationManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("ambient-footprints", "自动足迹", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            AmbientTracking.setEnabled(this, false)
            endService("已暂停自动足迹")
            return START_NOT_STICKY
        }
        if (!AmbientTracking.enabled(this)) {
            endService("自动足迹尚未启用")
            return START_NOT_STICKY
        }
        if (AmbientTracking.running) return START_STICKY
        if (!AmbientTracking.hasFineLocation(this)) {
            endService("精确定位权限未授权")
            return START_NOT_STICKY
        }
        if (!locations.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            endService("系统位置服务已关闭，请开启后重试")
            return START_NOT_STICKY
        }
        try {
            val ongoing = notification("自动足迹已开启 · GPS 智能采样")
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(17, ongoing, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else startForeground(17, ongoing)

            AmbientTracking.running = true
            AmbientTracking.status = "自动足迹运行中 · 正在定位"
            lastMovementElapsed = SystemClock.elapsedRealtime()
            startGps()
            if (locations.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locations.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60_000, 25f, this, Looper.getMainLooper())
            }
            locations.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 10_000, 0f, this, Looper.getMainLooper())
            mainHandler.postDelayed(checkIdle, 60_000)
        } catch (e: Exception) {
            endService("定位服务启动失败，请确认定位权限")
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private val checkIdle = object : Runnable {
        override fun run() {
            if (!AmbientTracking.running || isClosing.get()) return
            val now = SystemClock.elapsedRealtime()
            if (!idle && now - lastMovementElapsed > 4 * 60_000) {
                idle = true
                pauseGps()
                AmbientTracking.status = "自动足迹待机中 · 低功耗等待移动"
                updateNotification("智能待机 · 被动位置与周期 GPS 检测")
                lastGpsPaused = now
            } else if (idle && !gpsEnabled && now - lastGpsPaused > 8 * 60_000) {
                startGps()
                gpsProbeStarted = now
                AmbientTracking.status = "检查是否开始移动"
            } else if (idle && gpsEnabled && now - gpsProbeStarted > 65_000) {
                pauseGps()
                lastGpsPaused = now
            }
            mainHandler.postDelayed(this, 60_000)
        }
    }

    private fun startGps() {
        if (gpsEnabled) return
        if (!AmbientTracking.hasFineLocation(this) || !locations.isProviderEnabled(LocationManager.GPS_PROVIDER)) return
        locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 12_000, 7f, gpsListener, Looper.getMainLooper())
        gpsEnabled = true
    }

    private fun pauseGps() {
        if (!gpsEnabled) return
        // Remove only GPS listener registration using a dedicated listener.
        locations.removeUpdates(gpsListener)
        gpsEnabled = false
    }

    private val gpsListener = LocationListener { fix -> onLocationChanged(fix) }

    override fun onLocationChanged(location: Location) {
        if (isClosing.get() || !AmbientTracking.running || !location.hasAccuracy()) return
        val ageSeconds = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000_000.0
        if (ageSeconds !in 0.0..30.0 || !location.latitude.isFinite() || !location.longitude.isFinite()) return
        val now = System.currentTimeMillis()
        if (!Geo.plausibleTimestamp(location.time, now)) {
            AmbientTracking.status = "已忽略时间异常的定位点"
            return
        }
        val previousFix = AmbientTracking.lastFix
        if (location.accuracy <= 250f) {
            AmbientTracking.lastFix = location
        }

        if (idle && location.provider != LocationManager.GPS_PROVIDER) {
            val old = previousFix
            if (old != null && location.accuracy < 110f &&
                old !== location && old.distanceTo(location) > maxOf(90f, location.accuracy * 1.7f)) {
                idle = false
                lastMovementElapsed = SystemClock.elapsedRealtime()
                runCatching { startGps() }
            }
        }

        if (location.accuracy > 35f) return
        storeExecutor.execute {
            val accepted = runCatching { app.repository.addPosition(location) }.getOrDefault(false)
            if (accepted) {
                AmbientTracking.lastSaved = System.currentTimeMillis()
                mainHandler.post {
                    lastMovementElapsed = SystemClock.elapsedRealtime()
                    idle = false
                    AmbientTracking.status = "正在自动染色 · GPS 精度 ${location.accuracy.toInt()} 米"
                    updateNotification("刚刚记录新足迹 · 锁屏后仍尝试继续")
                }
            }
        }
    }

    private fun updateNotification(s: String) {
        getSystemService(NotificationManager::class.java).notify(17, notification(s))
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 10, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 11, Intent(this, RecordingService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, "ambient-footprints")
            .setSmallIcon(R.drawable.ic_footprint)
            .setContentTitle("足迹涂色 · 自动记录已开启")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "暂停自动足迹", stop).build())
            .build()
    }

    override fun onProviderDisabled(provider: String) {
        AmbientTracking.status = "系统定位已关闭，无法继续记录"
    }
    override fun onProviderEnabled(provider: String) {
        AmbientTracking.status = "位置服务已打开，正在恢复定位"
    }

    private fun endService(status: String) {
        isClosing.set(true)
        AmbientTracking.running = false
        AmbientTracking.status = status
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { locations.removeUpdates(this) }
        runCatching { locations.removeUpdates(gpsListener) }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onDestroy() {
        AmbientTracking.running = false
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { locations.removeUpdates(this) }
        runCatching { locations.removeUpdates(gpsListener) }
        storeExecutor.shutdown()
        super.onDestroy()
    }
}

/** Restart only when user enabled auto mode AND Android allows background location. */
class FootprintBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        if (!AmbientTracking.enabled(context)) return
        if (!AmbientTracking.hasBackgroundLocation(context)) {
            AmbientTracking.status = "重启后需要在应用里恢复自动足迹，请授权始终允许定位"
            return
        }
        AmbientTracking.start(context)
    }
}
