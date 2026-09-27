# CrashAlert

**A native Android ride monitor with on-device motion readings and an incident self-check.**

> **Current status:** Foreground Android build. Its motion rule is an unvalidated prompt heuristic, not a confirmed crash detector. It does not automatically send messages, place calls, or contact emergency services.

## What works today

- **Ride monitoring:** Start and end a ride; see live accelerometer and gyroscope X/Y/Z values, availability, accuracy, timestamps, and diagnostics.
- **On-device self-check:** A possible impact opens a 20-second “Are you okay?” prompt. An unanswered prompt advances to contact-help guidance, then urgent-help guidance after another 30 seconds. Cancelling pauses detection for 10 seconds before it resumes.
- **Test self-check:** Walk through the countdown without striking or dropping a phone. Test checks cannot request location or open an SMS draft.
- **Trusted contacts:** Save and remove up to three contacts locally on the device.
- **Optional location:** During a real unanswered check, request approximate foreground location. A map link enters the draft only while the fix is less than 30 seconds old.
- **User-controlled communication:** Open a prefilled SMS draft for a selected contact after a real unanswered check. The user must review and send it in their messaging app. Opening Messages ends ride monitoring.

### Incident flow

| Step | App behavior |
| --- | --- |
| Start Ride | Registers available motion sensors while the app is in the foreground. |
| Possible impact | Shows an on-screen self-check; this is not a confirmed crash. |
| No response after 20 seconds | Shows contact-help guidance and enables a user-initiated SMS draft for saved contacts. |
| Another 30 seconds without response | Shows urgent-help guidance. No SMS or call is placed automatically. |
| “I'm okay” | Cancels the check; detection resumes after a 10-second cooldown. |

## Technology

| In this Android app | Purpose |
| --- | --- |
| Kotlin + Jetpack Compose | Native UI and app logic |
| Android SensorManager | Accelerometer and gyroscope samples |
| Android LocationManager + AndroidX Core | Optional current approximate location |
| Private app preferences | Trusted contacts stored on the device |
| Gradle + GitHub Actions | Build, unit tests, and debug APK artifact |

**Planned:** Leaflet belongs to a future web map/dashboard layer. The current Android build does not use Leaflet, a backend, machine learning, or cloud services.

## Run on an Android phone

1. Open the latest app branch (`feat/optional-location`) in Android Studio. The four pull requests are stacked and have not yet been merged into `main`.
2. Install JDK 17 and Android SDK 36 if Android Studio prompts you, then sync Gradle.
3. Run the `app` configuration on a physical Android device. An emulator may not expose both motion sensors.
4. Tap **Start Ride**, move the phone normally, and confirm that the values and timestamps change. Use **Test self-check** to inspect the countdown and cancellation. Tap **End Ride** or leave the app to stop monitoring.

For a quick install, download the `crashalert-debug-apk` artifact from the latest successful **Android checks** workflow run, unzip it, and install the contained APK on your own test device. It is a debug build, not a release.

## Build and test

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`. The local debug APK is written to `app/build/outputs/apk/debug/`. CI runs these commands on pushes and pull requests and keeps the debug APK artifact for seven days.

The unit tests cover ride transitions, missing sensors, sample handling, impact gating, countdowns, cancellation, contact validation, and location freshness. An Android phone is still needed to validate sensor behavior, permission handling, and the SMS app handoff.

## Data and permissions

- Motion sensors do not need a runtime permission. Sensor readings and the optional location fix are held in memory during use.
- Approximate location permission is requested only when the user taps the location button during a real unanswered check. Denial, timeout, or an unavailable provider leaves location out of the draft.
- Contact names and numbers are stored in this app's private preferences on the device. The app does not read the system address book.
- Opening an SMS draft passes its text to the user's messaging app. The app does not send it itself.

## Project layout

```text
app/src/main/java/com/crashalert/app/
├── MainActivity.kt                  Activity and user actions
├── telemetry/
│   ├── AndroidMotionMonitor.kt      Sensor listener and foreground lifecycle
│   ├── IncidentEngine.kt            Pure self-check state machine
│   └── RideSession.kt               Pure ride and sample state
├── contacts/TrustedContacts.kt      Local contact storage and validation
├── location/RideLocation.kt         Freshness check and map link
└── ui/RideScreen.kt                  Compose screen
```

## Next engineering steps

- Add an incident history and a safe end-to-end simulation that cannot contact real people.
- Validate and tune the motion rule with labelled data and controlled device testing before making reliability claims.
- Design and test background monitoring and opt-in alert delivery separately, including permission, battery, failure, and cancellation behavior.
- Build the optional web dashboard and Leaflet map after its data contract and privacy model are defined.

CrashAlert is under active development. Do not rely on this build as an emergency response service.
