/**
 * @aegis-contract
 * @claim Searchable KeyId Metadata and Mapping Table
 * @true Maps KeyIds 0..61 to display names and categories matching docs/keys.md
 * @false Omits assigned keys or provides incorrect scan code metadata
 */
package com.gamepad.controller.data

data class KeyDefinition(
    val keyId: Int,
    val name: String,
    val category: String,
    val description: String = ""
)

object KeyMap {
    val ALL_KEYS: List<KeyDefinition> = listOf(
        // Letters (0..25)
        KeyDefinition(KeyId.A, "A", "Letters"),
        KeyDefinition(KeyId.B, "B", "Letters"),
        KeyDefinition(KeyId.C, "C", "Letters"),
        KeyDefinition(KeyId.D, "D", "Letters"),
        KeyDefinition(KeyId.E, "E", "Letters"),
        KeyDefinition(KeyId.F, "F", "Letters"),
        KeyDefinition(KeyId.G, "G", "Letters"),
        KeyDefinition(KeyId.H, "H", "Letters"),
        KeyDefinition(KeyId.I, "I", "Letters"),
        KeyDefinition(KeyId.J, "J", "Letters"),
        KeyDefinition(KeyId.K, "K", "Letters"),
        KeyDefinition(KeyId.L, "L", "Letters"),
        KeyDefinition(KeyId.M, "M", "Letters"),
        KeyDefinition(KeyId.N, "N", "Letters"),
        KeyDefinition(KeyId.O, "O", "Letters"),
        KeyDefinition(KeyId.P, "P", "Letters"),
        KeyDefinition(KeyId.Q, "Q", "Letters"),
        KeyDefinition(KeyId.R, "R", "Letters"),
        KeyDefinition(KeyId.S, "S", "Letters"),
        KeyDefinition(KeyId.T, "T", "Letters"),
        KeyDefinition(KeyId.U, "U", "Letters"),
        KeyDefinition(KeyId.V, "V", "Letters"),
        KeyDefinition(KeyId.W, "W", "Letters"),
        KeyDefinition(KeyId.X, "X", "Letters"),
        KeyDefinition(KeyId.Y, "Y", "Letters"),
        KeyDefinition(KeyId.Z, "Z", "Letters"),

        // Digits (26..35)
        KeyDefinition(KeyId.KEY_0, "0", "Digits"),
        KeyDefinition(KeyId.KEY_1, "1", "Digits"),
        KeyDefinition(KeyId.KEY_2, "2", "Digits"),
        KeyDefinition(KeyId.KEY_3, "3", "Digits"),
        KeyDefinition(KeyId.KEY_4, "4", "Digits"),
        KeyDefinition(KeyId.KEY_5, "5", "Digits"),
        KeyDefinition(KeyId.KEY_6, "6", "Digits"),
        KeyDefinition(KeyId.KEY_7, "7", "Digits"),
        KeyDefinition(KeyId.KEY_8, "8", "Digits"),
        KeyDefinition(KeyId.KEY_9, "9", "Digits"),

        // Whitespace & Modifiers (36..40)
        KeyDefinition(KeyId.SPACE, "Space", "Whitespace"),
        KeyDefinition(KeyId.TAB, "Tab", "Whitespace"),
        KeyDefinition(KeyId.LEFT_SHIFT, "Shift", "Modifiers"),
        KeyDefinition(KeyId.LEFT_CTRL, "Ctrl", "Modifiers"),
        KeyDefinition(KeyId.LEFT_ALT, "Alt", "Modifiers"),

        // Control (41..43, 60)
        KeyDefinition(KeyId.ESCAPE, "Escape", "Control"),
        KeyDefinition(KeyId.ENTER, "Enter", "Control"),
        KeyDefinition(KeyId.CAPSLOCK, "CapsLock", "Control"),
        KeyDefinition(KeyId.BACKSPACE, "Backspace", "Control"),

        // Function (44..55)
        KeyDefinition(KeyId.F1, "F1", "Function"),
        KeyDefinition(KeyId.F2, "F2", "Function"),
        KeyDefinition(KeyId.F3, "F3", "Function"),
        KeyDefinition(KeyId.F4, "F4", "Function"),
        KeyDefinition(KeyId.F5, "F5", "Function"),
        KeyDefinition(KeyId.F6, "F6", "Function"),
        KeyDefinition(KeyId.F7, "F7", "Function"),
        KeyDefinition(KeyId.F8, "F8", "Function"),
        KeyDefinition(KeyId.F9, "F9", "Function"),
        KeyDefinition(KeyId.F10, "F10", "Function"),
        KeyDefinition(KeyId.F11, "F11", "Function"),
        KeyDefinition(KeyId.F12, "F12", "Function"),

        // Navigation (56..59)
        KeyDefinition(KeyId.UP_ARROW, "Up Arrow", "Navigation"),
        KeyDefinition(KeyId.DOWN_ARROW, "Down Arrow", "Navigation"),
        KeyDefinition(KeyId.LEFT_ARROW, "Left Arrow", "Navigation"),
        KeyDefinition(KeyId.RIGHT_ARROW, "Right Arrow", "Navigation"),

        // Punctuation (61)
        KeyDefinition(KeyId.TILDE, "Tilde (~)", "Punctuation")
    )

    private val KEY_MAP: Map<Int, KeyDefinition> = ALL_KEYS.associateBy { it.keyId }

    fun getKeyName(keyId: Int?): String {
        if (keyId == null) return "None"
        return KEY_MAP[keyId]?.name ?: "Key $keyId"
    }

    fun getKeyDefinition(keyId: Int): KeyDefinition? = KEY_MAP[keyId]

    fun searchKeys(query: String): List<KeyDefinition> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return ALL_KEYS
        return ALL_KEYS.filter {
            it.name.contains(trimmed, ignoreCase = true) ||
            it.category.contains(trimmed, ignoreCase = true) ||
            it.keyId.toString() == trimmed
        }
    }
}
