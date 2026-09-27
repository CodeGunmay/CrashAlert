package com.crashalert.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.crashalert.app.telemetry.IncidentPhase

/** Owns sensor monitoring while the app is closed. An ongoing notification makes it visible. */
class MonitoringService : Service() {
    private val monitor get() = (application as CrashAlertApplication).monitor
    private val handler = Handler(Looper.getMainLooper())
    private var lastPhase: IncidentPhase? = null
    private var alarm: Ringtone? = null
    private val update = object : Runnable {
        override fun run() {
            if (!monitor.state.active) { stopSelf(); return }
            val phase = monitor.incident.phase
            if (phase != lastPhase) {
                lastPhase = phase
                updateAlarm(phase)
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification())
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
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(NOTIFICATION_ID, notification())
        monitor.startRide()
        handler.removeCallbacks(update)
        handler.post(update)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(update)
        alarm?.stop()
        alarm = null
        monitor.endRide("Protection stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
            "Tap to respond. No message is sent automatically."
        else "Motion sensors are monitoring. Tap to open the app."
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, MonitoringService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val channel = if (phase in setOf(IncidentPhase.SELF_CHECK, IncidentPhase.CONTACT_HELP, IncidentPhase.URGENT_HELP)) ALERT_CHANNEL_ID else CHANNEL_ID
        return Notification.Builder(this, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title).setContentText(detail)
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop protection", stop).build())
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.crashalert.app.STOP_PROTECTION"
        private const val CHANNEL_ID = "ride_protection"
        private const val ALERT_CHANNEL_ID = "safety_checks"
        private const val NOTIFICATION_ID = 710
    }
}
