package cn.walkpaint

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.GnssStatus
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object AmbientTracking {
    private const val PREFERENCES = "footprint_preferences"
    private const val KEY = "automatic_enabled"

    @Volatile var running = false
    @Volatile var status = "自动足迹尚未启用"
    @Volatile var lastFix: Location? = null
    @Volatile var lastGps: Location? = null
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
            RecordingDiagnostics.event(context, "failure", "START_BLOCKED_" + e.javaClass.simpleName)
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
 * Continuous GPS subscription through the location foreground service. No CPU wake lock.
 * Watchdog retries failed/stalled registrations without claiming a fix exists.
 * This is not an OS bypass; force-stop, power saving, and background limits still apply.
 */
class RecordingService : Service(), LocationListener {
    private val app get() = application as WalkPaintApp
    private lateinit var locations: LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val storeExecutor = Executors.newSingleThreadExecutor()
    private val isClosing = AtomicBoolean(false)
    private var gpsEnabled = false
    private var lastCallbackElapsed = 0L
    private var lastRegistrationElapsed = 0L
    private var health = RecordingHealth()
    private var lastNotificationAt = 0L
    private var rawFixBeforeLast: Location? = null
    private var lastRawFix: Location? = null
    private var gnssRegistered = false
    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onStarted() = gnssEvent("系统GNSS已启动")
        override fun onStopped() = gnssEvent("系统GNSS已停止")
        override fun onFirstFix(ttffMillis: Int) {
            if (isClosing.get()) return
            RecordingDiagnostics.event(this@RecordingService, "gnss_first", "GNSS_FIRST_FIX", ttffMillis.coerceAtLeast(0).toLong())
        }
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            if (isClosing.get()) return
            val used = (0 until status.satelliteCount).count { status.usedInFix(it) }
            RecordingDiagnostics.satellites(this@RecordingService, status.satelliteCount, used)
        }
    }
    private fun gnssEvent(reason: String) {
        if (!isClosing.get()) RecordingDiagnostics.event(this, "gnss", reason)
    }
    private fun ensureGnss() {
        if (gnssRegistered || isClosing.get() || !AmbientTracking.hasFineLocation(this)) return
        try {
            // Metadata only: no NMEA, raw measurements, satellite IDs or coordinates.
            gnssRegistered = locations.registerGnssStatusCallback(gnssCallback, mainHandler)
            gnssEvent(if (gnssRegistered) "已订阅，等待系统GNSS状态" else "系统未接受GNSS状态订阅")
        } catch (e: Exception) { gnssEvent("GNSS状态不可读_${e.javaClass.simpleName}") }
    }
    private fun releaseGnss() {
        if (gnssRegistered) runCatching { locations.unregisterGnssStatusCallback(gnssCallback) }
        gnssRegistered = false
    }
    private var powerReceiverRegistered = false
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { recordPowerState() }
    }
    private fun recordPowerState() {
        val power = getSystemService(PowerManager::class.java)
        RecordingDiagnostics.event(this, "power",
            "SCREEN_${if(power.isInteractive) "ON" else "OFF"}_IDLE_${power.isDeviceIdleMode}_SAVER_${power.isPowerSaveMode}")
    }
    private fun recordRecoveryEvidence() {
        val prior = RecordingDiagnostics.snapshot(this)
        fun priorTime(key: String) = (prior[key] as? Number)?.toLong() ?: 0L
        if (priorTime("service_start_at") > priorTime("service_stop_at"))
            RecordingDiagnostics.event(this, "recovery", "PREVIOUS_SESSION_WITHOUT_STOP_REASON")
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val exit = getSystemService(ActivityManager::class.java)
                    .getHistoricalProcessExitReasons(packageName, 0, 1).firstOrNull()
                if (exit != null && exit.timestamp > priorTime("process_exit_at"))
                    RecordingDiagnostics.event(this, "exit", "ANDROID_REASON_${exit.reason}", occurredAt=exit.timestamp)
            } catch (_: Exception) { RecordingDiagnostics.event(this, "recovery", "SYSTEM_EXIT_INFO_UNAVAILABLE") }
        }
    }
    private fun unregisterPowerReceiver() {
        if (powerReceiverRegistered) { runCatching { unregisterReceiver(powerReceiver) }; powerReceiverRegistered=false }
    }

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
        try {
            val ongoing = notification("持续 GPS 记录 · 可能增加电耗")
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(17, ongoing, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else startForeground(17, ongoing)

            AmbientTracking.running = true
            AmbientTracking.status = "自动足迹运行中 · 正在定位"
            val elapsed = SystemClock.elapsedRealtime()
            lastCallbackElapsed = elapsed
            val priorGps = getSharedPreferences("recording_diagnostics", MODE_PRIVATE).getLong("gps_callback_at", 0)
            val age = System.currentTimeMillis() - priorGps
            health = RecordingHealth(if (priorGps > 0 && age in 0..elapsed) elapsed - age else null)
            recordRecoveryEvidence()
            RecordingDiagnostics.event(this, "start", if (intent == null) "STICKY_RESTART" else "USER_OR_BOOT_START")
            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED); addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(powerReceiver,filter,Context.RECEIVER_NOT_EXPORTED)
            else registerReceiver(powerReceiver,filter)
            powerReceiverRegistered=true
            recordPowerState()
            ensureGnss()
            ensureGps()
            // Continuous GPS needs no passive subscription: passive also forwards GPS
            // and would double-count callbacks / disturb the movement evidence window.
            mainHandler.post(checkHealth)
        } catch (e: Exception) {
            RecordingDiagnostics.event(this, "failure", "FOREGROUND_START_" + e.javaClass.simpleName)
            endService("定位服务启动失败，请确认定位权限和系统位置开关")
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private val checkHealth = HealthCheckLoop(
        active = { AmbientTracking.running && !isClosing.get() },
        check = { checkRecordingHealth() },
        failed = { e -> RecordingDiagnostics.event(this, "failure", "HEALTH_CHECK_${e.javaClass.simpleName}") },
        schedule = { mainHandler.postDelayed(it, 30_000) }
    )

    private fun checkRecordingHealth() {
        if (!AmbientTracking.hasFineLocation(this@RecordingService)) {
            endService("精确定位权限已撤回 · 记录停止")
            return
        }
        RecordingDiagnostics.event(this@RecordingService, "heartbeat", "SERVICE_ALIVE")
        val elapsed = SystemClock.elapsedRealtime()
        val provider = locations.isProviderEnabled(LocationManager.GPS_PROVIDER)
        ensureGnss()
        RecordingDiagnostics.event(this, "provider", "${if(provider) "系统GPS已开启" else "系统GPS已关闭"} / ${if(gpsEnabled) "App已订阅" else "App未订阅"}")
        if (RecordingHealth.shouldRetry(gpsEnabled, provider,
                elapsed - lastCallbackElapsed, elapsed - lastRegistrationElapsed)) {
            // Never swallow registration failures or leave GPS unsubscribed after a retry.
            ensureGps(replace = gpsEnabled)
        }
        if (!provider) AmbientTracking.status = "GPS 已关闭 · 等待开启"
        else if (elapsed - lastCallbackElapsed > 45_000L)
            AmbientTracking.status = "GPS 回调已中断 · 正在监听/限频重试，尚未恢复记录"
        val status=RecordingStatus.assess(RecordingDiagnostics.signals(this@RecordingService,app.repository.lastError))
        updateNotification(status.title + " · " + status.hint)
    }

    private fun ensureGps(replace: Boolean = false) {
        if (isClosing.get() || !AmbientTracking.hasFineLocation(this)) return
        try {
            // Subscribe even while disabled, so an enable callback can recover GPS.
            if (gpsEnabled && !replace) return
            if (replace) locations.removeUpdates(gpsListener)
            gpsEnabled = false
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000L, 0f,
                gpsListener, Looper.getMainLooper())
            gpsEnabled = true
            lastRegistrationElapsed = SystemClock.elapsedRealtime()
            RecordingDiagnostics.event(this, "register", if (replace) "GPS_RETRY_REGISTERED" else "GPS_REGISTERED")
        } catch (e: Exception) {
            gpsEnabled = false
            AmbientTracking.status = "GPS 注册失败 · 将重试，请查看诊断"
            RecordingDiagnostics.event(this, "failure", "GPS_REGISTER_" + e.javaClass.simpleName)
        }
    }

    private fun filtered(reason: String) {
        AmbientTracking.status = reason
        RecordingDiagnostics.event(this, "filtered", reason)
    }

    private fun isConfidentMovement(fix: Location): Boolean {
        // Provider-reported speed is a good walking cue when reasonably accurate.
        if (MovementCue.fromProviderSpeed(fix.hasSpeed(), fix.speed,
                fix.hasSpeedAccuracy(), fix.speedAccuracyMetersPerSecond)) return true
        val a = rawFixBeforeLast ?: return false
        val b = lastRawFix ?: return false
        return MovementCue.fromGeometry(a.toMotionFix(), b.toMotionFix(), fix.toMotionFix())
    }

    private fun Location.toMotionFix() = MotionFix(latitude, longitude, time, accuracy)

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(fix: Location) = this@RecordingService.onLocationChanged(fix)
        override fun onProviderDisabled(provider: String) = this@RecordingService.onProviderDisabled(provider)
        override fun onProviderEnabled(provider: String) = this@RecordingService.onProviderEnabled(provider)
    }

    override fun onLocationChanged(location: Location) {
        if (isClosing.get() || !AmbientTracking.running) return
        val elapsed = SystemClock.elapsedRealtime()
        if (location.provider == LocationManager.GPS_PROVIDER) {
            lastCallbackElapsed = elapsed
            RecordingDiagnostics.event(this, "gps", "GPS_CALLBACK", health.callback(elapsed))
        }
        if (!location.hasAccuracy()) { filtered("MISSING_ACCURACY"); return }
        val ageMillis = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L
        if (!location.latitude.isFinite() || !location.longitude.isFinite() ||
            location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) {
            filtered("INVALID_COORDINATE"); return
        }
        val normalized = Geo.normalizedFixTime(location.time, System.currentTimeMillis(), ageMillis)
        if (normalized == null) { filtered("STALE_FIX"); return }
        val reliableFix = Location(location).apply { time = normalized }
        if (reliableFix.accuracy <= 250f) AmbientTracking.lastFix = reliableFix

        if (reliableFix.provider != LocationManager.GPS_PROVIDER) return
        AmbientTracking.lastGps = reliableFix
        if (!reliableFix.accuracy.isFinite() || reliableFix.accuracy !in 1f..35f) {
            rawFixBeforeLast = null
            lastRawFix = null
            filtered("UNRELIABLE_ACCURACY"); return
        }
        RecordingDiagnostics.event(this, "valid_gps", "VALID_GPS_RECEIVED")
        val inferredMovement = isConfidentMovement(reliableFix)
        // Recorded so the diagnosis can tell a desk-bound phone from a broken recorder.
        if (inferredMovement) RecordingDiagnostics.event(this, "moving", "CONFIDENT_MOVEMENT")
        if (reliableFix.accuracy <= 20f) {
            rawFixBeforeLast = lastRawFix
            lastRawFix = reliableFix
        } else {
            rawFixBeforeLast = null
            lastRawFix = null
        }
        storeExecutor.execute {
            if (isClosing.get() || !AmbientTracking.enabled(this)) return@execute
            try {
                val accepted = app.repository.addPosition(reliableFix, inferredMovement)
                if (accepted) {
                    AmbientTracking.lastSaved = System.currentTimeMillis()
                    RecordingDiagnostics.event(this, "saved", "SQLITE_SAVED")
                    mainHandler.post {
                        if (isClosing.get()) return@post
                        AmbientTracking.status = "刚刚保存 GPS 足迹 · 精度 ${reliableFix.accuracy.toInt()} 米"
                        updateNotification("刚刚记录新足迹 · 持续 GPS 监听")
                    }
                } else if (app.repository.lastError != null) {
                    AmbientTracking.status = app.repository.lastError!!
                    RecordingDiagnostics.event(this, "failure", "DATABASE_WRITE_FAILED")
                } else filtered(app.repository.lastFilterReason)
            } catch (e: Exception) {
                AmbientTracking.status = "足迹保存失败 · 请查看诊断，原数据保留"
                RecordingDiagnostics.event(this, "failure", "DATABASE_" + e.javaClass.simpleName)
            }
        }
    }

    private fun updateNotification(s: String) {
        // Don't wake system notification handling on every two-second GPS fix.
        val now = SystemClock.elapsedRealtime()
        if (lastNotificationAt != 0L && now - lastNotificationAt < 30_000L) return
        lastNotificationAt = now
        getSystemService(NotificationManager::class.java).notify(17, notification(s))
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 10, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 11, Intent(this, RecordingService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, "ambient-footprints")
            .setSmallIcon(R.drawable.ic_footprint)
            .setContentTitle("足迹涂色 · 记录状态")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "暂停自动足迹", stop).build())
            .build()
    }

    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            RecordingDiagnostics.event(this, "register", "GPS_PROVIDER_DISABLED")
            AmbientTracking.status = "GPS 已关闭 · 等待开启，不会绘制假足迹"
        }
    }
    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            AmbientTracking.status = "位置服务已打开，正在恢复定位"
            ensureGps(replace = true)
        }
    }

    private fun endService(status: String) {
        isClosing.set(true)
        AmbientTracking.running = false
        AmbientTracking.status = status
        RecordingDiagnostics.event(this, "stop", status)
        mainHandler.removeCallbacksAndMessages(null)
        unregisterPowerReceiver()
        releaseGnss()
        runCatching { locations.removeUpdates(gpsListener) }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onDestroy() {
        val wasClosing = isClosing.getAndSet(true)
        AmbientTracking.running = false
        mainHandler.removeCallbacksAndMessages(null)
        unregisterPowerReceiver()
        releaseGnss()
        runCatching { locations.removeUpdates(gpsListener) }
        if (!wasClosing) RecordingDiagnostics.event(this, "stop",
            if (AmbientTracking.enabled(this)) "SERVICE_DESTROYED_ENABLED" else "USER_PAUSED")
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
            RecordingDiagnostics.event(context, "failure", "BOOT_MISSING_BACKGROUND_PERMISSION")
            return
        }
        AmbientTracking.start(context)
    }
}
