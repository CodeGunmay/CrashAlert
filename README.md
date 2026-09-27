# CrashAlert

CrashAlert is an Android prototype for automatic motion checks. After saving a rider profile, the app starts a foreground service with an ongoing notification. It listens to the phone's accelerometer and gyroscope even when the app screen is closed. There is **no Start Ride button and no manual SOS button**. The Ride tab only displays live readings; protection does not depend on opening it.

## Run on a phone

Open this repository in Android Studio with JDK 17 and Android SDK 36, then run the `app` configuration on a physical phone with motion sensors. Complete rider setup, grant notification permission so alerts are visible, and grant location permission for speed, heading and coordinates while the screen is open. Check the persistent notification and the Home tab to see whether monitoring is active. Android and device power policies can interrupt a foreground service; startup after reboot is best effort. The app cannot guarantee uninterrupted operation.

The five tabs are **Home** (protection and location status), **Ride** (live speed, heading and sensor readings), **Services** (open a map app search for hospitals or police and open the 112 dialer), **Contacts** (save and manage up to three trusted contacts), and **Profile** (edit rider and encrypted Medical ID, view monitoring status, retry a stopped service, and test the detection screen). Rider setup collects name and optional date of birth, blood group, allergies, conditions and medications. There is no phone verification or account signup yet.

## Detection and escalation

An unvalidated motion heuristic may start a 60-second red self-check with an alarm and **I'm OK** cancel action. If unanswered, the app shows a two-minute contact-help prompt, then a repeating two-minute urgent reminder. The foreground notification opens the emergency screen if the app is in the background. The Profile tab offers a safe UI test that never contacts anyone. These prompts do not confirm a crash and **do not send an alert**. Do not rely on this prototype for emergency response.

Contacts and Medical ID stay on this device. Medical ID is encrypted with Android Keystore. A rider can opt in to include it in a manual SMS draft opened after a real unanswered prompt; the rider must review and send the draft. A fresh location link is added only if explicitly requested from the emergency screen. No automatic SMS, call, cloud dispatch, hospital outreach, or location transmission is connected. The Services tab opens the phone's map app for a user-initiated search; it does not list verified nearby facilities or embed Leaflet.

Speed and heading use Android's location fix, not the accelerometer, and are shown as unavailable if location is missing or stale. Location updates stop when the app leaves the screen; background monitoring currently covers **motion and timers**, not background location. Battery percentage is sampled on app launch. Reports, OTP, automatic contact escalation, n8n, Twilio, a hosted backend, and repeated cloud delivery are future integrations. Implementing automatic dispatch requires a secure server, configured provider accounts, consent and permissions, delivery safeguards, and on-device testing. Provider secrets must never be placed in the APK.

## Build and tests

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest` with the Android SDK installed, or use the repository's GitHub Actions workflow. APKs produced by different signing keys can conflict: uninstall the previous debug APK before installing a newly signed one, which deletes local profile and contacts. Do not use a debug APK for a public safety release.

Source is under `app/src/main/java/com/crashalert/app/`: `ui/CrashAlertAppScreen.kt` is the five-tab shell, `ui/RideScreen.kt` contains the rider and Medical ID form, `telemetry/` owns motion and escalation state, `MonitoringService.kt` runs background checks, `contacts/` stores trusted contacts, `profile/` stores encrypted Medical ID, and `location/` handles freshness and device speed/heading.
