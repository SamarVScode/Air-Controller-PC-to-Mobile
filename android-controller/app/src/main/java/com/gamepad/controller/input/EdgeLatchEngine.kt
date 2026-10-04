package com.gamepad.controller.input

import com.gamepad.controller.model.KeyBitmap
import com.gamepad.controller.model.ProtocolConstants

/**
 * Edge latch engine conforming to PLAN.md Rev 4 Section 12:
 * "A key or button press that starts and ends between two send ticks MUST still be sent
 * as pressed in at least 2 consecutive packets, so taps are never dropped by the latest-state model."
 */
class EdgeLatchEngine {
    private val lock = Any()

    // Key states
    private val physicallyDownKeys = BooleanArray(128)
    private val keyLatchRemaining = IntArray(128)

    // Mouse states: index 0: LMB, 1: RMB, 2: MMB, 3: X1, 4: X2
    private val physicallyDownMouse = BooleanArray(5)
    private val mouseLatchRemaining = IntArray(5)

    fun onKeyPressed(keyId: Int, isDown: Boolean) {
        if (keyId !in 0..127) return
        synchronized(lock) {
            physicallyDownKeys[keyId] = isDown
            if (isDown) {
                keyLatchRemaining[keyId] = 2 // Latched for at least 2 ticks
            }
        }
    }

    fun onMouseButtonPressed(buttonMask: UByte, isDown: Boolean) {
        val index = when (buttonMask) {
            ProtocolConstants.MOUSE_LMB -> 0
            ProtocolConstants.MOUSE_RMB -> 1
            ProtocolConstants.MOUSE_MMB -> 2
            ProtocolConstants.MOUSE_X1 -> 3
            ProtocolConstants.MOUSE_X2 -> 4
            else -> return
        }
        synchronized(lock) {
            physicallyDownMouse[index] = isDown
            if (isDown) {
                mouseLatchRemaining[index] = 2 // Latched for at least 2 ticks
            }
        }
    }

    fun getLatchedKeys(outKeyBytes: ByteArray) {
        require(outKeyBytes.size >= 16)
        outKeyBytes.fill(0)
        synchronized(lock) {
            for (keyId in 0..127) {
                val isLatched = keyLatchRemaining[keyId] > 0 || physicallyDownKeys[keyId]
                if (isLatched) {
                    val byteIndex = keyId / 8
                    val bitIndex = keyId % 8
                    outKeyBytes[byteIndex] = (outKeyBytes[byteIndex].toInt() or (1 shl bitIndex)).toByte()
                }
            }
        }
    }

    fun getLatchedMouse(): UByte {
        var mask = 0
        synchronized(lock) {
            if (mouseLatchRemaining[0] > 0 || physicallyDownMouse[0]) mask = mask or ProtocolConstants.MOUSE_LMB.toInt()
            if (mouseLatchRemaining[1] > 0 || physicallyDownMouse[1]) mask = mask or ProtocolConstants.MOUSE_RMB.toInt()
            if (mouseLatchRemaining[2] > 0 || physicallyDownMouse[2]) mask = mask or ProtocolConstants.MOUSE_MMB.toInt()
            if (mouseLatchRemaining[3] > 0 || physicallyDownMouse[3]) mask = mask or ProtocolConstants.MOUSE_X1.toInt()
            if (mouseLatchRemaining[4] > 0 || physicallyDownMouse[4]) mask = mask or ProtocolConstants.MOUSE_X2.toInt()
        }
        return mask.toUByte()
    }

    fun hasActiveInputs(): Boolean {
        synchronized(lock) {
            for (i in 0..127) {
                if (physicallyDownKeys[i] || keyLatchRemaining[i] > 0) return true
            }
            for (i in 0..4) {
                if (physicallyDownMouse[i] || mouseLatchRemaining[i] > 0) return true
            }
        }
        return false
    }

    /**
     * Decrements latch count after each 120 Hz packet transmission.
     */
    fun onTickSent() {
        synchronized(lock) {
            for (i in 0..127) {
                if (!physicallyDownKeys[i] && keyLatchRemaining[i] > 0) {
                    keyLatchRemaining[i]--
                }
            }
            for (i in 0..4) {
                if (!physicallyDownMouse[i] && mouseLatchRemaining[i] > 0) {
                    mouseLatchRemaining[i]--
                }
            }
        }
    }

    fun clearAll() {
        synchronized(lock) {
            physicallyDownKeys.fill(false)
            keyLatchRemaining.fill(0)
            physicallyDownMouse.fill(false)
            mouseLatchRemaining.fill(0)
        }
    }
}
