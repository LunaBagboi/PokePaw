package com.bagboi.pokepaw

import android.hardware.Sensor
import android.util.Log
import kotlin.math.sqrt

/**
 * Simple sensor-fusion wrapper around the baseline step detector.
 *
 * It never creates steps on its own – it only accepts/rejects baseline
 * step events using additional motion sensors:
 * - TYPE_LINEAR_ACCELERATION (stronger translation / impact signal)
 * - TYPE_GYROSCOPE (strong rotation / shaking)
 *
 * Design goals:
 * - If extra sensors are missing or quiet, behave like baseline.
 * - If they are active, use them to filter out obvious false positives.
 */
class StepFusionFilter(
    private val minFusedIntervalNanos: Long = 300_000_000L, // 300 ms
    private val linearActivationThreshold: Float = 0.04f,    // g, to mark sensor as active
    private val linearStepThreshold: Float = 0.06f,          // g, minimum envelope for a valid step
    private val gyroActivationThreshold: Float = 0.5f,       // rad/s, to mark gyro as active
    private val gyroHighThreshold: Float = 2.0f,             // rad/s, strong rotation
    private val envAlpha: Float = 0.3f,                      // envelope smoothing for both channels
    private val idleThresholdNanos: Long = 2_000_000_000L,   // 2 s without fused steps => treat as idle
    private val minStrideNanos: Long = 300_000_000L,         // 300 ms
    private val maxStrideNanos: Long = 2_000_000_000L        // 2 s
) {
    interface Listener {
        fun onFusedStep(timestampNanos: Long)
    }

    var listener: Listener? = null

    private var lastFusedStepTimestamp: Long = 0L

    // Used to gate the start of walking after a long idle. We require at least
    // two consistent baseline steps before we emit any fused steps, which helps
    // ignore single pulses from picking up/putting down the phone.
    private var pendingStartStepTimestamp: Long? = null

    // Envelopes and activity flags for sensors
    private var linearEnv: Float = 0f
    private var linearActive: Boolean = false

    private var gyroEnv: Float = 0f
    private var gyroActive: Boolean = false

    fun onLinearAccelSample(ax: Float, ay: Float, az: Float, timestampNanos: Long) {
        val mag = sqrt(ax * ax + ay * ay + az * az)
        updateEnv(isLinear = true, value = mag)

        if (!linearActive && mag > linearActivationThreshold) {
            linearActive = true
            Log.d("StepFusionFilter", "Linear acceleration sensor marked active (mag=${"%.3f".format(mag)})")
        }
    }

    fun onGyroSample(gx: Float, gy: Float, gz: Float, timestampNanos: Long) {
        val mag = sqrt(gx * gx + gy * gy + gz * gz)
        updateEnv(isLinear = false, value = mag)

        if (!gyroActive && mag > gyroActivationThreshold) {
            gyroActive = true
            Log.d("StepFusionFilter", "Gyro sensor marked active (mag=${"%.3f".format(mag)})")
        }
    }

    /**
     * Called whenever the baseline detector reports a step candidate.
     * We decide whether to accept it as a fused step.
     */
    fun onBaselineStep(timestampNanos: Long) {
        val dtFromLastFused = timestampNanos - lastFusedStepTimestamp

        // If linear sensor is active, require some minimum envelope
        if (linearActive && linearEnv < linearStepThreshold) {
            Log.d(
                "StepFusionFilter",
                "REJECT (linear too low): linearEnv=${"%.3f".format(linearEnv)}, thr=$linearStepThreshold"
            )
            return
        }

        // If gyro is very active and this step is very soon after the last fused one,
        // treat it as likely double-count from shaking.
        if (gyroActive && dtFromLastFused < minFusedIntervalNanos && gyroEnv > gyroHighThreshold) {
            Log.d(
                "StepFusionFilter",
                "REJECT (gyro double-hit): gyroEnv=${"%.3f".format(gyroEnv)}, dt=${dtFromLastFused / 1_000_000}ms"
            )
            return
        }

        // Idle/start gate: after a long idle (or at app start), do not emit fused
        // steps until we see at least two baseline steps with a plausible stride.
        val longIdle = lastFusedStepTimestamp == 0L || dtFromLastFused > idleThresholdNanos
        if (longIdle) {
            val pending = pendingStartStepTimestamp
            if (pending == null) {
                pendingStartStepTimestamp = timestampNanos
                Log.d("StepFusionFilter", "PENDING start step at $timestampNanos")
                return
            } else {
                val dtPending = timestampNanos - pending
                if (dtPending in minStrideNanos..maxStrideNanos) {
                    // Accept both pending and current as the start of a walk
                    pendingStartStepTimestamp = null
                    lastFusedStepTimestamp = timestampNanos
                    Log.d(
                        "StepFusionFilter",
                        "ACCEPT START: dt=${dtPending / 1_000_000}ms, " +
                            "linearEnv=${"%.3f".format(linearEnv)}, gyroEnv=${"%.3f".format(gyroEnv)}"
                    )
                    listener?.onFusedStep(pending)
                    listener?.onFusedStep(timestampNanos)
                    return
                } else {
                    // Replace pending and keep waiting for a second consistent step
                    pendingStartStepTimestamp = timestampNanos
                    Log.d(
                        "StepFusionFilter",
                        "REPLACE pending start step, dt=${dtPending / 1_000_000}ms"
                    )
                    return
                }
            }
        }

        // Normal walking: accept this baseline step
        pendingStartStepTimestamp = null
        lastFusedStepTimestamp = timestampNanos
        Log.d(
            "StepFusionFilter",
            "ACCEPT: linearEnv=${"%.3f".format(linearEnv)}, gyroEnv=${"%.3f".format(gyroEnv)}, dt=${dtFromLastFused / 1_000_000}ms"
        )
        listener?.onFusedStep(timestampNanos)
    }

    private fun updateEnv(isLinear: Boolean, value: Float) {
        if (isLinear) {
            val filtered = linearEnv * (1f - envAlpha) + value * envAlpha
            // Mild decay
            linearEnv = if (filtered > linearEnv) filtered else linearEnv * 0.98f
        } else {
            val filtered = gyroEnv * (1f - envAlpha) + value * envAlpha
            gyroEnv = if (filtered > gyroEnv) filtered else gyroEnv * 0.98f
        }
    }
}
