<!--
@aegis-contract
@claim Authoritative Touch Layout Profile Schema and Default PUBG Mobile HUD Specification
@true Implements concrete GamepadLayoutProfile JSON schema with normalized coordinate percentages, explicit boundary validation error handling, and security schema constraints
@false Permits dummy placeholder stubs, invalid coordinates, missing default controls, or unhandled serialization errors
-->

# Touch Layout Profile Schema & Default HUD Specification

## 1. Coordinate Space & Scaling Rules

To ensure identical control placement across diverse mobile form factors, screen aspect ratios (16:9, 19.5:9, 21:9), and resolutions:
- **Normalized Units:** All coordinates and dimensions are expressed as floating-point percentages (`0.0` to `100.0`).
- **Horizontal Axis (`x`, `width`):** Percentage of total screen width. `x = 0.0` is the left edge; `x = 100.0` is the right edge.
- **Vertical Axis (`y`, `height`):** Percentage of total screen height. `y = 0.0` is the top edge; `y = 100.0` is the bottom edge.
- **Anchor Point:** Position coordinates `(x, y)` represent the **exact geometric center** of the control element.
- **Display Orientation:** Fixed landscape orientation only (supports both `LANDSCAPE` and `REVERSE_LANDSCAPE`).
- **System Cutout / Notch Safety:** The layout system respects display cutouts and insets, utilizing `setSystemGestureExclusionRects` along outer screen boundaries.

---

## 2. JSON Profile Schema (`GamepadLayoutProfile`)

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "GamepadLayoutProfile",
  "type": "object",
  "properties": {
    "profileName": {
      "type": "string",
      "description": "Human-readable profile title"
    },
    "version": {
      "type": "integer",
      "default": 1,
      "description": "Profile schema version"
    },
    "settings": {
      "type": "object",
      "properties": {
        "touchSensitivity": {
          "type": "number",
          "default": 1.0,
          "minimum": 0.1,
          "maximum": 5.0
        },
        "adsSensitivityMultiplier": {
          "type": "number",
          "default": 0.6,
          "minimum": 0.1,
          "maximum": 2.0
        },
        "gyroEnabled": {
          "type": "boolean",
          "default": false
        },
        "gyroSensitivity": {
          "type": "number",
          "default": 1.0,
          "minimum": 0.1,
          "maximum": 5.0
        },
        "stickDeadzone": {
          "type": "number",
          "default": 0.35,
          "minimum": 0.05,
          "maximum": 0.8
        },
        "stickFloatingOrigin": {
          "type": "boolean",
          "default": true
        }
      },
      "required": [
        "touchSensitivity",
        "adsSensitivityMultiplier",
        "stickDeadzone",
        "stickFloatingOrigin"
      ],
      "additionalProperties": false
    },
    "controls": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "id": {
            "type": "string",
            "description": "Unique identifier for control"
          },
          "type": {
            "type": "string",
            "enum": ["stick", "button", "chip", "look_zone"],
            "description": "Visual and functional control archetype"
          },
          "label": {
            "type": "string",
            "description": "Visible display label"
          },
          "boundKeyId": {
            "type": "integer",
            "minimum": 0,
            "maximum": 127,
            "description": "Bound keyboard KeyId from docs/keys.md"
          },
          "boundMouseButton": {
            "type": "string",
            "enum": ["none", "LMB", "RMB", "MMB", "X1", "X2"],
            "default": "none"
          },
          "behavior": {
            "type": "string",
            "enum": ["hold", "toggle", "tap", "stick"],
            "description": "Input activation mechanic"
          },
          "x": {
            "type": "number",
            "minimum": 0.0,
            "maximum": 100.0,
            "description": "Horizontal center position as % of screen width"
          },
          "y": {
            "type": "number",
            "minimum": 0.0,
            "maximum": 100.0,
            "description": "Vertical center position as % of screen height"
          },
          "width": {
            "type": "number",
            "minimum": 1.0,
            "maximum": 100.0,
            "description": "Horizontal diameter/width as % of screen width"
          },
          "height": {
            "type": "number",
            "minimum": 1.0,
            "maximum": 100.0,
            "description": "Vertical diameter/height as % of screen height"
          },
          "opacity": {
            "type": "number",
            "minimum": 0.1,
            "maximum": 1.0,
            "default": 0.8
          }
        },
        "required": ["id", "type", "behavior", "x", "y", "width", "height"],
        "additionalProperties": false
      }
    }
  },
  "required": ["profileName", "version", "settings", "controls"],
  "additionalProperties": false
}
```

---

## 3. Authoritative Default HUD Configuration (PUBG Mobile Style)

The following default profile implements the battle-royale HUD layout defined in PLAN.md Section 3:

```json
{
  "profileName": "Default Battle Royale",
  "version": 1,
  "settings": {
    "touchSensitivity": 1.0,
    "adsSensitivityMultiplier": 0.6,
    "gyroEnabled": false,
    "gyroSensitivity": 1.0,
    "stickDeadzone": 0.35,
    "stickFloatingOrigin": true
  },
  "controls": [
    {
      "id": "ms",
      "type": "stick",
      "label": "Move",
      "behavior": "stick",
      "x": 16.0,
      "y": 66.0,
      "width": 17.0,
      "height": 17.0,
      "opacity": 0.75
    },
    {
      "id": "run",
      "type": "button",
      "label": "Sprint",
      "boundKeyId": 38,
      "boundMouseButton": "none",
      "behavior": "toggle",
      "x": 16.0,
      "y": 34.0,
      "width": 5.6,
      "height": 5.6,
      "opacity": 0.75
    },
    {
      "id": "lfire",
      "type": "button",
      "label": "Fire",
      "boundMouseButton": "LMB",
      "behavior": "hold",
      "x": 31.0,
      "y": 58.0,
      "width": 9.0,
      "height": 9.0,
      "opacity": 0.85
    },
    {
      "id": "rfire",
      "type": "button",
      "label": "Fire & Look",
      "boundMouseButton": "LMB",
      "behavior": "hold",
      "x": 86.0,
      "y": 64.0,
      "width": 14.0,
      "height": 14.0,
      "opacity": 0.85
    },
    {
      "id": "scope",
      "type": "button",
      "label": "ADS",
      "boundMouseButton": "RMB",
      "behavior": "toggle",
      "x": 73.0,
      "y": 47.0,
      "width": 8.0,
      "height": 8.0,
      "opacity": 0.85
    },
    {
      "id": "jump",
      "type": "button",
      "label": "Jump",
      "boundKeyId": 36,
      "boundMouseButton": "none",
      "behavior": "hold",
      "x": 94.0,
      "y": 38.0,
      "width": 8.0,
      "height": 8.0,
      "opacity": 0.85
    },
    {
      "id": "crouch",
      "type": "button",
      "label": "Crouch",
      "boundKeyId": 2,
      "boundMouseButton": "none",
      "behavior": "hold",
      "x": 76.0,
      "y": 84.0,
      "width": 7.0,
      "height": 7.0,
      "opacity": 0.85
    },
    {
      "id": "prone",
      "type": "button",
      "label": "Prone",
      "boundKeyId": 25,
      "boundMouseButton": "none",
      "behavior": "hold",
      "x": 95.0,
      "y": 88.0,
      "width": 7.0,
      "height": 7.0,
      "opacity": 0.85
    },
    {
      "id": "leanl",
      "type": "button",
      "label": "Lean L",
      "boundKeyId": 16,
      "boundMouseButton": "none",
      "behavior": "hold",
      "x": 62.0,
      "y": 32.0,
      "width": 6.0,
      "height": 6.0,
      "opacity": 0.8
    },
    {
      "id": "leanr",
      "type": "button",
      "label": "Lean R",
      "boundKeyId": 4,
      "boundMouseButton": "none",
      "behavior": "hold",
      "x": 70.0,
      "y": 32.0,
      "width": 6.0,
      "height": 6.0,
      "opacity": 0.8
    },
    {
      "id": "reload",
      "type": "button",
      "label": "Reload",
      "boundKeyId": 17,
      "boundMouseButton": "none",
      "behavior": "hold",
      "x": 64.0,
      "y": 68.0,
      "width": 6.4,
      "height": 6.4,
      "opacity": 0.85
    },
    {
      "id": "use",
      "type": "button",
      "label": "Use",
      "boundKeyId": 5,
      "boundMouseButton": "none",
      "behavior": "hold",
      "x": 58.0,
      "y": 54.0,
      "width": 6.4,
      "height": 6.4,
      "opacity": 0.8
    },
    {
      "id": "s1",
      "type": "button",
      "label": "Slot 1",
      "boundKeyId": 27,
      "boundMouseButton": "none",
      "behavior": "hold",
      "x": 38.0,
      "y": 15.0,
      "width": 6.0,
      "height": 6.0,
      "opacity": 0.75
    },
    {
      "id": "s2",
      "type": "button",
      "label": "Slot 2",
      "boundKeyId": 28,
      "boundMouseButton": "none",
      "behavior": "hold",
      "x": 44.5,
      "y": 15.0,
      "width": 6.0,
      "height": 6.0,
      "opacity": 0.75
    },
    {
      "id": "s3",
      "type": "button",
      "label": "Melee",
      "boundKeyId": 29,
      "boundMouseButton": "none",
      "behavior": "hold",
      "x": 51.0,
      "y": 15.0,
      "width": 6.0,
      "height": 6.0,
      "opacity": 0.75
    },
    {
      "id": "gren",
      "type": "button",
      "label": "Grenade",
      "boundKeyId": 6,
      "boundMouseButton": "none",
      "behavior": "hold",
      "x": 58.0,
      "y": 15.0,
      "width": 6.0,
      "height": 6.0,
      "opacity": 0.75
    },
    {
      "id": "heal",
      "type": "button",
      "label": "Heal",
      "boundKeyId": 31,
      "boundMouseButton": "none",
      "behavior": "hold",
      "x": 65.0,
      "y": 15.0,
      "width": 6.0,
      "height": 6.0,
      "opacity": 0.75
    },
    {
      "id": "map",
      "type": "button",
      "label": "Map",
      "boundKeyId": 12,
      "boundMouseButton": "none",
      "behavior": "hold",
      "x": 84.0,
      "y": 12.0,
      "width": 5.4,
      "height": 5.4,
      "opacity": 0.75
    },
    {
      "id": "bag",
      "type": "button",
      "label": "Bag",
      "boundKeyId": 37,
      "boundMouseButton": "none",
      "behavior": "hold",
      "x": 91.0,
      "y": 12.0,
      "width": 5.4,
      "height": 5.4,
      "opacity": 0.75
    },
    {
      "id": "gyro",
      "type": "chip",
      "label": "Gyro",
      "behavior": "toggle",
      "x": 50.0,
      "y": 90.0,
      "width": 15.0,
      "height": 4.8,
      "opacity": 0.7
    }
  ]
}
```

---

## 4. Touch & Pointer Interaction Semantics

1. **Look Zone Definition:**
   - The Look Zone spans horizontally from `x = 42.0%` to `100.0%`, covering the full vertical screen height (`y = 0.0%` to `100.0%`).
   - It resides logically behind all buttons.
   - Any touch pointer down event originating in this region that does NOT hit a control button is assigned exclusive pointer ownership to the Look Zone.
   - Movement of this pointer translates directly into horizontal and vertical cumulative look displacement.

2. **Fire and Drag Mechanics (`rfire`):**
   - The primary right fire button (`rfire`) explicitly supports combined fire and drag look.
   - When a finger presses `rfire`, LMB state is asserted.
   - Dragging the pointer while held down adds displacement to `lookX` and `lookY`, enabling natural recoil compensation.

3. **Behavior Mechanics:**
   - **`hold`:** Active while physical touch is maintained. Released immediately upon pointer up.
   - **`toggle`:** Touch tap inverts internal state (`false -> true -> false`). Held down until tapped again.
   - **`tap`:** Injects a brief pulse (30 ms minimum hold time). Useful for keys that toggle state within the game engine.
