package com.crashalert.app

import android.os.Bundle
import android.content.ActivityNotFoundException
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.IntentFilter
import android.os.BatteryManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.media.RingtoneManager
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
import androidx.compose.runtime.DisposableEffect
import com.crashalert.app.contacts.ContactRules
import com.crashalert.app.contacts.TrustedContact
import com.crashalert.app.contacts.TrustedContactStore
import com.crashalert.app.telemetry.IncidentPhase
import com.crashalert.app.location.RideLocation
import com.crashalert.app.location.RideMetrics
import com.crashalert.app.location.RideMetricRules
import com.crashalert.app.profile.RiderProfile
import com.crashalert.app.profile.RiderProfileStore
import com.crashalert.app.telemetry.AndroidMotionMonitor
import com.crashalert.app.ui.RideScreen

class MainActivity : ComponentActivity() {
    private lateinit var monitor: AndroidMotionMonitor
    private lateinit var contactStore: TrustedContactStore
    private lateinit var profileStore: RiderProfileStore
    private var profile by mutableStateOf<RiderProfile?>(null)
    private var profileMessage by mutableStateOf<String?>(null)
    private var contacts by mutableStateOf<List<TrustedContact>>(emptyList())
    private var contactMessage by mutableStateOf<String?>(null)
    private var location by mutableStateOf<RideLocation?>(null)
    private var includeLocationInDraft = false
    private var metrics by mutableStateOf(RideMetrics())
    private var batteryPercent by mutableStateOf<Int?>(null)
    private var locationMessage by mutableStateOf<String?>(null)
    private var locationRequest: CancellationSignal? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val rideLocationListener = LocationListener { fix -> acceptRideLocation(fix) }
    private val rideLocationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            startRideLocationUpdates()
        } else locationMessage = "Location off: speed and heading unavailable; motion check still works"
    }
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) locationMessage = "Location permission was not granted"
        else if (canUseContactActions()) fetchLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monitor = AndroidMotionMonitor(this)
        contactStore = TrustedContactStore(this)
        profileStore = RiderProfileStore(this)
        profile = profileStore.load()
        contacts = contactStore.load()
        readBattery()
        setContent {
            val phase = monitor.incident.phase
            DisposableEffect(phase) {
                val tone = if (phase == IncidentPhase.SELF_CHECK) {
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    uri?.let { RingtoneManager.getRingtone(this@MainActivity, it) }
                } else null
                try { tone?.play() } catch (_: RuntimeException) { /* Silent devices still show the check. */ }
                onDispose { tone?.stop() }
            }
            RideScreen(
                state = monitor.state,
                incident = monitor.incident,
                contacts = contacts,
                profile = profile,
                profileMessage = profileMessage,
                metrics = metrics,
                batteryPercent = batteryPercent,
                contactMessage = contactMessage,
                location = location,
                locationMessage = locationMessage,
                onStart = {
                    location = null
                    includeLocationInDraft = false
                    locationMessage = null
                    monitor.startRide()
                    if (monitor.state.active) requestRideLocation()
                },
                onEnd = {
                    stopRideLocationUpdates()
                    monitor.endRide()
                },
                onCancelCheck = monitor::cancelCheck,
                onTestCheck = monitor::testCheck,
                onAddContact = ::addContact,
                onRemoveContact = ::removeContact,
                onComposeSms = ::composeSms,
                onRequestLocation = ::requestLocation,
                onSaveProfile = ::saveProfile
            )
        }
    }

    override fun onStop() {
        stopRideLocationUpdates()
        locationRequest?.cancel()
        locationRequest = null
        location = null
        includeLocationInDraft = false
        locationMessage = null
        monitor.endRide("Ride ended when the app left the foreground")
        super.onStop()
    }

    private fun saveProfile(value: RiderProfile): Boolean {
        val saved = profileStore.save(value)
        if (saved) {
            profile = value.copy(fullName = value.fullName.trim())
            profileMessage = "Saved securely on this device"
        } else profileMessage = "Could not save. Check name, date (YYYY-MM-DD), and field lengths."
        return saved
    }

    private fun readBattery() {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else null
    }

    private fun requestRideLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startRideLocationUpdates()
        } else rideLocationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    private fun startRideLocationUpdates() {
        if (!monitor.state.active) return
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        stopRideLocationUpdates()
        try {
            val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val provider = when {
                fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) { locationMessage = "Location services off: speed unavailable"; return }
            manager.requestLocationUpdates(provider, 2_000L, 3f, rideLocationListener, Looper.getMainLooper())
            locationMessage = if (provider == LocationManager.GPS_PROVIDER) "Waiting for GPS fix" else "Waiting for approximate location fix"
        } catch (_: SecurityException) { locationMessage = "Location permission unavailable" }
        catch (_: IllegalArgumentException) { locationMessage = "Location provider unavailable" }
    }

    private fun stopRideLocationUpdates() {
        (getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(rideLocationListener)
        metrics = RideMetrics()
    }

    private fun acceptRideLocation(fix: Location) {
        if (!monitor.state.active) return
        val age = SystemClock.elapsedRealtime() - fix.elapsedRealtimeNanos / 1_000_000L
        if (age !in 0..15_000L) return
        metrics = RideMetricRules.fromLocation(fix)
        val newFix = RideLocation(fix.latitude, fix.longitude, fix.accuracy, fix.elapsedRealtimeNanos / 1_000_000L)
        if (location == null || location?.mapLinkIfFresh(SystemClock.elapsedRealtime()) == null || newFix.accuracyMeters <= location!!.accuracyMeters) {
            location = newFix
        }
        locationMessage = "Location fix ready (±${fix.accuracy.toInt()} m)"
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
        val mapLink = if (includeLocationInDraft) location?.mapLinkIfFresh(SystemClock.elapsedRealtime()) else null
        val medical = profile?.let(com.crashalert.app.profile.ProfileRules::medicalSummary).orEmpty()
        val body = "CrashAlert: ${profile?.fullName ?: "Rider"} may need help during a ride. Please call to check." +
            (mapLink?.let { " Last location (not live): $it" } ?: "") +
            (medical.takeIf(String::isNotEmpty)?.let { " $it" } ?: "")
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
        includeLocationInDraft = true
        if (location?.mapLinkIfFresh(SystemClock.elapsedRealtime()) != null) {
            locationMessage = "Recent location will be included in the SMS draft"
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchLocation()
        } else {
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun fetchLocation() {
        if (!canUseContactActions()) return
        locationRequest?.cancel()
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val provider = if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
        if (!manager.isProviderEnabled(provider)) {
            locationMessage = "Network location is unavailable; the draft can be sent without it"
            return
        }
        val signal = CancellationSignal()
        locationRequest = signal
        locationMessage = "Finding approximate location…"
        try {
            LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(this)) { fix ->
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
