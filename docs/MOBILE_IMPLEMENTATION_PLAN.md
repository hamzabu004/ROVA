# ROVA mobile app implementation plan

**Target outcome.** For each room selected by the operator, the phone coordinates travel to the doorway, verifies the room, inspects what the camera can see, and delivers evidence to the backend. The operator can take over at any point. After the final stop the phone requests return to the marked home spot. The first milestone is one doorway check in replay. The first cloud call comes in Phase 4.

Use the [project proposal](PROPOSAL_v2.md) for the overall product and research requirements. This plan covers the app on the mounted phone. The other components can supply fixtures or simulators until their live interfaces are available.

## Component ownership

| Component | Responsibility at the mobile boundary |
| --- | --- |
| Phone | Capture and blur frames, cache a versioned map, sequence the patrol, request perception, verify place, record observations, relay manual commands, and report progress. |
| ESP32 | Execute bounded motion commands and report completion or interruption. Enforce speed limits, obstacle stops, emergency stop, and the watchdog locally, including during manual control. |
| Backend | Authenticate operators and the device, assign patrols, relay console commands and status, accept evidence and observations, and build reports and WhatsApp summaries. The recommended cloud proxy keeps provider keys here. |
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

**Exit criteria.** Produce a patrol state diagram and example messages. Document which component owns each transition, how manual control takes priority, and how a reconnect is reconciled. Confirm the proposed phone → backend → provider route and the minimal backend dependency for Phase 4.

## Phase 2 — Implement one stop in replay

Drive the patrol coordinator with a saved map, pre-blurred images, simulated ESP32 events, and recorded model responses. Use the same state transitions and validation rules that live operation will use.

1. Load the assigned map version and requested room. Issue a simulated move and wait for completion.
2. Treat arrival as an estimate. Read the plate and verify the candidate room before attaching inspection evidence to it. Keep the requested room and verified room in separate fields.
3. Inspect door state, occupancy, lights, and projector. Preserve “no one visible,” “empty,” and “cannot determine” as distinct outcomes. A partial view cannot establish that a room is empty.
4. Record the evidence and outcome, then request return home. For an uncertain location, expose recheck, operator confirmation, or skip as explicit outcomes.

**Exit criteria.** Replay a correct plate, mismatched plate, closed door, and takeover. The correct stop yields one evidence-linked observation. A mismatch enters review, a closed door yields unknown occupancy, and a late perception reply cannot resume a suspended patrol.

## Phase 3 — Verify camera privacy on the mounted phone

Capture frames and apply face detection and blurring on-device before storage or transmission. Route live previews, cloud requests, evidence uploads, and diagnostics through the same processed-frame interface. Keep raw camera buffers transient. If processing fails, withhold the frame and expose the failure to the patrol coordinator.

Test at mounting height with side profiles, partial faces, groups, glare, and low light. Exercise the flashlight where appropriate. Inspect actual outputs and outgoing payloads. Track missed faces separately from pipeline errors because successful processing alone cannot establish that all faces were blurred. Follow the proposal’s consent and permission requirements for recording.

**Exit criteria.** Demonstrate that a forced processing failure blocks every export path. Review blur quality on representative scenes and document any conditions that require recapture or withholding an image before enabling live cloud requests.

## Phase 4 — Connect cloud AI and map retrieval

**First live cloud test.** With the phone stationary, capture one door plate, process it locally, and send it through the backend proxy for plate reading. The phone validates the response and compares the text with its cached map. An unreadable result leaves the room unverified. This milestone needs the handset, a small authenticated perception endpoint, and a provider account. It can run before live driving or the console is ready.

- Pin the map version for the patrol. Cache nodes, edges, plate text, door side, and relevant distances and headings. Reconcile a changed map before starting a new patrol.
- Begin with deterministic text matching inside the expected position window. Then add retrieval-assisted verification using the proposal’s embedding service and pgvector candidates. Allow the model to choose a candidate or none. Define when the phone can use the local baseline during a service outage and when it must ask the operator.
- Add doorway inspection using the model candidates in the proposal. Validate structured results, bound retries and provider fallback, and retain abstentions. Associate each accepted result with its original request, stop, verified room, and processed evidence frame.
- Record model and prompt versions, latency, token usage where available, retries, and fallback outcomes for evaluation. Keep provider credentials on the backend and redact sensitive payloads from diagnostics.

**Exit criteria.** Verify a known plate end to end. Exercise unreadable plates, unavailable retrieval, malformed responses, timeouts, and duplicate or late replies. None may silently assign evidence to the requested room. Preserve the selected verification method in the record so research runs can compare methods.

## Phase 5 — Integrate motion and manual takeover

Start with motors disabled or wheels raised. Connect the transport chosen in Phase 1 and exercise movement acknowledgement, completion, obstacle stop, cancellation, and return home. On takeover, suspend the autonomous sequence, invalidate pending motion intents, and relay only current authorized manual commands. Require explicit handback and position confirmation before resuming.

**Exit criteria.** Interrupt the connection before and after acknowledgement, lock the phone, restart the app mid-stop, and take over during a model call. Verify the ESP32 watchdog stops motion on link loss. Reconcile controller and patrol state after reconnect. A retry cannot repeat a movement, an expired command cannot execute, and a delayed reply cannot restore autonomous control.

## Phase 6 — Run a supervised patrol and capture evidence

After the project’s physical safety gate and testing permission, run one doorway and then the planned two-to-three-room route. Follow the full path from room selection through departure, verification, inspection, return home, and report review. Disconnect during evidence upload and verify that retrying produces one observation with the correct frame. Confirm that the console reports the phone’s actual state, including uncertainty and takeover.

**Exit criteria.** The operator can complete or interrupt a patrol and trace each reported observation to its verified room and evidence. Retain per-stop timing, verification method, errors, interventions, and return-home outcome for the evaluation harness. Turn field failures into replay cases that agents can rerun without moving the robot.
