package com.crashalert.app.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crashalert.app.contacts.ContactRules
import com.crashalert.app.contacts.TrustedContact
import com.crashalert.app.location.RideLocation
import com.crashalert.app.location.RideMetrics
import com.crashalert.app.profile.RiderProfile
import com.crashalert.app.telemetry.IncidentPhase
import com.crashalert.app.telemetry.IncidentState
import com.crashalert.app.telemetry.RideState
import com.crashalert.app.telemetry.VectorReading
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.sqrt

private val Dark = Color(0xFF0D1B2A)
private val Panel = Color(0xFF172939)
private val Aqua = Color(0xFF2EC4B6)
private val Red = Color(0xFFFF4B3E)
private val Pale = Color(0xFFF5F7FA)

private enum class Page(val title: String, val symbol: String) {
    HOME("Home", "⌂"), RIDE("Ride", "◉"), SERVICES("Services", "⌖"), CONTACTS("Contacts", "♧"), PROFILE("Profile", "●")
}

@Composable
fun CrashAlertAppScreen(
    state: RideState, incident: IncidentState, profile: RiderProfile?, profileMessage: String?,
    contacts: List<TrustedContact>, contactMessage: String?, location: RideLocation?, locationMessage: String?,
    metrics: RideMetrics, batteryPercent: Int?,
    onEnableProtection: () -> Unit, onPauseProtection: () -> Unit,
    onTestCheck: () -> Unit, onCancelCheck: () -> Unit, onCallEmergency: () -> Unit,
    onCallContact: (TrustedContact) -> Unit, onFindNearby: (String) -> Unit,
    onAddContact: (String, String) -> Unit, onRemoveContact: (TrustedContact) -> Unit,
    onComposeSms: (TrustedContact) -> Unit, onRequestLocation: () -> Unit,
    onSaveProfile: (RiderProfile) -> Boolean
) {
    var selected by rememberSaveable { mutableStateOf(Page.HOME.name) }
    var editingProfile by rememberSaveable { mutableStateOf(false) }
    val phaseVisible = incident.phase in setOf(IncidentPhase.SELF_CHECK, IncidentPhase.CONTACT_HELP, IncidentPhase.URGENT_HELP)
    MaterialTheme {
        if (profile == null || editingProfile) {
            Surface(color = Pale) {
                ProfileForm(profile, profileMessage,
                    onSave = { if (onSaveProfile(it)) editingProfile = false },
                    onCancel = { editingProfile = false })
            }
        } else if (phaseVisible) {
            EmergencyPage(incident, contacts, locationMessage, onCancelCheck,
                onCallEmergency, onCallContact, onComposeSms, onRequestLocation)
        } else {
            Scaffold(containerColor = Dark, bottomBar = {
                NavigationBar(containerColor = Panel) {
                    Page.entries.forEach { page ->
                        NavigationBarItem(
                            selected = selected == page.name, onClick = { selected = page.name },
                            icon = { Text(page.symbol, color = if (selected == page.name) Aqua else Color.LightGray, fontSize = 21.sp) },
                            label = { Text(page.title) }
                        )
                    }
                }
            }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (Page.valueOf(selected)) {
                        Page.HOME -> HomePage(profile, state, contacts, location, metrics, batteryPercent)
                        Page.RIDE -> RidePage(state, metrics, location, locationMessage, batteryPercent)
                        Page.SERVICES -> ServicesPage(location, onFindNearby, onCallEmergency)
                        Page.CONTACTS -> ContactsPage(contacts, contactMessage, onAddContact, onRemoveContact, onCallContact)
                        Page.PROFILE -> ProfilePage(profile, state, onEdit = { editingProfile = true }, onEnableProtection, onTestCheck)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePage(
    profile: RiderProfile, state: RideState, contacts: List<TrustedContact>,
    location: RideLocation?, metrics: RideMetrics, batteryPercent: Int?
) {
    Text("Good to see you,", color = Color.LightGray)
    Text(profile.fullName.substringBefore(' '), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
    DarkCard {
        Text(if (state.active) "PROTECTED" else "PROTECTION PAUSED", color = Aqua, fontWeight = FontWeight.Bold)
        Text(if (state.active) "Motion monitoring active" else "Motion monitoring is off", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(if (state.active) "Continues with the app closed · ongoing notification" else "Enable protection to resume sensor monitoring", color = Color.LightGray)
        Text(state.diagnostic, color = Color.LightGray, fontSize = 12.sp)
    }
    DarkCard {
        Text("CURRENT LOCATION", color = Aqua, fontWeight = FontWeight.Bold)
        val fresh = metrics.isFresh(SystemClock.elapsedRealtime())
        Text(if (fresh && location != null) "%.5f, %.5f".format(Locale.US, location.latitude, location.longitude) else "No current fix", color = Color.White)
        Text(if (fresh) "Reported accuracy ±${metrics.accuracyMeters?.toInt() ?: "?"} m" else "Location updates run while this screen is open", color = Color.LightGray)
        Text("Battery ${batteryPercent?.let { "$it%" } ?: "unavailable"}", color = Color.LightGray)
    }
    DarkCard {
        Text("EMERGENCY CONTACTS", color = Aqua, fontWeight = FontWeight.Bold)
        Text(if (contacts.isEmpty()) "Add a trusted contact in Contacts" else contacts.joinToString(" · ") { it.name }, color = Color.White)
        Text("Medical ID: ${profile.bloodGroup.ifBlank { "blood group not set" }} · ${if (profile.includeMedicalInDraft) "opted in for manual SMS drafts" else "private"}", color = Color.LightGray)
    }
}

@Composable
private fun RidePage(
    state: RideState, metrics: RideMetrics, location: RideLocation?, locationMessage: String?, batteryPercent: Int?
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.active) {
        while (state.active) { now = System.currentTimeMillis(); delay(1_000L) }
    }
    Text("Ride", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
    Text("Live sensor readings while monitoring runs automatically", color = Color.LightGray)
    val elapsed = state.startedAtMillis?.takeIf { state.active }?.let { ((now - it) / 1_000L).coerceAtLeast(0L) } ?: 0L
    DarkCard {
        Text("PROTECTION UPTIME", color = Aqua)
        Text("%02d:%02d:%02d".format(Locale.US, elapsed / 3600, (elapsed / 60) % 60, elapsed % 60),
            color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("Background crash checks ${if (state.active) "active" else "paused"}", color = Color.LightGray)
    }
    val fresh = metrics.isFresh(SystemClock.elapsedRealtime())
    DarkCard {
        Text("LIVE LOCATION DATA", color = Aqua, fontWeight = FontWeight.Bold)
        Text("Speed: ${if (fresh) metrics.speedKph?.let { "$it km/h" } ?: "Unavailable" else "Unavailable"}", color = Color.White, fontSize = 22.sp)
        Text("Heading: ${if (fresh) metrics.heading ?: "Unavailable" else "Unavailable"}", color = Color.White)
        Text(if (fresh && location != null) "%.5f, %.5f · ±%d m".format(Locale.US, location.latitude, location.longitude, location.accuracyMeters.toInt())
            else locationMessage ?: "Waiting for a usable location fix", color = Color.LightGray)
        Text("Battery ${batteryPercent?.let { "$it%" } ?: "unavailable"} · location only updates while app is open", color = Color.LightGray, fontSize = 12.sp)
    }
    DarkCard {
        Text("IMPACT MONITOR", color = Aqua, fontWeight = FontWeight.Bold)
        val acceleration = state.accelerometer?.let { sqrt((it.x * it.x + it.y * it.y + it.z * it.z).toDouble()) / 9.80665 }
        Text("${acceleration?.let { "%.1f G".format(Locale.US, it) } ?: "No reading"} · acceleration magnitude including gravity", color = Color.White)
        SensorLine("Accelerometer", state.accelerometer, "m/s²")
        SensorLine("Gyroscope", state.gyroscope, "rad/s")
        Text("Detection thresholds are an unvalidated heuristic; no accuracy claim is made.", color = Color.LightGray, fontSize = 12.sp)
    }
}

@Composable
private fun SensorLine(title: String, reading: VectorReading?, unit: String) {
    Text(if (reading == null) "$title: unavailable" else "$title: %.1f / %.1f / %.1f $unit".format(Locale.US, reading.x, reading.y, reading.z), color = Color.LightGray)
}

@Composable
private fun ServicesPage(location: RideLocation?, onFindNearby: (String) -> Unit, onCallEmergency: () -> Unit) {
    Text("Nearby help", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
    Text("Open a map search when you need a hospital or police station. Places are not auto-contacted.", color = Color.LightGray)
    DarkCard {
        Text("YOUR LAST LOCATION", color = Aqua, fontWeight = FontWeight.Bold)
        Text(if (location != null) "%.5f, %.5f · ±%d m".format(Locale.US, location.latitude, location.longitude, location.accuracyMeters.toInt()) else "Unavailable", color = Color.White)
        Text("The map app handles its own location. No hospital distance is guessed here.", color = Color.LightGray)
    }
    Button(onClick = { onFindNearby("emergency hospital") }, modifier = Modifier.fillMaxWidth()) { Text("Find hospitals in Maps") }
    Button(onClick = { onFindNearby("police station") }, modifier = Modifier.fillMaxWidth()) { Text("Find police stations in Maps") }
    OutlinedButton(onClick = onCallEmergency, modifier = Modifier.fillMaxWidth()) { Text("Open emergency dialer · 112") }
}

@Composable
private fun ContactsPage(
    contacts: List<TrustedContact>, message: String?,
    onAdd: (String, String) -> Unit, onRemove: (TrustedContact) -> Unit, onCall: (TrustedContact) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    Text("Emergency contacts", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
    Text("Up to three numbers saved on this device. No automatic message is sent yet.", color = Color.LightGray)
    contacts.forEachIndexed { index, contact ->
        DarkCard {
            Text(if (index == 0) "PRIMARY CONTACT" else "CONTACT ${index + 1}", color = Aqua, fontWeight = FontWeight.Bold)
            Text(contact.name, color = Color.White, fontSize = 21.sp)
            Text(contact.phone, color = Color.LightGray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onCall(contact) }) { Text("Call") }
                OutlinedButton(onClick = { onRemove(contact) }) { Text("Remove") }
            }
        }
    }
    if (contacts.size < ContactRules.MAX_CONTACTS) DarkCard {
        Text("Add contact", color = Color.White, fontWeight = FontWeight.Bold)
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text("Phone number") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            onAdd(name, phone)
            if (ContactRules.add(contacts, name, phone) != null) { name = ""; phone = "" }
        }) { Text("Save contact") }
    }
    if (message != null) Text(message, color = Color.LightGray)
}

@Composable
private fun ProfilePage(
    profile: RiderProfile, state: RideState, onEdit: () -> Unit, onEnable: () -> Unit, onTest: () -> Unit
) {
    Text("Profile", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
    DarkCard {
        Text(profile.fullName, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Medical ID · ${profile.bloodGroup.ifBlank { "Blood group not set" }}", color = Aqua)
        Text(if (profile.includeMedicalInDraft) "Included in manual SMS drafts" else "Medical details private on this device", color = Color.LightGray)
        OutlinedButton(onClick = onEdit) { Text("Edit rider and Medical ID") }
    }
    DarkCard {
        Text("SAFETY SETUP", color = Aqua, fontWeight = FontWeight.Bold)
        Text("Motion protection ${if (state.active) "active" else "not running"}", color = Color.White)
        Text("Ongoing notification and local sensor checks. No automatic contact alert is connected.", color = Color.LightGray)
        if (!state.active) Button(onClick = onEnable) { Text("Retry monitoring") }
        OutlinedButton(onClick = onTest, enabled = state.active) { Text("Test detection screen · no alert sent") }
    }
    DarkCard { Text("Reports", color = Color.White, fontWeight = FontWeight.Bold); Text("Ride summaries will appear when history recording is added.", color = Color.LightGray) }
}

@Composable
private fun EmergencyPage(
    incident: IncidentState, contacts: List<TrustedContact>, locationMessage: String?,
    onCancel: () -> Unit, onCallEmergency: () -> Unit,
    onCallContact: (TrustedContact) -> Unit, onComposeSms: (TrustedContact) -> Unit,
    onRequestLocation: () -> Unit
) {
    val test = incident.triggeredByTest
    val phase = incident.phase
    Surface(color = Color(0xFF7D1821), modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(18.dp))
            Text(if (test) "SAFE TEST · NO CONTACTS MESSAGED" else "POSSIBLE IMPACT", color = Color.White, fontWeight = FontWeight.Bold)
            Text(if (phase == IncidentPhase.SELF_CHECK) "Are you okay?" else "Check in now", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(if (phase == IncidentPhase.URGENT_HELP) "Next reminder in ${incident.secondsRemaining}s" else "${incident.secondsRemaining}s until next stage", color = Color.White, fontSize = 24.sp)
            Text(if (test) "This test never sends an alert." else "No automatic SMS or call is connected. Call or open a draft below if you need help.", color = Color.White)
            Button(onClick = onCancel, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Aqua)) { Text("I'M OK · CANCEL", color = Dark) }
            if (!test && phase != IncidentPhase.SELF_CHECK) {
                Button(onClick = onCallEmergency, modifier = Modifier.fillMaxWidth()) { Text("Call 112 in phone dialer") }
                OutlinedButton(onClick = onRequestLocation, modifier = Modifier.fillMaxWidth()) { Text("Get location for SMS draft", color = Color.White) }
                if (locationMessage != null) Text(locationMessage, color = Color.White)
                contacts.forEach { contact ->
                    DarkCard {
                        Text(contact.name, color = Color.White, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onCallContact(contact) }) { Text("Call") }
                            OutlinedButton(onClick = { onComposeSms(contact) }) { Text("SMS draft") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DarkCard(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}
