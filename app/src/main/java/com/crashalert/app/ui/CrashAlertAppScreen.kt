package com.crashalert.app.ui

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.Brightness4
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crashalert.app.contacts.ContactRules
import com.crashalert.app.R
import com.crashalert.app.contacts.TrustedContact
import com.crashalert.app.location.RideLocation
import com.crashalert.app.location.RideMetrics
import com.crashalert.app.profile.ProfileRules
import com.crashalert.app.profile.RiderProfile
import com.crashalert.app.report.MonitoringReport
import com.crashalert.app.telemetry.IncidentPhase
import com.crashalert.app.telemetry.IncidentState
import com.crashalert.app.telemetry.RideState
import com.crashalert.app.telemetry.VectorReading
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.sqrt

private data class ThemeTokens(val background: Color, val card: Color, val card2: Color, val ink: Color,
    val muted: Color, val line: Color, val teal: Color, val tealWash: Color, val amber: Color, val red: Color)
private val night = ThemeTokens(Color(0xFF0B1522), Color(0xFF142130), Color(0xFF1A2939), Color(0xFFF5F8FC),
    Color(0xFF93ABC6), Color(0xFF293A4C), Color(0xFF34C6BC), Color(0xFF133B43), Color(0xFFFFBA46), Color(0xFFFA3B55))
private val day = ThemeTokens(Color(0xFFF3F7FA), Color.White, Color(0xFFE9F0F4), Color(0xFF152738),
    Color(0xFF526B7C), Color(0xFFD6E2E8), Color(0xFF087F7D), Color(0xFFDDF4F0), Color(0xFFAA6900), Color(0xFFD52D49))
private val LocalTokens = staticCompositionLocalOf { night }
private val geist = FontFamily(Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium), Font(R.font.geist_bold, FontWeight.Bold))
private val type = Typography().let { base -> base.copy(
    headlineMedium = base.headlineMedium.copy(fontFamily = geist, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleLarge = base.titleLarge.copy(fontFamily = geist, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleMedium = base.titleMedium.copy(fontFamily = geist),
    bodyLarge = base.bodyLarge.copy(fontFamily = geist),
    bodyMedium = base.bodyMedium.copy(fontFamily = geist, letterSpacing = 0.sp),
    bodySmall = base.bodySmall.copy(fontFamily = geist),
    labelLarge = base.labelLarge.copy(fontFamily = geist),
    labelMedium = base.labelMedium.copy(fontFamily = geist),
    labelSmall = base.labelSmall.copy(fontFamily = geist)
) }
private enum class Page(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("Home", Icons.Outlined.Home), RIDE("Ride", Icons.Outlined.Speed),
    SERVICES("Services", Icons.Outlined.LocationOn), CONTACTS("Contacts", Icons.Outlined.Group),
    PROFILE("Profile", Icons.Outlined.Person)
}

@Composable
fun CrashAlertAppScreen(
    state: RideState, incident: IncidentState, profile: RiderProfile?, profileMessage: String?, authStatus: String,
    cloudAlertStatus: String, darkTheme: Boolean, weekReport: MonitoringReport, monthReport: MonitoringReport,
    contacts: List<TrustedContact>, contactMessage: String?, location: RideLocation?, locationMessage: String?,
    metrics: RideMetrics, batteryPercent: Int?,
    onEnableProtection: () -> Unit, onPauseProtection: () -> Unit,
    onTestCheck: () -> Unit, onCancelCheck: () -> Unit, onCallEmergency: () -> Unit,
    onCallContact: (TrustedContact) -> Unit, onFindNearby: (String) -> Unit,
    onAddContact: (String, String) -> Unit, onRemoveContact: (TrustedContact) -> Unit,
    onMakePrimary: (TrustedContact) -> Unit, onEditContact: (TrustedContact, String, String) -> Unit,
    onComposeSms: (TrustedContact) -> Unit, onRequestLocation: () -> Unit,
    onOpenPermissionSettings: () -> Unit, onSendOtp: (String) -> Unit, onVerifyOtp: (String) -> Unit,
    onToggleTheme: () -> Unit, onShareReport: (MonitoringReport) -> Unit,
    onSaveProfile: (RiderProfile) -> Boolean
) {
    val t = if (darkTheme) night else day
    var selected by rememberSaveable { mutableStateOf(Page.HOME.name) }
    var editingProfile by rememberSaveable { mutableStateOf(false) }
    val emergency = incident.phase in setOf(IncidentPhase.SELF_CHECK, IncidentPhase.CONTACT_HELP, IncidentPhase.URGENT_HELP)
    CompositionLocalProvider(LocalTokens provides t, LocalTextStyle provides TextStyle(fontFamily = geist)) {
        MaterialTheme(colorScheme = if (darkTheme) darkColorScheme(primary = t.teal, background = t.background,
            surface = t.card, onSurface = t.ink, onBackground = t.ink) else lightColorScheme(primary = t.teal,
            background = t.background, surface = t.card, onSurface = t.ink, onBackground = t.ink), typography = type) {
            when {
                emergency -> EmergencyPage(incident, contacts, locationMessage, cloudAlertStatus, onCancelCheck,
                    onCallEmergency, onCallContact, onComposeSms, onRequestLocation)
                profile == null || editingProfile -> MedicalIdForm(profile, profileMessage,
                    onSave = { if (onSaveProfile(it)) editingProfile = false }, onCancel = { editingProfile = false })
                else -> Scaffold(containerColor = t.background, bottomBar = {
                    NavigationBar(containerColor = t.card) {
                        Page.entries.forEach { page ->
                            NavigationBarItem(selected = selected == page.name, onClick = { selected = page.name },
                                icon = { Icon(page.icon, contentDescription = null, modifier = Modifier.size(21.dp)) },
                                label = { Text(page.label, fontSize = 10.sp) })
                        }
                    }
                }) { inset ->
                    Column(Modifier.fillMaxSize().padding(inset).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(15.dp)) {
                        when (Page.valueOf(selected)) {
                            Page.HOME -> HomePage(profile, state, contacts, location, metrics, batteryPercent, cloudAlertStatus)
                            Page.RIDE -> RidePage(state, metrics, location, locationMessage, batteryPercent)
                            Page.SERVICES -> ServicesPage(location, onFindNearby, onCallEmergency)
                            Page.CONTACTS -> ContactsPage(contacts, contactMessage, onAddContact, onRemoveContact,
                                onMakePrimary, onEditContact, onCallContact)
                            Page.PROFILE -> ProfilePage(profile, state, authStatus, darkTheme, weekReport, monthReport,
                                onEdit = { editingProfile = true }, onEnableProtection, onTestCheck, onOpenPermissionSettings,
                                onSendOtp, onVerifyOtp, onToggleTheme, onShareReport)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun SectionLabel(label: String) {
    val t = LocalTokens.current
    Text(label.uppercase(Locale.US), color = t.muted, fontSize = 10.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
}

@Composable private fun Panel(modifier: Modifier = Modifier, accent: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalTokens.current
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = t.card),
        border = BorderStroke(1.dp, accent ?: t.line)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}

@Composable private fun IconDisc(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(40.dp).background(color.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(21.dp))
    }
}

@Composable private fun PageHeading(title: String, subtitle: String? = null) {
    val t = LocalTokens.current
    Text(title, style = MaterialTheme.typography.titleLarge, color = t.ink)
    if (subtitle != null) Text(subtitle, color = t.muted, fontSize = 12.sp)
}

@Composable private fun HomePage(profile: RiderProfile, state: RideState, contacts: List<TrustedContact>,
    location: RideLocation?, metrics: RideMetrics, battery: Int?, cloudStatus: String) {
    val t = LocalTokens.current
    Text("Good to see you,", color = t.muted, fontSize = 13.sp)
    Text(profile.fullName.substringBefore(' '), style = MaterialTheme.typography.headlineMedium, color = t.ink)
    if (!state.active || !state.accelerometerAvailable || !state.gyroscopeAvailable) Panel(accent = t.amber.copy(alpha = .6f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.WarningAmber, null, tint = t.amber)
            Text(if (!state.active) "Monitoring needs attention" else "A motion sensor is unavailable", color = t.amber, fontWeight = FontWeight.Bold)
        }
        Text(state.diagnostic, color = t.muted, fontSize = 12.sp)
    }
    Panel(accent = t.teal.copy(alpha = .45f)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 15.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp)) {
            IconDisc(Icons.Outlined.Security, t.teal, Modifier.size(64.dp))
            Text(if (state.active) "Protected" else "Monitoring unavailable", style = MaterialTheme.typography.titleLarge, color = t.ink)
            Text(if (state.active) "●  Monitoring active" else "Sensor service not running", color = if (state.active) t.teal else t.amber, fontSize = 13.sp)
            Text(state.diagnostic, color = t.muted, fontSize = 11.sp)
        }
    }
    SectionLabel("Live ride data")
    SpeedCard(metrics)
    SensorTiles(state)
    SectionLabel("Current location")
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconDisc(Icons.Outlined.LocationOn, t.teal)
            Column {
                val fresh = location?.mapLinkIfFresh(SystemClock.elapsedRealtime()) != null
                Text(if (fresh) "%.5f°, %.5f°".format(Locale.US, location!!.latitude, location.longitude) else "Waiting for a recent fix", color = t.ink, fontWeight = FontWeight.Bold)
                Text(if (fresh) "Reported accuracy ±${location!!.accuracyMeters.toInt()} m · not live-shared" else "Grant location for coordinates and speed", color = t.muted, fontSize = 11.sp)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MiniTile("SENSORS", if (state.active) "Monitoring" else "Unavailable", Icons.Outlined.Explore, t.teal, Modifier.weight(1f))
        MiniTile("BATTERY", battery?.let { "$it%" } ?: "Unavailable", Icons.Outlined.BatteryFull, t.amber, Modifier.weight(1f))
    }
    SectionLabel("Emergency contacts")
    Panel {
        if (contacts.isEmpty()) Text("Add a trusted contact in Contacts", color = t.muted)
        contacts.take(2).forEachIndexed { index, c ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconDisc(Icons.Outlined.Person, t.teal)
                Column { Text(c.name, color = t.ink, fontWeight = FontWeight.SemiBold)
                    Text(if (index == 0) "Primary · ${c.phone}" else c.phone, color = t.muted, fontSize = 11.sp) }
            }
        }
    }
    Panel(accent = t.red.copy(alpha = .4f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconDisc(Icons.Outlined.FavoriteBorder, t.red)
            Column { Text("Medical ID", color = t.ink, fontWeight = FontWeight.Bold)
                Text(profile.bloodGroup.ifBlank { "Blood group not set" } + " · sharing controlled in Profile", color = t.muted, fontSize = 11.sp) }
        }
    }
    Text(cloudStatus, color = t.muted, fontSize = 11.sp)
}

@Composable private fun SpeedCard(metrics: RideMetrics) {
    val t = LocalTokens.current
    val fresh = metrics.isFresh(SystemClock.elapsedRealtime())
    Panel {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            SectionLabel("Speed")
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (fresh) metrics.speedKph?.toString() ?: "—" else "—", color = t.ink,
                    fontSize = 56.sp, fontWeight = FontWeight.Bold, lineHeight = 60.sp)
                Text(" km/h", color = t.muted, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(if (fresh) "●  Device location fix · ±${metrics.accuracyMeters?.toInt() ?: "?"} m" else "Speed needs a fresh location fix", color = if (fresh) t.teal else t.muted, fontSize = 11.sp)
        }
    }
}

@Composable private fun MiniTile(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Panel(modifier) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        SectionLabel(label)
        Text(value, color = t.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
    }
}

@Composable private fun SensorTiles(state: RideState) {
    val t = LocalTokens.current
    val accel = state.accelerometer?.let { sqrt((it.x * it.x + it.y * it.y + it.z * it.z).toDouble()) / 9.80665 }
    val gyro = state.gyroscope?.let { sqrt((it.x * it.x + it.y * it.y + it.z * it.z).toDouble()) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MiniTile("ACCEL", accel?.let { "%.1f G".format(Locale.US, it) } ?: "—", Icons.Outlined.Speed, t.amber, Modifier.weight(1f))
        MiniTile("GYRO", gyro?.let { "%.1f rad/s".format(Locale.US, it) } ?: "—", Icons.Outlined.Explore, t.teal, Modifier.weight(1f))
    }
}

@Composable private fun RidePage(state: RideState, metrics: RideMetrics, location: RideLocation?, message: String?, battery: Int?) {
    val t = LocalTokens.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.active) { while (state.active) { now = System.currentTimeMillis(); delay(1_000L) } }
    PageHeading("Ride monitoring", "Always on after setup · no manual start needed")
    val seconds = state.startedAtMillis?.takeIf { state.active }?.let { ((now - it) / 1_000).coerceAtLeast(0) } ?: 0
    Panel(accent = t.teal.copy(alpha = .45f)) {
        SectionLabel("Current monitoring session")
        Text("%02d:%02d:%02d".format(Locale.US, seconds / 3600, seconds / 60 % 60, seconds % 60),
            color = t.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(if (state.active) "●  Motion checks running" else "Monitoring not running", color = if (state.active) t.teal else t.amber)
    }
    SpeedCard(metrics)
    SensorTiles(state)
    SectionLabel("Impact monitor")
    Panel {
        val accel = state.accelerometer?.let { sqrt((it.x * it.x + it.y * it.y + it.z * it.z).toDouble()) / 9.80665 }
        Text(accel?.let { "%.1f G · acceleration including gravity".format(Locale.US, it) } ?: "No acceleration reading", color = t.ink)
        LinearProgressIndicator(progress = { ((accel ?: 0.0) / 10.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(7.dp), color = t.teal, trackColor = t.card2)
        Text("The impact check also considers rotation. This threshold is an unvalidated heuristic.", color = t.muted, fontSize = 11.sp)
    }
    SectionLabel("Detailed sensors")
    Panel {
        SensorLine("Accelerometer", state.accelerometer, "m/s²")
        SensorLine("Gyroscope", state.gyroscope, "rad/s")
        Text("Heading: ${if (metrics.isFresh(SystemClock.elapsedRealtime())) metrics.heading ?: "Unavailable" else "Unavailable"}", color = t.ink)
        Text("Battery: ${battery?.let { "$it%" } ?: "Unavailable"}", color = t.muted)
        Text(if (location?.mapLinkIfFresh(SystemClock.elapsedRealtime()) != null) "Location: %.5f, %.5f".format(Locale.US, location.latitude, location.longitude) else message ?: "No recent fix", color = t.muted, fontSize = 11.sp)
    }
}

@Composable private fun SensorLine(label: String, v: VectorReading?, units: String) {
    val t = LocalTokens.current
    Text(if (v == null) "$label: unavailable" else "$label: %.1f / %.1f / %.1f $units".format(Locale.US, v.x, v.y, v.z), color = t.ink, fontSize = 12.sp)
}

@Composable private fun ServicesPage(location: RideLocation?, onFind: (String) -> Unit, onEmergency: () -> Unit) {
    val t = LocalTokens.current
    PageHeading("Nearby help", "Search using the phone's map app")
    Panel {
        Box(Modifier.fillMaxWidth().height(130.dp).background(t.card2, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Map, null, tint = t.teal, modifier = Modifier.size(35.dp))
                Text("Open a live map search below", color = t.muted, fontSize = 12.sp)
            }
        }
        Text(if (location?.mapLinkIfFresh(SystemClock.elapsedRealtime()) != null)
            "Last recent fix: %.5f°, %.5f° · ±%d m".format(Locale.US, location.latitude, location.longitude, location.accuracyMeters.toInt())
            else "No recent location fix on this screen", color = t.muted, fontSize = 12.sp)
    }
    SectionLabel("Search for help")
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconDisc(Icons.Outlined.MedicalServices, t.red)
            Column { Text("Hospitals", color = t.ink, fontWeight = FontWeight.Bold)
                Text("Check opening hours and emergency facilities", color = t.muted, fontSize = 11.sp) }
        }
        Button(onClick = { onFind("emergency hospital") }, modifier = Modifier.fillMaxWidth()) { Text("Find hospitals in Maps") }
    }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconDisc(Icons.Outlined.Security, t.amber)
            Text("Police stations", color = t.ink, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = { onFind("police station") }, modifier = Modifier.fillMaxWidth()) { Text("Find police in Maps") }
    }
    OutlinedButton(onClick = onEmergency, modifier = Modifier.fillMaxWidth()) { Text("Open 112 dialer") }
    Text("No place, distance, opening hour or phone number is assumed by CrashAlert. No place is contacted automatically.", color = t.muted, fontSize = 11.sp)
}

@Composable private fun ContactsPage(contacts: List<TrustedContact>, message: String?, onAdd: (String, String) -> Unit,
    onRemove: (TrustedContact) -> Unit, onPrimary: (TrustedContact) -> Unit,
    onEdit: (TrustedContact, String, String) -> Unit, onCall: (TrustedContact) -> Unit) {
    val t = LocalTokens.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<TrustedContact?>(null) }
    var adding by remember { mutableStateOf(false) }
    PageHeading("Emergency contacts", "Alert requests use these numbers if cloud delivery is configured")
    contacts.forEachIndexed { index, contact ->
        Panel(accent = if (index == 0) t.teal.copy(alpha = .45f) else null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconDisc(Icons.Outlined.Person, t.teal)
                Column(Modifier.weight(1f)) {
                    Text(contact.name + if (index == 0) "  · PRIMARY" else "", color = t.ink, fontWeight = FontWeight.Bold)
                    Text(contact.phone, color = t.muted, fontSize = 11.sp)
                }
                OutlinedButton(onClick = { onCall(contact) }) { Icon(Icons.Outlined.Call, "Call", modifier = Modifier.size(17.dp)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (index != 0) OutlinedButton(onClick = { onPrimary(contact) }, modifier = Modifier.weight(1f)) { Text("Make primary", fontSize = 11.sp) }
                OutlinedButton(onClick = { editing = contact; name = contact.name; phone = contact.phone }, modifier = Modifier.weight(1f)) { Text("Edit", fontSize = 11.sp) }
                OutlinedButton(onClick = { onRemove(contact) }, modifier = Modifier.weight(1f)) { Text("Delete", color = t.red, fontSize = 11.sp) }
            }
        }
    }
    if (contacts.size < ContactRules.MAX_CONTACTS) OutlinedButton(onClick = { adding = !adding; editing = null; name = ""; phone = "" }, modifier = Modifier.fillMaxWidth()) { Text("+  Add contact") }
    if (adding || editing != null) Panel {
        Text(if (editing == null) "Add contact" else "Edit contact", color = t.ink, fontWeight = FontWeight.Bold)
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text("Number with +country code") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            val old = editing
            if (old == null) onAdd(name, phone) else onEdit(old, name, phone)
            if (ContactRules.normalizePhone(phone) != null && name.isNotBlank()) { adding = false; editing = null; name = ""; phone = "" }
        }, modifier = Modifier.fillMaxWidth()) { Text("Save contact") }
    }
    if (message != null) Text(message, color = t.muted, fontSize = 12.sp)
    Text("Up to three contacts. Cloud sending requires verified phone and international-format numbers; adding a contact alone never sends a message.", color = t.muted, fontSize = 11.sp)
}

@Composable private fun ProfilePage(profile: RiderProfile, state: RideState, authStatus: String, darkTheme: Boolean,
    week: MonitoringReport, month: MonitoringReport, onEdit: () -> Unit, onEnable: () -> Unit, onTest: () -> Unit,
    onSettings: () -> Unit, onSendOtp: (String) -> Unit, onVerifyOtp: (String) -> Unit,
    onToggleTheme: () -> Unit, onShareReport: (MonitoringReport) -> Unit) {
    val t = LocalTokens.current
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    PageHeading("Profile")
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconDisc(Icons.Outlined.Person, t.teal, Modifier.size(52.dp))
            Column { Text(profile.fullName, color = t.ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Rider profile · monitoring ${if (state.active) "active" else "unavailable"}", color = t.muted, fontSize = 11.sp) }
        }
    }
    SectionLabel("Safety setup")
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconDisc(Icons.Outlined.FavoriteBorder, t.red)
            Column(Modifier.weight(1f)) { Text("Medical ID", color = t.ink, fontWeight = FontWeight.Bold)
                Text("${profile.bloodGroup.ifBlank { "Blood group not set" }} · automatic sharing ${if (profile.includeMedicalInAutomaticAlerts) "on" else "off"}", color = t.muted, fontSize = 11.sp) }
        }
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Edit rider and Medical ID") }
    }
    Panel {
        Text("Permission health", color = t.ink, fontWeight = FontWeight.Bold)
        Text("Allow precise location and background access for best-effort location capture. Android can still stop the service.", color = t.muted, fontSize = 12.sp)
        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("Open app permissions") }
        if (!state.active) Button(onClick = onEnable, modifier = Modifier.fillMaxWidth()) { Text("Retry monitoring") }
    }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Brightness4, null, tint = t.teal)
            Text("Appearance", color = t.ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (darkTheme) "Dark" else "Light", color = t.muted)
        }
        OutlinedButton(onClick = onToggleTheme, modifier = Modifier.fillMaxWidth()) { Text("Switch to ${if (darkTheme) "light" else "dark"} theme") }
    }
    SectionLabel("Monitoring reports")
    ReportCard(week, onShareReport)
    ReportCard(month, onShareReport)
    SectionLabel("Try it out")
    Panel {
        Text("Test detection screen", color = t.ink, fontWeight = FontWeight.Bold)
        Text("Walk through the timed UI; no real contacts are messaged.", color = t.muted, fontSize = 11.sp)
        OutlinedButton(onClick = onTest, enabled = state.active, modifier = Modifier.fillMaxWidth()) { Text("Run safe test") }
    }
    SectionLabel("Cloud alert account")
    Panel {
        Text(authStatus, color = t.ink)
        Text("Firebase verification requires a configured build. No alert is sent during signup.", color = t.muted, fontSize = 11.sp)
        OutlinedTextField(phone, { phone = it }, label = { Text("Phone · +country code") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { onSendOtp(phone) }) { Text("Send verification code") }
        OutlinedTextField(code, { code = it }, label = { Text("Six-digit code") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { onVerifyOtp(code) }) { Text("Verify code") }
    }
}

@Composable private fun ReportCard(report: MonitoringReport, onShare: (MonitoringReport) -> Unit) {
    val t = LocalTokens.current
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconDisc(Icons.Outlined.Description, t.teal)
            Column { Text(if (report.days == 7) "Last 7 days" else "Last 30 days", color = t.ink, fontWeight = FontWeight.Bold)
                Text("${report.from} – ${report.through}", color = t.muted, fontSize = 11.sp) }
        }
        Text("${report.monitoringMinutes} recorded min  ·  ${report.impactChecks} possible-impact checks", color = t.ink)
        Text("${report.cancelledChecks} cancelled  ·  ${report.cloudRequestsAccepted} cloud requests accepted", color = t.muted, fontSize = 11.sp)
        OutlinedButton(onClick = { onShare(report) }, modifier = Modifier.fillMaxWidth()) { Text("Generate and share report") }
    }
}

@Composable private fun MedicalIdForm(profile: RiderProfile?, message: String?, onSave: (RiderProfile) -> Unit, onCancel: () -> Unit) {
    val t = LocalTokens.current
    var name by remember(profile) { mutableStateOf(profile?.fullName.orEmpty()) }
    var dob by remember(profile) { mutableStateOf(profile?.dateOfBirth.orEmpty()) }
    var blood by remember(profile) { mutableStateOf(profile?.bloodGroup.orEmpty()) }
    var allergies by remember(profile) { mutableStateOf(profile?.allergies.orEmpty()) }
    var conditions by remember(profile) { mutableStateOf(profile?.conditions.orEmpty()) }
    var medications by remember(profile) { mutableStateOf(profile?.medications.orEmpty()) }
    var draft by remember(profile) { mutableStateOf(profile?.includeMedicalInDraft ?: false) }
    var automatic by remember(profile) { mutableStateOf(profile?.includeMedicalInAutomaticAlerts ?: false) }
    Surface(color = t.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PageHeading(if (profile == null) "Welcome to CrashAlert" else "Medical ID", "Your details stay on this device until an enabled alert is sent")
            Panel(accent = t.red.copy(alpha = .45f)) {
                SectionLabel("Emergency medical card")
                Text(name.ifBlank { "Your name" }, color = t.ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("DOB ${dob.ifBlank { "not set" }}  ·  Blood ${blood.ifBlank { "not set" }}", color = t.muted)
                Text("Self-reported information; verify it for accuracy.", color = t.muted, fontSize = 11.sp)
            }
            OutlinedTextField(name, { name = it }, label = { Text("Full name *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(dob, { dob = it }, label = { Text("Date of birth · YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
            SectionLabel("Blood group")
            ProfileRules.bloodGroups.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { option -> FilterChip(selected = blood == option, onClick = { blood = if (blood == option) "" else option },
                        label = { Text(option, fontSize = 11.sp) }, modifier = Modifier.weight(1f)) }
                }
            }
            OutlinedTextField(allergies, { allergies = it }, label = { Text("Known allergies") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(conditions, { conditions = it }, label = { Text("Existing conditions") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(medications, { medications = it }, label = { Text("Current medications") }, modifier = Modifier.fillMaxWidth())
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(draft, { draft = it }); Text("Include in manual SMS drafts", color = t.ink, fontSize = 12.sp) }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(automatic, { automatic = it }); Text("Include in automatic contact alerts if cloud is connected", color = t.ink, fontSize = 12.sp) }
                Text("Saving does not send your Medical ID. Both sharing options are optional.", color = t.muted, fontSize = 11.sp)
            }
            if (message != null) Text(message, color = t.amber)
            Button(onClick = { onSave(RiderProfile(name, dob, blood, allergies, conditions, medications, draft, automatic)) },
                modifier = Modifier.fillMaxWidth()) { Text("Save Medical ID") }
            if (profile != null) OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}

@Composable private fun EmergencyPage(incident: IncidentState, contacts: List<TrustedContact>, locationMessage: String?,
    cloudStatus: String, onCancel: () -> Unit, onEmergency: () -> Unit,
    onCall: (TrustedContact) -> Unit, onSms: (TrustedContact) -> Unit, onLocation: () -> Unit) {
    val t = LocalTokens.current
    val test = incident.triggeredByTest
    Surface(color = Color(0xFF841E2B), modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(24.dp))
            Icon(Icons.Outlined.WarningAmber, null, tint = Color.White, modifier = Modifier.size(52.dp))
            Text(if (test) "SAFE TEST · NO CONTACTS MESSAGED" else "POSSIBLE IMPACT DETECTED", color = Color.White, fontWeight = FontWeight.Bold)
            Text(if (incident.phase == IncidentPhase.SELF_CHECK) "Are you okay?" else "Help may be needed", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Panel(accent = Color.White.copy(alpha = .3f)) {
                Text("${incident.secondsRemaining} seconds", color = t.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(if (incident.phase == IncidentPhase.SELF_CHECK) "until the contact-help stage" else "until the next reminder", color = t.muted)
                Text(if (test) "This test never sends an alert." else cloudStatus, color = t.ink, fontSize = 12.sp)
            }
            Button(onClick = onCancel, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C6BC))) {
                Text("I'M OK — CANCEL", color = Color(0xFF0B1522), fontWeight = FontWeight.Bold)
            }
            if (!test && incident.phase != IncidentPhase.SELF_CHECK) {
                OutlinedButton(onClick = onEmergency, modifier = Modifier.fillMaxWidth()) { Text("Open 112 dialer", color = Color.White) }
                OutlinedButton(onClick = onLocation, modifier = Modifier.fillMaxWidth()) { Text("Get location for manual SMS draft", color = Color.White) }
                if (locationMessage != null) Text(locationMessage, color = Color.White)
                contacts.forEach { c -> Panel {
                    Text(c.name, color = t.ink, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onCall(c) }, modifier = Modifier.weight(1f)) { Text("Call") }
                        OutlinedButton(onClick = { onSms(c) }, modifier = Modifier.weight(1f)) { Text("SMS draft") }
                    }
                } }
            }
        }
    }
}
