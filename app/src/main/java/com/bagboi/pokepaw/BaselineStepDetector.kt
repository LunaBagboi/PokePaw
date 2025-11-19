package com.bagboi.pokepaw

import kotlin.math.sqrt

/**
 * Baseline, real-time accelerometer-only step detector.
 *
 * Responsibilities:
 * - Accept individual acceleration samples (x, y, z, timestampNanos).
 * - Compute magnitude and apply simple smoothing.
 * - Apply fixed threshold + refractory period to detect steps.
 * - Notify a listener when a step is detected.
 *
 * This is intentionally simple so we can iterate toward the
 * envelope-based and sensor-fusion versions later.
 */
class BaselineStepDetector(
    // Balanced defaults: still sensitive, but less likely to double-count peaks.
    private val threshold: Float = 1.08f,                   // Threshold on smoothed magnitude above ~1 g
    private val minStepIntervalNanos: Long = 250_000_000L,  // 250 ms
    private val smoothingWindow: Int = 4                    // Simple moving average window size
) {
    interface Listener {
        fun onStep(timestampNanos: Long)
    }

    var listener: Listener? = null

    private val recentMagnitudes = FloatArray(smoothingWindow.coerceAtLeast(1))
    private var recentIndex = 0
    private var filledCount = 0

    private var lastStepTimestamp: Long = 0L
    private var wasAboveThreshold = false

    /**
     * Feed one accelerometer sample (in g units if possible).
     */
    fun addSample(ax: Float, ay: Float, az: Float, timestampNanos: Long) {
        val magnitude = sqrt(ax * ax + ay * ay + az * az)

        // Simple moving average smoothing
        recentMagnitudes[recentIndex] = magnitude
        recentIndex = (recentIndex + 1) % recentMagnitudes.size
        if (filledCount < recentMagnitudes.size) {
            filledCount++
        }

        val smoothed = if (filledCount == 0) {
            magnitude
        } else {
            var sum = 0f
            for (i in 0 until filledCount) {
                sum += recentMagnitudes[i]
            }
            sum / filledCount
        }

        val nowAbove = smoothed > threshold
        val timeSinceLastStep = timestampNanos - lastStepTimestamp

        // Detect a rising edge over the threshold with a refractory period
        if (!wasAboveThreshold && nowAbove) {
            if (timeSinceLastStep > minStepIntervalNanos) {
                lastStepTimestamp = timestampNanos
                listener?.onStep(timestampNanos)
            }
        }

        wasAboveThreshold = nowAbove
    }
}
