package com.gamepad.controller.model

/**
 * Authoritative wire protocol constants per PLAN.md Rev 4 Section 9 and .okf/nodes/schemas.md.
 */
object ProtocolConstants {
    // Protocol Identification
    const val MAGIC: UShort = 0x5047u // 'G' (0x47), 'P' (0x50) in Little-Endian: byte 0 = 0x47, byte 1 = 0x50
    const val VERSION: UByte = 1u
    const val DEFAULT_PORT: Int = 47789

    // Frame Sizing
    const val HEADER_SIZE: Int = 12
    const val MAC_SIZE: Int = 8

    // Packet Types
    const val TYPE_INPUT_KBM: UByte = 0u
    const val TYPE_RUMBLE: UByte = 1u
    const val TYPE_PING: UByte = 2u
    const val TYPE_PONG: UByte = 3u
    const val TYPE_HELLO: UByte = 4u
    const val TYPE_HELLO_ACK: UByte = 5u
    const val TYPE_BYE: UByte = 6u
    const val TYPE_INPUT_PAD: UByte = 7u
    const val TYPE_STATUS: UByte = 8u

    // Total Packet Lengths (Header 12 + Payload + MAC 8)
    const val LEN_INPUT_KBM: Int = 46    // 12 + 26 + 8
    const val LEN_RUMBLE: Int = 22       // 12 + 2 + 8
    const val LEN_PING: Int = 28         // 12 + 8 + 8
    const val LEN_PONG: Int = 28         // 12 + 8 + 8
    const val LEN_HELLO: Int = 36        // 12 + 16 + 8
    const val LEN_HELLO_ACK: Int = 36    // 12 + 16 + 8
    const val LEN_BYE: Int = 20          // 12 + 0 + 8
    const val LEN_INPUT_PAD: Int = 32    // 12 + 12 + 8
    const val LEN_STATUS: Int = 21       // 12 + 1 + 8

    // Mouse Button Bitmasks (Offset 16 in input_kbm payload)
    const val MOUSE_NONE: UByte = 0x00u
    const val MOUSE_LMB: UByte = 0x01u
    const val MOUSE_RMB: UByte = 0x02u
    const val MOUSE_MMB: UByte = 0x04u
    const val MOUSE_X1: UByte = 0x08u
    const val MOUSE_X2: UByte = 0x10u

    // Status Flags Bitmasks (Offset 0 in status payload)
    const val STATUS_ARMED: UByte = 0x01u
    const val STATUS_TARGET_FOCUSED: UByte = 0x02u
    const val STATUS_KBM_AVAILABLE: UByte = 0x04u
    const val STATUS_GAMEPAD_AVAILABLE: UByte = 0x08u
}
