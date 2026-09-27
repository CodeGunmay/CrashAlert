# CrashAlert

CrashAlert is a native Android ride monitoring app. Start a ride to view live accelerometer and gyroscope readings. A possible impact opens a prominent red emergency check with an alarm sound and an **I'm okay** action. Sampling stops when the app leaves the foreground.

## Open and run

1. Open this repository in Android Studio with JDK 17 and Android SDK 36 installed.
2. Let Android Studio sync Gradle dependencies (Android Gradle Plugin 8.13.2, Kotlin 2.2.20).
3. Run the `app` configuration on a physical Android device with motion sensors. An emulator may have limited sensors.
4. Tap **Start Ride**, move the device, and observe the live X/Y/Z values and last reading times. Use **Test self-check** to walk through the timed prompts without striking the device. Tap **End Ride** to stop.

### Implemented escalation UI

1. Possible impact: full-screen self-check and alarm for **60 seconds**. Tap **I'm okay** to cancel.
2. Unanswered check: contact-help stage with a **two-minute** timer.
3. Still unanswered: urgent-help stage and a repeated **two-minute** reminder timer.

These are **on-device prompts, not sent alerts**. No SMS, call, 112 request, or cloud event is dispatched by the timers. Test checks walk through the same UI without contacting anyone. The heuristic is unvalidated and a prompt is not confirmation of a crash.

Motion sensors need no runtime permission. Sensor data stays on the device while the app is open. Up to three trusted contacts are saved locally. After an unanswered real check, the app can open a prefilled SMS draft for a selected contact. You can optionally request approximate foreground location; only a recent fix is added to the draft. If a recent fix is unavailable, the draft omits location. You must review and send it yourself; opening Messages ends ride monitoring. Test checks cannot open drafts or request location.

### Planned integrations

Automatic multi-contact alerts, repeated delivery, background monitoring, cloud dispatch, n8n workflows, Twilio SMS, and a Leaflet map are **not connected yet**. Server-side SMS requires a configured Twilio account, sender, cloud host, access control, verified recipients where applicable, and a way to avoid duplicate or stale alerts. Do not embed provider credentials in an APK. The current app does not determine phone inactivity independently of unanswered prompts. Google Maps links in the optional SMS draft are map URLs, not a Google Maps API integration. No emergency services integration is claimed.

## Test

Run `:app:testDebugUnitTest` in Android Studio's Gradle tool window. The tests check ride transitions, missing sensors, sample handling, impact gating, countdowns, and cancellation.

## Structure

- `app/src/main/java/com/crashalert/app/telemetry/RideSession.kt`: Android-independent ride state and sample transitions.
- `app/src/main/java/com/crashalert/app/telemetry/AndroidMotionMonitor.kt`: Android sensor adapter and listener lifecycle.
- `app/src/main/java/com/crashalert/app/telemetry/IncidentEngine.kt`: pure impact prompt and timeout state machine.
- `app/src/main/java/com/crashalert/app/contacts/TrustedContacts.kt`: local contact storage and validation.
- `app/src/main/java/com/crashalert/app/location/RideLocation.kt`: location freshness check and map link.
- `app/src/main/java/com/crashalert/app/ui/RideScreen.kt`: Compose screen and diagnostics.
