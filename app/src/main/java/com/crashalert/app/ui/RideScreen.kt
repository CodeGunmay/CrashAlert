package com.crashalert.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crashalert.app.telemetry.RideState
import com.crashalert.app.telemetry.IncidentPhase
import com.crashalert.app.telemetry.IncidentState
import com.crashalert.app.telemetry.VectorReading
import com.crashalert.app.contacts.ContactRules
import com.crashalert.app.contacts.TrustedContact
import com.crashalert.app.location.RideLocation
import com.crashalert.app.location.RideMetrics
import com.crashalert.app.profile.RiderProfile
import com.crashalert.app.profile.ProfileRules
import android.os.SystemClock
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val Navy = Color(0xFF101B2E)
private val Teal = Color(0xFF087E82)
private val Muted = Color(0xFF5A6777)
private val Background = Color(0xFFF3F7F8)
private val Coral = Color(0xFFFF4B3E)

@Composable
fun RideScreen(
    state: RideState,
    incident: IncidentState,
    contacts: List<TrustedContact>,
    profile: RiderProfile?,
    profileMessage: String?,
    metrics: RideMetrics,
    batteryPercent: Int?,
    contactMessage: String?,
    location: RideLocation?,
    locationMessage: String?,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onCancelCheck: () -> Unit,
    onTestCheck: () -> Unit,
    onAddContact: (String, String) -> Unit,
    onRemoveContact: (TrustedContact) -> Unit,
    onComposeSms: (TrustedContact) -> Unit,
    onRequestLocation: () -> Unit,
    onSaveProfile: (RiderProfile) -> Boolean
) {
    var editingProfile by remember { mutableStateOf(false) }
    MaterialTheme {
        Surface(color = if (state.active && incident.phase in setOf(IncidentPhase.SELF_CHECK, IncidentPhase.CONTACT_HELP, IncidentPhase.URGENT_HELP)) Navy else Background, modifier = Modifier.fillMaxSize()) {
            if (profile == null || editingProfile) {
                ProfileForm(profile, profileMessage, onSave = { value ->
                    if (onSaveProfile(value)) editingProfile = false
                }, onCancel = { editingProfile = false })
            } else if (state.active && incident.phase in setOf(IncidentPhase.SELF_CHECK, IncidentPhase.CONTACT_HELP, IncidentPhase.URGENT_HELP)) {
                EmergencyScreen(incident, contacts, location, locationMessage, onRequestLocation, onComposeSms, onCancelCheck)
            } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                Text("CrashAlert", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Navy)
                Text("Your ride companion", fontSize = 16.sp, color = Muted)
                Text("Good to see you, ${profile.fullName.substringBefore(' ')}", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Navy)

                Card(colors = CardDefaults.cardColors(containerColor = Navy), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            if (state.active) "●  PROTECTION ACTIVE" else "●  PROTECTION PAUSED",
                            color = Color(0xFF70DDD5), fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (state.active) "Motion monitoring continues with an ongoing notification."
                            else "Enable protection to watch for unusual motion.",
                            color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold
                        )
                        if (state.active) Text("Started ${formatTime(state.startedAtMillis)}", color = Color.White)
                    }
                }

                if (state.active) {
                    OutlinedButton(onClick = onEnd, modifier = Modifier.fillMaxWidth()) { Text("Pause protection") }
                    IncidentCard(incident, onCancelCheck)
                    if (incident.phase == IncidentPhase.MONITORING) {
                        OutlinedButton(onClick = onTestCheck, modifier = Modifier.fillMaxWidth()) {
                            Text("Test self-check")
                        }
                    }
                } else {
                    Button(
                        onClick = onStart,
                        enabled = state.accelerometerAvailable || state.gyroscopeAvailable,
                        colors = ButtonDefaults.buttonColors(containerColor = Teal),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Enable protection") }
                }

                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("How the check works", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Navy)
                        Text("01  •  60 sec to confirm you're okay", color = Navy)
                        Text("02  •  2 min contact-help countdown", color = Navy)
                        Text("03  •  Urgent reminders every 2 min", color = Navy)
                        Text("Alerts are on-device prompts. Automatic SMS is not connected.", fontSize = 12.sp, color = Muted)
                    }
                }

                Text("Live sensors", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Navy)
                RideMetricsCard(metrics, location, locationMessage, batteryPercent, state.active)
                SensorCard("Accelerometer", "m/s²", state.accelerometerAvailable, state.accelerometer)
                SensorCard("Gyroscope", "rad/s", state.gyroscopeAvailable, state.gyroscope)

                Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Medical ID", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Navy)
                        Text("${profile.fullName} · ${profile.bloodGroup.ifBlank { "Blood group not set" }}", color = Navy)
                        Text(if (profile.includeMedicalInDraft) "Included in manual SMS drafts" else "Private on this device · sharing off", color = Muted)
                        if (!state.active) OutlinedButton(onClick = { editingProfile = true }) { Text("Edit rider & Medical ID") }
                    }
                }

                ContactCard(
                    contacts = contacts,
                    editingEnabled = !state.active,
                    canCompose = state.active && !incident.triggeredByTest &&
                        incident.phase in setOf(IncidentPhase.CONTACT_HELP, IncidentPhase.URGENT_HELP),
                    message = contactMessage,
                    location = location,
                    locationMessage = locationMessage,
                    onAdd = onAddContact,
                    onRemove = onRemoveContact,
                    onCompose = onComposeSms,
                    onRequestLocation = onRequestLocation
                )

                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Diagnostics", fontWeight = FontWeight.SemiBold, color = Navy)
                        Text(state.diagnostic, color = Muted)
                        Text("Motion monitoring continues after you leave the app. Location and speed update only while it is open.", color = Muted, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            }
        }
    }
}

@Composable
private fun EmergencyScreen(
    incident: IncidentState,
    contacts: List<TrustedContact>,
    location: RideLocation?,
    locationMessage: String?,
    onRequestLocation: () -> Unit,
    onComposeSms: (TrustedContact) -> Unit,
    onCancelCheck: () -> Unit
) {
    val phase = when (incident.phase) {
        IncidentPhase.SELF_CHECK -> 1
        IncidentPhase.CONTACT_HELP -> 2
        else -> 3
    }
    val heading = when (phase) {
        1 -> "Are you okay?"
        2 -> "Check in now"
        else -> "Still need help?"
    }
    val detail = when (phase) {
        1 -> "Unusual motion detected. Your phone is sounding an alert."
        2 -> "You haven't responded. Contact help is recommended."
        else -> "No response recorded. Seek help as soon as you can."
    }
    val background = if (phase == 1) Coral else Color(0xFF991D23)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(26.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("CrashAlert  •  EMERGENCY CHECK", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..3).forEach { step ->
                    Surface(shape = RoundedCornerShape(12.dp), color = if (step <= phase) Coral else Navy, modifier = Modifier.weight(1f)) {
                        Text("0$step", Modifier.padding(12.dp), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Surface(color = background, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("!", color = Color.White, fontSize = 78.sp, fontWeight = FontWeight.Bold)
                Text("PHASE $phase OF 3", color = Color.White, fontWeight = FontWeight.Bold)
                Text(heading, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(detail, color = Color.White, fontSize = 17.sp)
                Text(formatCountdown(incident.secondsRemaining), color = Color.White, fontSize = 62.sp, fontWeight = FontWeight.Bold)
                Text(if (phase == 3) "Until next reminder" else "Until next phase", color = Color.White)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (incident.triggeredByTest) Text("SAFE TEST • No messages are sent", color = Navy, fontWeight = FontWeight.Bold)
            else if (phase >= 2) {
                Text("No automatic SMS is configured yet. You can request a location and open a draft below.", color = Color.White)
                OutlinedButton(onClick = onRequestLocation, modifier = Modifier.fillMaxWidth()) { Text("Get approximate location", color = Color.White) }
                if (locationMessage != null) Text(locationMessage, color = Color.White)
                if (location?.mapLinkIfFresh(SystemClock.elapsedRealtime()) != null) {
                    Text("Recent location ready for the SMS draft (${location.accuracyMeters.toInt()} m reported accuracy).", color = Color.White)
                }
                contacts.forEach { contact ->
                    OutlinedButton(onClick = { onComposeSms(contact) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open SMS draft · ${contact.name}", color = Color.White)
                    }
                }
            } else {
                Text("Tap I'm okay if this was a false alarm.", color = Color.White)
            }
            Button(onClick = onCancelCheck, colors = ButtonDefaults.buttonColors(containerColor = Teal), modifier = Modifier.fillMaxWidth()) {
                Text("I'M OKAY  ·  CANCEL", Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatCountdown(seconds: Int): String = "%02d:%02d".format(Locale.US, seconds / 60, seconds % 60)

@Composable
private fun ProfileForm(
    profile: RiderProfile?,
    message: String?,
    onSave: (RiderProfile) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember(profile) { mutableStateOf(profile?.fullName.orEmpty()) }
    var dob by remember(profile) { mutableStateOf(profile?.dateOfBirth.orEmpty()) }
    var blood by remember(profile) { mutableStateOf(profile?.bloodGroup.orEmpty()) }
    var allergies by remember(profile) { mutableStateOf(profile?.allergies.orEmpty()) }
    var conditions by remember(profile) { mutableStateOf(profile?.conditions.orEmpty()) }
    var medications by remember(profile) { mutableStateOf(profile?.medications.orEmpty()) }
    var include by remember(profile) { mutableStateOf(profile?.includeMedicalInDraft ?: false) }
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(if (profile == null) "Welcome to CrashAlert" else "Rider profile", fontSize = 29.sp, color = Navy, fontWeight = FontWeight.Bold)
        Text("Set up your rider details. Medical ID is optional and stays on this device.", color = Muted)
        OutlinedTextField(name, { name = it }, label = { Text("Full name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Medical ID", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Navy)
                Text("Self-reported information for someone helping you. Check it for accuracy.", color = Muted, fontSize = 13.sp)
                OutlinedTextField(dob, { dob = it }, label = { Text("Date of birth (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text("Blood group: ${blood.ifBlank { "Not set" }}") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("Not set") }, onClick = { blood = ""; expanded = false })
                        ProfileRules.bloodGroups.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { blood = option; expanded = false })
                        }
                    }
                }
                OutlinedTextField(allergies, { allergies = it }, label = { Text("Known allergies") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(conditions, { conditions = it }, label = { Text("Existing conditions") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(medications, { medications = it }, label = { Text("Current medications") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = include, onCheckedChange = { include = it })
                    Text("Include my Medical ID in SMS drafts I open", color = Navy)
                }
                Text("You must review and send each draft yourself. No medical data is sent by saving this card.", color = Muted, fontSize = 12.sp)
            }
        }
        if (message != null) Text(message, color = Muted)
        Button(onClick = {
            onSave(RiderProfile(name, dob, blood, allergies, conditions, medications, include))
        }, colors = ButtonDefaults.buttonColors(containerColor = Teal), modifier = Modifier.fillMaxWidth()) { Text("Save and continue") }
        if (profile != null) OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
private fun RideMetricsCard(
    metrics: RideMetrics,
    location: RideLocation?,
    locationMessage: String?,
    batteryPercent: Int?,
    active: Boolean
) {
    val fresh = active && metrics.isFresh(SystemClock.elapsedRealtime())
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ride status", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Navy)
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Column { Text("Speed", color = Muted); Text(if (fresh && metrics.speedKph != null) "${metrics.speedKph} km/h" else "Unavailable", color = Navy, fontWeight = FontWeight.Bold) }
                Column { Text("Heading", color = Muted); Text(if (fresh) metrics.heading ?: "Unavailable" else "Unavailable", color = Navy, fontWeight = FontWeight.Bold) }
            }
            Text("Speed and direction come from location updates, not the accelerometer. Permission and a usable fix are required.", color = Muted, fontSize = 12.sp)
            Text(if (fresh && location != null) "Location: %.5f, %.5f · ±%d m".format(Locale.US, location.latitude, location.longitude, location.accuracyMeters.toInt()) else locationMessage ?: "Location not active", color = Muted)
            Text("Battery: ${batteryPercent?.let { "$it%" } ?: "Unavailable"}", color = Muted)
        }
    }
}

@Composable
private fun ContactCard(
    contacts: List<TrustedContact>,
    editingEnabled: Boolean,
    canCompose: Boolean,
    message: String?,
    location: RideLocation?,
    locationMessage: String?,
    onAdd: (String, String) -> Unit,
    onRemove: (TrustedContact) -> Unit,
    onCompose: (TrustedContact) -> Unit,
    onRequestLocation: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Trusted contacts", fontWeight = FontWeight.Bold, color = Navy, fontSize = 18.sp)
            Text("Saved on this device · ${contacts.size}/${ContactRules.MAX_CONTACTS}", color = Muted, fontSize = 13.sp)
            if (canCompose) {
                OutlinedButton(onClick = onRequestLocation) { Text("Get approximate location for draft") }
                Text(locationMessage ?: "Location is optional and requested only when you tap above.", color = Muted, fontSize = 13.sp)
                if (location?.mapLinkIfFresh(SystemClock.elapsedRealtime()) != null) {
                    Text("Fix accuracy about ${location.accuracyMeters.toInt()} m", color = Muted, fontSize = 13.sp)
                }
            }
            contacts.forEach { contact ->
                Text("${contact.name} · ${contact.phone}", color = Navy)
                if (editingEnabled) {
                    OutlinedButton(onClick = { onRemove(contact) }) { Text("Remove ${contact.name}") }
                }
                if (canCompose) {
                    OutlinedButton(onClick = { onCompose(contact) }) { Text("Open SMS draft for ${contact.name}") }
                }
            }
            if (editingEnabled && contacts.size < ContactRules.MAX_CONTACTS) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    if (ContactRules.add(contacts, name, phone) != null) {
                        onAdd(name, phone)
                        name = ""
                        phone = ""
                    } else {
                        onAdd(name, phone)
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Teal)) { Text("Save contact") }
            }
            if (message != null) Text(message, color = Muted, fontSize = 13.sp)
            if (canCompose) Text("A recent location fix is included only if you requested it. You must review and send the SMS yourself. Motion protection continues while Messages is open.", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun IncidentCard(incident: IncidentState, onCancelCheck: () -> Unit) {
    val (heading, detail) = when (incident.phase) {
        IncidentPhase.MONITORING -> "Monitoring motion" to "A possible impact will open a self-check."
        IncidentPhase.SELF_CHECK -> "Are you okay?" to "Possible impact detected. Confirm within ${incident.secondsRemaining} seconds."
        IncidentPhase.CONTACT_HELP -> "Contact help recommended" to "No response. Urgent prompt in ${incident.secondsRemaining} seconds."
        IncidentPhase.URGENT_HELP -> "Urgent help recommended" to "No response recorded. No message or call has been sent."
        IncidentPhase.CANCELLED -> "Check cancelled" to "You marked yourself okay. Detection resumes in ${incident.secondsRemaining} seconds."
    }
    Card(colors = CardDefaults.cardColors(containerColor = if (incident.phase == IncidentPhase.MONITORING) Color.White else Color(0xFFFFF0E8))) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(heading, fontWeight = FontWeight.Bold, color = Navy, fontSize = 18.sp)
            if (incident.triggeredByTest) Text("Test check · no alerts will be sent", color = Teal, fontSize = 13.sp)
            Text(detail, color = Muted)
            if (incident.phase == IncidentPhase.SELF_CHECK || incident.phase == IncidentPhase.CONTACT_HELP || incident.phase == IncidentPhase.URGENT_HELP) {
                Button(onClick = onCancelCheck, colors = ButtonDefaults.buttonColors(containerColor = Teal)) {
                    Text("I'm okay · cancel check")
                }
            }
        }
    }
}

@Composable
private fun SensorCard(title: String, unit: String, available: Boolean, reading: VectorReading?) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, color = Navy, modifier = Modifier.weight(1f))
                Text(if (available) "Available" else "Unavailable", color = if (available) Teal else Muted)
            }
            if (reading == null) {
                Text(if (available) "No reading yet" else "Sensor not found", color = Muted)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Axis("X", reading.x, unit)
                    Axis("Y", reading.y, unit)
                    Axis("Z", reading.z, unit)
                }
                Text("Last reading ${formatTime(reading.receivedAtMillis)} · Accuracy ${accuracyName(reading.accuracy)}", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Axis(label: String, value: Float, unit: String) {
    Column {
        Text(label, color = Muted, fontSize = 12.sp)
        Text(String.format(Locale.US, "%.2f", value), color = Navy, fontWeight = FontWeight.SemiBold)
        Text(unit, color = Muted, fontSize = 11.sp)
    }
}

private fun formatTime(millis: Long?): String = millis?.let {
    DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(it))
} ?: "—"

private fun accuracyName(accuracy: Int): String = when (accuracy) {
    3 -> "high"
    2 -> "medium"
    1 -> "low"
    else -> "unknown"
}
