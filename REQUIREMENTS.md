<!--
@aegis-contract
@claim Authoritative requirements checklist, error handling criteria, and security safety gates for Phone-as-Gamepad
@true Implements concrete domain task checklist across all 9 phases, explicit socket error handling and watchdog requirements, and cryptographic security constraints
@false Permits dummy placeholder stubs, missing domain requirements, or unverified tasks
-->

# Phone-as-Gamepad: Project Requirements Checklist

## Phase 0: Environment & Injection Validation
- [x] [validation] Verify Windows 11 environment, .NET 10 SDK, and WPF tooling installation
- [x] [validation] Implement SendInput scan-code injection CLI test tool with countdown
- [x] [validation] Build `tools/input-tester` diagnostic window logging key down/up scan codes and raw mouse deltas
- [x] [validation] Verify injected scan codes and mouse deltas appear accurately in input tester
- [ ] [validation] Test injected input in target game offline/practice mode (W/A/S/D movement, mouse camera rotation, LMB fire, Space jump)
- [ ] [validation] Test target game in borderless windowed and exclusive fullscreen display modes
- [ ] [validation] Detect elevated target game processes (UIPI) and log elevation requirement
- [x] [validation] Complete Phase 0 Track A GO/NO-GO gate decision
- [ ] [validation] (Conditional) If Track A fails, execute Track B ViGEm virtual Xbox 360 controller validation

## Phase 1: Windows Receiver Core Engine
- [x] [receiver] Scaffold .NET 10 WPF receiver solution and define `IInputSink` interface
- [x] [receiver] Implement `KeyboardMouseSink` with `KEYEVENTF_SCANCODE`, `KEYEVENTF_EXTENDEDKEY`, and `MOUSEEVENTF_MOVE`
- [x] [protocol] Implement shared packet encoder and decoder with Little-Endian binary framing
- [x] [protocol] Write unit tests for packet serialization, header validation, and endianness
- [x] [receiver] Build high-performance asynchronous UDP listener bound to port 47789
- [x] [receiver] Implement packet validation pipeline (magic 0x4750, version 1, length check, constant-time HMAC-SHA256)
- [x] [receiver] Implement sequence validation with RFC 1982 serial wraparound arithmetic
- [x] [receiver] Implement Key-State Differential Engine tracking held keys and enforcing 30 ms minimum hold time
- [x] [receiver] Implement Look Delta Processor with wraparound handling, 250 ms re-baseline, +/-4000 clamp, and fractional carry
- [x] [safety] Implement 500 ms watchdog timer triggering automatic `Neutralize()` on packet silence
- [x] [safety] Implement foreground focus guard checking `GetForegroundWindow` and neutralizing input on focus loss
- [x] [safety] Implement global emergency kill-switch hotkey (`Ctrl+Alt+F12`) instantly disarming receiver
- [x] [safety] Implement UIPI elevation detector warning user when target game is elevated
- [x] [receiver] Implement Status packet sender broadcasting armed state, focus, and engine availability at 1 Hz
- [x] [tools] Build `tools/test-sender` console utility simulating phone packets, held keys, and look sweeps
- [x] [ui] Implement system tray integration with status dashboard, tray icon indicators, and first-run anti-cheat notice

## Phase 2: Android Multi-Touch HUD
- [x] [android-ui] Scaffold Android project with Jetpack Compose, minSdk 26, targetSdk 35, and forced landscape orientation
- [x] [android-ui] Implement immersive sticky full-screen mode and display cutout / notch inset handling
- [x] [android-ui] Configure `View.setSystemGestureExclusionRects` to shield screen edges from navigation gestures
- [x] [android-hud] Implement MoveStick control with 0.35 deadzone mapping to W/A/S/D and optional floating origin
- [x] [android-hud] Implement action buttons (fire, ADS, jump, crouch, prone, lean, reload, slots) with hold, toggle, and tap behaviors
- [x] [android-hud] Implement background LookZone producing cumulative look counters
- [x] [android-hud] Implement fire-and-drag mechanics allowing fire buttons to feed look counters while dragging
- [x] [input-engine] Implement MultiTouch Pointer Manager assigning 1:1 exclusive ownership of pointers to controls
- [x] [input-engine] Implement Edge Latching mechanism ensuring sub-frame taps persist for at least 2 consecutive packets
- [x] [network] Implement dedicated high-precision 120 Hz UDP sender thread decoupled from Compose rendering
- [x] [network] Optimize touch latency with `View.requestUnbufferedDispatch`
- [x] [android-ui] Implement HUD status overlay displaying connection state, RTT, and paused reason

## Phase 3: Pairing & Cryptographic Security
- [x] [security] Implement Windows QR generator and key display in Receiver WPF dashboard
- [x] [security] Protect Windows pairing key at rest using DPAPI (`ProtectedData.Protect`)
- [x] [security] Implement Android QR scanner using Google Play Services Code Scanner without camera permissions
- [x] [security] Protect Android pairing key at rest using secure device key store
- [x] [protocol] Implement cryptographic handshake (`hello` -> `hello_ack`) deriving 4-byte session ID
- [x] [protocol] Implement `hello_ack` MAC binding over header + server nonce + client nonce
- [x] [protocol] Implement seamless session replacement on valid incoming `hello` from paired device
- [x] [protocol] Enforce constant-time HMAC-SHA256 signature verification on all post-handshake packets
- [x] [protocol] Implement automatic reconnection and network interface change handling

## Phase 4: Tactile Haptics & Status Feedback
- [x] [haptics] Implement Android tactile button feedback using `VibratorManager` (API 31+) and `Vibrator` fallback
- [x] [android-ui] Implement status feedback display with visual indicators for Armed, Focused, and Paused states
- [x] [network] Implement high RTT and packet drop rate warning indicators on mobile HUD

## Phase 5: Gyroscope Precision Aiming
- [x] [sensors] Implement `SensorManager` gyroscope listener with `Sensor.TYPE_GYROSCOPE` at `SENSOR_DELAY_FASTEST`
- [x] [sensors] Implement gyroscope pipeline: Angular velocity integration, display rotation remapping, bias calibration, and LPF
- [x] [sensors] Feed gyroscope deltas directly into cumulative look counters (`lookX`, `lookY`)
- [x] [android-hud] Implement HUD gyro toggle chip and activation modes (always-on, toggle, hold-to-aim ADS)

## Phase 6: Custom Layout Editor & Profiles
- [x] [editor] Implement per-control key rebinding to any KeyId (0..127) or mouse button (LMB, RMB, MMB)
- [x] [profiles] Define `GamepadLayoutProfile` JSON schema and implement Kotlinx serialization
- [x] [profiles] Implement profile persistence, duplication, reset, and JSON file import/export

## Phase 7: Polish & Latency Benchmarks
- [x] [android-net] Implement Android `WIFI_MODE_FULL_LOW_LATENCY` lock during active gameplay
- [x] [receiver] Enforce Windows single-instance execution using named mutex
- [x] [receiver] Add automated Windows Defender Firewall UDP rule configuration script (`windows-receiver/scripts/setup-firewall.ps1`)
- [ ] [benchmarks] Measure and verify p95 network one-way latency < 15.0 ms over 5 GHz Wi-Fi
- [x] [benchmarks] Verify zero heap allocations in Android 120 Hz sender thread hot path

## Phase 8: Virtual Gamepad Fallback (Conditional)
- [ ] [vigem] Complete Phase 0 Track B ViGEmBus verification
- [ ] [vigem] Implement `GamepadSink` using `Nefarius.ViGEm.Client` behind `IInputSink`
- [ ] [vigem] Implement `input_pad` packet handling (Type 7) and map HUD look zone to virtual right stick
- [ ] [vigem] Implement `rumble` packet handling (Type 1) with dual-motor vibration and 300 ms watchdog
