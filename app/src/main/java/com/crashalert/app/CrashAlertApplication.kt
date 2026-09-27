package com.crashalert.app

import android.app.Application
import com.crashalert.app.telemetry.AndroidMotionMonitor

class CrashAlertApplication : Application() {
    val monitor: AndroidMotionMonitor by lazy { AndroidMotionMonitor(this) }
}
