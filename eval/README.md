# Evaluation & Research (RQ1 & RQ2)

- **`DoorPlates` / `DoorwayViews` (RQ1):** Offline comparison of `Nemotron 3.5 Super VL`, `Cosmos 3 Nano Reasoner`, and specialist baselines (OCR, person detector, brightness thresholds), reporting false-claim rate, abstention rate, latency, and cost.
- **`NodeStops` (RQ2):** Offline and live comparison of odometry-only lookup, odometry + deterministic plate-text matching, and odometry + `pgvector` retrieval + VLM choice.
- **Replay Mode:** Re-runs perception, retrieval, and agent steps on recorded stops without moving the car.
- **Safety Evaluation:** 10 approaches per obstacle (standing person, legs, chair legs, 10 cm box, floor bag, closed door) before populated-corridor patrols.
