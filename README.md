# ROVA

**A Low-Cost Robot for Doorway Room Checks and Patrol Reports**  
*Course: AI-4009 Generative AI · Track: R&D Product · Author: Hamza Tariq (23I-0510) · Supervisor: Dr Akhter Jamil*

ROVA is a phone-carrying robot car that patrols a university corridor, verifies each room at its doorway using open vision-language models (VLMs) and map retrieval, inspects what is visible from the door (door state, occupancy, lights, projector), returns to a marked home spot, and delivers an evidence-backed report with a WhatsApp summary.

## Architecture

ROVA separates deterministic motion/safety from AI perception:

- **`firmware/`** — ESP32 navigation controller: calibrated timed moves, IMU (gyroscope) heading-hold and 90° turns, ultrasonic + downward distance + bumper obstacle stop, speed limit, and watchdog.
- **`mobile/`** — Phone app on the robot: camera capture, on-device face blurring before upload, flashlight control, patrol agent loop, perception API calls, local map cache, and manual-control relay.
- **`backend/`** — FastAPI service (Render): patrol task orchestration, live status streaming, report generation, correction storage, and WhatsApp Cloud API summaries.
- **`frontend/`** — Next.js web console (Vercel): corridor room picker, live view, manual driving controls, evidence review dashboard, uncertain-result queue, and past reports.
- **`supabase/`** — PostgreSQL + `pgvector` schema and versioned migrations for corridor maps, patrols, reports, photos, and corrections.
- **`eval/`** — Offline & replay evaluation harness for **RQ1** (VLMs vs. specialist baselines on `DoorPlates` & `DoorwayViews`), **RQ2** (place verification on `NodeStops` & live patrols), and safety obstacle-stop trials.

## Models & Services

- **VLMs:** `Nemotron 3.5 Super VL` (Nebius Token Factory), `Cosmos 3 Nano Reasoner` (NVIDIA API)
- **Embeddings:** `Qwen3-Embedding-8B` (Nebius Token Factory)
- **Observability:** Langfuse (prompt versioning, latency, token/cost logging)
- **Storage & Vector DB:** Supabase (PostgreSQL + `pgvector`)
- **Notifications:** Meta WhatsApp Cloud API

## Getting Started

1. Copy `.env.example` to `.env` and fill in your API keys (never commit `.env`).
2. See each module's `README.md` for component-specific setup.
