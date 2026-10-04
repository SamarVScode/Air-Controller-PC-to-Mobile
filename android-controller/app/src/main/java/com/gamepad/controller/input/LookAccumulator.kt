package com.gamepad.controller.input

import java.util.concurrent.atomic.AtomicInteger

data class LookSnapshot(val lookX: Int, val lookY: Int)

/**
 * Cumulative 32-bit running look accumulator conforming to PLAN.md Rev 4 Section 9.3 & 13.
 * Wraps around naturally without losing movement on dropped packets.
 * Maintains sub-pixel fractional remainders.
 */
class LookAccumulator {
    private val lookX = AtomicInteger(0)
    private val lookY = AtomicInteger(0)

    private var fractionalX: Float = 0.0f
    private var fractionalY: Float = 0.0f
    private val lock = Any()

    /**
     * Ingests physical screen touch delta (pixels/dp) and converts to mouse counts.
     */
    fun addTouchDelta(deltaPxX: Float, deltaPxY: Float, sensitivity: Float, isAds: Boolean, adsMultiplier: Float) {
        val effectiveSens = sensitivity * (if (isAds) adsMultiplier else 1.0f)
        val rawCountsX = deltaPxX * effectiveSens
        val rawCountsY = deltaPxY * effectiveSens

        synchronized(lock) {
            val totalX = rawCountsX + fractionalX
            val totalY = rawCountsY + fractionalY

            val intCountsX = totalX.toInt()
            val intCountsY = totalY.toInt()

            fractionalX = totalX - intCountsX
            fractionalY = totalY - intCountsY

            lookX.addAndGet(intCountsX)
            lookY.addAndGet(intCountsY)
        }
    }

    /**
     * Ingests gyroscope angular rate counts.
     */
    fun addGyroDelta(countsX: Float, countsY: Float) {
        synchronized(lock) {
            val totalX = countsX + fractionalX
            val totalY = countsY + fractionalY

            val intCountsX = totalX.toInt()
            val intCountsY = totalY.toInt()

            fractionalX = totalX - intCountsX
            fractionalY = totalY - intCountsY

            lookX.addAndGet(intCountsX)
            lookY.addAndGet(intCountsY)
        }
    }


    fun getCumulativeX(): Int = lookX.get()
    fun getCumulativeY(): Int = lookY.get()

    fun addDeltas(dx: Float, dy: Float) {
        addTouchDelta(dx, dy, 1.0f, false, 1.0f)
    }

    fun getSnapshot(): LookSnapshot {
        return LookSnapshot(lookX.get(), lookY.get())
    }

    fun reset() {
        synchronized(lock) {
            lookX.set(0)
            lookY.set(0)
            fractionalX = 0.0f
            fractionalY = 0.0f
        }
    }
}
