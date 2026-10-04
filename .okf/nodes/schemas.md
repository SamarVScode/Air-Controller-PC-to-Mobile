---
id: node:schemas
title: Data Models, Schemas & Wire Protocol Specifications
version: 1.0.0
framework: Google OKF (Open Knowledge Framework)
category: schemas
tags: [schemas, wire-protocol, binary, packets, hmac, scancodes, json-profile, qr-pairing]
relations: [node:architecture]
summary: Concrete binary wire protocol layouts, KeyId-to-scancode table, QR payload schema, and JSON layout profile specification.
---

<!--
@aegis-contract
@claim Wire protocol binary packet schemas and KeyId scancode mapping
@true Defines exact byte layouts, offsets, and types for all 9 packet types (input_kbm, rumble, ping, pong, hello, hello_ack, bye, input_pad, status) with Little-Endian framing
@true Specifies authoritative PS/2 Set 1 scan code mapping for KeyIds 0 to 61 with extended key flags
@true Specifies JSON schema models for pairing QR payloads and HUD touch layout profiles
@false Contains no empty stubs, placeholder types, or mock schemas
-->

# Data Models, Schemas & Wire Protocol Specifications

## 1. Wire Protocol Specification (v1)

- **Transport:** UDP datagrams, default port `47789`.
- **Byte Order:** Little-Endian for all multi-byte numeric fields (`u16`, `u32`, `u64`, `i16`, `i32`).
- **Framing Structure:** Every transmitted datagram adheres to a three-tier binary structure:
  ```
  +--------------------+------------------------+-------------------+
  |  HEADER (12 bytes) |  PAYLOAD (N bytes)     |  MAC (8 bytes)    |
  +--------------------+------------------------+-------------------+
  ```
- **Message Authentication Code (MAC):**
  - First 8 bytes of `HMAC-SHA256(Key, Data)`.
  - Calculated across all preceding bytes (`Header || Payload`) for all packet types except `hello_ack`.
  - For `hello_ack`, MAC calculation spans `Header[12] || ServerNonce[16] || ClientNonce[16]`, cryptographically tying the server acknowledgement directly to the client hello challenge.

---

## 2. Binary Packet Layouts & Structural Definitions

### 2.1 Standard Header (12 Bytes)
- **Offset 0 (2 bytes):** `magic` (`u16`) - Constant `0x4750` (`'G'`, `'P'`).
- **Offset 2 (1 byte):** `version` (`u8`) - Protocol version constant `1`.
- **Offset 3 (1 byte):** `type` (`u8`) - Packet identifier (values `0` through `8`).
- **Offset 4 (4 bytes):** `session` (`u32`) - Session ID generated during handshake (`0` for `hello`/`hello_ack`).
- **Offset 8 (4 bytes):** `seq` (`u32`) - Monotonically increasing sequence number (wraparound safe).

### 2.2 Packet Type Index
- **Type 0 (`input_kbm`):** Phone to PC, 26 bytes payload, **46 bytes total**.
- **Type 1 (`rumble`):** PC to Phone, 2 bytes payload, **22 bytes total** (Phase 8 fallback).
- **Type 2 (`ping`):** Bidirectional, 8 bytes payload, **28 bytes total**.
- **Type 3 (`pong`):** Bidirectional, 8 bytes payload, **28 bytes total**.
- **Type 4 (`hello`):** Phone to PC, 16 bytes payload, **36 bytes total**.
- **Type 5 (`hello_ack`):** PC to Phone, 16 bytes payload, **36 bytes total**.
- **Type 6 (`bye`):** Bidirectional, 0 bytes payload, **20 bytes total**.
- **Type 7 (`input_pad`):** Phone to PC, 12 bytes payload, **32 bytes total** (Phase 8 fallback).
- **Type 8 (`status`):** PC to Phone, 1 byte payload, **21 bytes total**.

---

### 2.3 Comprehensive Payload Byte Definitions

#### Type 0: `input_kbm` (46 Bytes Total)
- **Payload Offset 0 (16 bytes):** `keys` (`byte[16]`) - 128-bit key bitmap. Bit index `N` corresponds to `KeyId N`. Value `1` denotes key-down; value `0` denotes key-up.
- **Payload Offset 16 (1 byte):** `mouse` (`u8`) - Mouse button bitmask:
  - Bit 0: Left Mouse Button (LMB)
  - Bit 1: Right Mouse Button (RMB)
  - Bit 2: Middle Mouse Button (MMB)
  - Bit 3: X1 Button (Browser Back)
  - Bit 4: X2 Button (Browser Forward)
  - Bits 5-7: Reserved (zero)
- **Payload Offset 17 (1 byte):** `flags` (`u8`) - Protocol flags (reserved, set to 0).
- **Payload Offset 18 (4 bytes):** `lookX` (`i32`) - Cumulative horizontal look accumulator. Wraps at boundaries.
- **Payload Offset 22 (4 bytes):** `lookY` (`i32`) - Cumulative vertical look accumulator. Wraps at boundaries.
- **Trailing 8 bytes:** Truncated HMAC-SHA256 authentication tag.

#### Type 1: `rumble` (22 Bytes Total - Gamepad Fallback)
- **Payload Offset 0 (1 byte):** `large` (`u8`) - Heavy low-frequency motor strength (`0` to `255`).
- **Payload Offset 1 (1 byte):** `small` (`u8`) - Light high-frequency motor strength (`0` to `255`).

#### Types 2 & 3: `ping` & `pong` (28 Bytes Total)
- **Payload Offset 0 (8 bytes):** `timestamp` (`u64`) - High-resolution monotonic timestamp emitted by sender and echoed unchanged by receiver for RTT measurement.

#### Type 4: `hello` (36 Bytes Total)
- **Payload Offset 0 (16 bytes):** `client_nonce` (`byte[16]`) - Cryptographically secure pseudo-random client challenge.

#### Type 5: `hello_ack` (36 Bytes Total)
- **Payload Offset 0 (16 bytes):** `server_nonce` (`byte[16]`) - Cryptographically secure pseudo-random server challenge.
- Session derivation: `session_id = HMAC-SHA256(Key, client_nonce || server_nonce)[0..3]`.

#### Type 6: `bye` (20 Bytes Total)
- Header-only packet (payload size 0). Signals immediate graceful session disconnect and mandates instant input neutralization.

#### Type 7: `input_pad` (32 Bytes Total - Gamepad Fallback)
- **Payload Offset 0 (2 bytes):** `buttons` (`u16`) - XInput digital buttons bitmask.
- **Payload Offset 2 (1 byte):** `lt` (`u8`) - Left trigger analog value (`0` to `255`).
- **Payload Offset 3 (1 byte):** `rt` (`u8`) - Right trigger analog value (`0` to `255`).
- **Payload Offset 4 (2 bytes):** `lx` (`i16`) - Left thumbstick X coordinate (`-32768` to `32767`).
- **Payload Offset 6 (2 bytes):** `ly` (`i16`) - Left thumbstick Y coordinate (`-32768` to `32767`).
- **Payload Offset 8 (2 bytes):** `rx` (`i16`) - Right thumbstick X coordinate (`-32768` to `32767`).
- **Payload Offset 10 (2 bytes):** `ry` (`i16`) - Right thumbstick Y coordinate (`-32768` to `32767`).

#### Type 8: `status` (21 Bytes Total)
- **Payload Offset 0 (1 byte):** `flags` (`u8`) - Receiver operational state bitmask:
  - Bit 0: Armed state (`1` = Armed, `0` = Disarmed)
  - Bit 1: Target window focus state (`1` = Game focused, `0` = Foreground lost)
  - Bit 2: Keyboard & Mouse engine status (`1` = Available)
  - Bit 3: Gamepad driver status (`1` = Virtual ViGEm controller available)
  - Bits 4-7: Reserved (zero)

---

## 3. KeyId to PS/2 Scan Code Mapping Table (`docs/keys.md`)

Hardware scan codes injected via Win32 `SendInput` using `KEYEVENTF_SCANCODE`:

| KeyId | Key Name | PS/2 Make Code (Hex) | Extended Key (`KEYEVENTF_EXTENDEDKEY`) |
|---|---|---|---|
| `0` | A | `0x1E` | No |
| `1` | B | `0x30` | No |
| `2` | C | `0x2E` | No |
| `3` | D | `0x20` | No |
| `4` | E | `0x12` | No |
| `5` | F | `0x21` | No |
| `6` | G | `0x22` | No |
| `7` | H | `0x23` | No |
| `8` | I | `0x17` | No |
| `9` | J | `0x24` | No |
| `10` | K | `0x25` | No |
| `11` | L | `0x26` | No |
| `12` | M | `0x32` | No |
| `13` | N | `0x31` | No |
| `14` | O | `0x18` | No |
| `15` | P | `0x19` | No |
| `16` | Q | `0x10` | No |
| `17` | R | `0x13` | No |
| `18` | S | `0x1F` | No |
| `19` | T | `0x14` | No |
| `20` | U | `0x16` | No |
| `21` | V | `0x2F` | No |
| `22` | W | `0x11` | No |
| `23` | X | `0x2D` | No |
| `24` | Y | `0x15` | No |
| `25` | Z | `0x2C` | No |
| `26` | 0 | `0x0B` | No |
| `27` | 1 | `0x02` | No |
| `28` | 2 | `0x03` | No |
| `29` | 3 | `0x04` | No |
| `30` | 4 | `0x05` | No |
| `31` | 5 | `0x06` | No |
| `32` | 6 | `0x07` | No |
| `33` | 7 | `0x08` | No |
| `34` | 8 | `0x09` | No |
| `35` | 9 | `0x0A` | No |
| `36` | Space | `0x39` | No |
| `37` | Tab | `0x0F` | No |
| `38` | LeftShift | `0x2A` | No |
| `39` | LeftCtrl | `0x1D` | No |
| `40` | LeftAlt | `0x38` | No |
| `41` | Escape | `0x01` | No |
| `42` | Enter | `0x1C` | No |
| `43` | CapsLock | `0x3A` | No |
| `44` | F1 | `0x3B` | No |
| `45` | F2 | `0x3C` | No |
| `46` | F3 | `0x3D` | No |
| `47` | F4 | `0x3E` | No |
| `48` | F5 | `0x3F` | No |
| `49` | F6 | `0x40` | No |
| `50` | F7 | `0x41` | No |
| `51` | F8 | `0x42` | No |
| `52` | F9 | `0x43` | No |
| `53` | F10 | `0x44` | No |
| `54` | F11 | `0x57` | No |
| `55` | F12 | `0x58` | No |
| `56` | UpArrow | `0x48` | Yes |
| `57` | DownArrow | `0x50` | Yes |
| `58` | LeftArrow | `0x4B` | Yes |
| `59` | RightArrow | `0x4D` | Yes |
| `60` | Backspace | `0x0E` | No |
| `61` | Tilde | `0x29` | No |

---

## 4. Pairing QR Payload Schema

JSON structure encoded inside pairing QR codes:

```json
{
  "title": "PairingPayload",
  "type": "object",
  "properties": {
    "ips": {
      "type": "array",
      "items": { "type": "string" },
      "description": "Active local network IPv4 addresses of receiver host"
    },
    "port": {
      "type": "integer",
      "minimum": 1024,
      "maximum": 65535,
      "default": 47789
    },
    "key": {
      "type": "string",
      "minLength": 64,
      "maxLength": 64,
      "description": "Hexadecimal string representing 32-byte pre-shared secret"
    },
    "name": {
      "type": "string",
      "description": "Friendly machine hostname"
    }
  },
  "required": ["ips", "port", "key", "name"]
}
```

---

## 5. Layout Profile Schema (`docs/layout-format.md`)

JSON profile schema representing custom HUD layouts:

```json
{
  "title": "GamepadLayoutProfile",
  "type": "object",
  "properties": {
    "profileName": { "type": "string" },
    "version": { "type": "integer", "default": 1 },
    "settings": {
      "type": "object",
      "properties": {
        "touchSensitivity": { "type": "number", "default": 1.0 },
        "adsSensitivityMultiplier": { "type": "number", "default": 0.6 },
        "gyroEnabled": { "type": "boolean", "default": false },
        "gyroSensitivity": { "type": "number", "default": 1.0 },
        "stickDeadzone": { "type": "number", "default": 0.35 },
        "stickFloatingOrigin": { "type": "boolean", "default": true }
      },
      "required": ["touchSensitivity", "adsSensitivityMultiplier", "stickDeadzone"]
    },
    "controls": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "id": { "type": "string" },
          "type": { "type": "string", "enum": ["stick", "button", "chip", "look_zone"] },
          "label": { "type": "string" },
          "boundKeyId": { "type": "integer", "minimum": 0, "maximum": 127 },
          "boundMouseButton": { "type": "string", "enum": ["none", "LMB", "RMB", "MMB"] },
          "behavior": { "type": "string", "enum": ["hold", "toggle", "tap"] },
          "x": { "type": "number", "minimum": 0.0, "maximum": 100.0 },
          "y": { "type": "number", "minimum": 0.0, "maximum": 100.0 },
          "width": { "type": "number" },
          "height": { "type": "number" },
          "opacity": { "type": "number", "minimum": 0.1, "maximum": 1.0, "default": 0.8 }
        },
        "required": ["id", "type", "x", "y", "behavior"]
      }
    }
  },
  "required": ["profileName", "version", "settings", "controls"]
}
```
