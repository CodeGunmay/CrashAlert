package com.crashalert.app.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentEngineTest {
    @Test fun moderateMotionNeedsRecentRotation() {
        val engine = IncidentEngine()
        engine.onSample(MotionSensor.ACCELEROMETER, floatArrayOf(31f, 0f, 0f), 1_000)
        assertEquals(IncidentPhase.MONITORING, engine.state.phase)
        engine.onSample(MotionSensor.GYROSCOPE, floatArrayOf(4f, 0f, 0f), 1_100)
        engine.onSample(MotionSensor.ACCELEROMETER, floatArrayOf(31f, 0f, 0f), 1_300)
        assertEquals(IncidentPhase.SELF_CHECK, engine.state.phase)
        assertFalse(engine.state.triggeredByTest)
    }

    @Test fun staleRotationDoesNotTriggerButSevereImpactDoes() {
        val engine = IncidentEngine()
        engine.onSample(MotionSensor.GYROSCOPE, floatArrayOf(4f, 0f, 0f), 1_000)
        engine.onSample(MotionSensor.ACCELEROMETER, floatArrayOf(31f, 0f, 0f), 2_100)
        assertEquals(IncidentPhase.MONITORING, engine.state.phase)
        engine.onSample(MotionSensor.ACCELEROMETER, floatArrayOf(51f, 0f, 0f), 2_200)
        assertEquals(IncidentPhase.SELF_CHECK, engine.state.phase)
    }

    @Test fun impactThenRotationAlsoOpensSelfCheck() {
        val engine = IncidentEngine()
        engine.onSample(MotionSensor.ACCELEROMETER, floatArrayOf(32f, 0f, 0f), 1_000)
        engine.onSample(MotionSensor.GYROSCOPE, floatArrayOf(4f, 0f, 0f), 1_200)
        assertEquals(IncidentPhase.SELF_CHECK, engine.state.phase)
    }

    @Test fun unansweredCheckAdvancesAcrossThreeTimedStagesWithoutSendingAnything() {
        val engine = IncidentEngine()
        engine.triggerTest(10_000)
        assertTrue(engine.state.triggeredByTest)
        engine.tick(69_001)
        assertEquals(1, engine.state.secondsRemaining)
        engine.tick(70_000)
        assertEquals(IncidentPhase.CONTACT_HELP, engine.state.phase)
        assertEquals(120, engine.state.secondsRemaining)
        engine.tick(190_000)
        assertEquals(IncidentPhase.URGENT_HELP, engine.state.phase)
        engine.tick(310_000)
        assertEquals(1, engine.state.repeatCount)
        assertTrue(engine.state.triggeredByTest)
    }

    @Test fun cancellationBlocksEscalationAndNewRideCanReset() {
        val engine = IncidentEngine()
        engine.triggerTest(10_000)
        engine.cancel(11_000)
        assertEquals(IncidentPhase.CANCELLED, engine.state.phase)
        engine.tick(21_000)
        assertEquals(IncidentPhase.MONITORING, engine.state.phase)
        engine.reset()
        assertEquals(IncidentPhase.MONITORING, engine.state.phase)
    }

    @Test fun invalidSamplesAreIgnored() {
        val engine = IncidentEngine()
        engine.onSample(MotionSensor.ACCELEROMETER, floatArrayOf(Float.NaN, 99f, 99f), 10L)
        engine.onSample(MotionSensor.ACCELEROMETER, floatArrayOf(99f), 20L)
        assertEquals(IncidentPhase.MONITORING, engine.state.phase)
    }
}
