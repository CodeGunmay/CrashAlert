package com.crashalert.app.telemetry

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
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

    var state by mutableStateOf(session.state)
        private set

    init {
        session.availability(accelerometer != null, gyroscope != null)
        state = session.state
    }

    fun startRide() {
        if (!session.start(System.currentTimeMillis())) return
        var registered = false
        accelerometer?.let {
            registered = manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI, mainHandler) || registered
        }
        gyroscope?.let {
            registered = manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI, mainHandler) || registered
        }
        if (!registered) session.stop("Could not start sensors; try again")
        state = session.state
    }

    fun endRide(reason: String = "Ride ended") {
        manager.unregisterListener(this)
        session.stop(reason)
        state = session.state
    }

    override fun onSensorChanged(event: SensorEvent) {
        val sensor = when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> MotionSensor.ACCELEROMETER
            Sensor.TYPE_GYROSCOPE -> MotionSensor.GYROSCOPE
            else -> return
        }
        session.sample(sensor, event.values, event.accuracy, System.currentTimeMillis())
        state = session.state
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
