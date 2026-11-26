# ROVA Mobile App

## Development status

ROVA includes a debug Android app, a Kotlin/JVM patrol core, and a console replay runner. The app opens to five simulated patrol scenarios and has a separate doorway camera screen. Camera captures can be reviewed and saved in app-private storage. Face blur defaults to On and can be switched off before capture. Synthetic replay observations and live photos are independent at this stage.

### Install the Android app

With JDK 17 and Android SDK Platform 36 installed, open `mobile/` in Android Studio and run the `android-app` configuration. To build from PowerShell instead, run:

```powershell
.\gradlew.bat :android-app:assembleDebug
```

The debug APK is at `android-app/build/outputs/apk/debug/android-app-debug.apk`. On a connected device with USB debugging enabled, install with `adb install -r android-app/build/outputs/apk/debug/android-app-debug.apk`, then open **ROVA**. Select **Capture a doorway photo** and grant permission to use the rear camera. Inspect the full image before saving because on-device face detection can miss faces.

### Run and verify

Install JDK 17 and run from `mobile/`:

```powershell
.\gradlew.bat :test
.\gradlew.bat run --args=correct
.\gradlew.bat run --args=mismatch
.\gradlew.bat run --args=closed
.\gradlew.bat run --args=takeover
.\gradlew.bat run --args=blur-off
```

On Linux/macOS, use `sh gradlew` instead. The first build downloads Gradle/dependencies; the replay runner itself makes no network calls.

`Patrol` accepts typed assignment, map, frame, inspection, and motion-completion events. Its caller must serialize events on one loop. It pins map/room/blur settings, validates inspection invariants, records one in-memory observation, and requests home return. Mismatches wait for explicit confirmation, recheck, or skip. Takeover adopts a newer control session, requests motion cancellation, and rejects late replies. Real transport acknowledgement/watchdog enforcement, obstacle recovery, handback, and multi-stop sequencing remain future work.

Replay's blur scenarios validate fixture metadata. The separate camera screen applies the selected setting to a live capture. Replay results are marked `executionMode=replay` and `perceptionProvider=recorded`. Tests cover evidence attribution, duplicate replies, stale sessions, cancellation, map mismatch, partial views, closed doors, and both blur settings.

## Planned Android responsibilities

Runs on the smartphone mounted on the robot:
- **Privacy:** Captures frames and blurs faces on-device before any image leaves the phone.
- **Patrol Agent:** Sequences deterministic navigation tools (`go_to`, `face_door`, `return_home`) and AI perception calls (`read_plate`, `verify_place`, `inspect_door`, `ask_operator`).
- **Local Cache & Relay:** Caches the corridor topological map for retrieval and relays manual driving commands to the ESP32.
