---
id: node:memory
title: Product Domain & Session Memory
version: 1.0.0
framework: Google OKF (Open Knowledge Framework)
category: domain
tags: [memory, domain, product, vision, scope, gamepad, battle-royale]
relations: [node:architecture, node:rules, node:timeline, node:specs, node:schemas]
summary: Core product vision, user personas, domain entities, system boundaries, and scope constraints for Phone-as-Gamepad.
---

# Product Domain & Session Memory: Phone-as-Gamepad

## 1. Product Vision & Value Proposition
**Phone-as-Gamepad** transforms a modern Android mobile device into a battle-royale style low-latency touch controller and precision gyroscope for Windows 11 PC games (e.g., PUBG, Apex Legends, Warzone, Fortnite).
- **Core Motivation:** Mobile battle-royale players have high mechanical mastery with multi-finger claw grip, touch aim, and gyroscope micro-adjustments, but cannot intuitively use physical gamepads or standard keyboard/mouse setups when transitioning to PC.
- **Solution:** A dedicated two-part system:
  1. An Android HUD application running at 120 Hz displaying a PUBG Mobile style layout (move stick, swipe-to-look zone, dual fire buttons, ADS, jump, crouch, prone, lean, reload, inventory/map, gyro aim).
  2. A lightweight Windows 11 background receiver communicating over low-latency UDP (Wi-Fi/LAN) that injects human touch input as hardware scan-code keyboard and relative mouse events using Win32 `SendInput` (with an optional virtual Xbox 360 controller fallback via ViGEmBus).

## 2. Target Personas
1. **Competitive Mobile Shooter Player:** Wants to leverage hundreds of hours of mobile touch and gyro muscle memory directly inside PC shooter games with zero input lag, 1:1 look response, and claw-grip HUD ergonomics.
2. **Casual/Laptop Gamer on the Go:** Wants to play shooter games on a Windows laptop without carrying an external physical mouse, keyboard, or bulky gamepad.
3. **Ergonomic Shooter Streamer:** Desires a customizable touchscreen controller with custom button placement, transparent controls, and tactile feedback.

## 3. Core Domain Entities
- **Controller Client (Android):**
  - `TouchPointer`: Active finger touch on the screen tracked by unique pointer ID, screen coordinates, historical samples, and ownership.
  - `ControlElement`: Interactive HUD component (MoveStick, ActionButton, ToggleChip, LookZone) bound to specific actions or scan codes.
  - `LookAccumulator`: High-precision cumulative motion vector aggregating touch drag deltas and gyroscope angular velocity rate integrations.
  - `GamepadProfile`: User-defined layout configuration storing button positions, scales, opacities, behaviors, and key bindings.
  - `PairingSecret`: Cryptographically generated 256-bit symmetric key stored securely in hardware Keystore.
- **Receiver Host (Windows 11):**
  - `UdpReceiverSession`: Authenticated, active session bound to a verified controller with monotonically tracked sequence numbers.
  - `InputSink (`IInputSink`)`: Abstraction layer translating incoming controller state into OS input events.
    - `KeyboardMouseSink` (Primary): Win32 `SendInput` hardware scan codes and relative mouse movements.
    - `GamepadSink` (Fallback): Virtual Xbox 360 controller via ViGEmBus client driver.
  - `SafetyGuardManager`: Tri-guard protection subsystem (Target Process Focus Guard, Watchdog Neutralizer, Global Kill-Switch Hotkey).
  - `KeyStateDiffEngine`: State-tracking differential engine maintaining down/up state transitions and enforcing minimum hold times (30ms).

## 4. System Boundaries & Scope Guardrails
### In Scope (v1 MVP & Rev 4 Baseline)
- High-frequency UDP transport over local private Wi-Fi (default port `47789`).
- PUBG Mobile style HUD layout with multi-touch pointer ownership.
- Dual fire buttons with fire-and-drag recoil tracking.
- Gyroscope look integration (angular velocity integrated into cumulative counters).
- Local tactile haptic feedback on Android (`VibratorManager`).
- Seamless QR code pairing (LAN IP list + 32-byte secret) with DPAPI (Windows) and Keystore (Android).
- Cumulative wraparound-safe look counters avoiding lost motion across UDP packet loss.
- Key-state diffing with hardware scan-code injection (`KEYEVENTF_SCANCODE`, `KEYEVENTF_EXTENDEDKEY`).
- Comprehensive safety guards: 500ms watchdog timeout, target process foreground focus check, emergency kill-switch (`Ctrl+Alt+F12`), neutral state on bye/pause.
- Custom layout editor with drag/resize, per-control key remapping, and JSON profile export/import.

### Strictly Out of Scope
- Internet / remote play / WAN traversal (LAN only for minimal latency and security).
- iOS / iPadOS client (Android minSdk 26+ only).
- Bluetooth or USB HID communication (Wi-Fi UDP only).
- Multi-controller simultaneous receiver pairing (1 active controller per receiver session).
- Per-packet symmetric payload encryption (HMAC-SHA256 authentication and integrity are used; input data is not secret).
- Macros, auto-fire, automated recoil compensation, or synthetic bot input (all input must originate strictly from user touch/gyro).
- Text typing or chat emulation; absolute cursor coordinate mapping.
