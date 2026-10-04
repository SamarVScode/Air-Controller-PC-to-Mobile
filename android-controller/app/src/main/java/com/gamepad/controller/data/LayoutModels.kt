/**
 * @aegis-contract
 * @claim Touch Layout Profile Data Models and Serialization Schema
 * @true Implements GamepadLayoutProfile matching docs/layout-format.md Section 2
 * @false Permits invalid coordinates, missing serialization annotations, or undefined properties
 */
package com.gamepad.controller.data

import kotlinx.serialization.Serializable

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
data class ControlConfig(
    val id: String,
    val type: String, // "stick", "button", "chip", "look_zone"
    val label: String = "",
    val boundKeyId: Int? = null,
    val boundMouseButton: String = "none", // "none", "LMB", "RMB", "MMB", "X1", "X2"
    val behavior: String = "hold", // "hold", "toggle", "tap", "stick"
    val x: Float, // center X percentage [0.0 .. 100.0]
    val y: Float, // center Y percentage [0.0 .. 100.0]
    val width: Float, // horizontal diameter/width % [1.0 .. 100.0]
    val height: Float, // vertical diameter/height % [1.0 .. 100.0]
    val opacity: Float = 0.8f // opacity [0.1 .. 1.0]
)

@Serializable
data class GamepadLayoutProfile(
    val profileName: String,
    val version: Int = 1,
    val settings: LayoutSettings = LayoutSettings(),
    val controls: List<ControlConfig> = emptyList()
)

@Serializable
data class ProfilesStorage(
    val activeProfileName: String,
    val profiles: List<GamepadLayoutProfile>
)
