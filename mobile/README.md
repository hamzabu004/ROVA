# ROVA Mobile App

## Development status

The first implementation is a pure Kotlin/JVM patrol core with a console replay runner. It does not yet contain an Android UI, camera/face processor, durable evidence store, real perception, BLE/USB adapter, or firmware. Synthetic fixtures and `fixture://` references demonstrate state flow only; they are not captured photo evidence. Keep physical motors disconnected for this milestone.

### Run and verify

Install JDK 17 and run from `mobile/`:

```powershell
.\gradlew.bat test
.\gradlew.bat run --args=correct
.\gradlew.bat run --args=mismatch
.\gradlew.bat run --args=closed
.\gradlew.bat run --args=takeover
.\gradlew.bat run --args=blur-off
```

On Linux/macOS, use `sh gradlew` instead. The first build downloads Gradle/dependencies; the replay runner itself makes no network calls.

`Patrol` accepts typed assignment, map, frame, inspection, and motion-completion events. Its caller must serialize events on one loop. It pins map/room/blur settings, validates inspection invariants, records one in-memory observation, and requests home return. Mismatches wait for explicit confirmation, recheck, or skip. Takeover adopts a newer control session, requests motion cancellation, and rejects late replies. Real transport acknowledgement/watchdog enforcement, obstacle recovery, handback, and multi-stop sequencing remain future work.

The blur switch currently validates fixture metadata; it does not blur an image. Replay results are explicitly marked `executionMode=replay` and `perceptionProvider=recorded`. Tests cover evidence attribution, duplicate replies, stale sessions, cancellation, map mismatch, partial views, closed doors, and both blur settings.

## Planned Android responsibilities

Runs on the smartphone mounted on the robot:
- **Privacy:** Captures frames and blurs faces on-device before any image leaves the phone.
- **Patrol Agent:** Sequences deterministic navigation tools (`go_to`, `face_door`, `return_home`) and AI perception calls (`read_plate`, `verify_place`, `inspect_door`, `ask_operator`).
- **Local Cache & Relay:** Caches the corridor topological map for retrieval and relays manual driving commands to the ESP32.
