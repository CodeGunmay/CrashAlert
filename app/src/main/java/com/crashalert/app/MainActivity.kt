package com.crashalert.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.crashalert.app.telemetry.AndroidMotionMonitor
import com.crashalert.app.ui.RideScreen

class MainActivity : ComponentActivity() {
    private lateinit var monitor: AndroidMotionMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monitor = AndroidMotionMonitor(this)
        setContent {
            RideScreen(
                state = monitor.state,
                incident = monitor.incident,
                onStart = monitor::startRide,
                onEnd = { monitor.endRide() },
                onCancelCheck = monitor::cancelCheck,
                onTestCheck = monitor::testCheck
            )
        }
    }

    override fun onStop() {
        monitor.endRide("Ride ended when the app left the foreground")
        super.onStop()
    }
}
