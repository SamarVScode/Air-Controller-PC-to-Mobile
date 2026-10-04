package com.gamepad.controller.data

import kotlinx.serialization.Serializable

/**
 * Authoritative KeyId table matching docs/keys.md and PLAN.md Section 9.7.
 */
object KeyId {
    const val A = 0
    const val B = 1
    const val C = 2
    const val D = 3
    const val E = 4
    const val F = 5
    const val G = 6
    const val H = 7
    const val I = 8
    const val J = 9
    const val K = 10
    const val L = 11
    const val M = 12
    const val N = 13
    const val O = 14
    const val P = 15
    const val Q = 16
    const val R = 17
    const val S = 18
    const val T = 19
    const val U = 20
    const val V = 21
    const val W = 22
    const val X = 23
    const val Y = 24
    const val Z = 25
    const val KEY_0 = 26
    const val KEY_1 = 27
    const val KEY_2 = 28
    const val KEY_3 = 29
    const val KEY_4 = 30
    const val KEY_5 = 31
    const val KEY_6 = 32
    const val KEY_7 = 33
    const val KEY_8 = 34
    const val KEY_9 = 35
    const val SPACE = 36
    const val TAB = 37
    const val LEFT_SHIFT = 38
    const val LEFT_CTRL = 39
    const val LEFT_ALT = 40
    const val ESCAPE = 41
    const val ENTER = 42
    const val CAPSLOCK = 43
    const val F1 = 44
    const val F2 = 45
    const val F3 = 46
    const val F4 = 47
    const val F5 = 48
    const val F6 = 49
    const val F7 = 50
    const val F8 = 51
    const val F9 = 52
    const val F10 = 53
    const val F11 = 54
    const val F12 = 55
    const val UP_ARROW = 56
    const val DOWN_ARROW = 57
    const val LEFT_ARROW = 58
    const val RIGHT_ARROW = 59
    const val BACKSPACE = 60
    const val TILDE = 61
}

object MouseButton {
    const val LMB = 0x01
    const val RMB = 0x02
    const val MMB = 0x04
    const val X1 = 0x08
    const val X2 = 0x10
}

enum class ButtonBehavior {
    HOLD,
    TOGGLE,
    TAP
}

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    PAUSED
}

@Serializable
data class PairingQrPayload(
    val ips: List<String>,
    val port: Int = 47789,
    val key: String,
    val name: String
)
