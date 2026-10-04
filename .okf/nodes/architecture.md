---
id: node:architecture
title: Technical System Architecture & Execution Flow
version: 1.0.0
framework: Google OKF (Open Knowledge Framework)
category: architecture
tags: [architecture, tech-stack, windows, android, udp, sendinput, jetpack-compose, net10, vigem]
relations: [node:memory, node:rules, node:schemas, node:integrations, node:specs]
summary: Enriched technical stack versions, module architecture, repository layout, data flow pipelines, and component execution specifications.
---

# Technical System Architecture & Execution Flow

## 1. System Overview & End-to-End Execution Flow

```
+-----------------------------------------------------------------------------------------+
|                                    ANDROID CONTROLLER                                   |
|                                                                                         |
|  +------------------------+      +---------------------------+                          |
|  | Multi-Touch HUD        |      | Gyroscope Pipeline        |                          |
|  | - Move Stick (WASD)    |      | - Angular Velocity Sensor |                          |
|  | - Look Zone & Drag-Fire|      | - Axis Reorientation      |                          |
|  | - Action Buttons       |      | - Bias Calibration / LPF  |                          |
|  +-----------+------------+      +-------------+-------------+                          |
|              |                                 |                                        |
|              +----------------+----------------+                                        |
|                               |                                                         |
|                               v                                                         |
|                     +-------------------+                                               |
|                     | Input State Engine|                                               |
|                     | - Edge Latching   |                                               |
|                     | - Cumulative Look |                                               |
|                     +---------+---------+                                               |
|                               |                                                         |
|                               v                                                         |
|                     +-------------------+                                               |
|                     | Packet Serializer |                                               |
|                     | - Little-Endian   |                                               |
|                     | - HMAC-SHA256 Sig |                                               |
|                     +---------+---------+                                               |
|                               |                                                         |
|                               v (120 Hz UDP / Wi-Fi)                                    |
+-------------------------------|---------------------------------------------------------+
                                |
                                | Port 47789 UDP Datagrams
                                v
+-----------------------------------------------------------------------------------------+
|                                  WINDOWS 11 RECEIVER                                    |
|                                                                                         |
|                     +-------------------+                                               |
|                     | UDP Listener Loop |                                               |
|                     | (Async Socket)    |                                               |
|                     +---------+---------+                                               |
|                               |                                                         |
|                               v                                                         |
|                     +-------------------+                                               |
|                     | Packet Validator  |                                               |
|                     | - Magic & Version |                                               |
|                     | - Constant-Time   |                                               |
|                     |   HMAC-SHA256     |                                               |
|                     | - Serial Wraparound                                               |
|                     |   Sequence Check  |                                               |
|                     +---------+---------+                                               |
|                               |                                                         |
|                               v                                                         |
|                     +-------------------+                                               |
|                     | State Processor   |                                               |
|                     | - Look Delta Calc |                                               |
|                     |   (Wraparound/Frac|                                               |
|                     | - Key-State Diff  |                                               |
|                     |   (30ms Min Hold) |                                               |
|                     +---------+---------+                                               |
|                               |                                                         |
|                               v                                                         |
|                     +-------------------+                                               |
|                     | Safety Guard Hub  |                                               |
|                     | - 500ms Watchdog  |                                               |
|                     | - Foreground Focus|                                               |
|                     | - Kill-Switch Hot |                                               |
|                     +---------+---------+                                               |
|                               |                                                         |
|                               v                                                         |
|                     +-------------------+                                               |
|                     | IInputSink Router |                                               |
|                     +----+---------+----+                                               |
|                          |         |                                                    |
|           (Primary)      |         |  (Phase 8 Fallback)                                |
|                          v         v                                                    |
|  +------------------------+       +------------------------+                            |
|  | KeyboardMouseSink      |       | GamepadSink            |                            |
|  | - SendInput Scan Codes |       | - ViGEmBus Client      |                            |
|  | - MOUSEEVENTF_MOVE     |       | - Virtual Xbox 360 Pad |                            |
|  +-----------+------------+       +-----------+------------+                            |
|              |                                |                                         |
|              +----------------+---------------+                                         |
|                               |                                                         |
|                               v                                                         |
|                        PC Game Window                                                   |
+-----------------------------------------------------------------------------------------+
```

---

## 2. Technology Stack & Framework Specifications

### 2.1 Android Controller
- **Language & Runtime:** Kotlin 2.0+, Java 17 target bytecode.
- **UI Framework:** Jetpack Compose (BOM 2024.09.00+), Material 3.
- **Platform SDK:** `minSdk = 26` (Android 8.0 Oreo), `targetSdk = 35` (Android 15).
- **Core Libraries:**
  - `androidx.core:core-ktx:1.13.1`
  - `androidx.lifecycle:lifecycle-runtime-compose:2.8.6`
  - `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1`
  - `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3`
  - `com.google.android.gms:play-services-code-scanner:16.1.0` (Zero camera permissions required)
- **Low-Latency Touch & Motion APIs:**
  - `View.requestUnbufferedDispatch(MotionEvent)`: Bypasses Android UI frame batching to deliver touches directly to the input pipeline.
  - `View.setSystemGestureExclusionRects(List<Rect>)`: Shields display edges (up to 200dp per edge) from triggering Android 10+ Back and Home navigation gestures.
  - `SensorManager`: Configured with `Sensor.TYPE_GYROSCOPE` at `SensorManager.SENSOR_DELAY_FASTEST` (~200 Hz).
  - `WifiManager.WifiLock`: Held with `WifiManager.WIFI_MODE_FULL_LOW_LATENCY` (API 29+) or `WIFI_MODE_FULL_HIGH_PERF` to suppress 802.11 power saving during active gameplay.
  - `VibratorManager` (API 31+) & `Vibrator` (API 26-30): Sub-millisecond tactile transient clicks via `VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)`.
  - AndroidKeyStore: AES-256-GCM hardware-backed key encryption for storing the 32-byte pairing secret.

### 2.2 Windows 11 Receiver
- **Language & Runtime:** C# 13, .NET 10.0 (LTS), Windows SDK `net10.0-windows10.0.22621.0` (or `net10.0-windows`).
- **UI Framework:** Windows Presentation Foundation (WPF) with modern Fluent styling and dark mode.
- **System Tray Integration:** `H.NotifyIcon.Wpf` (v2.1.3+) for native tray presence, icon state indication (Armed / Disarmed / Paused), and context menu.
- **QR Code Generation:** `QRCoder` (v1.6.0+) with `PngByteQRCode` renderer.
- **OS Native Interop (P/Invoke `user32.dll`):**
  - `SendInput(uint nInputs, INPUT[] pInputs, int cbSize)`
  - Keyboard injection flags: `KEYEVENTF_SCANCODE` (0x0008), `KEYEVENTF_KEYUP` (0x0002), `KEYEVENTF_EXTENDEDKEY` (0x0001).
  - Mouse injection flags: `MOUSEEVENTF_MOVE` (0x0001), `MOUSEEVENTF_LEFTDOWN` (0x0002), `MOUSEEVENTF_LEFTUP` (0x0004), `MOUSEEVENTF_RIGHTDOWN` (0x0008), `MOUSEEVENTF_RIGHTUP` (0x0010), `MOUSEEVENTF_MIDDLEDOWN` (0x0020), `MOUSEEVENTF_MIDDLEUP` (0x0040).
  - Global Hotkey: `RegisterHotKey`, `UnregisterHotKey` hooked via WPF `HwndSource`.
  - Focus Tracking: `GetForegroundWindow()`, `GetWindowThreadProcessId()`, `QueryFullProcessImageName()`.
- **Cryptography & Secrets:**
  - `System.Security.Cryptography.HMACSHA256` for packet authentication.
  - `CryptographicOperations.FixedTimeEquals` for constant-time MAC verification.
  - `System.Security.Cryptography.ProtectedData` (`DataProtectionScope.CurrentUser`) for Windows DPAPI-encrypted pairing key persistence.
- **Virtual Gamepad Fallback (Phase 8):**
  - `Nefarius.ViGEm.Client` (v1.21.256) communicating with ViGEmBus kernel driver.

---

## 3. Repository Directory Structure

```
/
├── .okf/                              # Google OKF Knowledge Graph
│   ├── okf_manifest.json              # Canonical node registry and relations
│   └── nodes/                         # 7 canonical knowledge nodes (.md)
│       ├── memory.md
│       ├── architecture.md
│       ├── schemas.md
│       ├── integrations.md
│       ├── specs.md
│       ├── rules.md
│       └── timeline.md
├── REQUIREMENTS.md                    # Root feature requirement checklist for harness safety hooks
├── PLAN.md                            # Authoritative implementation plan (Rev 4)
├── docs/                              # Project technical documentation
│   ├── protocol.md                    # Authoritative wire protocol specification
│   ├── keys.md                        # Authoritative KeyId (0..127) to PS/2 scan code mapping
│   ├── pairing.md                     # QR format and cryptographic handshake sequence
│   ├── layout-format.md               # JSON profile schema for touch HUD
│   └── ui-mockup.html                 # Visual HTML/JS reference mockup of the touch HUD
├── android-controller/                # Android Mobile Application (Gradle)
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── app/
│       ├── build.gradle.kts
│       └── src/main/
│           ├── AndroidManifest.xml
│           └── java/com/gamepad/controller/
│               ├── MainActivity.kt
│               ├── ui/
│               │   ├── hud/           # Compose HUD, MoveStick, LookZone, ActionButtons
│               │   ├── editor/        # HUD Customizer (drag, resize, snap, opacity)
│               │   ├── pairing/       # QR Scanner screen (Google Code Scanner)
│               │   └── status/        # RTT, connection state, paused indicators
│               ├── input/
│               │   ├── PointerManager.kt
│               │   ├── LookAccumulator.kt
│               │   ├── GyroProcessor.kt
│               │   └── EdgeLatchEngine.kt
│               ├── network/
│               │   ├── UdpSenderThread.kt
│               │   ├── PacketBuilder.kt
│               │   └── SessionClient.kt
│               ├── security/
│               │   ├── KeystoreManager.kt
│               │   └── HmacSigner.kt
│               └── profiles/
│                   ├── ProfileRepository.kt
│                   └── DefaultLayouts.kt
├── windows-receiver/                  # Windows 11 Receiver Application (.NET 10 WPF)
│   ├── GamepadReceiver.sln
│   ├── src/
│   │   └── GamepadReceiver/
│   │       ├── GamepadReceiver.csproj
│   │       ├── App.xaml / App.xaml.cs
│   │       ├── MainWindow.xaml / MainWindow.xaml.cs
│   │       ├── Core/
│   │       │   ├── UdpReceiverService.kt -> UdpReceiverService.cs
│   │       │   ├── PacketValidator.cs
│   │       │   ├── KeyStateDiffEngine.cs
│   │       │   └── LookDeltaProcessor.cs
│   │       ├── Sinks/
│   │       │   ├── IInputSink.cs
│   │       │   ├── KeyboardMouseSink.cs
│   │       │   └── GamepadSink.cs (Phase 8)
│   │       ├── Safety/
│   │       │   ├── FocusGuard.cs
│   │       │   ├── WatchdogTimer.cs
│   │       │   ├── KillSwitchManager.cs
│   │       │   └── ElevationDetector.cs
│   │       ├── Security/
│   │       │   ├── PairingService.cs
│   │       │   ├── DpapiStorage.cs
│   │       │   └── HmacAuthenticator.cs
│   │       └── Interop/
│   │           ├── Win32SendInput.cs
│   │           └── KeyCodeTable.cs
│   └── tests/
│       └── GamepadReceiver.Tests/
│           ├── GamepadReceiver.Tests.csproj
│           ├── PacketSerializationTests.cs
│           ├── SequenceWraparoundTests.cs
│           ├── KeyDiffEngineTests.cs
│           ├── LookDeltaProcessorTests.cs
│           └── ReplayProtectionTests.cs
└── tools/
    ├── test-sender/                   # CLI packet transmitter for automated testing
    │   └── Program.cs
    └── input-tester/                  # Diagnostic WPF window logging raw scan codes & mouse deltas
        ├── MainWindow.xaml
        └── MainWindow.xaml.cs
```

---

## 4. Subsystem Architecture

### 4.1 Android Controller Subsystems
1. **Multi-Touch Pointer Manager:** Tracks active touch points. Assigns pointer IDs exclusively to controls (MoveStick, ActionButtons) or to the background LookZone. Fires `ACTION_DOWN`, `ACTION_MOVE`, `ACTION_UP` into the input engine.
2. **Look Accumulator:** Aggregates touch deltas and gyro angular rate into cumulative `int32` horizontal (`lookX`) and vertical (`lookY`) counters that wrap smoothly around `Int32.MaxValue` and `Int32.MinValue`.
3. **Edge Latch Engine:** Guarantees that short taps (e.g. quick jump or weapon swap) occurring between 120 Hz sender ticks are latched as active for at least two consecutive UDP packets, preventing lost inputs.
4. **Dedicated Timed Sender Thread:** Uses a high-precision `Thread.sleep` / `nanoTime` loop decoupled from Android Compose UI rendering. Emits packets at 120 Hz during activity; emits keepalive packets every 50 ms when idle.

### 4.2 Windows Receiver Subsystems
1. **Async UDP Listener:** Uses `System.Net.Sockets.Socket` with `SocketAsyncEventArgs` or `UdpClient.ReceiveAsync` bound to port 47789.
2. **Packet Validation Pipeline:** Rejects corrupt packets (invalid magic, unknown version, mismatched size, failed constant-time HMAC, mismatched session ID, or stale sequence number using RFC 1982 serial number arithmetic).
3. **Look Delta Processor:** Computes relative mouse motion as `current - previous` with wraparound safety. Re-baselines baseline counter without motion if gap > 250 ms. Carries sub-pixel fractional remainders.
4. **Key-State Diff Engine:** Maintains bitmap of physically down keys. Computes `down_events = new_keys & ~held_keys` and `up_events = held_keys & ~new_keys`. Enforces 30 ms minimum hold time per key.
5. **Safety Tri-Guard:**
   - **Neutralizer:** Immediately sends `KEYUP` for all down keys and releases all mouse buttons.
   - **Watchdog:** Triggers neutralization if no valid input packet arrives within 500 ms.
   - **Focus Guard:** Neutralizes and ignores input if the target game process does not own the foreground window (`GetForegroundWindow`).
   - **Kill-Switch:** Global hotkey (`Ctrl+Alt+F12`) instantly toggles Armed state and forces neutralization.
