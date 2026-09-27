package com.crashalert.app

import android.os.Bundle
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crashalert.app.contacts.ContactRules
import com.crashalert.app.contacts.TrustedContact
import com.crashalert.app.contacts.TrustedContactStore
import com.crashalert.app.telemetry.IncidentPhase
import com.crashalert.app.telemetry.AndroidMotionMonitor
import com.crashalert.app.ui.RideScreen

class MainActivity : ComponentActivity() {
    private lateinit var monitor: AndroidMotionMonitor
    private lateinit var contactStore: TrustedContactStore
    private var contacts by mutableStateOf<List<TrustedContact>>(emptyList())
    private var contactMessage by mutableStateOf<String?>(null)

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
                onStart = monitor::startRide,
                onEnd = { monitor.endRide() },
                onCancelCheck = monitor::cancelCheck,
                onTestCheck = monitor::testCheck,
                onAddContact = ::addContact,
                onRemoveContact = ::removeContact,
                onComposeSms = ::composeSms
            )
        }
    }

    override fun onStop() {
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
        if (!monitor.state.active || monitor.incident.triggeredByTest ||
            monitor.incident.phase !in setOf(IncidentPhase.CONTACT_HELP, IncidentPhase.URGENT_HELP) ||
            contact !in contacts
        ) return
        val draft = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", contact.phone, null)).apply {
            putExtra("sms_body", "CrashAlert: I may need help during a ride. Please call me to check on me.")
        }
        try {
            contactMessage = "Opening Messages ends ride monitoring. Review and send the draft yourself."
            startActivity(draft)
        } catch (_: ActivityNotFoundException) {
            contactMessage = "No SMS app is available on this device"
        }
    }
}
