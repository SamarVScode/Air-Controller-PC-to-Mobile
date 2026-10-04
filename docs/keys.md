<!--
@aegis-contract
@claim Authoritative KeyId to PS/2 Set 1 Scan Code Mapping Table
@true Defines exact mapping from KeyId 0..127 to PS/2 Make Codes and Extended-Key flags for Win32 SendInput
@true Documents exact scan codes for letters, digits, control keys, function keys, and arrows
@false Contains missing keys, renumbered IDs, or undefined scan codes for assigned IDs
-->

# Authoritative KeyId Mapping Specification

## 1. Architecture & Design Principles

Input injection into Windows 11 PC games requires hardware scan codes rather than virtual-key (VK) codes. Modern game engines (Unreal, Unity, proprietary shooter engines) read raw keyboard input through DirectInput or Raw Input, which bypasses VK translation.

- **Payload Representation:** Packet Type 0 (`input_kbm`) transmits a 16-byte (128-bit) bitmap.
- **Bit Mapping:** Bit index `N` (`0 <= N <= 127`) set to `1` indicates `KeyId N` is pressed; `0` indicates released.
- **Win32 Injection Flags:**
  - `KEYEVENTF_SCANCODE` (`0x0008`): Informs `SendInput` that `wScan` holds a hardware scan code.
  - `KEYEVENTF_EXTENDEDKEY` (`0x0001`): Must be set for keys requiring the `0xE0` prefix byte (e.g., arrow keys).
  - `KEYEVENTF_KEYUP` (`0x0002`): Set when injecting key release.
- **Immutability Rules:**
  - KeyIds are immutable and will NEVER be renumbered or reused.
  - Unassigned KeyIds (`62..127`) are reserved and MUST be silently ignored by the receiver.
  - Mouse buttons are intentionally excluded from this table and handled in the dedicated mouse bitmask.

---

## 2. Authoritative KeyId Mapping Table

| KeyId | Key Name | PS/2 Set 1 Make Code (Hex) | Decimal | Extended (`KEYEVENTF_EXTENDEDKEY`) | Category |
|---|---|---|---|---|---|
| `0` | A | `0x1E` | 30 | No | Letters |
| `1` | B | `0x30` | 48 | No | Letters |
| `2` | C | `0x2E` | 46 | No | Letters |
| `3` | D | `0x20` | 32 | No | Letters |
| `4` | E | `0x12` | 18 | No | Letters |
| `5` | F | `0x21` | 33 | No | Letters |
| `6` | G | `0x22` | 34 | No | Letters |
| `7` | H | `0x23` | 35 | No | Letters |
| `8` | I | `0x17` | 23 | No | Letters |
| `9` | J | `0x24` | 36 | No | Letters |
| `10` | K | `0x25` | 37 | No | Letters |
| `11` | L | `0x26` | 38 | No | Letters |
| `12` | M | `0x32` | 50 | No | Letters |
| `13` | N | `0x31` | 49 | No | Letters |
| `14` | O | `0x18` | 24 | No | Letters |
| `15` | P | `0x19` | 25 | No | Letters |
| `16` | Q | `0x10` | 16 | No | Letters |
| `17` | R | `0x13` | 19 | No | Letters |
| `18` | S | `0x1F` | 31 | No | Letters |
| `19` | T | `0x14` | 20 | No | Letters |
| `20` | U | `0x16` | 22 | No | Letters |
| `21` | V | `0x2F` | 47 | No | Letters |
| `22` | W | `0x11` | 17 | No | Letters |
| `23` | X | `0x2D` | 45 | No | Letters |
| `24` | Y | `0x15` | 21 | No | Letters |
| `25` | Z | `0x2C` | 44 | No | Letters |
| `26` | 0 | `0x0B` | 11 | No | Digits |
| `27` | 1 | `0x02` | 2 | No | Digits |
| `28` | 2 | `0x03` | 3 | No | Digits |
| `29` | 3 | `0x04` | 4 | No | Digits |
| `30` | 4 | `0x05` | 5 | No | Digits |
| `31` | 5 | `0x06` | 6 | No | Digits |
| `32` | 6 | `0x07` | 7 | No | Digits |
| `33` | 7 | `0x08` | 8 | No | Digits |
| `34` | 8 | `0x09` | 9 | No | Digits |
| `35` | 9 | `0x0A` | 10 | No | Digits |
| `36` | Space | `0x39` | 57 | No | Whitespace |
| `37` | Tab | `0x0F` | 15 | No | Whitespace |
| `38` | LeftShift | `0x2A` | 42 | No | Modifiers |
| `39` | LeftCtrl | `0x1D` | 29 | No | Modifiers |
| `40` | LeftAlt | `0x38` | 56 | No | Modifiers |
| `41` | Escape | `0x01` | 1 | No | Control |
| `42` | Enter | `0x1C` | 28 | No | Control |
| `43` | CapsLock | `0x3A` | 58 | No | Control |
| `44` | F1 | `0x3B` | 59 | No | Function |
| `45` | F2 | `0x3C` | 60 | No | Function |
| `46` | F3 | `0x3D` | 61 | No | Function |
| `47` | F4 | `0x3E` | 62 | No | Function |
| `48` | F5 | `0x3F` | 63 | No | Function |
| `49` | F6 | `0x40` | 64 | No | Function |
| `50` | F7 | `0x41` | 65 | No | Function |
| `51` | F8 | `0x42` | 66 | No | Function |
| `52` | F9 | `0x43` | 67 | No | Function |
| `53` | F10 | `0x44` | 68 | No | Function |
| `54` | F11 | `0x57` | 87 | No | Function |
| `55` | F12 | `0x58` | 88 | No | Function |
| `56` | UpArrow | `0x48` | 72 | **Yes** (`0xE0 0x48`) | Navigation |
| `57` | DownArrow | `0x50` | 80 | **Yes** (`0xE0 0x50`) | Navigation |
| `58` | LeftArrow | `0x4B` | 75 | **Yes** (`0xE0 0x4B`) | Navigation |
| `59` | RightArrow | `0x4D` | 77 | **Yes** (`0xE0 0x4D`) | Navigation |
| `60` | Backspace | `0x0E` | 14 | No | Control |
| `61` | Tilde / Grave (`` ` ``) | `0x29` | 41 | No | Punctuation |
| `62..127` | *Reserved* | N/A | N/A | N/A | Reserved |

---

## 3. Win32 SendInput Construction Rules

When emitting input events in `KeyboardMouseSink`:

```csharp
public void SendKey(ushort scanCode, bool isExtended, bool isKeyUp)
{
    var input = new INPUT
    {
        type = INPUT_KEYBOARD,
        u = new InputUnion
        {
            ki = new KEYBDINPUT
            {
                wVk = 0, // Must be 0 when KEYEVENTF_SCANCODE is specified
                wScan = scanCode,
                dwFlags = KEYEVENTF_SCANCODE 
                    | (isExtended ? KEYEVENTF_EXTENDEDKEY : 0)
                    | (isKeyUp ? KEYEVENTF_KEYUP : 0),
                time = 0,
                dwExtraInfo = IntPtr.Zero
            }
        }
    };
    SendInput(1, new[] { input }, Marshal.SizeOf<INPUT>());
}
```

### Minimum Key Hold Time
To ensure game engines polling input at display frame rates (e.g. 60 Hz = 16.6 ms per frame) register brief touch taps reliably:
- Key-down events are injected immediately.
- The receiver delays corresponding key-up injection until the key has been held for at least **30 ms** (configurable 0..50 ms).
- **Emergency Exception:** Watchdog timeouts, foreground focus loss, and emergency kill-switch (`Ctrl+Alt+F12`) neutralize keys immediately, bypassing the minimum hold timer.
