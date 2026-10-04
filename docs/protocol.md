<!--
@aegis-contract
@claim Authoritative Wire Protocol Specification for Phone-as-Gamepad
@true Defines exact byte layouts, offsets, and types for all 9 packet types with Little-Endian framing
@true Specifies standard 12-byte header, RFC 1982 serial arithmetic, and cumulative look counter algorithms
@false Contains no placeholder stubs, undefined packet fields, or ambiguous framing rules
-->

# Phone-as-Gamepad Wire Protocol Specification (v1)

## 1. Overview & Transport Layer

- **Transport:** UDP datagrams.
- **Default Port:** `47789` (configurable in client and receiver).
- **Endianness:** Little-Endian for all multi-byte primitive fields (`u16`, `u32`, `u64`, `i16`, `i32`).
- **Framing Structure:** Every datagram is atomic and strictly framed into three contiguous segments:
  ```
  +-----------------------+--------------------------+-----------------------+
  |  HEADER (12 bytes)    |  PAYLOAD (N bytes)       |  MAC (8 bytes)        |
  +-----------------------+--------------------------+-----------------------+
  ```
- **Integrity & Authentication:**
  - Truncated `HMAC-SHA256`: First 8 bytes of `HMAC-SHA256(Key, Data)`.
  - Calculated over `Header || Payload` for all packet types except `hello_ack` (Type 5).
  - For `hello_ack`, MAC calculation covers `Header[12] || ServerNonce[16] || ClientNonce[16]`, cryptographically binding the server challenge to the initiating client challenge.
  - Verification MUST use constant-time equality comparisons (`CryptographicOperations.FixedTimeEquals` in .NET, `MessageDigest.isEqual` on Android) to prevent timing side-channel attacks.

---

## 2. Standard Header (12 Bytes)

Every packet begins with a standard 12-byte header:

| Offset | Field | Type | Description |
|---|---|---|---|
| 0 | `magic` | `u16` | Magic identifier: constant `0x4750` (`'G'`, `'P'` in ASCII) |
| 2 | `version` | `u8` | Protocol version: constant `1` |
| 3 | `type` | `u8` | Packet type identifier (`0` through `8`) |
| 4 | `session` | `u32` | Session ID. Value `0` for `hello` and `hello_ack`; session-derived ID otherwise |
| 8 | `seq` | `u32` | Sequence number. Value `0` for `hello`/`hello_ack`; monotonically incrementing uint32 starting at 1 per session |

---

## 3. Packet Type Catalog & Byte Sizes

| Type | Name | Direction | Payload Size | Total Size | Description |
|---|---|---|---|---|---|
| `0` | `input_kbm` | Phone -> PC | 26 bytes | **46 bytes** | Primary keyboard, mouse button, and cumulative look state |
| `1` | `rumble` | PC -> Phone | 2 bytes | **22 bytes** | Dual-motor rumble feedback (Phase 8 Gamepad fallback only) |
| `2` | `ping` | Bidirectional | 8 bytes | **28 bytes** | Monotonic timestamp for RTT and latency measurement |
| `3` | `pong` | Bidirectional | 8 bytes | **28 bytes** | Echo of ping timestamp unchanged |
| `4` | `hello` | Phone -> PC | 16 bytes | **36 bytes** | Client handshake request with 16-byte random challenge nonce |
| `5` | `hello_ack` | PC -> Phone | 16 bytes | **36 bytes** | Server handshake response with 16-byte random server nonce |
| `6` | `bye` | Bidirectional | 0 bytes | **20 bytes** | Graceful disconnect notification (mandates immediate neutralization) |
| `7` | `input_pad` | Phone -> PC | 12 bytes | **32 bytes** | XInput digital buttons, triggers, and thumbsticks (Phase 8 fallback) |
| `8` | `status` | PC -> Phone | 1 byte | **21 bytes** | Receiver armed state, foreground focus, and sink availability flags |

---

## 4. Comprehensive Payload Specifications

### 4.1 Type 0: `input_kbm` (46 Bytes Total)
- **Header:** 12 bytes (`type = 0`).
- **Payload:** 26 bytes:
  - **Offset 12 (16 bytes):** `keys` (`byte[16]`) - 128-bit key bitmap. Bit `N` corresponds to `KeyId N` (0 to 127). Bit set (`1`) = Key Down, bit cleared (`0`) = Key Up. See `docs/keys.md`.
  - **Offset 28 (1 byte):** `mouse` (`u8`) - Mouse button bitmask:
    - Bit 0: Left Mouse Button (LMB)
    - Bit 1: Right Mouse Button (RMB)
    - Bit 2: Middle Mouse Button (MMB)
    - Bit 3: X1 Button (Browser Back)
    - Bit 4: X2 Button (Browser Forward)
    - Bits 5-7: Reserved (must be sent as `0`, ignored by receiver)
  - **Offset 29 (1 byte):** `flags` (`u8`) - Protocol flags (reserved, set to `0x00`).
  - **Offset 30 (4 bytes):** `lookX` (`i32`) - Cumulative horizontal look accumulator. 32-bit signed integer wrapping on overflow.
  - **Offset 34 (4 bytes):** `lookY` (`i32`) - Cumulative vertical look accumulator. 32-bit signed integer wrapping on overflow.
- **Trailing (8 bytes, Offset 38..45):** Truncated `HMAC-SHA256` tag calculated over bytes 0..37.

### 4.2 Type 1: `rumble` (22 Bytes Total - Phase 8 Fallback)
- **Header:** 12 bytes (`type = 1`).
- **Payload:** 2 bytes:
  - **Offset 12 (1 byte):** `large` (`u8`) - Low-frequency heavy vibration motor strength (`0` to `255`).
  - **Offset 13 (1 byte):** `small` (`u8`) - High-frequency light vibration motor strength (`0` to `255`).
- **Trailing (8 bytes, Offset 14..21):** Truncated `HMAC-SHA256` tag calculated over bytes 0..13.

### 4.3 Types 2 & 3: `ping` and `pong` (28 Bytes Total)
- **Header:** 12 bytes (`type = 2` for ping, `type = 3` for pong).
- **Payload:** 8 bytes:
  - **Offset 12 (8 bytes):** `timestamp` (`u64`) - High-resolution monotonic timestamp emitted by sender. Responder copies this value verbatim into `pong`.
- **Trailing (8 bytes, Offset 20..27):** Truncated `HMAC-SHA256` tag calculated over bytes 0..19.

### 4.4 Type 4: `hello` (36 Bytes Total)
- **Header:** 12 bytes (`type = 4`, `session = 0`, `seq = 0`).
- **Payload:** 16 bytes:
  - **Offset 12 (16 bytes):** `client_nonce` (`byte[16]`) - Cryptographically secure random challenge generated by phone.
- **Trailing (8 bytes, Offset 28..35):** Truncated `HMAC-SHA256` tag calculated over bytes 0..27.

### 4.5 Type 5: `hello_ack` (36 Bytes Total)
- **Header:** 12 bytes (`type = 5`, `session = 0`, `seq = 0`).
- **Payload:** 16 bytes:
  - **Offset 12 (16 bytes):** `server_nonce` (`byte[16]`) - Cryptographically secure random challenge generated by receiver.
- **Trailing (8 bytes, Offset 28..35):** Truncated `HMAC-SHA256` calculated over:
  `Header[0..11] || ServerNonce[12..27] || ClientNonce[16]` (ClientNonce matched from preceding `hello`).
  *Note: ClientNonce is not retransmitted in packet body.*

### 4.6 Type 6: `bye` (20 Bytes Total)
- **Header:** 12 bytes (`type = 6`).
- **Payload:** 0 bytes.
- **Trailing (8 bytes, Offset 12..19):** Truncated `HMAC-SHA256` tag calculated over bytes 0..11.

### 4.7 Type 7: `input_pad` (32 Bytes Total - Phase 8 Fallback)
- **Header:** 12 bytes (`type = 7`).
- **Payload:** 12 bytes:
  - **Offset 12 (2 bytes):** `buttons` (`u16`) - Digital buttons bitmask:
    - Bit 0: A
    - Bit 1: B
    - Bit 2: X
    - Bit 3: Y
    - Bit 4: LB (Left Bumper)
    - Bit 5: RB (Right Bumper)
    - Bit 6: Back
    - Bit 7: Start
    - Bit 8: L3 (Left Stick Click)
    - Bit 9: R3 (Right Stick Click)
    - Bit 10: D-Pad Up
    - Bit 11: D-Pad Down
    - Bit 12: D-Pad Left
    - Bit 13: D-Pad Right
    - Bit 14: Guide
    - Bit 15: Reserved (`0`)
  - **Offset 14 (1 byte):** `lt` (`u8`) - Left analog trigger (`0` to `255`).
  - **Offset 15 (1 byte):** `rt` (`u8`) - Right analog trigger (`0` to `255`).
  - **Offset 16 (2 bytes):** `lx` (`i16`) - Left thumbstick X (`-32768` to `32767`).
  - **Offset 18 (2 bytes):** `ly` (`i16`) - Left thumbstick Y (`-32768` to `32767`).
  - **Offset 20 (2 bytes):** `rx` (`i16`) - Right thumbstick X (`-32768` to `32767`).
  - **Offset 22 (2 bytes):** `ry` (`i16`) - Right thumbstick Y (`-32768` to `32767`).
- **Trailing (8 bytes, Offset 24..31):** Truncated `HMAC-SHA256` tag calculated over bytes 0..23.

### 4.8 Type 8: `status` (21 Bytes Total)
- **Header:** 12 bytes (`type = 8`).
- **Payload:** 1 byte:
  - **Offset 12 (1 byte):** `flags` (`u8`) - Operational state bitmask:
    - Bit 0: Receiver Armed (`1` = Armed, `0` = Disarmed)
    - Bit 1: Target Window in Foreground Focus (`1` = Focused, `0` = Lost focus)
    - Bit 2: Keyboard/Mouse Sink Available (`1` = Ready)
    - Bit 3: Virtual Gamepad Sink Available (`1` = Ready)
    - Bits 4-7: Reserved (`0`)
- **Trailing (8 bytes, Offset 13..20):** Truncated `HMAC-SHA256` tag calculated over bytes 0..12.

---

## 5. Sequence Number Arithmetic (RFC 1982)

Sequence numbers are 32-bit unsigned integers initialized to `1` upon session creation and incremented per datagram. To handle the 32-bit integer wraparound gracefully:
- Relational operators such as `seq > lastSeq` are strictly prohibited.
- Implementations MUST use RFC 1982 serial number arithmetic:
  ```csharp
  // C# Receiver Implementation
  bool IsNewer(uint seq, uint lastSeq)
  {
      return unchecked((int)(seq - lastSeq)) > 0;
  }
  ```
  ```kotlin
  // Kotlin Implementation
  fun isNewer(seq: UInt, lastSeq: UInt): Boolean {
      return (seq - lastSeq).toInt() > 0
  }
  ```
- If `IsNewer(seq, lastSeq)` is false, the datagram is an out-of-order or duplicate packet and MUST be silently discarded without altering receiver state.

---

## 6. Cumulative Look Counter Mechanics

Rather than transmitting relative per-packet displacement deltas (which permanently drop camera movement on UDP packet loss), look input utilizes **cumulative wrapping counters**:

1. **Accumulator Representation:**
   The phone maintains running 32-bit signed integers `lookX` and `lookY`. When touch drag or gyro integration occurs, deltas are added directly:
   `lookX = unchecked(lookX + deltaX)`
   `lookY = unchecked(lookY + deltaY)`

2. **Receiver Delta Extraction:**
   The receiver calculates per-packet delta via wraparound-safe subtraction:
   ```csharp
   int deltaX = unchecked(currentLookX - previousLookX);
   int deltaY = unchecked(currentLookY - previousLookY);
   ```

3. **Session Baseline Rule:**
   The very first valid `input_kbm` packet accepted in a session establishes the baseline:
   `previousLookX = currentLookX; previousLookY = currentLookY;`
   No mouse motion is injected for this initial packet.

4. **250 ms Silence Re-baseline Rule:**
   If the elapsed duration between the current packet and the previous accepted input packet exceeds **250 ms** (due to network stall, Wi-Fi handoff, or idle pause):
   - The receiver resets the baseline to `currentLookX` and `currentLookY`.
   - Injected delta is set to `(0, 0)`.
   - This strictly prevents camera teleportation / violent view snapping upon reconnection.

5. **Clamping Safety Limits:**
   Extracted deltas are clamped to a sane threshold to eliminate corrupted or abnormal jumps:
   `clampedDeltaX = Math.Clamp(deltaX, -4000, 4000);`
   `clampedDeltaY = Math.Clamp(deltaY, -4000, 4000);`
   Whenever clamping occurs, the receiver logs a warning event.

6. **Fractional Remainder Carry:**
   Slow touch movements or small gyro adjustments produce sub-pixel fractions.
   - The phone accumulates fractional counts in local state and only transfers whole integers to the counters.
   - The receiver multiplies integer counts by the configured PC-side sensitivity scale (e.g. `rawDelta * scale`). The fractional remainder is preserved across frames so slow aiming is never lost to rounding.

---

## 7. Receiver Validation Pipeline

The receiver processes datagrams through a strict fail-fast pipeline:
1. **Length Validation:** Datagram length MUST exactly match the expected total size for its packet type.
2. **Magic Validation:** `magic == 0x4750`.
3. **Version Validation:** `version == 1`.
4. **Session Validation:** For non-handshake packets, `session == current_session_id`.
5. **HMAC Signature Check:** Constant-time comparison of trailing 8 bytes with computed HMAC-SHA256.
6. **Sequence Check:** Must satisfy RFC 1982 `IsNewer(seq, last_accepted_seq)`.
7. **Rate Limiting:** Discard traffic from sources exceeding 1,000 packets per second.
