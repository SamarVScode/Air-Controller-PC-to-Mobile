package com.gamepad.controller.network

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * High-performance HMAC-SHA256 authentication engine conforming to PLAN.md Rev 4 Section 9.
 */
object HmacSigner {
    private const val ALGORITHM = "HmacSHA256"

    /**
     * Calculates the 8-byte truncated HMAC-SHA256 tag over the supplied buffer slice.
     */
    fun calculateMac(key: ByteArray, data: ByteArray, offset: Int = 0, length: Int = data.size): ByteArray {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(key, ALGORITHM))
        mac.update(data, offset, length)
        val fullHash = mac.doFinal()
        return fullHash.copyOf(8)
    }

    /**
     * Calculates hello_ack MAC binding: HMAC-SHA256(key, Header[12] || ServerNonce[16] || ClientNonce[16])[0..7].
     */
    fun calculateHelloAckMac(
        key: ByteArray,
        headerBytes: ByteArray,
        serverNonce: ByteArray,
        clientNonce: ByteArray
    ): ByteArray {
        require(headerBytes.size == 12)
        require(serverNonce.size == 16)
        require(clientNonce.size == 16)

        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(key, ALGORITHM))
        mac.update(headerBytes)
        mac.update(serverNonce)
        mac.update(clientNonce)
        return mac.doFinal().copyOf(8)
    }

    /**
     * Derives a 32-bit session ID: HMAC-SHA256(key, clientNonce || serverNonce)[0..3] Little-Endian.
     */
    fun deriveSessionId(key: ByteArray, clientNonce: ByteArray, serverNonce: ByteArray): UInt {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(key, ALGORITHM))
        mac.update(clientNonce)
        mac.update(serverNonce)
        val hash = mac.doFinal()
        val buffer = ByteBuffer.wrap(hash, 0, 4).order(ByteOrder.LITTLE_ENDIAN)
        return buffer.int.toUInt()
    }

    /**
     * Constant-time MAC equality check to prevent timing analysis attacks.
     */
    fun verifyMac(expectedMac: ByteArray, candidateMac: ByteArray): Boolean {
        return MessageDigest.isEqual(expectedMac, candidateMac)
    }
}
