package com.crashalert.app.dispatch

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.crashalert.app.BuildConfig
import com.crashalert.app.contacts.TrustedContactStore
import com.crashalert.app.location.RideLocation
import com.crashalert.app.profile.ProfileRules
import com.crashalert.app.profile.RiderProfileStore
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Best-effort cloud request. Backend owns provider credentials and scheduled follow-ups. */
class AlertDispatcher(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun ready(): Boolean = BuildConfig.API_URL.startsWith("https://") && FirebaseApp.getApps(context).isNotEmpty() &&
        FirebaseAuth.getInstance().currentUser != null

    fun send(eventId: String, fix: RideLocation?, onDone: (Boolean, String) -> Unit) {
        if (!ready()) { onDone(false, "Cloud alerts unavailable: configure and verify phone in Profile"); return }
        val profile = RiderProfileStore(context).load()
        val contacts = TrustedContactStore(context).load()
        if (profile == null || contacts.isEmpty() || contacts.any { !it.phone.matches(Regex("\\+[1-9][0-9]{6,14}")) }) {
            onDone(false, "Cloud alert needs a rider and contacts with international +country-code numbers")
            return
        }
        val payload = JSONObject().put("eventId", eventId).put("riderName", profile.fullName)
            .put("contacts", JSONArray().apply { contacts.forEach { put(JSONObject().put("name", it.name).put("phone", it.phone)) } })
        ProfileRules.automaticMedicalSummary(profile).takeIf { it.isNotEmpty() }?.let { payload.put("medicalId", it) }
        fix?.let {
            val age = SystemClock.elapsedRealtime() - it.capturedAtElapsedMillis
            if (age in 0..30 * 60_000L) payload.put("location", JSONObject()
                .put("latitude", it.latitude).put("longitude", it.longitude)
                .put("accuracyMeters", it.accuracyMeters).put("capturedAt", System.currentTimeMillis() - age))
        }
        FirebaseAuth.getInstance().currentUser!!.getIdToken(false).addOnSuccessListener { token ->
            val jwt = token.token ?: run { onDone(false, "Phone session unavailable"); return@addOnSuccessListener }
            executor.execute {
                val result = try {
                    val connection = URL(BuildConfig.API_URL.trimEnd('/') + "/v1/incidents").openConnection() as HttpURLConnection
                    try {
                        connection.requestMethod = "POST"
                        connection.connectTimeout = 8_000
                        connection.readTimeout = 15_000
                        connection.setRequestProperty("Authorization", "Bearer $jwt")
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.doOutput = true
                        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                        if (connection.responseCode in 200..299) true to "Cloud received alert request; SMS delivery is not confirmed"
                        else false to "Cloud rejected alert request (HTTP ${connection.responseCode})"
                    } finally { connection.disconnect() }
                } catch (_: Exception) { false to "No cloud connection; retrying while monitoring runs" }
                main.post { onDone(result.first, result.second) }
            }
        }.addOnFailureListener { onDone(false, "Phone sign-in expired; verify in Profile") }
    }

    fun cancel(eventId: String, onDone: (Boolean) -> Unit) {
        if (!ready()) { onDone(false); return }
        FirebaseAuth.getInstance().currentUser!!.getIdToken(false).addOnSuccessListener { token ->
            val jwt = token.token ?: run { onDone(false); return@addOnSuccessListener }
            executor.execute {
                val success = try {
                    val connection = URL(BuildConfig.API_URL.trimEnd('/') + "/v1/incidents/$eventId/cancel").openConnection() as HttpURLConnection
                    try {
                        connection.requestMethod = "POST"
                        connection.connectTimeout = 8_000
                        connection.readTimeout = 8_000
                        connection.setRequestProperty("Authorization", "Bearer $jwt")
                        connection.responseCode in 200..299
                    } finally { connection.disconnect() }
                } catch (_: Exception) { false }
                main.post { onDone(success) }
            }
        }.addOnFailureListener { onDone(false) }
    }
}
