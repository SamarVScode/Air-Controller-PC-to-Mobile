package com.gamepad.controller.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ControlType {
    @SerialName("stick") STICK,
    @SerialName("button") BUTTON,
    @SerialName("chip") CHIP,
    @SerialName("look_zone") LOOK_ZONE
}

@Serializable
enum class BehaviorType {
    @SerialName("hold") HOLD,
    @SerialName("toggle") TOGGLE,
    @SerialName("tap") TAP
}

@Serializable
enum class MouseButton {
    @SerialName("none") NONE,
    @SerialName("LMB") LMB,
    @SerialName("RMB") RMB,
    @SerialName("MMB") MMB
}

@Serializable
data class LayoutSettings(
    val touchSensitivity: Float = 1.0f,
    val adsSensitivityMultiplier: Float = 0.6f,
    val gyroEnabled: Boolean = false,
    val gyroSensitivity: Float = 1.0f,
    val stickDeadzone: Float = 0.35f,
    val stickFloatingOrigin: Boolean = true
)

@Serializable
data class ControlElement(
    val id: String,
    val type: ControlType,
    val label: String? = null,
    val boundKeyId: Int? = null,
    val boundMouseButton: MouseButton = MouseButton.NONE,
    val behavior: BehaviorType = BehaviorType.HOLD,
    val x: Float, // Center X percentage (0.0 to 100.0)
    val y: Float, // Center Y percentage (0.0 to 100.0)
    val width: Float, // Width percentage
    val height: Float, // Height percentage
    val opacity: Float = 0.8f
)

@Serializable
data class GamepadLayoutProfile(
    val profileName: String,
    val version: Int = 1,
    val settings: LayoutSettings,
    val controls: List<ControlElement>
) {
    companion object {
        /**
         * Returns authoritative PUBG Mobile default layout profile specified in PLAN.md Rev 4 Section 3.
         */
        fun createDefaultProfile(): GamepadLayoutProfile {
            return GamepadLayoutProfile(
                profileName = "Default PUBG Mobile",
                version = 1,
                settings = LayoutSettings(
                    touchSensitivity = 1.0f,
                    adsSensitivityMultiplier = 0.6f,
                    gyroEnabled = false,
                    gyroSensitivity = 1.0f,
                    stickDeadzone = 0.35f,
                    stickFloatingOrigin = true
                ),
                controls = listOf(
                    // ms: Move stick (WASD)
                    ControlElement("ms", ControlType.STICK, "Move", null, MouseButton.NONE, BehaviorType.HOLD, 16.0f, 66.0f, 17.0f, 17.0f),
                    // run: Run lock (Shift toggle)
                    ControlElement("run", ControlType.BUTTON, "Sprint", KeyId.KEY_LEFT_SHIFT.id, MouseButton.NONE, BehaviorType.TOGGLE, 16.0f, 34.0f, 5.6f, 5.6f),
                    // lfire: Fire (left LMB hold)
                    ControlElement("lfire", ControlType.BUTTON, "Fire L", null, MouseButton.LMB, BehaviorType.HOLD, 31.0f, 58.0f, 9.0f, 9.0f),
                    // rfire: Fire (right LMB hold + drag look)
                    ControlElement("rfire", ControlType.BUTTON, "Fire R", null, MouseButton.LMB, BehaviorType.HOLD, 86.0f, 64.0f, 14.0f, 14.0f),
                    // scope: Aim down sights (RMB toggle)
                    ControlElement("scope", ControlType.BUTTON, "ADS", null, MouseButton.RMB, BehaviorType.TOGGLE, 73.0f, 47.0f, 8.0f, 8.0f),
                    // jump: Jump (Space hold)
                    ControlElement("jump", ControlType.BUTTON, "Jump", KeyId.KEY_SPACE.id, MouseButton.NONE, BehaviorType.HOLD, 94.0f, 38.0f, 8.0f, 8.0f),
                    // crouch: Crouch (C hold)
                    ControlElement("crouch", ControlType.BUTTON, "Crouch", KeyId.KEY_C.id, MouseButton.NONE, BehaviorType.HOLD, 76.0f, 84.0f, 7.0f, 7.0f),
                    // prone: Prone (Z hold)
                    ControlElement("prone", ControlType.BUTTON, "Prone", KeyId.KEY_Z.id, MouseButton.NONE, BehaviorType.HOLD, 95.0f, 88.0f, 7.0f, 7.0f),
                    // leanl: Lean left (Q hold)
                    ControlElement("leanl", ControlType.BUTTON, "Lean L", KeyId.KEY_Q.id, MouseButton.NONE, BehaviorType.HOLD, 62.0f, 32.0f, 6.0f, 6.0f),
                    // leanr: Lean right (E hold)
                    ControlElement("leanr", ControlType.BUTTON, "Lean R", KeyId.KEY_E.id, MouseButton.NONE, BehaviorType.HOLD, 70.0f, 32.0f, 6.0f, 6.0f),
                    // reload: Reload (R hold)
                    ControlElement("reload", ControlType.BUTTON, "Reload", KeyId.KEY_R.id, MouseButton.NONE, BehaviorType.HOLD, 64.0f, 68.0f, 6.4f, 6.4f),
                    // use: Pick up / use (F hold)
                    ControlElement("use", ControlType.BUTTON, "Use", KeyId.KEY_F.id, MouseButton.NONE, BehaviorType.HOLD, 58.0f, 54.0f, 6.4f, 6.4f),
                    // s1: Weapon slot 1 (1 hold)
                    ControlElement("s1", ControlType.BUTTON, "1", KeyId.KEY_1.id, MouseButton.NONE, BehaviorType.HOLD, 38.0f, 15.0f, 6.0f, 6.0f),
                    // s2: Weapon slot 2 (2 hold)
                    ControlElement("s2", ControlType.BUTTON, "2", KeyId.KEY_2.id, MouseButton.NONE, BehaviorType.HOLD, 44.5f, 15.0f, 6.0f, 6.0f),
                    // s3: Melee slot (3 hold)
                    ControlElement("s3", ControlType.BUTTON, "3", KeyId.KEY_3.id, MouseButton.NONE, BehaviorType.HOLD, 51.0f, 15.0f, 6.0f, 6.0f),
                    // gren: Grenade (G hold)
                    ControlElement("gren", ControlType.BUTTON, "G", KeyId.KEY_G.id, MouseButton.NONE, BehaviorType.HOLD, 58.0f, 15.0f, 6.0f, 6.0f),
                    // heal: Heal (5 hold)
                    ControlElement("heal", ControlType.BUTTON, "5", KeyId.KEY_5.id, MouseButton.NONE, BehaviorType.HOLD, 65.0f, 15.0f, 6.0f, 6.0f),
                    // map: Map (M hold)
                    ControlElement("map", ControlType.BUTTON, "Map", KeyId.KEY_M.id, MouseButton.NONE, BehaviorType.HOLD, 84.0f, 12.0f, 5.4f, 5.4f),
                    // bag: Backpack (Tab hold)
                    ControlElement("bag", ControlType.BUTTON, "Bag", KeyId.KEY_TAB.id, MouseButton.NONE, BehaviorType.HOLD, 91.0f, 12.0f, 5.4f, 5.4f),
                    // gyro: Gyro toggle chip (phone only)
                    ControlElement("gyro", ControlType.CHIP, "Gyro", null, MouseButton.NONE, BehaviorType.TOGGLE, 50.0f, 90.0f, 15.0f, 4.8f),
                    // look zone: background area from x = 42% to 100%
                    ControlElement("look_zone", ControlType.LOOK_ZONE, "Look", null, MouseButton.NONE, BehaviorType.HOLD, 71.0f, 50.0f, 58.0f, 100.0f, 0.0f)
                )
            )
        }
    }
}
