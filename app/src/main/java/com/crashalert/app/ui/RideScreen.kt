package com.crashalert.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crashalert.app.telemetry.RideState
import com.crashalert.app.telemetry.VectorReading
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val Navy = Color(0xFF101B2E)
private val Teal = Color(0xFF087E82)
private val Muted = Color(0xFF5A6777)
private val Background = Color(0xFFF3F7F8)

@Composable
fun RideScreen(state: RideState, onStart: () -> Unit, onEnd: () -> Unit) {
    MaterialTheme {
        Surface(color = Background, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                Text("CrashAlert", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Navy)
                Text("Ride monitoring", fontSize = 16.sp, color = Muted)

                Card(colors = CardDefaults.cardColors(containerColor = Navy), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            if (state.active) "RIDE ACTIVE" else "READY TO RIDE",
                            color = Color(0xFF70DDD5), fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (state.active) "Motion readings are live while this screen is open."
                            else "Start a ride to view motion readings from your phone.",
                            color = Color.White, fontSize = 18.sp
                        )
                        if (state.active) Text("Started ${formatTime(state.startedAtMillis)}", color = Color.White)
                    }
                }

                if (state.active) {
                    OutlinedButton(onClick = onEnd, modifier = Modifier.fillMaxWidth()) { Text("End Ride") }
                } else {
                    Button(
                        onClick = onStart,
                        enabled = state.accelerometerAvailable || state.gyroscopeAvailable,
                        colors = ButtonDefaults.buttonColors(containerColor = Teal),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Start Ride") }
                }

                Text("Live sensors", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Navy)
                SensorCard("Accelerometer", "m/s²", state.accelerometerAvailable, state.accelerometer)
                SensorCard("Gyroscope", "rad/s", state.gyroscopeAvailable, state.gyroscope)

                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Diagnostics", fontWeight = FontWeight.SemiBold, color = Navy)
                        Text(state.diagnostic, color = Muted)
                        Text("Readings stop when you leave the app.", color = Muted, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
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
