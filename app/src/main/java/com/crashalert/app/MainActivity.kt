package com.crashalert.app

import android.os.Bundle
import android.content.ActivityNotFoundException
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crashalert.app.contacts.ContactRules
import com.crashalert.app.contacts.TrustedContact
import com.crashalert.app.contacts.TrustedContactStore
import com.crashalert.app.telemetry.IncidentPhase
import com.crashalert.app.location.RideLocation
import com.crashalert.app.telemetry.AndroidMotionMonitor
import com.crashalert.app.ui.RideScreen

class MainActivity : ComponentActivity() {
    private lateinit var monitor: AndroidMotionMonitor
    private lateinit var contactStore: TrustedContactStore
    private var contacts by mutableStateOf<List<TrustedContact>>(emptyList())
    private var contactMessage by mutableStateOf<String?>(null)
    private var location by mutableStateOf<RideLocation?>(null)
    private var locationMessage by mutableStateOf<String?>(null)
    private var locationRequest: CancellationSignal? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) locationMessage = "Location permission was not granted"
        else if (canUseContactActions()) fetchLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monitor = AndroidMotionMonitor(this)
        contactStore = TrustedContactStore(this)
        contacts = contactStore.load()
        setContent {
            RideScreen(
                state = monitor.state,
                incident = monitor.incident,
                contacts = contacts,
                contactMessage = contactMessage,
                location = location,
                locationMessage = locationMessage,
                onStart = {
                    location = null
                    locationMessage = null
                    monitor.startRide()
                },
                onEnd = { monitor.endRide() },
                onCancelCheck = monitor::cancelCheck,
                onTestCheck = monitor::testCheck,
                onAddContact = ::addContact,
                onRemoveContact = ::removeContact,
                onComposeSms = ::composeSms,
                onRequestLocation = ::requestLocation
            )
        }
    }

    override fun onStop() {
        locationRequest?.cancel()
        locationRequest = null
        location = null
        locationMessage = null
        monitor.endRide("Ride ended when the app left the foreground")
        super.onStop()
    }

    private fun addContact(name: String, phone: String) {
        val updated = ContactRules.add(contacts, name, phone)
        if (updated == null) {
            contactMessage = "Enter a name and valid number; duplicate numbers are not allowed."
            return
        }
        contacts = updated
        contactStore.save(updated)
        contactMessage = "Contact saved on this device"
    }

    private fun removeContact(contact: TrustedContact) {
        contacts = contacts - contact
        contactStore.save(contacts)
        contactMessage = "Contact removed"
    }

    private fun composeSms(contact: TrustedContact) {
        if (!canUseContactActions() || contact !in contacts) return
        val mapLink = location?.mapLinkIfFresh(SystemClock.elapsedRealtime())
        val body = "CrashAlert: I may need help during a ride. Please call me to check on me." +
            (mapLink?.let { " Approximate location: $it" } ?: "")
        val draft = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", contact.phone, null)).apply {
            putExtra("sms_body", body)
        }
        try {
            contactMessage = "Opening Messages ends ride monitoring. Review and send the draft yourself."
            startActivity(draft)
        } catch (_: ActivityNotFoundException) {
            contactMessage = "No SMS app is available on this device"
        }
    }

    private fun canUseContactActions(): Boolean = monitor.state.active && !monitor.incident.triggeredByTest &&
        monitor.incident.phase in setOf(IncidentPhase.CONTACT_HELP, IncidentPhase.URGENT_HELP)

    private fun requestLocation() {
        if (!canUseContactActions()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchLocation()
        } else {
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun fetchLocation() {
        if (!canUseContactActions()) return
        locationRequest?.cancel()
        location = null
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        if (!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationMessage = "Network location is unavailable; the draft can be sent without it"
            return
        }
        val signal = CancellationSignal()
        locationRequest = signal
        locationMessage = "Finding approximate location…"
        try {
            LocationManagerCompat.getCurrentLocation(manager, LocationManager.NETWORK_PROVIDER, signal, ContextCompat.getMainExecutor(this)) { fix ->
                if (signal.isCanceled || !canUseContactActions()) return@getCurrentLocation
                locationRequest = null
                val ageMillis = fix?.let { SystemClock.elapsedRealtime() - it.elapsedRealtimeNanos / 1_000_000L }
                if (fix == null || ageMillis == null || ageMillis !in 0..30_000L) {
                    locationMessage = "A recent location fix is unavailable; the draft can be sent without it"
                } else {
                    location = RideLocation(fix.latitude, fix.longitude, fix.accuracy, fix.elapsedRealtimeNanos / 1_000_000L)
                    locationMessage = "Approximate location ready. It will be included if still recent when you open a draft."
                }
            }
            mainHandler.postDelayed({
                if (locationRequest === signal) {
                    signal.cancel()
                    locationRequest = null
                    locationMessage = "Location timed out; the draft can be sent without it"
                }
            }, 12_000L)
        } catch (_: SecurityException) {
            signal.cancel()
            locationRequest = null
            locationMessage = "Location permission is unavailable"
        } catch (_: IllegalArgumentException) {
            signal.cancel()
            locationRequest = null
            locationMessage = "Location provider is unavailable"
        }
    }
}
