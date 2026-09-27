package com.crashalert.app.telemetry

import kotlin.math.sqrt

enum class IncidentPhase { MONITORING, SELF_CHECK, CONTACT_HELP, URGENT_HELP, CANCELLED }

data class IncidentState(
    val phase: IncidentPhase = IncidentPhase.MONITORING,
    val triggeredByTest: Boolean = false,
    val impactAtMillis: Long? = null,
    val nextTransitionAtMillis: Long? = null,
    val secondsRemaining: Int = 0,
    val repeatCount: Int = 0
)

/**
 * A conservative, unvalidated heuristic for raising a self-check prompt.
 * It is not a medical or road-safety classification. This engine never sends messages.
 * All times use the monotonic sensor/elapsed-realtime clock, never wall time.
 */
class IncidentEngine {
    var state = IncidentState()
        private set

    private var recentRotationAtMillis: Long? = null
    private var recentImpactAtMillis: Long? = null

    fun reset() {
        recentRotationAtMillis = null
        recentImpactAtMillis = null
        state = IncidentState()
    }

    fun onSample(sensor: MotionSensor, values: FloatArray, atMillis: Long) {
        if (state.phase != IncidentPhase.MONITORING || values.size < 3 || values.any { !it.isFinite() }) return
        val magnitude = sqrt(values.take(3).sumOf { (it * it).toDouble() })
        if (sensor == MotionSensor.GYROSCOPE) {
            if (magnitude >= 3.0) {
                recentRotationAtMillis = atMillis
                if (recentImpactAtMillis?.let { atMillis >= it && atMillis - it <= 1_000L } == true) {
                    beginSelfCheck(atMillis, test = false)
                }
            }
            return
        }
        val rotationIsRecent = recentRotationAtMillis?.let { atMillis >= it && atMillis - it <= 1_000L } == true
        // Accelerometer includes gravity. These values only prompt a user check.
        if (magnitude >= 50.0 || (magnitude >= 30.0 && rotationIsRecent)) {
            beginSelfCheck(atMillis, test = false)
        } else if (magnitude >= 30.0) {
            recentImpactAtMillis = atMillis
        }
    }

    fun triggerTest(atMillis: Long) {
        if (state.phase == IncidentPhase.MONITORING) beginSelfCheck(atMillis, test = true)
    }

    fun tick(atMillis: Long) {
        val deadline = state.nextTransitionAtMillis ?: return
        if (atMillis >= deadline) {
            state = when (state.phase) {
                IncidentPhase.SELF_CHECK -> state.copy(
                    phase = IncidentPhase.CONTACT_HELP,
                    nextTransitionAtMillis = deadline + 120_000L,
                    secondsRemaining = 120
                )
                IncidentPhase.CONTACT_HELP -> state.copy(
                    phase = IncidentPhase.URGENT_HELP,
                    nextTransitionAtMillis = deadline + 120_000L,
                    secondsRemaining = 120
                )
                IncidentPhase.URGENT_HELP -> state.copy(
                    nextTransitionAtMillis = deadline + 120_000L,
                    secondsRemaining = 120,
                    repeatCount = state.repeatCount + 1
                )
                IncidentPhase.CANCELLED -> IncidentState()
                else -> state
            }
            // At most one extra call is needed to cross from self-check to urgent help.
            if (state.phase == IncidentPhase.CONTACT_HELP && atMillis >= state.nextTransitionAtMillis!!) {
                tick(atMillis)
                return
            }
            // Catch up across missed repeats without one call per interval.
            if (state.phase == IncidentPhase.URGENT_HELP && state.nextTransitionAtMillis != null &&
                atMillis >= state.nextTransitionAtMillis!!
            ) {
                val missed = (atMillis - state.nextTransitionAtMillis!!) / 120_000L + 1L
                state = state.copy(
                    repeatCount = (state.repeatCount.toLong() + missed).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    nextTransitionAtMillis = state.nextTransitionAtMillis!! + missed * 120_000L
                )
            }
            state.nextTransitionAtMillis?.let { next ->
                state = state.copy(secondsRemaining = ((next - atMillis + 999L) / 1_000L).toInt())
            }
        } else {
            state = state.copy(secondsRemaining = ((deadline - atMillis + 999L) / 1_000L).toInt())
        }
    }

    fun cancel(atMillis: Long) {
        if (state.phase == IncidentPhase.SELF_CHECK ||
            state.phase == IncidentPhase.CONTACT_HELP ||
            state.phase == IncidentPhase.URGENT_HELP
        ) {
            state = state.copy(phase = IncidentPhase.CANCELLED, nextTransitionAtMillis = atMillis + 10_000L, secondsRemaining = 10)
        }
    }

    private fun beginSelfCheck(atMillis: Long, test: Boolean) {
        state = IncidentState(
            phase = IncidentPhase.SELF_CHECK,
            triggeredByTest = test,
            impactAtMillis = atMillis,
            nextTransitionAtMillis = atMillis + 60_000L,
            secondsRemaining = 60
        )
    }
}
