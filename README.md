# CrashAlert

CrashAlert is an Android ride monitoring app. Start a ride to view live accelerometer and gyroscope values, sensor availability, last reading times, and diagnostics. A possible impact opens a timed self-check that can be cancelled. End Ride stops sampling. Sampling also stops when the app leaves the foreground.

## Open and run

1. Open this repository in Android Studio with JDK 17 and Android SDK 36 installed.
2. Let Android Studio sync Gradle dependencies (Android Gradle Plugin 8.13.2, Kotlin 2.2.20).
3. Run the `app` configuration on a physical Android device with motion sensors. An emulator may have limited sensors.
4. Tap **Start Ride**, move the device, and observe the live X/Y/Z values and last reading times. Use **Test self-check** to walk through the timed prompts without striking the device. Tap **End Ride** to stop.

The app needs no runtime permissions. Sensor data stays on the device and is held in memory only while the app is open. Its impact heuristic is unvalidated and only opens an on-screen prompt. No emergency messaging or background monitoring is active in this version. A prompt is not confirmation of a crash.

## Test

Run `:app:testDebugUnitTest` in Android Studio's Gradle tool window. The tests check ride transitions, missing sensors, sample handling, impact gating, countdowns, and cancellation.

## Structure

- `app/src/main/java/com/crashalert/app/telemetry/RideSession.kt`: Android-independent ride state and sample transitions.
- `app/src/main/java/com/crashalert/app/telemetry/AndroidMotionMonitor.kt`: Android sensor adapter and listener lifecycle.
- `app/src/main/java/com/crashalert/app/telemetry/IncidentEngine.kt`: pure impact prompt and timeout state machine.
- `app/src/main/java/com/crashalert/app/ui/RideScreen.kt`: Compose screen and diagnostics.
