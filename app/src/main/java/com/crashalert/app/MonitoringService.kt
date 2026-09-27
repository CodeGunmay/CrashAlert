package com.crashalert.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.pm.PackageManager
import android.Manifest
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.crashalert.app.telemetry.IncidentPhase
import com.crashalert.app.location.RideLocation
import com.crashalert.app.dispatch.AlertDispatcher
import androidx.core.content.ContextCompat
import java.util.UUID

/** Owns sensor monitoring while the app is closed. An ongoing notification makes it visible. */
class MonitoringService : Service() {
    private val monitor get() = (application as CrashAlertApplication).monitor
    private val handler = Handler(Looper.getMainLooper())
    private var lastPhase: IncidentPhase? = null
    private var alarm: Ringtone? = null
    private var trackingLocation = false
    private val dispatcher by lazy { AlertDispatcher(this) }
    private var incidentId: String? = null
    private var cloudAccepted = false
    private var requestInFlight = false
    private var lastAttemptAt = 0L
    private val locationListener = LocationListener { fix: Location ->
        val age = android.os.SystemClock.elapsedRealtime() - fix.elapsedRealtimeNanos / 1_000_000L
        if (age in 0..30_000L && fix.hasAccuracy()) {
            (application as CrashAlertApplication).recordLocation(
                RideLocation(fix.latitude, fix.longitude, fix.accuracy, fix.elapsedRealtimeNanos / 1_000_000L)
            )
        }
    }
    private val update = object : Runnable {
        override fun run() {
            if (!monitor.state.active) { stopSelf(); return }
            val phase = monitor.incident.phase
            if (phase != lastPhase) {
                lastPhase = phase
                updateAlarm(phase)
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification())
                if (phase == IncidentPhase.SELF_CHECK && !monitor.incident.triggeredByTest) {
                    incidentId = UUID.randomUUID().toString()
                    cloudAccepted = false
                    lastAttemptAt = 0L
                    (application as CrashAlertApplication).cloudAlertStatus = "60-second check; no alert requested yet"
                }
                if (phase == IncidentPhase.CANCELLED) {
                    if (cloudAccepted) incidentId?.let(dispatcher::cancel)
                    (application as CrashAlertApplication).cloudAlertStatus = if (cloudAccepted)
                        "Cancellation requested; a provider may already have queued messages" else "Check cancelled before any cloud request was accepted"
                }
            }
            if (phase in setOf(IncidentPhase.CONTACT_HELP, IncidentPhase.URGENT_HELP) &&
                !monitor.incident.triggeredByTest && !cloudAccepted && !requestInFlight &&
                android.os.SystemClock.elapsedRealtime() - lastAttemptAt >= 30_000L) {
                val id = incidentId ?: UUID.randomUUID().toString().also { incidentId = it }
                lastAttemptAt = android.os.SystemClock.elapsedRealtime()
                requestInFlight = true
                dispatcher.send(id, (application as CrashAlertApplication).latestBackgroundLocation) { accepted, status ->
                    requestInFlight = false
                    cloudAccepted = accepted
                    (application as CrashAlertApplication).cloudAlertStatus = status
                    if (accepted && monitor.incident.phase == IncidentPhase.CANCELLED) dispatcher.cancel(id)
                }
            }
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL_ID, "Ride protection", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows active ride monitoring and emergency checks"
        }
        val alertChannel = NotificationChannel(ALERT_CHANNEL_ID, "Safety checks", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Visible alerts when unusual motion needs a response"
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).apply {
            createNotificationChannel(channel)
            createNotificationChannel(alertChannel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            getSharedPreferences("protection", MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        val permitted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val locationType = if (permitted) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or locationType)
                if (permitted) startLocationTracking()
            } catch (_: SecurityException) {
                // A boot restart may lack while-in-use location access. Motion checks continue.
                startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification())
            if (permitted) startLocationTracking()
        }
        monitor.startRide()
        handler.removeCallbacks(update)
        handler.post(update)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(update)
        stopLocationTracking()
        alarm?.stop()
        alarm = null
        monitor.endRide("Protection stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocationTracking() {
        if (trackingLocation) return
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        try {
            val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val provider = when {
                fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> return
            }
            manager.requestLocationUpdates(provider, 15_000L, 15f, locationListener, Looper.getMainLooper())
            trackingLocation = true
        } catch (_: SecurityException) { /* Permission or foreground eligibility changed. */ }
        catch (_: IllegalArgumentException) { /* Provider was disabled. */ }
    }

    private fun stopLocationTracking() {
        if (trackingLocation) (getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(locationListener)
        trackingLocation = false
    }

    private fun updateAlarm(phase: IncidentPhase) {
        alarm?.stop()
        alarm = null
        if (phase == IncidentPhase.SELF_CHECK) {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            try { alarm = uri?.let { RingtoneManager.getRingtone(this, it) }; alarm?.play() }
            catch (_: RuntimeException) { /* Notification still opens the self-check. */ }
        }
    }

    private fun notification(): Notification {
        val phase = monitor.incident.phase
        val title = when (phase) {
            IncidentPhase.SELF_CHECK -> "CrashAlert: are you okay?"
            IncidentPhase.CONTACT_HELP -> "CrashAlert: check in now"
            IncidentPhase.URGENT_HELP -> "CrashAlert: urgent check pending"
            else -> "CrashAlert protection active"
        }
        val detail = if (phase in setOf(IncidentPhase.SELF_CHECK, IncidentPhase.CONTACT_HELP, IncidentPhase.URGENT_HELP))
            "Tap to respond. ${ (application as CrashAlertApplication).cloudAlertStatus }"
        else "Motion sensors are monitoring. Tap to open the app."
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val channel = if (phase in setOf(IncidentPhase.SELF_CHECK, IncidentPhase.CONTACT_HELP, IncidentPhase.URGENT_HELP)) ALERT_CHANNEL_ID else CHANNEL_ID
        return Notification.Builder(this, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title).setContentText(detail)
            .setContentIntent(open).setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.crashalert.app.STOP_PROTECTION"
        private const val CHANNEL_ID = "ride_protection"
        private const val ALERT_CHANNEL_ID = "safety_checks"
        private const val NOTIFICATION_ID = 710
    }
}
