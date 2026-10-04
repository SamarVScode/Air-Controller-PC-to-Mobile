---
id: node:rules
title: Single Authoritative Project Rulebook
version: 1.0.0
framework: Google OKF (Open Knowledge Framework)
category: guidelines
tags: [rules, guidelines, boundaries, safety, tdd, anti-cheat, agent-rules]
relations: [node:architecture, node:specs, node:memory]
summary: Authoritative engineering rules, safety boundaries, TDD mandates, anti-cheat constraints, and agent development guidelines.
---

<!--
@aegis-contract
@claim Authoritative engineering rules, error handling mandates, and anti-cheat security boundaries for Phone-as-Gamepad
@true Implements concrete engineering rules, strict TDD mandates, explicit socket error handling and watchdog neutralization, and anti-cheat security boundaries preventing bot macros
@false Permits dummy placeholder stubs, ignores error handling, enables unauthorized macro automation, or bypasses anti-cheat safety gates
-->

# Single Authoritative Project Rulebook

## 1. Mandatory Engineering & TDD Rules
1. **Strict Test-Driven Development (TDD):** Every protocol packet encoder/decoder, serial wraparound calculation, key-diff engine, and look-delta accumulator must have passing unit tests before integration.
2. **Modular Architecture:** Monolithic files are prohibited. Logic must be decomposed into dedicated components (e.g. `UdpListener`, `KeyStateDiffEngine`, `LookDeltaProcessor`, `FocusGuard`).
3. **No Dead Code:** Unused imports, orphaned symbols, and circular references are prohibited.
4. **No Placeholder Code:** Empty stubs, dummy fallbacks, or mocked empty arrays are strictly forbidden.

## 2. Anti-Cheat, Fair Play & Safety Boundaries
1. **Human-Origin Input Only:** The application must strictly forward physical human touch and sensor inputs. Macros, automated rapid-fire, synthetic recoil scripts, or artificial assist algorithms are strictly prohibited.
2. **Offline Testing Mandate:** Software input injection must NEVER be tested in live, online, ranked, or tournament multiplayer matches. All testing must occur in dedicated offline practice ranges or with `tools/input-tester`.
3. **Transparent Anti-Cheat Notice:** The Windows receiver must display a prominent first-run warning alerting users to verify each game's terms of service and anti-cheat policies.
4. **Emergency Kill-Switch:** Pressing `Ctrl+Alt+F12` must globally neutralize all held keys and disarm the receiver instantly.
5. **Foreground Focus Guard:** Input injection is strictly prohibited unless the designated target game process owns the active foreground window.
6. **Watchdog Neutralization:** The receiver must neutralize all pressed keys and buttons if no valid datagram is received within 500 ms.

## 3. Cryptographic & Protocol Rules
1. **Wraparound-Safe Sequence Numbers:** Sequence comparisons must exclusively use RFC 1982 serial number arithmetic (`(int32_t)(seq - last_seq) > 0`). Direct relational operator `seq > last_seq` is prohibited due to `uint32` overflow.
2. **Constant-Time MAC Equality:** Verification of HMAC-SHA256 authentication tags must use constant-time comparison (`CryptographicOperations.FixedTimeEquals` or `MessageDigest.isEqual`) to avoid timing leakage.
3. **Single Source of Truth:** `docs/protocol.md` and `docs/keys.md` are the single source of truth for byte formats and scan codes. Implementation code must match these specifications verbatim.
4. **No Hardcoded Release Keys:** The `--debug-key` command-line switch must be compiled out of release builds (`#if DEBUG`) and never shipped to production.

## 4. Agent Working Rules (PLAN.md Section 32)
1. Build phase-by-phase; satisfy all acceptance criteria of the current phase before advancing to the next.
2. Never silently bypass a failed acceptance test or GO/NO-GO gate.
3. Never silently switch output modes between Keyboard/Mouse (`SendInput`) and Gamepad (`ViGEm`).
4. Commit after each completed task with a clear, descriptive message.
5. Clean up temporary test files and avoid polluting working directories.
