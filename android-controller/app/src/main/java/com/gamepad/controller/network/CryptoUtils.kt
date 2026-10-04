package com.gamepad.controller.network

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {

    fun computeHmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    fun computeTruncatedMac(key: ByteArray, data: ByteArray): ByteArray {
        val full = computeHmacSha256(key, data)
        val truncated = ByteArray(8)
        System.arraycopy(full, 0, truncated, 0, 8)
        return truncated
    }
}
