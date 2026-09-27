package com.crashalert.app

import android.app.Application
import com.crashalert.app.telemetry.AndroidMotionMonitor
import com.crashalert.app.location.RideLocation
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class CrashAlertApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.FIREBASE_APP_ID.isNotBlank() && BuildConfig.FIREBASE_API_KEY.isNotBlank() && BuildConfig.FIREBASE_PROJECT_ID.isNotBlank()) {
            FirebaseApp.initializeApp(this, FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.FIREBASE_APP_ID).setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID).build())
        }
    }
    val monitor: AndroidMotionMonitor by lazy { AndroidMotionMonitor(this) }
    var latestBackgroundLocation by mutableStateOf<RideLocation?>(null)
        private set
    var cloudAlertStatus by mutableStateOf("Cloud delivery not connected")

    fun recordLocation(fix: RideLocation) {
        val previous = latestBackgroundLocation
        val stale = previous == null || fix.capturedAtElapsedMillis - previous.capturedAtElapsedMillis > 120_000L
        if (stale || fix.accuracyMeters <= previous!!.accuracyMeters) latestBackgroundLocation = fix
    }
}
