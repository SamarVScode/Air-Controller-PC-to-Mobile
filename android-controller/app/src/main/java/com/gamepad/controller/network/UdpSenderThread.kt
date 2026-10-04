package com.gamepad.controller.network

import com.gamepad.controller.data.ConnectionStatus
import com.gamepad.controller.input.InputStateHolder
import com.gamepad.controller.input.LookAccumulator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport

/**
 * Dedicated 120 Hz UDP Sender Thread with precision microsecond timing.
 * Transmits Packet Type 0 (`input_kbm`) at 8.33 ms intervals.
 */
class UdpSenderThread(
    private val targetHost: String,
    private val targetPort: Int,
    private val sessionId: Long,
    private val psk: ByteArray,
    private val inputStateHolder: InputStateHolder,
    private val lookAccumulator: LookAccumulator
) : Thread("UdpSenderThread") {

    private val isRunning = AtomicBoolean(true)
    private var sequenceNumber = 1L
    private val keyBuffer = ByteArray(16)

    private val _connectionStatusFlow = MutableStateFlow(ConnectionStatus.CONNECTED)
    val connectionStatusFlow = _connectionStatusFlow.asStateFlow()

    private val _rttMsFlow = MutableStateFlow(0L)
    val rttMsFlow = _rttMsFlow.asStateFlow()

    override fun run() {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            val targetAddress = InetAddress.getByName(targetHost)

            val framePeriodNanos = 8_333_333L // ~120 Hz
            var nextTick = System.nanoTime()

            val packetBuffer = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN)
            val packetBytes = packetBuffer.array()
            val datagram = DatagramPacket(packetBytes, 46, targetAddress, targetPort)

            while (isRunning.get()) {
                val now = System.nanoTime()
                if (now < nextTick) {
                    LockSupport.parkNanos(nextTick - now)
                    continue
                }
                nextTick += framePeriodNanos

                packetBuffer.clear()

                // Header (12 bytes)
                packetBuffer.putShort(0x5047.toShort()) // Magic 'GP'
                packetBuffer.put(1.toByte())           // Version
                packetBuffer.put(0.toByte())           // Type 0: input_kbm
                packetBuffer.putInt(sessionId.toInt())
                packetBuffer.putInt((sequenceNumber++ and 0xFFFFFFFFL).toInt())

                // Payload (26 bytes)
                inputStateHolder.snapshotKeys(keyBuffer)
                packetBuffer.put(keyBuffer)
                packetBuffer.put(inputStateHolder.getMouseButtonState())
                packetBuffer.put(0.toByte())           // Flags (reserved)
                packetBuffer.putInt(lookAccumulator.getCumulativeX())
                packetBuffer.putInt(lookAccumulator.getCumulativeY())

                // Truncated HMAC-SHA256 (8 bytes) over Header + Payload
                val mac = CryptoUtils.computeTruncatedMac(psk, packetBytes.copyOfRange(0, 38))
                packetBuffer.put(mac)

                datagram.setData(packetBytes, 0, 46)
                socket.send(datagram)
            }

            // On exit, transmit bye packet (Type 6, 20 bytes)
            sendBye(socket, targetAddress)
        } catch (_: Exception) {
            _connectionStatusFlow.value = ConnectionStatus.DISCONNECTED
        } finally {
            socket?.close()
        }
    }

    fun sendNeutralState() {
        inputStateHolder.neutralize()
    }

    fun stopSending() {
        isRunning.set(false)
        interrupt()
    }

    private fun sendBye(socket: DatagramSocket, targetAddress: InetAddress) {
        try {
            val byeBuffer = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
            byeBuffer.putShort(0x5047.toShort())
            byeBuffer.put(1.toByte())
            byeBuffer.put(6.toByte()) // Type 6: bye
            byeBuffer.putInt(sessionId.toInt())
            byeBuffer.putInt((sequenceNumber++ and 0xFFFFFFFFL).toInt())

            val mac = CryptoUtils.computeTruncatedMac(psk, byeBuffer.array().copyOfRange(0, 12))
            byeBuffer.put(mac)

            val packet = DatagramPacket(byeBuffer.array(), 20, targetAddress, targetPort)
            socket.send(packet)
        } catch (_: Exception) {
        }
    }
}
