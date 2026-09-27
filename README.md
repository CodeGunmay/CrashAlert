# CrashAlert

CrashAlert is an Android ride monitoring app. Start a ride to view live accelerometer and gyroscope values, sensor availability, last reading times, and diagnostics. A possible impact opens a timed self-check that can be cancelled. End Ride stops sampling. Sampling also stops when the app leaves the foreground.

## Open and run

1. Open this repository in Android Studio with JDK 17 and Android SDK 36 installed.
2. Let Android Studio sync Gradle dependencies (Android Gradle Plugin 8.13.2, Kotlin 2.2.20).
3. Run the `app` configuration on a physical Android device with motion sensors. An emulator may have limited sensors.
4. Tap **Start Ride**, move the device, and observe the live X/Y/Z values and last reading times. Use **Test self-check** to walk through the timed prompts without striking the device. Tap **End Ride** to stop.

Motion sensors need no runtime permission. Sensor data stays on the device and is held in memory only while the app is open. You can save up to three trusted contacts locally. After an unanswered real check, the app can open a prefilled SMS draft for a selected contact. You can optionally request approximate foreground location; only a recent fix is added to the draft. If permission is denied, location is disabled, or a recent fix is unavailable, the draft omits location. You must review and send it yourself; opening Messages ends ride monitoring. Test checks cannot open drafts or request location. Its impact heuristic is unvalidated, and no automatic messaging or background monitoring is active. A prompt is not confirmation of a crash.

## Test

Run `:app:testDebugUnitTest` in Android Studio's Gradle tool window. The tests check ride transitions, missing sensors, sample handling, impact gating, countdowns, and cancellation.

## Structure

- `app/src/main/java/com/crashalert/app/telemetry/RideSession.kt`: Android-independent ride state and sample transitions.
- `app/src/main/java/com/crashalert/app/telemetry/AndroidMotionMonitor.kt`: Android sensor adapter and listener lifecycle.
- `app/src/main/java/com/crashalert/app/telemetry/IncidentEngine.kt`: pure impact prompt and timeout state machine.
- `app/src/main/java/com/crashalert/app/contacts/TrustedContacts.kt`: local contact storage and validation.
- `app/src/main/java/com/crashalert/app/location/RideLocation.kt`: location freshness check and map link.
- `app/src/main/java/com/crashalert/app/ui/RideScreen.kt`: Compose screen and diagnostics.
