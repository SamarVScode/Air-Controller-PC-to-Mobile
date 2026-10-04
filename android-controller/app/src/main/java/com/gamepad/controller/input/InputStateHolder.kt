package com.gamepad.controller.input

import com.gamepad.controller.data.KeyId
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread-safe controller input state holder with edge-latching mechanics.
 * Guarantees key taps are sent in at least 2 consecutive frames at 120Hz.
 */
class InputStateHolder {

    private val keyState = ByteArray(16)
    private val edgeLatches = IntArray(128)
    private val mouseState = AtomicInteger(0)

    @Synchronized
    fun setKey(keyId: Int, isDown: Boolean) {
        if (keyId !in 0..127) return
        val byteIndex = keyId / 8
        val bitIndex = keyId % 8

        if (isDown) {
            keyState[byteIndex] = (keyState[byteIndex].toInt() or (1 shl bitIndex)).toByte()
            edgeLatches[keyId] = 2 // Latch active for 2 frames
        } else {
            keyState[byteIndex] = (keyState[byteIndex].toInt() and (1 shl bitIndex).inv()).toByte()
        }
    }

    fun setMouseButton(buttonMask: Int, isDown: Boolean) {
        mouseState.updateAndGet { current ->
            if (isDown) current or buttonMask else current and buttonMask.inv()
        }
    }

    fun setStickDirection(normX: Float, normY: Float, isSprint: Boolean) {
        val deadzone = 0.35f
        setKey(KeyId.W, normY < -deadzone)
        setKey(KeyId.S, normY > deadzone)
        setKey(KeyId.A, normX < -deadzone)
        setKey(KeyId.D, normX > deadzone)
        if (isSprint) {
            setKey(KeyId.LEFT_SHIFT, true)
        }
    }

    @Synchronized
    fun snapshotKeys(destination: ByteArray) {
        System.arraycopy(keyState, 0, destination, 0, 16)
        // Apply and decrement edge latches
        for (i in 0 until 128) {
            if (edgeLatches[i] > 0) {
                destination[i / 8] = (destination[i / 8].toInt() or (1 shl (i % 8))).toByte()
                edgeLatches[i]--
            }
        }
    }

    fun getMouseButtonState(): Byte = mouseState.get().toByte()

    @Synchronized
    fun neutralize() {
        for (i in 0 until 16) keyState[i] = 0
        for (i in 0 until 128) edgeLatches[i] = 0
        mouseState.set(0)
    }
}
