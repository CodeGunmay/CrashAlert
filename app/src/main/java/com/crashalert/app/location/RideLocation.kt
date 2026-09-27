package com.crashalert.app.location

import java.util.Locale

data class RideLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAtElapsedMillis: Long
) {
    fun mapLinkIfFresh(nowElapsedMillis: Long): String? {
        val age = nowElapsedMillis - capturedAtElapsedMillis
        if (age !in 0..30_000L || !latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) return null
        return String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", latitude, longitude)
    }
}
