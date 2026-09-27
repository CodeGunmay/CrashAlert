# CrashAlert

CrashAlert is a native Android motion monitoring prototype. On first launch, set up a rider name and optionally a Medical ID. Once set up, motion protection starts in an Android foreground service with an ongoing notification, and continues when you leave the app or lock the phone. You can pause it from the screen or notification and enable it again later. The app tries to restart previously enabled protection after reboot, subject to Android and device restrictions. A possible impact opens a red emergency check with an alarm sound and an **I'm okay** action. Location-derived speed and heading only update while the app screen is open.

## Open and run

1. Open this repository in Android Studio with JDK 17 and Android SDK 36 installed.
2. Let Android Studio sync Gradle dependencies (Android Gradle Plugin 8.13.2, Kotlin 2.2.20).
3. Run the `app` configuration on a physical Android device with motion sensors. An emulator may have limited sensors.
4. Complete rider setup; protection enables and an ongoing notification appears. Grant notification permission for visible background check alerts. Grant location permission if you want speed, heading and coordinates **while the app is open**; motion monitoring works without it. Close the app and reopen it to confirm that protection remains active. Use **Test self-check** to walk through timed prompts without striking the device. Tap **Pause protection** or use the notification action to stop it.

### Implemented escalation UI

1. Possible impact: full-screen self-check and alarm for **60 seconds**. Tap **I'm okay** to cancel.
2. Unanswered check: contact-help stage with a **two-minute** timer.
3. Still unanswered: urgent-help stage and a repeated **two-minute** reminder timer.

These are **on-device prompts, not sent alerts**. No SMS, call, 112 request, or cloud event is dispatched by the timers. Test checks walk through the same UI without contacting anyone. The heuristic is unvalidated and a prompt is not confirmation of a crash.

Motion sensors need no runtime permission. Up to three trusted contacts are saved locally. Rider name, date of birth, blood group, allergies, conditions, and medications can be edited under **Medical ID**; the profile is encrypted on this device using Android Keystore. No medical data is sent by saving it. If the rider explicitly enables the Medical ID checkbox, it is included only in an SMS draft the rider opens and sends. This is self-reported information, not a medical assessment.

While the app is open, optional location updates may show device-reported speed, heading, coordinates, accuracy, and battery percentage. A missing or stale fix is shown as unavailable; speed is not inferred from accelerometer readings. A location permission grant does not automatically attach a location to SMS drafts. After a real unanswered check, the rider must tap **Get approximate location** to include a fresh location link in a manual SMS draft. If no recent fix exists, the draft omits it. Opening Messages stops location updates but **motion protection continues**. Test checks cannot open drafts or request location.

The foreground service holds the sensor listener and countdown when the Activity leaves the screen. Android may stop services under system or manufacturer restrictions, users can stop an active service from system controls, and reboot restart may be blocked on some devices. This prototype is not a validated safety device. Continuous sensor monitoring uses battery; test screen-off and reboot behavior on the target phone. Background location and automatic messaging are not implemented.

### Planned integrations

Automatic multi-contact alerts, repeated delivery, **background location**, cloud dispatch, n8n workflows, Twilio SMS, and a Leaflet map are **not connected yet**. Server-side SMS requires a configured Twilio account, sender, cloud host, access control, verified recipients where applicable, and a way to avoid duplicate or stale alerts. Do not embed provider credentials in an APK. The current app does not determine phone inactivity independently of unanswered prompts. Google Maps links in the optional SMS draft are map URLs, not a Google Maps API integration. No emergency services integration is claimed.

## Test

Run `:app:testDebugUnitTest` in Android Studio's Gradle tool window. The tests check ride transitions, missing sensors, sample handling, impact gating, countdowns, and cancellation.

## Structure

- `app/src/main/java/com/crashalert/app/telemetry/RideSession.kt`: Android-independent ride state and sample transitions.
- `app/src/main/java/com/crashalert/app/telemetry/AndroidMotionMonitor.kt`: Android sensor adapter and listener lifecycle.
- `app/src/main/java/com/crashalert/app/MonitoringService.kt`: foreground sensor protection, notification and background countdown.
- `app/src/main/java/com/crashalert/app/ProtectionBootReceiver.kt`: best-effort opt-in restart after boot or package update.
- `app/src/main/java/com/crashalert/app/telemetry/IncidentEngine.kt`: pure impact prompt and timeout state machine.
- `app/src/main/java/com/crashalert/app/contacts/TrustedContacts.kt`: local contact storage and validation.
- `app/src/main/java/com/crashalert/app/location/RideLocation.kt`: location freshness check and map link.
- `app/src/main/java/com/crashalert/app/location/RideMetrics.kt`: optional device-reported speed and bearing display.
- `app/src/main/java/com/crashalert/app/profile/RiderProfile.kt`: validated rider setup and encrypted Medical ID storage.
- `app/src/main/java/com/crashalert/app/ui/RideScreen.kt`: Compose screen and diagnostics.
