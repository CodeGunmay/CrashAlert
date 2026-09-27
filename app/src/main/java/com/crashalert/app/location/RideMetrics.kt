package com.crashalert.app.location

import android.location.Location
import android.os.SystemClock

data class RideMetrics(
    val speedKph: Int? = null,
    val heading: String? = null,
    val accuracyMeters: Float? = null,
    val capturedAtElapsedMillis: Long? = null
) {
    fun isFresh(nowElapsedMillis: Long): Boolean = capturedAtElapsedMillis?.let {
        nowElapsedMillis - it in 0..15_000L
    } ?: false
}

object RideMetricRules {
    private val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    fun fromLocation(fix: Location): RideMetrics = RideMetrics(
        speedKph = if (fix.hasSpeed() && fix.speed.isFinite() && fix.speed >= 0f) (fix.speed * 3.6f).toInt() else null,
        heading = if (fix.hasBearing() && fix.bearing.isFinite()) directions[((fix.bearing + 22.5f).toInt() / 45) % 8] else null,
        accuracyMeters = if (fix.hasAccuracy()) fix.accuracy else null,
        capturedAtElapsedMillis = fix.elapsedRealtimeNanos / 1_000_000L
    )
}
