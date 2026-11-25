ROVA — Project Proposal v2
1. Title and Team
ROVA: A Low-Cost Robot for Doorway Room Checks and Patrol Reports
Track: R&D Product
ROVA is a phone-carrying robot car that patrols one university corridor, checks each room from its doorway with open vision-language models (VLMs), returns to a marked home spot and delivers an evidence-backed report. Navigation is deterministic; the AI reads door plates, confirms where the robot is, and inspects what is visible from the door.
The research asks where a VLM is worth its latency and cost in a low-cost robot's loop: for which doorway checks it beats specialist vision methods, and how much map-based place verification improves live task completion over deterministic localisation.
Member
Roll number
Role
Hamza Tariq
23I-0510
Sole member: AI, engineering and research
Course: AI-4009 Generative AI. Supervisor: Dr Akhter Jamil.
2. Problem and Motivation
Checking rooms across a building is still done by people walking the floor. Every evening, staff or guards walk each corridor to see which rooms are occupied and whether lights or projectors were left on; during the day the same questions come up ("is Lab 3 free?"). The round is slow, repeated daily and leaves no record.
Who faces it. Facilities, administration and security staff, lab in-charges, and faculty or students who need a quick answer about a room.
How it is solved today
Approach
Examples
Where it falls short
Manual rounds
Guards or staff walking the floor
Slow, repeated daily, no record kept
Room booking and timetables
University timetable, booking systems
Show what was booked, not what is actually happening
Occupancy sensors
[Density](https://density.io/competitors/butlr), [Butlr](https://www.butlr.com/blog/butlr-vs-density)
Installed room by room; one fixed question each; cost grows with the number of rooms
AI video analytics on CCTV
[Verkada AI-Powered Search](https://verkada.com/blog/ai-powered-search)
Only where cameras are fixed; corridor cameras do not see inside rooms
Security patrol robots
[Knightscope K5](https://knightscope.com/k5), [Cobalt Robotics](https://www.securitysales.com/news/cobalt_robotics_releases_autonomous_mobile_security_robot/)
Large, costly enterprise services built for security, not room-state reports
Why AI is needed. A doorway check is an open-ended visual judgement: is anyone inside, are the lights on, is the projector showing an image, and can this even be seen from here? One VLM answers all of these from one photo and can say "cannot determine" when the view does not allow an answer, where specialist methods need a separate model or rule per question. Movement, by contrast, is a well-understood control problem, so ROVA keeps it deterministic and uses AI only where a fixed rule cannot cope.
What ROVA adds. One mobile camera covers a corridor with nothing installed in the rooms, every result carries its photo as evidence, and staff can review and correct results in one place.
3. Product Overview and Features
A staff member picks rooms in the web console. ROVA drives to each door, confirms it is the right room, checks what is visible from the doorway, returns to its home spot and sends a report with a photo behind every result. Staff can take over driving at any time and can review and correct every result.
Core features (must have)
ID
Feature (user view)
C1
Pick rooms from a list in the console and start a patrol
C2
ROVA drives to each selected door on one corridor and stops facing it
C3
ROVA confirms it is at the right room before checking, by reading the door plate and matching it to the map
C4
Doorway check: door state, occupancy, lights and projector, with "cannot determine" when something is not visible
C5
Manual control and recovery: the operator takes over driving when ROVA is uncertain, blocked or lost, then hands control back
C6
Review dashboard: the evidence photo for every result, a queue of uncertain results, corrections and past reports
C7
Return to a marked home spot after each patrol
C8
Report summary sent to WhatsApp with a link to the full report
C9
Safety: obstacle stop, speed limit, emergency stop and a watchdog that halts the car if commands stop
Extended features (if time permits)
ID
Feature (user view)
E1
Free-text commands ("check the labs on this corridor")
E2
Activity description (lecture, lab work, cleaning)
E3
Scheduled patrols
E4
Automatic parking or docking for charging
E5
More corridors and a full floor
E6
VLM-guided steering and door centring
E7
Driving around obstacles instead of waiting
E8
Local model inference
What can be checked from the doorway. ROVA never enters rooms, so each check is limited to what the doorway view allows. "Empty" is reported only when the model judges the whole room to be in view; otherwise the result is "no one visible".
Check
Door open
Door closed
Door state
Yes
Yes
Occupancy
Yes, for the part of the room in view
Cannot determine
Lights
Yes
Only "light visible under the door"; otherwise cannot determine
Projector
Yes, if the screen is in view
Cannot determine
[embed: node/63bc8c61-0c41]
If the plate does not match, ROVA re-localises using the map or asks the operator. An obstacle stops the robot until the path clears or the operator takes over.
4. System Components and Architecture
ROVA is split into two independent modules: a deterministic navigation module on the car, and an AI perception module (plate reading, place verification, doorway inspection) called from the phone. A patrol agent on the phone sequences them, so each module can be built, debugged and evaluated on its own.
[embed: node/42aec410-81da]
Components
Frontend (Next.js on Vercel). Room picker, live view, manual driving controls, and the review dashboard with evidence photos, uncertain results, corrections and past reports.
Backend (FastAPI on Render). Turns a room selection into a task, streams live status, builds reports, stores corrections and sends WhatsApp summaries.
Data (Supabase PostgreSQL with pgvector; Langfuse). The corridor map, reports, photos and corrections; every model call is logged with its input, prompt version, output, latency and cost.
Cloud AI. Nemotron 3.5 Super VL and Qwen3-Embedding-8B on Nebius Token Factory, and Cosmos 3 Nano Reasoner on the NVIDIA API, all through OpenAI-compatible endpoints.
ROVA mobile app (phone on the car). Captures frames and blurs faces before any image leaves the phone, runs the patrol agent, calls the perception models, keeps a copy of the map for retrieval and relays manual-control commands.
ESP32 navigation controller. Runs the motion loop on its own: motor control with calibrated timed moves, a dedicated gyroscope (IMU) wired to the ESP32 for heading, and a front ultrasonic sensor, a downward-angled distance sensor and bumper switches that stop it for obstacles. The phone also supplies the camera, a flashlight for dark corridors and the network link.
How the robot estimates its movement
Map. A topological map of the corridor, built by hand in the console: nodes are the home spot, doors and junctions; edges store the measured distance, heading and which side the door is on.
Distance. Timed moves at a fixed motor speed, calibrated in seconds per metre over a measured run and re-checked as the battery drains. The drift per metre is measured in the feasibility test; wheel encoders are an Assignment 4 upgrade.
Orientation and turns. The dedicated gyroscope on the ESP32 holds the heading while driving straight and closes the loop on 90° turns, with a target error of 5° or less.
Staying straight. Gyro heading-hold keeps the car straight on short edges; side distance sensors for wall-following are added only if the feasibility test shows drift toward a wall.
Accumulated error. Odometry error grows with distance, so it is reset at every verified door. Error therefore builds up only along a single edge, never across the whole patrol.
Hard cases.
Long corridors without signs: odometry and gyro heading-hold carry the robot to the next node, where the error is reset. If no matching door is found within a search window around the expected position, ROVA stops and asks the operator.
Identical-looking corridor sections: position comes from odometry along the known sequence of edges, and door plates disambiguate; appearance alone never decides position.
Unexpected obstacles: ROVA stops, waits for the path to clear, and after a short timeout asks the operator to take over.
Changes to the environment: the map stores geometry and plate text, not appearance, and the operator can edit a node in the console when a plate changes.
Split between AI and deterministic code
Responsibility
AI component
Deterministic code
Patrol
Orders the rooms; handles uncertain results by re-checking or asking the operator
Route on the map; executes the agent's tool calls
Moving
None
Motor control, timed moves, gyro heading, turns
Position
Reads the plate and picks the matching node from retrieved candidates
Odometry estimate; accepts a match only when plate and odometry agree
Doorway check
Door state, occupancy, lights, projector, "cannot determine"
Schema validation, confidence thresholds, evidence storage
Report
Writes the short summary
Builds the report table and checks the summary against it
Safety
None
Obstacle stop, speed limit, watchdog, emergency stop, manual takeover
5. Generative AI Techniques
AI is used only where a fixed rule cannot cope: reading and matching door plates, judging the doorway view, deciding what to do with an uncertain result, and writing the summary.
Technique
Where it is used
Why
Foundation models via API (open-weight)
All perception calls, through Nebius Token Factory and the NVIDIA API
No GPU to manage; models are compared through the same OpenAI-compatible interface
Multimodal perception
Plate reading and doorway inspection from phone photos
One model answers several visual questions from one photo and can abstain
Retrieval-augmented generation
Place verification at each door (below)
Grounds "which room is this?" in the stored map instead of the model's guess
Agentic tool calling
The patrol agent calls go_to(node), face_door, read_plate, verify_place, inspect_door, ask_operator and return_home
Handles room order and uncertain outcomes (re-check from a second view, skip, or ask the operator); every tool is executed by deterministic code
Structured output and abstention
A JSON schema for every result, with "cannot determine" and a confidence; replies are validated, retried once, then sent to the other model
A malformed or unsure answer never becomes a report entry
Prompt engineering and observability
Few-shot prompts built only from development doors, versioned and logged in Langfuse with latency and cost
Repeatable, comparable runs
Integration: WhatsApp Cloud API
Report summaries with a link to the full report
The real external integration
What the retrieval step retrieves and how it is used. At each stop, the phone builds a query from the plate text it read and a short VLM description of the view. The three map nodes closest to that query, among those within the odometry window, are retrieved from pgvector with their plate text, door side and neighbours, and placed in the verification prompt. The VLM must pick one candidate or answer "none", and code accepts the choice only if it agrees with odometry. RQ2 compares this with odometry-only lookup and deterministic plate-text matching (Section 6).
Candidate models
Model
Served by
Role
[Nemotron 3.5 Super VL](https://developer.nvidia.com/blog/nvidia-nemotron-3-5-super-vl-powers-specialized-agents-with-multimodal-reasoning)
Nebius Token Factory
Plate reading, doorway inspection, place verification, patrol agent, summary
[Cosmos 3 Nano Reasoner](https://build.nvidia.com/nvidia/cosmos3-nano-reasoner/modelcard)
NVIDIA API (free trial endpoint)
The small VLM compared on the same perception tasks (RQ1)
Qwen3-Embedding-8B
Nebius Token Factory
Map retrieval
Conventional OCR, a person detector, a brightness threshold
Backend
Specialist (non-generative) methods compared in RQ1
These models are fixed for the MVP. Other models are tested only if measured latency, cost or accuracy falls short.
6. Technical Alternatives
The project makes two primary comparisons, and both are the research questions of Section 13. Everything else uses the default design.
AI task
Alternatives compared
Treatment
Doorway perception: plate reading, occupancy, lights, projector
Nemotron 3.5 Super VL; Cosmos 3 Nano Reasoner; specialist methods (conventional OCR, a person detector, a brightness threshold)
RQ1
Place verification at a door
Odometry-only map lookup; odometry plus deterministic plate-text matching; odometry plus retrieval and VLM choice (RAG)
RQ2
How we choose. Each alternative runs on the same held-out test doors. For each task, ROVA uses the option with the lowest false-claim rate whose median latency stays under 3 seconds per call and whose cost fits the Section 10 budget; ties go to the simpler or deterministic option. A specialist method that matches the VLM on a check replaces it for that check.
7. Data
All data comes from our own floor, and none of it trains a model. Development and test data come from different doors, so no test door, and no route through one, is seen while prompts are written.
Collection. Photos are taken from the robot's stopping position at each door, under daylight, evening and after-dark lighting, with doors open, half-open and closed. Consenting volunteers stage occupied rooms, and lights and projectors are switched on and off. Ground truth for every live patrol is written down by hand during the run.
Dataset
Size (Assignment 3 / Assignment 4)
Labels
Use
DoorPlates
70 / 300 plate photos from the stopping position
Exact plate text
RQ1
DoorwayViews
100 / 400 doorway photos
Door state; occupancy (occupied with count, no one visible, cannot determine); lights; projector
RQ1
NodeStops
None / 200 stops with their recorded odometry
True node
RQ2 offline
Live patrols
12 / 48 patrols
True node at each stop, room state, position errors, interventions, takeovers
End-to-end evaluation, RQ2 live
Corridor map
One corridor / two corridors
Nodes, distances, plate text, view descriptions, embeddings
Runtime retrieval
Each dataset is balanced across door states, lighting, occupied and unoccupied rooms, and lights and projectors on and off, and includes hard cases such as glare on plates and people partly hidden by the door frame. Every test set has at least 50 examples.
Splits. About a quarter of the doors are development doors, used only to write prompts and choose few-shot examples. All test photos, node stops and live-patrol scoring use the remaining test doors only. In Assignment 4, a second corridor with no development doors serves as a fully unseen test. Prompts are frozen before any test run.
Annotation. Labelling follows written guidelines. A second labeller, a classmate or volunteer, independently labels 20% of each dataset, and Cohen's kappa is reported (target 0.7 or higher); if no second labeller is available, the same 20% is re-labelled after two weeks and self-agreement is reported.
Privacy. Volunteers give written consent, and recording happens only with the department's written permission. Faces are blurred on the phone before any frame is sent to a cloud model or stored, Nebius Zero Data Retention is on, and WhatsApp messages carry only the summary and a link. Published datasets contain blurred frames only.
8. Evaluation Plan
ROVA is judged mainly on live patrols, because a correct decision on one photo does not guarantee a completed patrol. Offline photo sets are used only to compare methods fairly on identical inputs.
Doorway perception (offline, RQ1). Each error type is reported separately rather than as one accuracy figure.
Check
Errors reported separately
Baseline
Initial target
Plate reading
Exact match; character error rate
Conventional OCR
90% or higher exact match
Occupancy
False occupancy; missed occupant; count error; abstention
Person detector
False occupancy and missed occupant each 5% or lower
Lights
Wrong on/off; abstention
Brightness threshold
Wrong status 5% or lower
Projector
Wrong on/off; abstention
Brightness threshold on the screen region
Wrong status 5% or lower
Door state
Accuracy
None
95% or higher
For every check, the false-claim rate (a definite but wrong answer) and the abstention rate ("cannot determine") are reported side by side.
Place verification (RQ2). Node-identification accuracy on NodeStops offline, and the wrong-room rate in live patrols.
Live patrols (end to end). Assignment 3 runs 12 patrols on a 2–3 room stretch (2 lighting conditions x 2 obstacle conditions x 3 repeats); Assignment 4 adds 36 more on the full corridor and the second corridor, split across the RQ2 conditions. Each patrol records:
task completion: the share of selected rooms reached, verified and checked correctly
wrong-room rate
position error at each door (cm) and heading error after each turn (degrees)
return-home error, with a target of 30 cm or less in at least 90% of patrols
interventions and manual takeovers per patrol, with their causes
patrol duration
Safety evaluation. Before any patrol with people around, the obstacle stop is tested at operating speed against a fixed set: a standing person, a person's legs, a chair with thin legs, a low box of about 10 cm, a bag on the floor and a closed door. Each object gets 10 approaches, straight and at an angle, and the detection rate and stopping distance are recorded. Only if every object is stopped in every trial does ROVA patrol populated corridors; otherwise it runs only in low-traffic hours with the operator walking beside it until the failing case is fixed.
System metrics. Latency per call (median and 95th percentile), cost per request, schema-valid output rate, and failure, retry and fallback rates. Costs come from logged token counts and the providers' prices (as of November 2026): Nemotron 3.5 Super VL USD 0.30 input and 0.90 output per 1M tokens; Qwen3-Embedding-8B USD 0.01 per 1M tokens; Cosmos 3 Nano Reasoner is a free trial endpoint with no published price. The average cost per room and per patrol is stated once the system has been tested.
Statistics. Results are reported with 95% confidence intervals; methods are compared on the same items with McNemar tests, and every offline condition is run three times.
User benefit. We time a person doing the round of the same rooms against reading ROVA's report, record how many rooms still needed a visit, and track the share of results staff corrected in the review dashboard.
Error analysis. At least ten failures per component are grouped by cause, such as glare, partial views, drift, missed obstacles and API timeouts, each with a planned fix.
9. Deployment Plan
Every part runs on a named managed service except the mobile app on the robot's phone and the ESP32 firmware. No laptop or local GPU is part of the system.
Part
Host
How it is deployed
Web console
Vercel
Auto-deploys from the main branch on GitHub
Backend (FastAPI)
Render
Docker container, auto-deployed from the main branch
Database and photo storage
Supabase (PostgreSQL with pgvector)
Managed; schema changes through versioned migrations
Model-call logging
Langfuse (hosted)
Managed
Nemotron 3.5 Super VL, Qwen3-Embedding-8B
Nebius Token Factory
Managed API
Cosmos 3 Nano Reasoner
NVIDIA API (free trial endpoint)
Managed API; a paid endpoint is needed for real deployment
WhatsApp summaries
Meta WhatsApp Cloud API (test number)
Business verification before wider rollout
ROVA mobile app
The robot's phone
Installed directly on the phone
Navigation firmware
ESP32 on the car
Flashed over USB
Run modes. Live runs the car on the corridor with the operator present. Replay runs the perception, retrieval and agent steps on recorded stops, without moving the car, for evaluation and a public demo link. A recorded patrol video beside its report is kept as a demonstration backup.
Updates. Every push runs automated tests and a small replay evaluation in GitHub Actions, and nothing deploys if either fails. Models are set by configuration, prompts are versioned in the repository, and the app is reinstalled on the phone when it changes. API keys sit in the phone's secure storage, each provider account has a spending limit, and keys are rotated if exposed.
10. Funding and Business Model
ROVA is sold as a robot kit plus a monthly subscription per corridor or floor. The robot's own hardware is kept separate from the phone and the cloud infrastructure, so the full system cost is clear.
Target customers. Universities and degree-awarding institutes first (Pakistan has more than 200 recognised by HEC), where facilities, security and lab staff run daily room rounds; later, offices, hospitals and hostels. The buyer is the facilities or administration office.
System cost for one corridor (indicative; hardware to be confirmed with supplier quotes)
Part
Cost
Notes
Robot hardware: ESP32, chassis, motors and driver
PKR 6,100
v1 estimate
Robot sensors: ultrasonic sensor, downward distance sensor, bumper switches, gyroscope (IMU) module, battery and fittings
Quote pending
Added after the review for deterministic navigation and safety
Phone on the car
PKR 20,000 (used smartphone)
The developer's own phone is used during the pilot
Infrastructure
About USD 10 a month in the pilot; about USD 35 in live use
Table below
Operator
None
Any web browser; no laptop or GPU is needed
Monthly operating cost (pilot: one corridor, one patrol a day; prices as of November 2026)
Item
USD per month
Basis
Backend on Render
7
[Starter instance](https://render.com/pricing)
Database and storage (Supabase)
0 in the pilot; 25 in live use
[Free plan, then Pro](https://supabase.com/pricing)
Vercel and Langfuse
0
Free tiers in the pilot
Nemotron and embeddings
About 2 (estimate)
About 600 room checks a month at a few calls each, priced at [USD 0.30 / 0.90 per 1M tokens](https://openrouter.ai/provider/nebius); replaced by the measured cost
Cosmos (NVIDIA API)
0 during the trial
Paid price added once known
WhatsApp
0 on the test number; about 0.45 live
[USD 0.015 per message in Pakistan](https://propakistani.pk/2026/09/15/meta-charges-pakistan-11x-more-than-india-for-whatsapp-business-messages/)
Revenue model. A one-time kit price covering hardware and setup, plus a monthly subscription for software, map updates, report history and support. The first pilot runs free for one semester in our own department.
Seed funding
Source
What it offers
Eligibility and process
[NIC Islamabad](https://propakistani.pk/2026/02/13/ready-to-build-pakistans-next-unicorn-lets-make-it-happen/)
Fully funded incubation, mentorship and investor access, funded by the Ignite National Technology Fund; 0% equity
Cohort-based applications (Cohort 6 open); startups with a working prototype or MVP; application and pitch
[NVIDIA Inception](https://www.nvidia.com/en-gb/startups)
Free, no equity; training, preferred pricing, and credits or discounts from NVIDIA cloud partners including Nebius
Rolling applications; the company must be incorporated first
Minimum viable product. One robot on one corridor: staff pick rooms, ROVA reaches and verifies each door, checks what is visible from the doorway, returns to its home spot, and delivers a reviewable report with photos and a WhatsApp summary. Staff can take over driving at any point.
Lean Canvas
Block
Content
Problem
Daily room rounds are slow and leave no record; fixed sensors need installation in every room and answer one question
Customer segments
Universities first, then offices, hospitals and hostels; early adopters are departments with many labs and evening classes
Unique value proposition
Evidence-backed room checks along a corridor from one low-cost robot, with nothing installed in the rooms
Solution
Deterministic navigation with calibrated timed moves and a dedicated gyroscope, VLM doorway checks and place verification, a review dashboard and WhatsApp summaries
Channels
A pilot in our department, university facilities networks, NIC Islamabad demo days
Revenue streams
Kit price plus a monthly subscription
Cost structure
Hardware, model API usage, cloud hosting, support
Key metrics
Task completion, false-claim rate, takeovers per patrol, corridors subscribed
Unfair advantage
A labelled doorway benchmark and measured evidence of which checks a VLM can be trusted with
11. Risks and Responsible AI
ROVA never guesses: when it cannot see something it reports "cannot determine", motion and safety never depend on the AI, and an operator can take over at any moment.
Risk
Effect
Safeguard
Low or thin obstacles (chair legs, bags, low boxes) missed by the ultrasonic sensor
Collision
A downward-angled distance sensor and a bumper switch besides the ultrasonic sensor; low speed limit; the safety evaluation of Section 8 must pass before patrols in populated corridors
Odometry drift on long edges without signs
The robot stops at the wrong door
Speed recalibrated as the battery drains; error reset at every verified door; a search window around the expected position; stop and ask the operator if no match is found
Identical-looking corridor sections
Wrong room assumed
Position comes from odometry along the known edge sequence and the plate text, never from appearance alone
Closed doors or a partial view
Nothing visible inside
Only the checks in Section 3's doorway table are attempted; anything else is "cannot determine"; abstention rate tracked
Hallucinated inspection result
A wrong report entry
Schema with "cannot determine" and confidence thresholds; evidence photo with every result; staff review queue; false-claim rate measured by error type
Prompt injection through signs
A printed note steers the agent
Sign text is data checked against the map; tools come from a fixed registry and are executed by code
Network drop, API timeout or rate limit
Missing or late results
The car is controlled by the ESP32 and never waits on a model to stay safe; failed calls retry, fall back to the other model, or are marked "cannot determine"
API keys exposed from the phone
Misuse and unexpected bills
Secure storage, spending limits on each provider account, key rotation
Filming people
Loss of privacy
Counts only, never identities; faces blurred on the phone before upload; Zero Data Retention; notices on the floor
Where people decide. The operator builds and confirms the corridor map, picks the rooms for every patrol, can take over driving at any time, and reviews and corrects uncertain results before they are final. ROVA never enters rooms or opens doors, and its reports are advisory: switching off lights or following up with occupants stays with staff. WhatsApp is outbound only, so messages cannot steer the robot.
Permissions. Testing on the floor starts only after written permission from the department administration, which is kept with the project records.
12. Work Plan and Team Roles
Assignment 3 is due one week from now and covers 60–70% of the product: every core feature (C1–C9) working live on a 2–3 room stretch of one corridor, the first research comparison (RQ1) and the quantitative evaluation. The car is built first, and the patrol shrinks if it cannot reach doors reliably.
Part
Product work
Research work
Assignment 3 (60–70%, this week)
Car with calibrated timed moves, gyroscope heading and obstacle stop; core features C1–C9 live on a 2–3 room stretch; review dashboard; WhatsApp summaries; public replay link
DoorPlates and DoorwayViews for the test doors; RQ1 offline; 12 live patrols; safety evaluation
Assignment 4 (final)
Full corridor and a second corridor; wheel encoders, and side distance sensors if drift requires them; Docker deployment; extended features only if core targets are met
RQ2 offline and live; unseen-corridor test; statistics; paper and reproducibility package
Day
Milestone
Main outputs and gate
1
Parts and car
Buy parts; assemble the car; ESP32 holds heading with the gyroscope, drives straight and stops on obstacles; phone streams the camera
2
Feasibility test
Speed calibration in seconds per metre; drift per metre; 90° turn error; drive to 2–3 doors and back to the home mark; first safety evaluation. Gate: go, or fall back to operator-confirmed arrival
3
Data and backend
Photos of all doors; plate and doorway labels; map of the stretch; backend, database and console running
4
AI slice
Patrol agent and tools; plate reading and place verification with retrieval; doorway inspection with both models; specialist baselines
5
Product
Review dashboard, manual control, WhatsApp summaries, deployment and replay link
6
Trials
12 live patrols; RQ1 offline runs; latency and cost logged; error analysis
7
Demonstration
Results on the evaluation page; fixes; rehearsal
Assignment 4 then proceeds in four steps: map and data for the full corridor and the unseen second corridor; RQ2 offline and live trials; analysis, statistics and hardening; and the paper, reproducibility package, documentation and investor pitch.
If the day 2 feasibility test shows arrival at doors is unreliable, the patrol shrinks to two doors with operator-confirmed arrival and the week concentrates on doorway inspection and RQ1.
Member
AI work
Engineering work
Research role
Hamza Tariq
Patrol agent, perception prompts, place verification with retrieval
Car and firmware, mobile app, backend, console, deployment
Datasets, both research questions, statistics, paper
Resources to confirm before work starts
Resource
Status
ESP32, 2WD chassis with gear motors, motor driver
To buy on day 1
Gyroscope (IMU) module, ultrasonic sensor, downward distance sensor, bumper switches, battery pack
To buy on day 1
Phone for the car (provides camera, flashlight and connectivity)
Own phone; model to be stated
Nebius and NVIDIA API accounts
To set up on day 1
Written permission for indoor testing
To request immediately
Second labeller
To confirm
All code goes through pull requests that must pass the automated checks, and the supervisor reviews progress at each milestone.
13. Research Plan (R&D Track)
The research asks where a vision-language model is worth its latency and cost in a low-cost inspection robot. Instead of ranking models, it measures, for each doorway check and each localisation situation, whether a VLM, a specialist method or a deterministic rule is the right component, and what kind of errors each makes.
Related work
Navigation with language and vision models. Room-to-Room [1] and the SPL measure [2] set up instruction-following navigation, evaluated in simulation. SayCan [3] grounds a language model's plans in fixed robot skills, and LM-Nav [4] and Mobility VLA [7] combine VLMs with topological graphs built from earlier exploration or a recorded tour.
VLMs as controllers. PIVOT [9] and VLMnav [8] let an off-the-shelf VLM choose robot actions directly, and Cosmos-Reason1 [10] trains VLMs for embodied, physical reasoning.
Maps. VLMaps [5] and VLFM [6] build vision-language maps that rely on depth sensing and metric mapping, and Qwen3 Embedding [13] provides the text embeddings used here for retrieval.
Cost and reliability. FrugalGPT [11] shows that routing between models can cut cost without losing accuracy. VLMs describe objects that are not in the image [12], and text in the environment can act as a prompt injection [14].
Research gap and contribution. Prior systems show that VLMs can drive or localise robots, but they report overall success, usually with depth sensors, research robots or simulation. They do not say which parts of an inspection task a VLM should own on a cheap robot, or what that costs in latency, money and error types. This project contributes that measured breakdown, per doorway check and per localisation situation, together with a design rule for when a deterministic or specialist component is enough.
Research questions
ID
Research question
Hypothesis
RQ1
For plate reading, occupancy, lights and projector checks from a doorway, how do open VLMs (Cosmos 3 Nano Reasoner and Nemotron 3.5 Super VL) compare with specialist vision methods (OCR, a person detector, brightness thresholds) on each error type, abstention, latency and cost?
H1: The VLMs match the specialists on plate reading and on occupancy through open doors, and have a lower overall false-claim rate because they abstain on partial views; the specialists remain faster and cheaper
RQ2
Does retrieval-based place verification improve live task completion over odometry-only lookup and deterministic plate-text matching, and in which situations?
H2: Against odometry-only lookup, it halves wrong-room arrivals; against plate-text matching, it helps mainly where plates are unreadable or missing and makes no difference where plates are clear
Experimental design
Experiment 1 (RQ1, offline). Every method runs on the same DoorPlates and DoorwayViews photos from the test doors, with fixed prompts and logged model IDs, endpoints and run dates. Results are reported per error type and per door state.
Experiment 2 (RQ2, offline and live). Offline, the three conditions run on the same NodeStops, grouped by plate condition (clear, hard to read, no sign). Live in Assignment 4, each condition runs 8 patrols on the unseen second corridor over the same rooms, in a counterbalanced order, measuring task completion, wrong-room rate and takeovers.
Protocol. Offline conditions run three times at temperature 0. Results carry 95% confidence intervals, paired outcomes are compared with McNemar tests, and Holm correction is applied across comparisons.
Ablations (two only). Zero-shot vs few-shot prompts, and one vs three retrieved candidates.
Threats to validity. One building, partly addressed by the unseen second corridor. Occupancy is partly staged by volunteers. The builder is also the main labeller, mitigated by written guidelines and a second labeller. API models can change without notice, so model IDs and dates are logged and key runs repeated before submission.
Expected contributions. A working inspection robot for one corridor; a labelled doorway and node-stop benchmark with faces blurred; a measured breakdown of where VLMs beat specialist and deterministic components, with error types, latency and cost.
Target venue and reproducibility. A short paper at an embodied-AI or robot-learning workshop, such as the CVPR Embodied AI Workshop or an ICRA or IROS workshop, in the IEEE template. The reproducibility package holds the code, blurred datasets, configuration, experiment logs and a script that regenerates the results tables in replay mode.
References
P. Anderson et al. Vision-and-language navigation: Interpreting visually-grounded navigation instructions in real environments. CVPR 2018.
P. Anderson et al. On evaluation of embodied navigation agents. arXiv:1807.06757, 2018.
M. Ahn et al. Do as I can, not as I say: Grounding language in robotic affordances. CoRL 2022.
D. Shah, B. Osiński, B. Ichter, S. Levine. LM-Nav: Robotic navigation with large pre-trained models of language, vision, and action. CoRL 2022.
C. Huang, O. Mees, A. Zeng, W. Burgard. Visual language maps for robot navigation. ICRA 2023.
N. Yokoyama et al. VLFM: Vision-language frontier maps for zero-shot semantic navigation. ICRA 2024.
Z. Xu, H.-T. L. Chiang, Z. Fu et al. [Mobility VLA: Multimodal instruction navigation with long-context VLMs and topological graphs](https://arxiv.org/abs/2407.07775). CoRL 2024, PMLR 270.
D. Goetting, H. G. Singh, A. Loquercio. [End-to-end navigation with vision-language models: Transforming spatial reasoning into question-answering](https://proceedings.mlr.press/v288/goetting25a.html). NeuS 2025, PMLR 288.
S. Nasiriany, F. Xia, W. Yu, T. Xiao et al. [PIVOT: Iterative visual prompting elicits actionable knowledge for VLMs](https://proceedings.mlr.press/v235/nasiriany24a.html). ICML 2024, PMLR 235.
NVIDIA. [Cosmos-Reason1: From physical common sense to embodied reasoning](https://arxiv.org/abs/2503.15558). arXiv:2503.15558, 2025.
L. Chen, M. Zaharia, J. Zou. FrugalGPT: How to use large language models while reducing cost and improving performance. arXiv:2305.05176, 2023.
Y. Li et al. Evaluating object hallucination in large vision-language models. EMNLP 2023.
Y. Zhang, M. Li, D. Long, X. Zhang et al. [Qwen3 Embedding: Advancing text embedding and reranking through foundation models](https://arxiv.org/abs/2506.05176). arXiv:2506.05176, 2025.
K. Greshake et al. Not what you've signed up for: Compromising real-world LLM-integrated applications with indirect prompt injection. AISec 2023.
Appendix: Response to the Review
Review point
Change in v2
Where
Scope too large for one student
Core cut to one corridor: reach, verify and check doors, return home, review and report; free-text commands, parking, scheduling, VLM steering and full-floor coverage moved to extended
Sections 3, 12
New technical insight beyond comparing models
Research reframed around where a VLM is worth its cost against specialist and deterministic components, per check and per situation
Section 13
Corridors without signs, identical corridors, obstacles, changes
Odometry along a known edge sequence, error reset at verified doors, search window, stop and ask the operator
Section 4
How movement between nodes is estimated
Calibrated timed moves (wheel encoders in Assignment 4), closed-loop turns on a dedicated gyroscope, optional wall-following sensors, drift reset per edge; measured in the feasibility prototype
Sections 4, 12
Ultrasonic sensor misses low objects
Downward distance sensor and bumper added; safety evaluation with a fixed obstacle set gates patrols in populated corridors
Sections 8, 11
What can be checked from the doorway
Explicit table by door state; "empty" only when the whole room is in view
Section 3
Separate navigation and inspection
Deterministic navigation module on the ESP32, AI perception module called from the phone
Section 4
Return to base before automatic parking
Core is return to a marked spot within a measured error; parking moved to extended
Sections 3, 8
Laptop hardware not specified
No laptop: models are served by cloud APIs; latency measured in week 1
Sections 4, 9, 12
Cost excludes the laptop
Robot hardware, phone and infrastructure listed separately; no laptop or GPU needed
Section 10
Reduce to two primary comparisons
RQ1: VLMs vs specialist methods; RQ2: place verification with vs without retrieval; two ablations only
Sections 6, 13
Isolated-frame decisions are not task completion
Live patrols are the main evaluation: 12 in Assignment 3, 48 in total
Sections 8, 13
Evaluation data not independent of development
Development and test doors are disjoint; an unseen second corridor in Assignment 4
Section 7
Five patrols are too few
12 live patrols across lighting and obstacle conditions in Assignment 3, 48 in total
Section 8
Report inspection errors separately
False occupancy, missed occupants, light and projector errors and abstentions reported separately
Section 8
Justify the RAG component
What is retrieved, how it enters the prompt, and comparison with odometry-only and plate-text matching
Sections 5, 13
Manual control and recovery as a core feature
Core feature C5, with takeovers counted in every patrol
Sections 3, 8
Dashboard for evidence and corrections
Core feature C6: evidence photos, uncertain-result queue, corrections, history
Sections 3, 4
Feasibility prototype first
Days 1–2 with a go or no-go gate, and a stated fallback scope
Section 12
Assignment 3 is now 60–75%
Work plan revised: all core features live on a 2–3 room stretch plus initial RQ1 results
Section 12
Realistic schedule, confirmed hardware, testing permission
Day-by-day plan for the Assignment 3 week, then four steps for Assignment 4; resources and permission listed with their status
Section 12
