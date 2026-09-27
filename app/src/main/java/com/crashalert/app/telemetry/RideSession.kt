package com.crashalert.app.telemetry

enum class MotionSensor { ACCELEROMETER, GYROSCOPE }

data class VectorReading(
    val x: Float,
    val y: Float,
    val z: Float,
    val receivedAtMillis: Long,
    val accuracy: Int
)

data class RideState(
    val active: Boolean = false,
    val accelerometerAvailable: Boolean = false,
    val gyroscopeAvailable: Boolean = false,
    val startedAtMillis: Long? = null,
    val accelerometer: VectorReading? = null,
    val gyroscope: VectorReading? = null,
    val diagnostic: String = "Checking motion sensors"
)

/** Pure state transitions; Android sensor registration belongs to [AndroidMotionMonitor]. */
class RideSession(initial: RideState = RideState()) {
    var state: RideState = initial
        private set

    fun availability(accelerometer: Boolean, gyroscope: Boolean) {
        state = state.copy(
            accelerometerAvailable = accelerometer,
            gyroscopeAvailable = gyroscope,
            diagnostic = when {
                !accelerometer && !gyroscope -> "No motion sensors found on this device"
                !accelerometer || !gyroscope -> "Limited readings: one motion sensor unavailable"
                else -> "Motion sensors ready"
            }
        )
    }

    fun start(atMillis: Long): Boolean {
        if (state.active || (!state.accelerometerAvailable && !state.gyroscopeAvailable)) return false
        state = state.copy(
            active = true,
            startedAtMillis = atMillis,
            accelerometer = null,
            gyroscope = null,
            diagnostic = "Waiting for sensor readings"
        )
        return true
    }

    fun sample(sensor: MotionSensor, values: FloatArray, accuracy: Int, atMillis: Long) {
        if (!state.active || values.size < 3) return
        val available = when (sensor) {
            MotionSensor.ACCELEROMETER -> state.accelerometerAvailable
            MotionSensor.GYROSCOPE -> state.gyroscopeAvailable
        }
        if (!available) return
        val reading = VectorReading(values[0], values[1], values[2], atMillis, accuracy)
        state = when (sensor) {
            MotionSensor.ACCELEROMETER -> state.copy(accelerometer = reading, diagnostic = "Receiving live readings")
            MotionSensor.GYROSCOPE -> state.copy(gyroscope = reading, diagnostic = "Receiving live readings")
        }
    }

    fun stop(reason: String = "Ride ended") {
        if (state.active) state = state.copy(active = false, diagnostic = reason)
    }
}
