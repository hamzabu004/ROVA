# ROVA Mobile App

Runs on the smartphone mounted on the robot:
- **Privacy:** Captures frames and blurs faces on-device before any image leaves the phone.
- **Patrol Agent:** Sequences deterministic navigation tools (`go_to`, `face_door`, `return_home`) and AI perception calls (`read_plate`, `verify_place`, `inspect_door`, `ask_operator`).
- **Local Cache & Relay:** Caches the corridor topological map for retrieval and relays manual driving commands to the ESP32.
