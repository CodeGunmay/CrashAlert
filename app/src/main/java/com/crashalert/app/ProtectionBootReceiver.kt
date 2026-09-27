package com.crashalert.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Restarts opt-in motion protection after boot or package update when Android allows it. */
class ProtectionBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        if (!context.getSharedPreferences("protection", Context.MODE_PRIVATE).getBoolean("enabled", false)) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, MonitoringService::class.java))
        } catch (_: RuntimeException) {
            // Some devices block background starts. Opening the app can restart protection.
        }
    }
}
