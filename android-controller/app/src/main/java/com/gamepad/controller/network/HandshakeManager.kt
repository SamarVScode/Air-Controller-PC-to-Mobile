package com.gamepad.controller.network

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom

object HandshakeManager {

    private val random = SecureRandom()

    fun performHandshake(targetHost: String, targetPort: Int, psk: ByteArray): Result<Long> {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            socket.soTimeout = 3000

            val clientNonce = ByteArray(16)
            random.nextBytes(clientNonce)

            // 1. Build Type 4 `hello` packet (36 bytes)
            val helloBuffer = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN)
            helloBuffer.putShort(0x5047.toShort()) // Magic 'GP'
            helloBuffer.put(1.toByte())           // Version
            helloBuffer.put(4.toByte())           // Type 4: hello
            helloBuffer.putInt(0)                 // Session 0
            helloBuffer.putInt(0)                 // Seq 0
            helloBuffer.put(clientNonce)

            val helloBody = ByteArray(28)
            System.arraycopy(helloBuffer.array(), 0, helloBody, 0, 28)
            val helloMac = CryptoUtils.computeTruncatedMac(psk, helloBody)
            helloBuffer.put(helloMac)

            val address = InetAddress.getByName(targetHost)
            val sendPacket = DatagramPacket(helloBuffer.array(), 36, address, targetPort)
            socket.send(sendPacket)

            // 2. Await Type 5 `hello_ack` packet (36 bytes)
            val ackData = ByteArray(36)
            val receivePacket = DatagramPacket(ackData, 36)
            socket.receive(receivePacket)

            val ackBuffer = ByteBuffer.wrap(ackData).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ackBuffer.short
            val version = ackBuffer.get()
            val type = ackBuffer.get()
            val session = ackBuffer.int
            val seq = ackBuffer.int

            if (magic != 0x5047.toShort() || version != 1.toByte() || type != 5.toByte()) {
                return Result.failure(IllegalStateException("Invalid hello_ack header"))
            }

            val serverNonce = ByteArray(16)
            ackBuffer.get(serverNonce)
            val receivedMac = ByteArray(8)
            ackBuffer.get(receivedMac)

            // Verify MAC calculated over: Header[12] || ServerNonce[16] || ClientNonce[16]
            val macData = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            macData.put(ackData, 0, 12)
            macData.put(serverNonce)
            macData.put(clientNonce)

            val expectedMac = CryptoUtils.computeTruncatedMac(psk, macData.array())
            if (!MessageDigest.isEqual(receivedMac, expectedMac)) {
                return Result.failure(SecurityException("Invalid hello_ack HMAC signature"))
            }

            // 3. Derive 4-byte Session ID: HMAC-SHA256(Key, ClientNonce || ServerNonce)[0..3]
            val nonceCombined = ByteArray(32)
            System.arraycopy(clientNonce, 0, nonceCombined, 0, 16)
            System.arraycopy(serverNonce, 0, nonceCombined, 16, 16)
            val sessionDigest = CryptoUtils.computeHmacSha256(psk, nonceCombined)
            val sessionIdBuffer = ByteBuffer.wrap(sessionDigest).order(ByteOrder.LITTLE_ENDIAN)
            val derivedSessionId = sessionIdBuffer.int.toLong() and 0xFFFFFFFFL

            Result.success(derivedSessionId)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            socket?.close()
        }
    }

    fun hexToBytes(hex: String): ByteArray {
        val s = hex.trim()
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
