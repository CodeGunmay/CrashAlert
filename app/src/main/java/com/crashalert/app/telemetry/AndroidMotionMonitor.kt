package com.crashalert.app.telemetry

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Samples sensors only while the ride and its Activity are in the foreground. */
class AndroidMotionMonitor(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val session = RideSession()
    private val incidentEngine = IncidentEngine()
    private val timer = object : Runnable {
        override fun run() {
            if (!state.active) return
            incidentEngine.tick(SystemClock.elapsedRealtime())
            incident = incidentEngine.state
            mainHandler.postDelayed(this, 250L)
        }
    }

    var state by mutableStateOf(session.state)
        private set
    var incident by mutableStateOf(incidentEngine.state)
        private set

    init {
        session.availability(accelerometer != null, gyroscope != null)
        state = session.state
    }

    fun startRide() {
        if (!session.start(System.currentTimeMillis())) return
        incidentEngine.reset()
        incident = incidentEngine.state
        var registered = false
        accelerometer?.let {
            registered = manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI, mainHandler) || registered
        }
        gyroscope?.let {
            registered = manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI, mainHandler) || registered
        }
        if (!registered) session.stop("Could not start sensors; try again")
        state = session.state
        if (state.active) mainHandler.post(timer)
    }

    fun endRide(reason: String = "Ride ended") {
        mainHandler.removeCallbacks(timer)
        manager.unregisterListener(this)
        session.stop(reason)
        incidentEngine.reset()
        incident = incidentEngine.state
        state = session.state
    }

    fun cancelCheck() {
        incidentEngine.cancel(SystemClock.elapsedRealtime())
        incident = incidentEngine.state
    }

    fun testCheck() {
        if (!state.active) return
        incidentEngine.triggerTest(SystemClock.elapsedRealtime())
        incident = incidentEngine.state
    }

    override fun onSensorChanged(event: SensorEvent) {
        val sensor = when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> MotionSensor.ACCELEROMETER
            Sensor.TYPE_GYROSCOPE -> MotionSensor.GYROSCOPE
            else -> return
        }
        session.sample(sensor, event.values, event.accuracy, System.currentTimeMillis())
        if (state.active) {
            incidentEngine.onSample(sensor, event.values, event.timestamp / 1_000_000L)
            incident = incidentEngine.state
        }
        state = session.state
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
