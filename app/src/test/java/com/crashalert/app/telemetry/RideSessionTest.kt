package com.crashalert.app.telemetry

import org.junit.Assert.*
import org.junit.Test

class RideSessionTest {
    @Test fun noSensorsPreventsStart() {
        val session = RideSession()
        session.availability(false, false)
        assertFalse(session.start(100L))
        assertFalse(session.state.active)
    }

    @Test fun oneAvailableSensorStillReportsReadings() {
        val session = RideSession()
        session.availability(true, false)
        assertTrue(session.start(100L))
        session.sample(MotionSensor.ACCELEROMETER, floatArrayOf(1f, 2f, 3f), 3, 120L)
        session.sample(MotionSensor.GYROSCOPE, floatArrayOf(4f, 5f, 6f), 3, 121L)
        assertEquals(2f, session.state.accelerometer?.y)
        assertEquals(120L, session.state.accelerometer?.receivedAtMillis)
        assertNull(session.state.gyroscope)
    }

    @Test fun stoppedRideIgnoresEventsAndNewRideClearsOldReadings() {
        val session = RideSession()
        session.availability(true, true)
        session.start(100L)
        session.sample(MotionSensor.GYROSCOPE, floatArrayOf(1f, 2f, 3f), 2, 110L)
        session.stop()
        session.sample(MotionSensor.GYROSCOPE, floatArrayOf(9f, 9f, 9f), 2, 120L)
        assertEquals(1f, session.state.gyroscope?.x)
        session.start(200L)
        assertNull(session.state.gyroscope)
        assertEquals(200L, session.state.startedAtMillis)
    }

    @Test fun invalidVectorIsIgnored() {
        val session = RideSession()
        session.availability(true, true)
        session.start(100L)
        session.sample(MotionSensor.ACCELEROMETER, floatArrayOf(1f), 0, 110L)
        assertNull(session.state.accelerometer)
    }
}
