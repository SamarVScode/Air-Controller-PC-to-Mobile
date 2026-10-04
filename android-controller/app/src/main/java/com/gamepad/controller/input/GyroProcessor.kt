package com.gamepad.controller.input

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import kotlin.math.abs

/**
 * High-speed 200 Hz Gyroscope processor feeding the cumulative LookAccumulator.
 * Implements display axis remapping, rolling-average bias calibration, LPF, and deadzone.
 */
class GyroProcessor(
    private val lookAccumulator: LookAccumulator
) : SensorEventListener {

    var isEnabled: Boolean = false
    var sensitivity: Float = 1.0f
    var adsMultiplier: Float = 0.6f
    var isAdsActive: Boolean = false
    var deadzoneRadPerSec: Float = 0.015f
    var invertX: Boolean = false
    var invertY: Boolean = false
    var displayRotation: Int = Surface.ROTATION_90

    // Bias calibration
    private var biasX = 0.0f
    private var biasY = 0.0f
    private var biasZ = 0.0f
    private var calibrationSamples = 0
    private var isCalibrating = false

    // Low-Pass Filter state (alpha ~ 0.8)
    private val lpfAlpha = 0.8f
    private var filteredRateX = 0.0f
    private var filteredRateY = 0.0f

    private var lastTimestampNs: Long = 0L
    private val countsPerRadian = 800.0f // Tuned baseline counts per radian

    fun startCalibration() {
        biasX = 0.0f
        biasY = 0.0f
        biasZ = 0.0f
        calibrationSamples = 0
        isCalibrating = true
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_GYROSCOPE) return
        val currentNs = event.timestamp
        if (lastTimestampNs == 0L) {
            lastTimestampNs = currentNs
            return
        }

        val dt = (currentNs - lastTimestampNs) * 1e-9f // seconds
        lastTimestampNs = currentNs
        if (dt <= 0.0f || dt > 0.05f) return // Guard against large timestamp leaps

        val rawX = event.values[0]
        val rawY = event.values[1]
        val rawZ = event.values[2]

        if (isCalibrating) {
            biasX += rawX
            biasY += rawY
            biasZ += rawZ
            calibrationSamples++
            if (calibrationSamples >= 200) { // ~1 second of samples
                biasX /= calibrationSamples
                biasY /= calibrationSamples
                biasZ /= calibrationSamples
                isCalibrating = false
            }
            return
        }

        if (!isEnabled) return

        // Subtract calibrated bias
        val zeroedX = rawX - biasX
        val zeroedY = rawY - biasY
        val zeroedZ = rawZ - biasZ

        // Remap device axes according to landscape display rotation
        // In landscape: Phone yaw (Z) turns camera horizontally; Phone pitch (X) turns vertically.
        val (lookRateX, lookRateY) = if (displayRotation == Surface.ROTATION_270) {
            Pair(zeroedZ, zeroedX)
        } else {
            // Default ROTATION_90
            Pair(-zeroedZ, -zeroedX)
        }

        // Apply Deadzone
        val deadzonedX = if (abs(lookRateX) < deadzoneRadPerSec) 0.0f else lookRateX
        val deadzonedY = if (abs(lookRateY) < deadzoneRadPerSec) 0.0f else lookRateY

        // Low-Pass Filter
        filteredRateX = lpfAlpha * filteredRateX + (1.0f - lpfAlpha) * deadzonedX
        filteredRateY = lpfAlpha * filteredRateY + (1.0f - lpfAlpha) * deadzonedY

        val effectiveSens = sensitivity * (if (isAdsActive) adsMultiplier else 1.0f)
        val signX = if (invertX) -1.0f else 1.0f
        val signY = if (invertY) -1.0f else 1.0f

        val deltaCountsX = filteredRateX * dt * countsPerRadian * effectiveSens * signX
        val deltaCountsY = filteredRateY * dt * countsPerRadian * effectiveSens * signY

        lookAccumulator.addGyroDelta(deltaCountsX, deltaCountsY)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
