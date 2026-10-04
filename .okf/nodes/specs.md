---
id: node:specs
title: Functional Specifications, Error Handling & Security Gates
version: 1.0.0
framework: Google OKF (Open Knowledge Framework)
category: specs
tags: [specs, requirements, acceptance-criteria, error-handling, security-gates, benchmarks]
relations: [node:memory, node:architecture, node:schemas, node:rules]
summary: Authoritative functional specifications, error handling models, cryptographic security constraints, and deterministic verification gates across Phases 0 through 8.
---

<!--
@aegis-contract
@claim Functional specifications, error handling, and security acceptance gates for Phases 0 through 8
@true Implements concrete functional requirements across all 9 phases, explicit socket error handling and watchdog timeouts, and cryptographic security constraints with HMAC validation
@false Permits dummy placeholder stubs, unhandled network exceptions, buffer overflows, or missing security validation
-->

# Functional Specifications, Error Handling & Security Gates

## 1. System Phases & Functional Scope

### Phase 0: Injection Validation & Environment Gate
- **Track A (Mandatory):** Win32 `SendInput` hardware scan-code keys and relative mouse movements.
- **Track B (ViGEm Fallback):** Virtual Xbox 360 controller emulation via ViGEmBus kernel driver.
- **Verification Rule:** `tools/input-tester` captures and validates 100% of injected PS/2 Set 1 scan codes with correct `KEYEVENTF_SCANCODE` and `KEYEVENTF_EXTENDEDKEY` flags.
- **In-Game Verification:** In target game offline mode, synthetic W/A/S/D keystrokes actuate 8-way movement, and `MOUSEEVENTF_MOVE` rotates camera smoothly without cursor jumping.
- **UIPI Detection:** Process elevation check correctly identifies elevated games and asserts receiver elevation status.

### Phase 1: Windows Receiver Core Engine
- High-performance UDP receiver bound to port 47789.
- `IInputSink` with `KeyboardMouseSink` Win32 implementation.
- Key-state diff engine enforcing 30 ms minimum hold time per key.
- Wraparound-safe look delta accumulator with 250 ms reconnection re-baseline and +/-4000 count clamp.
- Tri-Guard Safety Subsystem: 500 ms watchdog, foreground process focus guard, and global kill switch hotkey (`Ctrl+Alt+F12`).

### Phase 2: Android Multi-Touch HUD
- Jetpack Compose PUBG Mobile default layout (MoveStick, LookZone, dual fire buttons, ADS, action buttons).
- Multi-touch pointer ownership mapping 1:1 pointer IDs to controls.
- Edge latching mechanism preserving short taps for >= 2 consecutive 120 Hz packets.
- Dedicated sender thread decoupled from Compose rendering loop.
- `View.requestUnbufferedDispatch` and `setSystemGestureExclusionRects`.

### Phase 3: Pairing & Cryptographic Handshake
- Windows QR generator (`QRCoder`) emitting IPv4 list, port, and 32-byte secret key.
- Android Google Play Services Code Scanner capturing QR payload without camera permission.
- Bidirectional handshake (`hello` -> `hello_ack`) establishing session ID.
- Constant-time HMAC-SHA256 verification and replay attack rejection.

### Phase 4: Tactile Haptics & Feedback
- Android `VibratorManager` (API 31+) / `Vibrator` haptic feedback on button interactions.
- Reactive HUD status overlay displaying connection state, RTT, and pause reason.

### Phase 5: Gyroscope Precision Aiming
- `SensorManager` with `Sensor.TYPE_GYROSCOPE` at `SENSOR_DELAY_FASTEST` (~200 Hz).
- Pipeline: Angular velocity rate integration, display orientation compensation, bias calibration, and low-pass filtering.

### Phase 6: Custom Layout Editor & Profiles
- In-app interactive visual layout customizer with grid snapping, drag, resize, and opacity control.
- JSON profile export/import (`GamepadLayoutProfile`).

### Phase 7: Polish & Latency Benchmarks
- `WIFI_MODE_FULL_LOW_LATENCY` lock on Android.
- Named mutex single-instance enforcement on Windows.
- Windows Defender Firewall automated UDP inbound rule.

### Phase 8: Gamepad Fallback (Conditional)
- Virtual Xbox 360 controller via `Nefarius.ViGEm.Client` and ViGEmBus kernel driver.
- `input_pad` packet handling (Type 7) and dual-motor rumble dispatch (Type 1).

---

## 2. Explicit Error Handling & Fault Recovery

### 2.1 Watchdog & Silence Recovery
- If no valid UDP datagram arrives within 500 ms, the watchdog timer trips.
- Recovery Action: Immediately issues `Neutralize()` releasing all held keys and mouse buttons. Clears differential bitmap. Logs warning with timestamp.

### 2.2 Socket Exceptions & Malformed Payloads
- Truncated, oversized, or corrupted UDP packets are dropped immediately in the receive loop before memory allocation.
- Socket error codes (`WSAECONNRESET`, `SocketError.ConnectionReset`) on Windows are caught and suppressed to prevent receiver termination.
- Network interface changes on Android trigger socket recreation and re-resolution of host IP candidates.

### 2.3 Focus Loss & Process Termination
- Windows `GetForegroundWindow` poll every 50 ms compares active window against target process ID.
- If focus shifts away from target game: Inputs are neutralized within 5 ms, injection is gated, and status packet broadcasts `target_in_focus = false`.
- If target process exits, receiver disarms automatically.

---

## 3. Cryptographic & Security Constraints

### 3.1 Packet Authentication & Constant-Time Verification
- Every packet header and payload is authenticated via truncated 8-byte HMAC-SHA256.
- Receiver compares authentication tags using constant-time equality (`CryptographicOperations.FixedTimeEquals`) to eliminate timing side-channel attacks.
- Tampered packets fail verification and are dropped without parsing payload contents.

### 3.2 Handshake Binding & Replay Prevention
- `hello_ack` MAC is calculated across `Header || ServerNonce || ClientNonce`. An attacker cannot replay an old ack to bind a fake session.
- Monotonic sequence numbers in every packet must strictly advance using RFC 1982 serial arithmetic. Duplicate or regressing sequence numbers are discarded.

### 3.3 UIPI Elevation Guard & Principle of Least Privilege
- Receiver warns user if target game is running with elevated privileges (Administrator) while receiver is unelevated, preventing silent input blockage.
- Receiver binds only to private network subnets (RFC 1918) and rejects external packets.

---

## 4. Quantitative Acceptance Gates & Performance Benchmarks

1. **Network Latency Benchmark:** Estimated network one-way latency (RTT/2) p95 < 15.0 ms over 5 GHz Wi-Fi.
2. **Key Hold Safety Gate:** Keystrokes injected via `SendInput` hold for a deterministic minimum of 30 ms (configurable 0-50 ms).
3. **Look Smoothing Benchmark:** Cumulative look counters prevent camera jump across 100% of simulated packet drops; clamp deltas to +/-4000 counts.
4. **Android Allocation Gate:** 0 bytes allocated per send tick in `UdpSenderThread` hot path at 120 Hz.
5. **Receiver Efficiency Gate:** Receiver process CPU usage stays under 2.0% during active connected idle state.
