package com.gamepad.controller.model

/**
 * Authoritative KeyId table matching .okf/nodes/schemas.md Section 3 and docs/keys.md.
 */
enum class KeyId(val id: Int, val ps2MakeCode: Int, val isExtended: Boolean, val keyName: String) {
    KEY_A(0, 0x1E, false, "A"),
    KEY_B(1, 0x30, false, "B"),
    KEY_C(2, 0x2E, false, "C"),
    KEY_D(3, 0x20, false, "D"),
    KEY_E(4, 0x12, false, "E"),
    KEY_F(5, 0x21, false, "F"),
    KEY_G(6, 0x22, false, "G"),
    KEY_H(7, 0x23, false, "H"),
    KEY_I(8, 0x17, false, "I"),
    KEY_J(9, 0x24, false, "J"),
    KEY_K(10, 0x25, false, "K"),
    KEY_L(11, 0x26, false, "L"),
    KEY_M(12, 0x32, false, "M"),
    KEY_N(13, 0x31, false, "N"),
    KEY_O(14, 0x18, false, "O"),
    KEY_P(15, 0x19, false, "P"),
    KEY_Q(16, 0x10, false, "Q"),
    KEY_R(17, 0x13, false, "R"),
    KEY_S(18, 0x1F, false, "S"),
    KEY_T(19, 0x14, false, "T"),
    KEY_U(20, 0x16, false, "U"),
    KEY_V(21, 0x2F, false, "V"),
    KEY_W(22, 0x11, false, "W"),
    KEY_X(23, 0x2D, false, "X"),
    KEY_Y(24, 0x15, false, "Y"),
    KEY_Z(25, 0x2C, false, "Z"),
    KEY_0(26, 0x0B, false, "0"),
    KEY_1(27, 0x02, false, "1"),
    KEY_2(28, 0x03, false, "2"),
    KEY_3(29, 0x04, false, "3"),
    KEY_4(30, 0x05, false, "4"),
    KEY_5(31, 0x06, false, "5"),
    KEY_6(32, 0x07, false, "6"),
    KEY_7(33, 0x08, false, "7"),
    KEY_8(34, 0x09, false, "8"),
    KEY_9(35, 0x0A, false, "9"),
    KEY_SPACE(36, 0x39, false, "Space"),
    KEY_TAB(37, 0x0F, false, "Tab"),
    KEY_LEFT_SHIFT(38, 0x2A, false, "LeftShift"),
    KEY_LEFT_CTRL(39, 0x1D, false, "LeftCtrl"),
    KEY_LEFT_ALT(40, 0x38, false, "LeftAlt"),
    KEY_ESCAPE(41, 0x01, false, "Escape"),
    KEY_ENTER(42, 0x1C, false, "Enter"),
    KEY_CAPS_LOCK(43, 0x3A, false, "CapsLock"),
    KEY_F1(44, 0x3B, false, "F1"),
    KEY_F2(45, 0x3C, false, "F2"),
    KEY_F3(46, 0x3D, false, "F3"),
    KEY_F4(47, 0x3E, false, "F4"),
    KEY_F5(48, 0x3F, false, "F5"),
    KEY_F6(49, 0x40, false, "F6"),
    KEY_F7(50, 0x41, false, "F7"),
    KEY_F8(51, 0x42, false, "F8"),
    KEY_F9(52, 0x43, false, "F9"),
    KEY_F10(53, 0x44, false, "F10"),
    KEY_F11(54, 0x57, false, "F11"),
    KEY_F12(55, 0x58, false, "F12"),
    KEY_UP(56, 0x48, true, "UpArrow"),
    KEY_DOWN(57, 0x50, true, "DownArrow"),
    KEY_LEFT(58, 0x4B, true, "LeftArrow"),
    KEY_RIGHT(59, 0x4D, true, "RightArrow"),
    KEY_BACKSPACE(60, 0x0E, false, "Backspace"),
    KEY_TILDE(61, 0x29, false, "Tilde");

    companion object {
        private val ID_MAP = entries.associateBy { it.id }
        fun fromId(id: Int): KeyId? = ID_MAP[id]
    }
}

/**
 * 128-bit (16-byte) bitmask engine representing active key states.
 */
class KeyBitmap(private val bytes: ByteArray = ByteArray(16)) {
    init {
        require(bytes.size == 16) { "KeyBitmap must be exactly 16 bytes" }
    }

    fun setKey(keyId: Int, isDown: Boolean) {
        if (keyId !in 0..127) return
        val byteIndex = keyId / 8
        val bitIndex = keyId % 8
        val currentByte = bytes[byteIndex].toInt()
        bytes[byteIndex] = if (isDown) {
            (currentByte or (1 shl bitIndex)).toByte()
        } else {
            (currentByte and (1 shl bitIndex).inv()).toByte()
        }
    }

    fun isKeyDown(keyId: Int): Boolean {
        if (keyId !in 0..127) return false
        val byteIndex = keyId / 8
        val bitIndex = keyId % 8
        return (bytes[byteIndex].toInt() and (1 shl bitIndex)) != 0
    }

    fun copyBytes(): ByteArray = bytes.copyOf()

    fun copyInto(destination: ByteArray) {
        require(destination.size >= 16)
        System.arraycopy(bytes, 0, destination, 0, 16)
    }

    fun clear() {
        bytes.fill(0)
    }

    fun hasAnyKeyDown(): Boolean {
        for (b in bytes) {
            if (b.toInt() != 0) return true
        }
        return false
    }
}
