# Firmware (ESP32 Navigation Controller)

Deterministic motion control and hardware safety loop running on the ESP32:
- **Motion:** Calibrated timed moves (`seconds/metre`), motor driver control, speed limit.
- **Orientation:** Dedicated gyroscope (IMU) for heading-hold on straight edges and closed-loop 90° turns (target error ≤ 5°).
- **Safety:** Front ultrasonic sensor, downward-angled distance sensor, bumper switches, emergency stop, and command watchdog.
