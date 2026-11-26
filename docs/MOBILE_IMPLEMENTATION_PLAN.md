# ROVA mobile app implementation plan

**Target outcome.** For each room selected by the operator, the phone coordinates travel to the doorway, verifies the room, inspects what the camera can see, and delivers evidence to the backend. The operator can take over at any point. After the final stop the phone requests return to the marked home spot. The first milestone is one doorway check in replay. Phase 4 starts with local services; cloud calls are optional later.

Use the [project proposal](PROPOSAL_v2.md) for the overall product and research requirements. This plan covers the app on the mounted phone. The other components can supply fixtures or simulators until their live interfaces are available. The additions below supersede cloud-only and mandatory-blur assumptions: start locally, enable cloud later, and let the operator disable face blur.

## Execution modes, transport, and frame policy

- **Replay first:** Bundled versioned map, recorded perception, simulated ESP32, and local evidence records. No backend or internet needed.
- **Local live next:** Run the backend, console, and map/evidence storage on a development computer reachable over the same Wi-Fi/hotspot. Use local perception adapters where available; unsupported checks return unavailable/unknown or ask the operator. This does not require a large VLM on the handset. Recorded outputs remain labelled replay.
- **Cloud later:** Point the phone at the hosted backend and explicitly select provider/remote-storage adapters. A local backend calling a cloud model still uses cloud perception. Never silently switch to cloud after local failure; defer WhatsApp while offline.
- Configure `execution_mode: replay | local | cloud`, `backend_base_url`, and `perception_provider: recorded | local | cloud`; record the effective route on every result. Use the computer’s LAN address rather than phone `localhost`. Use authenticated HTTPS for live deployment and limit any debug HTTP exception to the development endpoint. Change routes while idle.
- **ESP32 link:** One Kotlin `Esp32Transport` interface has simulator, BLE GATT, and USB-C OTG serial adapters. BLE uses Android as central and ESP32 as peripheral, with command writes and event notifications. USB uses Android USB-host permission and the board’s serial driver. Neither carries images or requires internet. Use one active adapter and the framing, acknowledgement, heartbeat, and reconciliation rules in [Phase 1 contracts](PHASE1_CONTRACTS.md#how-the-esp32-transport-works).
- **Face blur:** Add an On/Off phone setting, default On, pinned before each patrol. Off bypasses face processing and allows unblurred previews, local storage, perception calls, and uploads. Display the effective setting and cloud transmission consequence before starting. All export paths share the frame-policy interface and record `face_blur_enabled` and `privacy_applied: blurred | unblurred`. On-mode failures block export; they never switch the setting Off. Queued evidence retains the original setting. Recording permissions still apply.

The state diagram is also available as a [separate PNG](diagrams/patrol-state-control.png), with [editable source](diagrams/patrol-state-control.dot).

## Component ownership

| Component | Responsibility at the mobile boundary |
| --- | --- |
| Phone | Apply the selected frame policy, cache a versioned map, sequence the patrol, request perception, verify place, record observations, relay manual commands, and report progress. |
| ESP32 | Execute bounded motion commands and report completion or interruption. Enforce speed limits, obstacle stops, emergency stop, and the watchdog locally, including during manual control. |
| Backend | Authenticate operators and the device, assign patrols, relay console commands and status, accept evidence and observations, and build reports and WhatsApp summaries. Runs locally first, then optionally hosted; keeps any provider keys here. |
| Console | Select rooms, display progress, initiate takeover and handback, and review evidence or correct uncertain results. |
| Supabase | Store the authoritative map and evidence records. Provide map candidates for the proposal’s vector retrieval method through an agreed service interface. |

## Phase 1 — Agree on contracts and choose the device

1. Walk through home → one door → home with the operator. Include a wrong plate, a blocked route, a closed door, and takeover during inspection.
2. Record the target phone and OS. On that handset, evaluate camera access, face-processing support, flashlight control, and the proposed ESP32 link under screen lock and app backgrounding. Choose the mobile stack and transport based on those results.
3. Agree on the following exchanges with the component owners. Specify IDs, required fields, ordering, acknowledgements, timeouts, and what can be retried safely.

| Exchange | Minimum information |
| --- | --- |
| Backend → phone | Patrol ID, selected room IDs, map version, cancellation, authorized takeover or handback, and expiring manual commands. |
| Phone ↔ ESP32 | Command ID, current control session, bounded action and target, acknowledgement, completion, interruption reason, and controller status for reconciliation. |
| Phone ↔ perception service | Operation, request and frame IDs, processed image or authorized reference, expected response schema, and explicit uncertain or failed result. |
| Phone → backend | Patrol and stop IDs, requested room, verified room if known, progress, observation fields, evidence reference, and upload acknowledgement. |

**Exit criteria.** Produce a patrol state diagram and separate PNG, plus example messages. Document transition ownership, manual-control priority, and reconnect reconciliation. Record replay/local/cloud routes, selected transport, framing/heartbeat rules, and the On/Off frame policy. Complete handset checks before treating the transport as verified.

## Phase 2 — Implement one stop in replay

**Development checkpoint:** `mobile/` now contains a Kotlin/JVM single-stop coordinator, simulated command sink, synthetic replay scenarios, and tests. It demonstrates state flow and validates fixture metadata; it does not yet provide actual image evidence, durable storage, an Android app, hardware transport, or live face processing. Phase 2 remains in progress until saved image/map fixtures and the remaining integration are exercised.

Drive the patrol coordinator with a saved map, labelled image fixtures for both blur settings, simulated ESP32 events, and recorded model responses. Use the same state transitions and validation rules that live operation will use. Keep replay results distinguishable from live results.

1. Load the assigned map version and requested room. Issue a simulated move and wait for completion.
2. Treat arrival as an estimate. Read the plate and verify the candidate room before attaching inspection evidence to it. Keep the requested room and verified room in separate fields.
3. Inspect door state, occupancy, lights, and projector. Preserve “no one visible,” “empty,” and “cannot determine” as distinct outcomes. A partial view cannot establish that a room is empty.
4. Record the evidence and outcome, then request return home. For an uncertain location, expose recheck, operator confirmation, or skip as explicit outcomes.

**Exit criteria.** Replay a correct plate, mismatched plate, closed door, and takeover. The correct stop yields one evidence-linked observation. A mismatch enters review, a closed door yields unknown occupancy, and a late perception reply cannot resume a suspended patrol.

## Phase 3 — Verify camera privacy on the mounted phone

Implement the On/Off setting and shared frame-policy interface first. With On, capture and blur on-device before export, keep raw buffers transient, and withhold frames on processing failure. With Off, bypass face processing and allow explicitly labelled unblurred output. Route previews, local/cloud requests, evidence storage/uploads, and diagnostics through this interface. Pin the setting for the patrol and retain per-frame metadata through retries.

Test at mounting height with side profiles, partial faces, groups, glare, and low light. Exercise the flashlight where appropriate. Inspect actual outputs and outgoing payloads. Track missed faces separately from pipeline errors because successful processing alone cannot establish that all faces were blurred. Follow the proposal’s consent and permission requirements for recording.

**Exit criteria.** With On, forced processing failure blocks every export path. With Off, face processing is bypassed and every output is labelled unblurred. Verify the default, visible setting, patrol pinning, and queued-frame metadata across later setting changes. Review On-mode blur quality on representative scenes; successful processing alone does not guarantee that every face was detected.

## Phase 4 — Connect local services, then optional cloud AI and retrieval

**First local live test.** With the phone stationary, capture a plate using the selected blur policy, call the authenticated LAN backend’s local plate-reading adapter, validate the result, and compare it with a pinned cached map. Store evidence locally. If local plate reading is unavailable, keep replay labelled and record the missing capability rather than reporting a live success. Exercise loss of LAN access without any automatic cloud call.

**Cloud promotion.** Reuse the same API schemas and replace endpoint/provider/storage configuration while idle. The cloud test below is a later optional step; its blurred capture describes the default-On case. Off uses labelled unblurred output. Add Supabase and WhatsApp only when their services are enabled.

**First live cloud test.** With the phone stationary, capture one door plate, process it locally, and send it through the backend proxy for plate reading. The phone validates the response and compares the text with its cached map. An unreadable result leaves the room unverified. This milestone needs the handset, a small authenticated perception endpoint, and a provider account. It can run before live driving or the console is ready.

- Pin the map version for the patrol. Cache nodes, edges, plate text, door side, and relevant distances and headings. Reconcile a changed map before starting a new patrol.
- Begin with deterministic text matching inside the expected position window. Then add retrieval-assisted verification using the proposal’s embedding service and pgvector candidates. Allow the model to choose a candidate or none. Define when the phone can use the local baseline during a service outage and when it must ask the operator.
- Add doorway inspection using the model candidates in the proposal. Validate structured results, bound retries and provider fallback, and retain abstentions. Associate each accepted result with its original request, stop, verified room, and processed evidence frame.
- Record model and prompt versions, latency, token usage where available, retries, and fallback outcomes for evaluation. Keep provider credentials on the backend and redact sensitive payloads from diagnostics.

**Exit criteria.** Verify a known plate end to end. Exercise unreadable plates, unavailable retrieval, malformed responses, timeouts, and duplicate or late replies. None may silently assign evidence to the requested room. Preserve the selected verification method in the record so research runs can compare methods.

## Phase 5 — Integrate motion and manual takeover

Start with motors disabled or wheels raised. Connect the transport chosen in Phase 1 and exercise movement acknowledgement, completion, obstacle stop, cancellation, and return home. On takeover, suspend the autonomous sequence, invalidate pending motion intents, and relay only current authorized manual commands. Require explicit handback and position confirmation before resuming.

Implement newline-delimited JSON buffering, BLE fragmentation, and USB split/coalesced reads. Send 100 ms heartbeats and verify the 500 ms controller watchdog. Test BLE/USB switching only after stopping and reconciling `get_status`; never replay queued drive commands. Repeat the transport checks in local and cloud modes to establish that safety does not depend on internet access.

**Exit criteria.** Interrupt the connection before and after acknowledgement, lock the phone, restart the app mid-stop, and take over during a model call. Verify the ESP32 watchdog stops motion on link loss. Reconcile controller and patrol state after reconnect. A retry cannot repeat a movement, an expired command cannot execute, and a delayed reply cannot restore autonomous control.

## Phase 6 — Run a supervised patrol and capture evidence

After the project’s physical safety gate and testing permission, run one doorway and then the planned two-to-three-room route. Follow the full path from room selection through departure, verification, inspection, return home, and report review. Disconnect during evidence upload and verify that retrying produces one observation with the correct frame. Confirm that the console reports the phone’s actual state, including uncertainty and takeover.

**Exit criteria.** The operator can complete or interrupt a patrol and trace each reported observation to its verified room and evidence. Retain per-stop timing, verification method, errors, interventions, and return-home outcome for the evaluation harness. Turn field failures into replay cases that agents can rerun without moving the robot.
