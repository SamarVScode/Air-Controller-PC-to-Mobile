package com.gamepad.controller.model

import kotlinx.serialization.Serializable

@Serializable
data class PairingPayload(
    val ips: List<String>,
    val port: Int = ProtocolConstants.DEFAULT_PORT,
    val key: String, // 64-char Hex encoding of 32-byte secret
    val name: String // Hostname of Windows receiver
) {
    fun getSecretBytes(): ByteArray {
        require(key.length == 64) { "Pairing key must be a 64-character hex string" }
        return key.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
