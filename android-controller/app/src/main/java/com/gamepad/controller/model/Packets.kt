package com.gamepad.controller.model

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Standard 12-byte header present on every protocol packet.
 */
data class PacketHeader(
    val magic: UShort = ProtocolConstants.MAGIC,
    val version: UByte = ProtocolConstants.VERSION,
    val type: UByte,
    val session: UInt,
    val seq: UInt
) {
    fun writeTo(buffer: ByteBuffer) {
        buffer.putShort(magic.toShort())
        buffer.put(version.toByte())
        buffer.put(type.toByte())
        buffer.putInt(session.toInt())
        buffer.putInt(seq.toInt())
    }

    companion object {
        fun readFrom(buffer: ByteBuffer): PacketHeader {
            val magic = buffer.short.toUShort()
            val version = buffer.get().toUByte()
            val type = buffer.get().toUByte()
            val session = buffer.int.toUInt()
            val seq = buffer.int.toUInt()
            return PacketHeader(magic, version, type, session, seq)
        }
    }
}

/**
 * Type 0: input_kbm datagram (46 bytes total).
 */
data class InputKbmPacket(
    val header: PacketHeader,
    val keys: ByteArray, // 16 bytes (128-bit key bitmap)
    val mouse: UByte,
    val flags: UByte = 0u,
    val lookX: Int,      // Cumulative int32
    val lookY: Int,      // Cumulative int32
    var mac: ByteArray = ByteArray(8)
) {
    init {
        require(keys.size == 16) { "keys bitmap must be exactly 16 bytes" }
        require(mac.size == 8) { "mac must be exactly 8 bytes" }
    }

    fun serialize(buffer: ByteBuffer = ByteBuffer.allocate(ProtocolConstants.LEN_INPUT_KBM)): ByteBuffer {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.clear()
        header.writeTo(buffer)
        buffer.put(keys)
        buffer.put(mouse.toByte())
        buffer.put(flags.toByte())
        buffer.putInt(lookX)
        buffer.putInt(lookY)
        buffer.put(mac)
        buffer.flip()
        return buffer
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as InputKbmPacket
        return header == other.header &&
                keys.contentEquals(other.keys) &&
                mouse == other.mouse &&
                flags == other.flags &&
                lookX == other.lookX &&
                lookY == other.lookY &&
                mac.contentEquals(other.mac)
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + keys.contentHashCode()
        result = 31 * result + mouse.hashCode()
        result = 31 * result + flags.hashCode()
        result = 31 * result + lookX
        result = 31 * result + lookY
        result = 31 * result + mac.contentHashCode()
        return result
    }
}

/**
 * Type 4: hello datagram (36 bytes total).
 */
data class HelloPacket(
    val header: PacketHeader,
    val clientNonce: ByteArray, // 16 bytes
    var mac: ByteArray = ByteArray(8)
) {
    init {
        require(clientNonce.size == 16) { "clientNonce must be 16 bytes" }
        require(mac.size == 8) { "mac must be 8 bytes" }
    }

    fun serialize(buffer: ByteBuffer = ByteBuffer.allocate(ProtocolConstants.LEN_HELLO)): ByteBuffer {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.clear()
        header.writeTo(buffer)
        buffer.put(clientNonce)
        buffer.put(mac)
        buffer.flip()
        return buffer
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HelloPacket
        return header == other.header &&
                clientNonce.contentEquals(other.clientNonce) &&
                mac.contentEquals(other.mac)
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + clientNonce.contentHashCode()
        result = 31 * result + mac.contentHashCode()
        return result
    }
}

/**
 * Type 5: hello_ack datagram (36 bytes total).
 */
data class HelloAckPacket(
    val header: PacketHeader,
    val serverNonce: ByteArray, // 16 bytes
    val mac: ByteArray          // 8 bytes
) {
    init {
        require(serverNonce.size == 16) { "serverNonce must be 16 bytes" }
        require(mac.size == 8) { "mac must be 8 bytes" }
    }

    companion object {
        fun deserialize(bytes: ByteArray): HelloAckPacket {
            require(bytes.size == ProtocolConstants.LEN_HELLO_ACK) { "Invalid hello_ack packet size" }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val header = PacketHeader.readFrom(buffer)
            val serverNonce = ByteArray(16)
            buffer.get(serverNonce)
            val mac = ByteArray(8)
            buffer.get(mac)
            return HelloAckPacket(header, serverNonce, mac)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HelloAckPacket
        return header == other.header &&
                serverNonce.contentEquals(other.serverNonce) &&
                mac.contentEquals(other.mac)
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + serverNonce.contentHashCode()
        result = 31 * result + mac.contentHashCode()
        return result
    }
}

/**
 * Type 6: bye datagram (20 bytes total).
 */
data class ByePacket(
    val header: PacketHeader,
    var mac: ByteArray = ByteArray(8)
) {
    fun serialize(buffer: ByteBuffer = ByteBuffer.allocate(ProtocolConstants.LEN_BYE)): ByteBuffer {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.clear()
        header.writeTo(buffer)
        buffer.put(mac)
        buffer.flip()
        return buffer
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ByePacket
        return header == other.header && mac.contentEquals(other.mac)
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + mac.contentHashCode()
        return result
    }
}

/**
 * Type 8: status datagram (21 bytes total).
 */
data class StatusPacket(
    val header: PacketHeader,
    val flags: UByte,
    val mac: ByteArray
) {
    val isArmed: Boolean get() = (flags and ProtocolConstants.STATUS_ARMED) != 0u.toUByte()
    val isTargetFocused: Boolean get() = (flags and ProtocolConstants.STATUS_TARGET_FOCUSED) != 0u.toUByte()
    val isKbmAvailable: Boolean get() = (flags and ProtocolConstants.STATUS_KBM_AVAILABLE) != 0u.toUByte()
    val isGamepadAvailable: Boolean get() = (flags and ProtocolConstants.STATUS_GAMEPAD_AVAILABLE) != 0u.toUByte()

    companion object {
        fun deserialize(bytes: ByteArray): StatusPacket {
            require(bytes.size == ProtocolConstants.LEN_STATUS) { "Invalid status packet size" }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val header = PacketHeader.readFrom(buffer)
            val flags = buffer.get().toUByte()
            val mac = ByteArray(8)
            buffer.get(mac)
            return StatusPacket(header, flags, mac)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as StatusPacket
        return header == other.header && flags == other.flags && mac.contentEquals(other.mac)
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + flags.hashCode()
        result = 31 * result + mac.contentHashCode()
        return result
    }
}

/**
 * Wraparound-safe sequence number comparison per RFC 1982.
 */
fun isSequenceNewer(candidateSeq: UInt, referenceSeq: UInt): Boolean {
    return (candidateSeq.toInt() - referenceSeq.toInt()) > 0
}
