# Phone-as-Gamepad: Implementation Plan (Rev 4, final)

Project: Android phone as a battle-royale style touch controller for Windows 11 PC games
Status: implementation-ready
Revision: 4 (Rev 3.1 reworked for the PUBG Mobile style layout; changes listed at the end)

---

## 1. Goal

Build two applications:

- Android controller
- Windows 11 receiver

The phone shows a PUBG Mobile style touch HUD: a move stick on the left, a swipe-to-look area on the right, fire buttons, ADS, jump, crouch, prone, lean, reload, weapon slots, and more. Touch and gyro input is sent over Wi-Fi using UDP to the Windows receiver, which injects it into Windows as keyboard and mouse input for the running game.

```
Android phone                          Windows receiver
Touch HUD: move stick, look zone,      UDP listener, validation, session/HMAC,
fire/ADS/jump/... buttons, gyro        key-state diff engine, safety guards
                    --- UDP/Wi-Fi --->        |
                    up to 120 Hz          IInputSink
                                          |-- KeyboardMouseSink (SendInput)   primary
                                          |-- GamepadSink (ViGEm)             optional fallback, Phase 8
                                                 |
                                               Game
```

An interactive mockup of the HUD is included as `/docs/ui-mockup.html`. Open it in a browser to try the controls, the layout editor, and the Windows receiver window. The control table in section 3 is the authoritative spec; the mockup is a visual reference.

---

## 2. Fixed decisions

Do not re-ask these unless implementation becomes impossible.

**Android:** Kotlin, Jetpack Compose, minSdk 26, forced landscape, multi-touch, swipe-to-look, gyro look, local haptics, QR pairing, custom layout editor with per-control key binding, JSON profiles.

**Windows:** C#, .NET 10 (LTS), WPF, `net10.0-windows`, system tray receiver, output through an `IInputSink` abstraction. (.NET 8 is not used: it reaches end of support on 10 Nov 2026.)

**Output:** primary is keyboard and mouse through `SendInput`. Optional fallback is a virtual Xbox 360 gamepad through ViGEm (Phase 8), built only if Phase 0 shows keyboard/mouse injection does not work in the target game, or if the user asks for it later.

**Transport:** Wi-Fi, UDP, default port `47789`, configurable.

**v1 features:** touch HUD, swipe-to-look, gyro look, local haptic feedback, custom layout editor with key binding, QR pairing, automatic reconnect, connection/RTT/status display, safety guards (focus guard, kill switch, watchdog).

**Rumble:** games do not send rumble to a keyboard and mouse, so rumble exists only in the gamepad fallback (Phase 8). In keyboard/mouse mode the phone gives local haptic feedback instead.

**Out of scope:** multiple simultaneous controllers, DualShock emulation, internet or remote play, iOS, Bluetooth, USB transport, per-packet encryption, mouse wheel, text typing or chat, absolute cursor positioning, macros or any automated input.

Input data is not secret. Authentication and integrity are sufficient for the LAN threat model. The app only forwards the player's own touch input; it must never generate input by itself.

---

## 3. HUD specification (default layout)

Screen coordinates: `x` and `y` are percentages of the screen width and height, measured at the control's center. `size` is the control diameter as a percentage of the screen width (the stick is a ring of that diameter; chips are width x height). Landscape only, both orientations. All keys are defaults and can be rebound per control and per game profile. Confirm each game's own bindings; the defaults below follow common PC shooter bindings.

| id | Control | Default key | Behavior | x | y | size |
|---|---|---|---|---|---|---|
| ms | Move stick | W A S D | stick | 16 | 66 | 17 |
| run | Run lock | Shift | toggle | 16 | 34 | 5.6 |
| lfire | Fire (left) | LMB | hold | 31 | 58 | 9 |
| rfire | Fire (right) | LMB | hold, drag also looks | 86 | 64 | 14 |
| scope | Aim down sights | RMB | toggle | 73 | 47 | 8 |
| jump | Jump | Space | hold | 94 | 38 | 8 |
| crouch | Crouch | C | hold | 76 | 84 | 7 |
| prone | Prone | Z | hold | 95 | 88 | 7 |
| leanl | Lean left | Q | hold | 62 | 32 | 6 |
| leanr | Lean right | E | hold | 70 | 32 | 6 |
| reload | Reload | R | hold | 64 | 68 | 6.4 |
| use | Pick up / use | F | hold | 58 | 54 | 6.4 |
| s1 | Weapon slot 1 | 1 | hold | 38 | 15 | 6 |
| s2 | Weapon slot 2 | 2 | hold | 44.5 | 15 | 6 |
| s3 | Melee slot | 3 | hold | 51 | 15 | 6 |
| gren | Grenade | G | hold | 58 | 15 | 6 |
| heal | Heal | 5 | hold | 65 | 15 | 6 |
| map | Map | M | hold | 84 | 12 | 5.4 |
| bag | Backpack | Tab | hold | 91 | 12 | 5.4 |
| gyro | Gyro toggle | none (phone only) | toggle chip | 50 | 90 | 15 x 4.8 |

**Look zone:** the area from x = 42% to the right edge, behind all controls. A touch that starts in the zone, not on a control, is a look touch.

**Behavior types** (selectable per control):
- `hold`: key is down while the finger is down.
- `toggle`: first tap presses and holds the key, next tap releases it (used for ADS and run lock).
- `tap`: a short press and release on touch, for games where the key is itself a toggle in-game (for example crouch).

**Move stick:** the stick maps to W/A/S/D with a 0.35 deadzone per axis (diagonals press two keys). An optional floating origin places the stick where the thumb lands inside its zone. The WASD keys are fixed for the stick; they can be changed in the profile, not per direction at runtime.

**Fire and drag:** a finger that is holding a fire button also contributes look motion when it moves. This is how players control recoil while firing. (The mockup does not show this; the app must.)

**Menus:** in inventory and map screens the look zone moves the cursor and a fire button clicks. No special mode is needed; test it.

---

## 4. Critical risks (read first)

### 4.1 Injected input, anti-cheat, and account safety

Games with kernel-level anti-cheat (for example BattlEye or Easy Anti-Cheat) may block, ignore, or flag input injected by software, and some actions can lead to bans. The app only forwards a human's touch input, but anti-cheat systems cannot tell the difference. Consequences for this plan:

- Phase 0 tests injection first, and only in offline or practice modes. Never test in online, ranked, or tournament matches until the anti-cheat behavior of that game is known.
- The user is responsible for checking each game's terms and anti-cheat policy before using this online. Put this in the README and in a first-run notice in the receiver.
- If injection is blocked, Phase 8 (gamepad fallback) becomes mandatory. Games that officially support controllers are less likely to object to a virtual gamepad, but this is not guaranteed either.

### 4.2 ViGEmBus (gamepad fallback only)

ViGEmBus is retired and its repository was archived (Nov 2023). It receives no updates, and its bundled auto-updater pointed at domains the author no longer controls, so the updater must stay disabled. All gamepad code goes through `IInputSink` so the backend can be replaced. Only relevant to Phase 8.

### 4.3 Other injection pitfalls

- Many games read scan codes, so keys MUST be injected with `KEYEVENTF_SCANCODE`, not only virtual-key codes. Arrow keys and other extended keys need the extended-key flag.
- Games using raw input generally accept relative `MOUSEEVENTF_MOVE`, but this must be verified per game. Do not use absolute mouse positioning.
- Windows blocks injection into elevated windows (UIPI). If the game runs as administrator, the receiver must run as administrator too. Detect and explain this.
- "Enhance pointer precision" distorts injected relative motion in games that do not use raw input; document how to turn it off.

---

## 5. Phase 0: environment and injection validation

Exists to avoid building the whole project on input that the target game ignores.

**Ask the user at the start of Phase 0 which game or games are the test targets**, and confirm each has an offline or practice mode.

Track A (mandatory):
- [ ] Verify Windows 11 build, .NET 10 SDK, WPF tooling
- [ ] Build a tiny console tool that injects scan-code key presses and relative mouse moves with `SendInput` after a countdown
- [ ] Build `tools/input-tester`: a small window that logs key down/up with scan codes and raw mouse deltas, so injection can be inspected without a game
- [ ] Confirm the injected keys and mouse deltas appear in the input tester
- [ ] In the target game's offline or practice mode, confirm: W/A/S/D move, the mouse turns the camera, LMB fires, RMB aims, Space jumps, and a held key stays held
- [ ] Test with the game in borderless and fullscreen modes
- [ ] Test with the game running normally and, if relevant, elevated
- [ ] Record the exact working method, flags, and game settings

**GO/NO-GO (Track A):** proceed only if the target game accepts injected keyboard and mouse input reliably in offline or practice mode.

**NO-GO:** stop, report the exact failure, and run Track B before deciding. Never switch output method silently.

Track B (only if Track A is NO-GO, or when starting Phase 8):
- [ ] Verify the Nefarius ViGEm client NuGet package is available; record its version
- [ ] Identify one specific ViGEmBus release and record its checksum
- [ ] Install the driver manually (updater disabled)
- [ ] Confirm Windows and XInput detect the virtual controller
- [ ] Test with Windows Memory Integrity ON and OFF (only place this is tested; re-enable it afterwards)
- [ ] Confirm the target game accepts the virtual gamepad
- [ ] Record the exact working driver and package versions

If both tracks fail: stop and report to the user.

---

## 6. Repository structure

```
/android-controller/   app/ ...
/windows-receiver/     src/, tests/ ...
/docs/                 protocol.md, keys.md, pairing.md, layout-format.md, ui-mockup.html
/tools/test-sender/    console sender for testing without a phone
/tools/input-tester/   window that logs injected keys and mouse deltas
/PLAN.md
```

`/docs/protocol.md` is the single source of truth for the wire protocol. `/docs/keys.md` is the single source of truth for key IDs. Implementations and the test sender must conform to them.

---

## 7. Windows architecture

```
UDP listener -> packet parser -> validation (magic, version, length, MAC, session, sequence)
  -> input state -> look-counter delta + key-state diff -> safety guards -> IInputSink -> SendInput
```

Components: pairing manager, QR generator, session manager, key-state diff engine, look-delta processor, safety guards (armed switch, focus guard, kill-switch hotkey, watchdog), status sender, tray icon, settings, firewall configuration, logging, connection state, single-instance protection.

Receiver settings: armed on/off, target process (required before arming), kill-switch hotkey (default Ctrl+Alt+F12, configurable), PC-side look scale, minimum key hold time (default 30 ms), allowed output modes.

## 8. Android architecture

```
Touch HUD (stick, buttons, look zone) -> input state
  -> gyro processor -> look counters + key bitmap + mouse buttons -> UDP sender -> receiver
```

Components: Compose HUD, multi-touch pointer manager, look processor (touch and gyro), UDP networking, pairing manager, QR scanner, haptics manager, layout editor, JSON profile storage, status UI.

---

## 9. Protocol v1

UDP, default port 47789, little-endian binary. Every packet is `HEADER + PAYLOAD + MAC`.

**MAC:** HMAC-SHA256, first 8 bytes transmitted, calculated over all bytes before the MAC (exception: `hello_ack`, see 9.6). The receiver compares MACs in constant time.

### 9.1 Header (12 bytes)

| Field | Size | Description |
|---|---|---|
| magic | 2 | `0x47 0x50` |
| version | 1 | protocol version (1) |
| type | 1 | packet type |
| session | 4 | session ID; `0` for `hello` and `hello_ack` |
| seq | 4 | sequence number |

Sequence numbers: `uint32`, start at 1 within a session, separate sequence space per direction, reset for each session. `hello` and `hello_ack` use seq 0.

### 9.2 Packet types

| Type | Name | Direction |
|---|---|---|
| 0 | input_kbm | phone to PC |
| 1 | rumble | PC to phone (gamepad mode only) |
| 2 | ping | either |
| 3 | pong | either |
| 4 | hello | phone to PC |
| 5 | hello_ack | PC to phone |
| 6 | bye | either |
| 7 | input_pad | phone to PC (gamepad mode only, Phase 8) |
| 8 | status | PC to phone |

### 9.3 input_kbm (type 0)

Payload is 26 bytes:

| Payload offset | Field | Size | Description |
|---|---|---|---|
| 0 | keys | 16 | 128-bit bitmap, bit n set means KeyId n is down (see `/docs/keys.md`) |
| 16 | mouse | 1 | bit0 left, bit1 right, bit2 middle, bit3 X1, bit4 X2 |
| 17 | flags | 1 | reserved, send 0, receiver ignores |
| 18 | lookX | 4 | `int32` cumulative look counter, horizontal |
| 22 | lookY | 4 | `int32` cumulative look counter, vertical |

Total: 12 header + 26 payload + 8 MAC = **46 bytes**.

**Look counters are cumulative, not per-packet deltas.** The phone adds all look motion (touch and gyro) to two running `int32` counters that wrap around, and sends the current values in every packet. The receiver computes motion as `current - previous` using wraparound-safe arithmetic. This way a lost UDP packet does not lose motion: the next packet carries it. Rules:
- The first accepted input packet in a session sets the baseline and produces no motion.
- If more than 250 ms passed since the last accepted input packet, set the baseline to the current counters and produce no motion, so a reconnect never causes a camera jump.
- Clamp the per-packet delta to a sane maximum (default +/-4000 counts) and log when clamping occurs.
- One count equals one raw mouse count at sensitivity 1.0. The receiver multiplies by its PC-side look scale and carries the fractional remainder to the next packet so slow motion is not lost.

**Mouse buttons and keys** are state: the receiver compares the new state with the state currently held down and injects only the key-down and key-up events that changed.

### 9.4 input_pad (type 7, Phase 8 only)

Payload is 12 bytes: `buttons u16, lt u8, rt u8, lx i16, ly i16, rx i16, ry i16`. Total **32 bytes**. Buttons bitmask: bit0 A, 1 B, 2 X, 3 Y, 4 LB, 5 RB, 6 Back, 7 Start, 8 L3, 9 R3, 10 D-Up, 11 D-Down, 12 D-Left, 13 D-Right, 14 Guide, 15 reserved. Gyro is merged into the right stick on the phone.

### 9.5 Other packets

| Packet | Payload | Total |
|---|---|---|
| rumble (type 1) | `large u8, small u8` | 22 bytes |
| ping / pong (types 2, 3) | `timestamp u64`, echoed unchanged | 28 bytes |
| bye (type 6) | none | 20 bytes |
| status (type 8) | `flags u8`: bit0 armed, bit1 target in focus, bit2 keyboard/mouse available, bit3 gamepad available | 21 bytes |

Status is sent on change and every 1 s. The phone shows "Paused" when the receiver is not armed or the target game is not in focus, so the player knows why nothing happens.

### 9.6 hello / hello_ack

- `hello` (phone to PC): payload is a 16-byte client nonce. Total 12 + 16 + 8 = **36 bytes**.
- `hello_ack` (PC to phone): payload is a 16-byte server nonce. Total **36 bytes**. Its MAC is calculated over header + server nonce + **client nonce** (the client nonce is not sent again). This binds the ack to one specific hello, so an old ack cannot be replayed to desync the phone.

### 9.7 Key IDs

`/docs/keys.md` defines a fixed table from KeyId (0 to 127) to a PS/2 set 1 scan code plus an extended-key flag. v1 assigns at least: 0 to 25 letters A to Z, 26 to 35 digits 0 to 9, then Space, Tab, Left Shift, Left Ctrl, Left Alt, Esc, Enter, Caps Lock, F1 to F12, arrow keys, Backspace, and the tilde key. Mouse buttons are not in the key table. Unknown or unassigned KeyIds are ignored by the receiver. IDs are never reused or renumbered; add new keys at the end.

---

## 10. Receiver validation rules

The receiver MUST reject packets with: wrong magic, unsupported version, incorrect length for the type, invalid MAC, wrong session ID, or a sequence number not newer than the last accepted one.

Sequence comparison MUST use wraparound-safe serial-number arithmetic. Do not use `seq > lastSeq`, because `uint32` wraps.

Ignore reserved bits and unknown KeyIds. Rate-limit or drop sources sending more than about 1000 packets/sec. Drop invalid or unpaired traffic silently.

## 11. Input safety (mandatory)

Stuck keys are worse than a stuck gamepad: a stuck W runs forever and a stuck LMB keeps firing. Layers:

1. **Neutralize** means key-up for every key and mouse button the sink currently holds down, immediately, ignoring the minimum hold time.
2. **Watchdog:** neutralize if no valid input packet arrives for **500 ms**.
3. **Bye, session replace, shutdown:** neutralize on `bye`, when a new session replaces the current one, on receiver shutdown, and when the receiver is disarmed.
4. **Focus guard:** inject only while the selected target process owns the foreground window. When focus leaves, neutralize and report it in the status packet. The receiver cannot be armed until a target process is chosen (an explicit "any window" option exists but shows a warning).
5. **Kill switch:** a global hotkey (default Ctrl+Alt+F12) toggles armed state and neutralizes at once. Show armed state in the tray icon.
6. **Phone side:** on pause, screen off, or disconnect, send a neutral state, then `bye`.
7. **Minimum hold time:** each key stays down at least 30 ms (configurable, 0 to 50) before its key-up is injected, so very short taps are visible to games that poll once per frame. Does not apply to neutralize.

## 12. Sender rules

- While input is active (any key, mouse button, or look motion in the last 100 ms): send at 120 Hz, latest state only, never queue stale states.
- When idle: keepalive every 50 ms.
- **Edge latching:** a key or button press that starts and ends between two send ticks MUST still be sent as pressed in at least 2 consecutive packets, so taps are never dropped by the latest-state model.
- Use a dedicated sender thread with precise timing, not a coroutine delay loop.
- On app pause, screen off, or disconnect: send neutral state, send `bye` where possible, release all held controls.

## 13. Look input

- **Touch:** movement of a look touch in dp is converted to counts with a sensitivity setting. Separate multiplier while ADS is active (default 0.6). Use all historical touch samples, not only the latest point, and request unbuffered touch dispatch to reduce latency.
- **Fire and drag:** movement of a finger that is holding a fire button also feeds the look counters.
- **Gyro:** use the gyroscope's angular velocity (not rotation-vector integration). Integrate rate into the look counters; this adds to touch look. Pipeline: gyroscope, axis remapping for display rotation, bias calibration, low-pass filter, sensitivity, deadzone, invert options, add to counters. Activation modes: always on, toggle, hold a button, only while ADS is on. Request up to 200 Hz sampling (higher rates need the `HIGH_SAMPLING_RATE_SENSORS` permission on Android 12+).
- **Fractions:** carry fractional counts on the phone so slow movement is not lost to rounding.

---

## 14. Pairing and security

The receiver generates a random 32-byte pairing key and a QR containing `{ ips[], port, key, name }`. The key is a secret; the QR is displayed only on explicit request.

Key storage: Android uses Keystore-backed secure storage. Windows stores the key protected with DPAPI.

The phone tries each IP in the QR (the PC may have several adapters, or the phone may host a hotspot).

### Handshake

1. Phone sends `hello` with a random 16-byte client nonce.
2. Receiver generates a random 16-byte server nonce and replies with `hello_ack`.
3. Both compute `HMAC-SHA256(key, clientNonce || serverNonce)`. The first four bytes are the session ID.
4. All subsequent packets use that session ID and are MAC-authenticated.

Old-session packets are rejected because their session ID is no longer valid. Sequence numbers prevent replay and reordering within a session.

**Session replacement:** a valid `hello` (correct MAC with the paired key) always replaces the current session, even if the old one is still alive. This is how the phone reconnects after a crash or network change without waiting for a timeout. The receiver neutralizes and re-baselines the look counters when a session is replaced.

### Pairing management

One active controller in v1. Receiver settings: Forget device, Regenerate key (invalidates previously paired devices). The receiver listens only on private LAN interfaces and is never exposed to the public internet.

---

## 15. Phase 1: Windows receiver core

Until Phase 3, the receiver and test sender use a **debug-only fixed test key and fixed test session**, enabled by an explicit flag (`--debug-key`) and compiled out of release builds. This lets MAC, session, and sequence code be built and tested before pairing exists.

- [ ] Create .NET 10 WPF project; define `IInputSink`
- [ ] `KeyboardMouseSink`: scan-code `SendInput` for keys (extended flag where needed), relative mouse move, mouse buttons; tracks what is currently down
- [ ] `docs/keys.md` and the KeyId to scan code table, with unit tests
- [ ] Packet encoder/decoder (shared by receiver and test sender) with unit tests
- [ ] UDP listener and validation pipeline (sequence, session, MAC with debug key)
- [ ] Key-state diff engine and minimum hold time
- [ ] Look-counter delta processor: baseline, 250 ms re-baseline, clamp, fractional carry, wraparound
- [ ] Safety guards: watchdog, bye handling, armed switch, target-process focus guard, kill-switch hotkey, UIPI/elevation detection with a clear message
- [ ] Status packet sender
- [ ] Test sender: holds keys, taps keys, sweeps look counters, replays recorded sessions
- [ ] Tray icon, first-run notice about anti-cheat (see 4.1)

**Phase 1 gate:** test sender to receiver to `SendInput` works in the input tester (scan codes and raw deltas correct, no stuck keys after watchdog, kill switch, focus loss, and bye) and in the target game's offline or practice mode. If injection fails, STOP; do not continue to Phase 2.

## 16. Phase 2: Android HUD

The sender uses the same debug key and fixed session as Phase 1 (debug flag only).

- [ ] Compose HUD with the default layout from section 3, both landscape orientations, immersive sticky mode, keep screen on, notch and inset handling
- [ ] Exclude the screen edges from system gestures (`setSystemGestureExclusionRects`, API 29+) so controls near the edges do not trigger back or home gestures
- [ ] Move stick with 0.35 deadzone to WASD, optional floating origin
- [ ] All buttons with hold, toggle, and tap behaviors
- [ ] Look zone and fire-and-drag, producing the cumulative look counters
- [ ] Multi-touch pointer ownership: each finger owns one control or the look zone
- [ ] Sender per sections 12 and 9.3, edge latching, dedicated timed thread; manual IP entry
- [ ] Unbuffered touch dispatch; status display (connected, RTT, paused reason)
- [ ] Optional local button haptic (setting)

**Acceptance:** you can move, look, fire, aim, jump, crouch, and lean at the same time with several fingers, in a real game's offline or practice mode.

## 17. Phase 3: Pairing and security

Receiver: QR generation, LAN IP discovery, key generation and storage (DPAPI), Forget device, Regenerate key.
Android: QR scanner, pairing storage, handshake, HMAC signing and verification, session ID, **mandatory signed packets (debug key removed from the normal path)**, automatic reconnect, Wi-Fi change handling, connection status.
Optional: mDNS discovery (needs `CHANGE_WIFI_MULTICAST_STATE` and a multicast lock).

**Acceptance:**
- [ ] Pairing takes under 10 seconds
- [ ] Reconnect after app restart and after phone crash (session replacement works, no camera jump)
- [ ] Invalid packets rejected; old-session packets rejected
- [ ] Replayed `hello_ack` is rejected
- [ ] Replay tests pass

## 18. Phase 4: haptics and status feedback

Replaces the old rumble phase for keyboard/mouse mode.

- [ ] Local button-press haptics with intensity and on/off settings; use `VibratorManager` on Android 12+, amplitude control when supported
- [ ] Status UI states: connecting, connected, paused (not armed, game not focused, receiver busy), disconnected
- [ ] Phone-side warning when RTT is high or packets are being lost

**Acceptance:** haptics feel immediate and never run after pause or disconnect; the paused reason is always visible.

## 19. Phase 5: gyro look

- [ ] Gyro pipeline from section 13 feeding the look counters
- [ ] Settings: sensitivity, deadzone, invert axes, activation mode, ADS multiplier
- [ ] Bias calibration at startup and on demand
- [ ] Gyro toggle chip on the HUD

**Acceptance:** smooth aim adjustment on top of touch look, no drift when the phone is still, correct axes in both landscape orientations.

## 20. Phase 6: layout editor and profiles

Schema in `/docs/layout-format.md`. Coordinates are screen-relative fractions, not pixels. Elements have id, type, behavior, bound key, position, size, opacity.

- [ ] Edit mode: drag, resize, opacity, snap to grid, add and remove controls, notch-safe placement
- [ ] Per-control key binding from the key table, plus LMB, RMB, MMB; per-control behavior (hold, toggle, tap)
- [ ] Look sensitivity, ADS multiplier, stick floating origin, deadzone
- [ ] Profiles: save, load, rename, duplicate, delete, reset to default; per-game profiles with a quick switcher
- [ ] Import and export of a profile as JSON

**Acceptance:** an edited layout and its bindings persist after restart and keep their relative arrangement on different screen sizes.

## 21. Phase 7: polish and release

Windows: installer (WiX or Inno Setup), firewall rule, optional auto-start, tray mode, single-instance enforcement, optional "run as administrator" helper for elevated games, clear first-run anti-cheat notice.
Android: settings, onboarding, battery guidance, connection diagnostics.

Wi-Fi optimization (best effort, not a functional dependency, subject to foreground and screen-on limits): `WIFI_MODE_FULL_LOW_LATENCY` on API 29+, `WIFI_MODE_FULL_HIGH_PERF` below that.

Also handle: PC sleep and wake, phone screen lock, Wi-Fi changes, receiver shutdown, Android process termination, game alt-tab.

## 22. Phase 8: gamepad fallback (conditional)

Required if Phase 0 Track A was NO-GO. Otherwise optional and only built if the user asks.

- [ ] Complete Phase 0 Track B
- [ ] `GamepadSink` behind `IInputSink` using the ViGEm client; neutralize on the same triggers as section 11
- [ ] `input_pad` packets (type 7) and a gamepad HUD profile; the look zone maps to the right stick with a response curve
- [ ] Rumble: subscribe to virtual pad feedback, send rumble state (strength 0 to 255) on change and every 100 ms while non-zero; the phone stops vibrating after 300 ms without a rumble packet; combine large and small motors with `max(large, small)` or a documented blend
- [ ] Guide button check: standard XInput may not expose it; record the result
- [ ] Installer bundles the pinned driver with the updater disabled

**Acceptance:** the target game plays with the gamepad profile, rumble works and stops cleanly, and no input sticks after a disconnect.

---

## 23. Performance targets and measurement

**Estimated network one-way latency:** p95 under 15 ms on 5 GHz Wi-Fi, estimated as RTT/2 from ping/pong. Label it "estimated", because RTT/2 assumes roughly symmetric delay.

**Touch-to-injection latency:** the phone and PC clocks are not synchronized, so this cannot be measured by subtracting timestamps directly. Use one of:
- Clock-offset estimation from ping/pong (NTP-style, using the lowest-RTT samples), then compare phone touch timestamps with receiver injection timestamps through the estimated offset; or
- An external test: high-speed camera or loopback rig.

Report which method was used and its uncertainty.

**Game-side latency** is separate: game polling, rendering, display latency, touch sampling, and OS scheduling.

**CPU:** receiver under 2% while connected and idle. **Android:** no allocations in the 120 Hz sender hot path.

## 24. Network behavior

UDP is latest-state-wins. Do not retransmit old input and do not queue input. If packet 100 arrives after packet 101, discard it. Cumulative look counters and edge latching make this safe for motion and taps. Recommended network: 5 GHz Wi-Fi, PC on Ethernet when possible.

## 25. Windows firewall

The receiver needs inbound UDP. The installer may create a rule for private and public profiles, but the app must still bind only to intended private LAN interfaces. If packets are not arriving, show a connection failure, point to firewall or network profile as a likely cause, and give troubleshooting guidance.

## 26. Android permissions

As applicable: `INTERNET`, `ACCESS_WIFI_STATE`, `VIBRATE`. Request `CAMERA` only if the chosen QR scanner needs it; a system or Google code scanner does not, so do not request it in that case. Do not request `HIGH_SAMPLING_RATE_SENSORS` unless sampling above 200 Hz is actually needed.

## 27. Security model

Threat model: trusted private LAN plus a paired controller.
Goals: authenticate the controller, prevent unauthorized input injection (this matters more now, since a spoofed packet would type on the PC), detect modified packets, reject old-session and replayed packets, keep the receiver off the public internet.
Not provided: payload encryption, internet-grade security, protection against anyone holding the pairing key (treat it as a secret).

---

## 28. Testing

**Unit tests:** packet encode/decode, packet lengths per type, endianness, MAC calculation and rejection, sequence handling and wraparound, session creation, handshake (including `hello_ack` client-nonce binding and session replacement), KeyId to scan code table, key-state diff, edge latching, minimum hold time, look-counter delta (wraparound, baseline, 250 ms re-baseline, clamp, fractional carry), stick deadzone and WASD mapping, gyro mapping, layout serialization.

**Fuzz tests:** the receiver parser must survive random, truncated, and oversized packets, invalid lengths, types, and MACs, random sequence numbers, malformed payloads, and random key bitmaps. No malformed packet may crash the receiver or leave a key stuck down.

**Integration:** test sender to receiver to input tester; then Android to receiver to input tester; then Android to receiver to the game (offline or practice only).

**Replay tests:** capture a valid packet and replay it during the same session and after a new session is established; both must be rejected. Replay an old `hello_ack` against a new `hello`.

## 29. Manual testing

At least two games or modes if available, minimum 30-minute session, offline or practice only. Test: moving while looking while firing, rapid taps (jump, crouch, reload), ADS toggle and run lock, lean while firing, fire-and-drag recoil control, several fingers at once, inventory and map menus with look zone and fire button, gyro, Wi-Fi disconnect and reconnect, phone screen lock, app pause, Android process termination, PC sleep and wake, receiver restart, alt-tab out of the game while holding keys, kill-switch hotkey while holding keys, elevated game window, stuck-key prevention in every case.

## 30. Known risks

- **Anti-cheat and bans:** see 4.1. Mitigation: Phase 0 Track A in offline modes, user notice, README warning, gamepad fallback.
- **Games ignoring injected input:** mitigation: scan codes, raw-input relative moves, per-game verification, gamepad fallback.
- **UIPI:** elevated games need an elevated receiver; detect and explain.
- **ViGEmBus (fallback only):** retired kernel driver. Mitigation: `IInputSink`, pinned tested version, checksum, Phase 0 Track B.
- **Stuck keys and runaway input:** watchdog, bye, focus guard, kill switch, phone neutral on pause.
- **Wi-Fi jitter:** latest-state-wins, cumulative look counters, 120 Hz updates, 5 GHz or Ethernet recommended.
- **Android system gestures at screen edges:** gesture exclusion rects and immersive sticky mode.
- **Android background restrictions:** the controller runs only in the foreground; wake and Wi-Fi locks only where appropriate.
- **Windows firewall:** Public network profile may block inbound UDP; provide diagnostics.
- **Rumble not available in keyboard/mouse mode:** by design, local haptics instead.

## 31. Documentation requirements

Create `/docs/protocol.md`, `/docs/keys.md`, `/docs/pairing.md`, `/docs/layout-format.md`, and keep `/docs/ui-mockup.html`. `protocol.md` must exactly describe the implemented wire protocol; any incompatible change requires a protocol version bump. Never silently change packet layouts or key IDs.

README must cover: installation, choosing the target game and arming the receiver, the kill-switch hotkey, anti-cheat and account warning, elevated games, pairing, HUD usage and defaults, rebinding keys, gyro setup, "Enhance pointer precision", firewall troubleshooting, network recommendations, known game compatibility, known limitations (no rumble in keyboard/mouse mode), gamepad fallback (if built), uninstallation.

## 32. Agent working rules

The coding agent MUST:
1. Build phase by phase.
2. Complete a phase's acceptance criteria before starting the next.
3. Treat `/docs/protocol.md` and `/docs/keys.md` as the source of truth and keep implementations in sync.
4. Write tests alongside protocol and input logic.
5. Commit after each completed task with a clear message.
6. Never silently bypass a failed acceptance test.
7. Never silently change the output method (keyboard/mouse versus gamepad).
8. Never continue past a failed GO/NO-GO gate.
9. Never test injected input in online, ranked, or tournament matches.
10. Never generate input that did not come from the player's touch (no macros, no auto-fire, no recoil scripts).
11. Never ship the debug key path in release builds.
12. Report blockers instead of inventing solutions outside the defined architecture.
13. Ask the user only when blocked by a decision not covered by this plan. The test-target game in Phase 0 is such a decision.

## 33. Development order

Phase 0 (injection validation), Phase 1 (Windows receiver), Phase 2 (Android HUD), Phase 3 (pairing and security), Phase 4 (haptics and status), Phase 5 (gyro look), Phase 6 (layout editor and profiles), Phase 7 (polish and release), Phase 8 (gamepad fallback, conditional). Do not develop everything at once; the order removes the highest-risk dependency first.

## 34. Final acceptance criteria

v1 is complete only when this chain works: Android phone, QR pairing, authenticated UDP session, 120 Hz input state, Windows receiver, `SendInput` keyboard and mouse, real game (offline or practice mode). And:

- Move stick drives W, A, S, D with diagonals
- Look zone and fire-and-drag turn the camera smoothly, with no jump after a dropped packet or reconnect
- Fire, ADS toggle, jump, crouch, prone, lean, reload, use, slots, grenade, heal, map, and backpack work, and short taps are never lost
- Several fingers work at once
- Gyro look works
- Layout editor and key binding work and persist
- Reconnection works
- Wi-Fi interruption, phone pause, screen off, game focus loss, and the kill switch never leave a key or button stuck
- Receiver timeout neutralizes all input
- Invalid and replayed packets are rejected
- Malformed packets cannot crash the receiver
- Protocol and key documentation match the implementation
- Tested games and known incompatibilities are documented
- Gamepad fallback works if Phase 8 was required

## 35. Definition of done

Compiling is not done. The project is complete only after build, unit tests, fuzz tests, integration test, input-tester verification, real game test (offline or practice), disconnect and recovery test, stuck-key test, security and replay test, and performance measurement all pass their acceptance criteria.

---

## Changelog: Rev 3.1 to Rev 4

1. HUD changed from an Xbox-style pad to a PUBG Mobile style layout (move stick, look zone, fire buttons, ADS, jump, crouch, prone, lean, reload, slots, and more), with a full control table.
2. Primary output changed from a virtual Xbox 360 pad to keyboard and mouse through `SendInput`; ViGEm becomes the optional gamepad fallback (Phase 8).
3. Phase 0 now validates injection in the user's target game first (offline or practice modes only); ViGEm validation moved to Track B.
4. Protocol: new `input_kbm` packet (46 bytes) with a 128-bit key bitmap, mouse buttons, and cumulative look counters; `input_pad` kept for the fallback; new `status` packet; new `docs/keys.md`.
5. Look motion uses cumulative counters, so a lost packet does not lose motion; reconnect re-baselines to avoid camera jumps.
6. New safety layers: focus guard, kill-switch hotkey, armed switch, minimum key hold time, edge latching for taps.
7. Rumble limited to gamepad mode; keyboard/mouse mode gets local haptics. Gyro now feeds the look counters instead of the right stick.
8. Added anti-cheat and account-safety warning, UIPI/elevation handling, scan-code injection, system gesture exclusion, unbuffered touch dispatch, and per-control key binding with hold, toggle, and tap behaviors.
