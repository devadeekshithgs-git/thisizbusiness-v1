package com.kiranaflow.app.core.feedback

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.VisibleForTesting

/**
 * Haptic controller for POS-style scan confirmations.
 *
 * Design goals:
 * - Very short pulse (30–50ms) for professional feel.
 * - Works on Android 8+ and degrades gracefully on older APIs.
 * - No UI-thread blocking (API calls are fast).
 */
class HapticController(
    private val vibrator: Vibrator?
) {
    @VisibleForTesting
    internal val durationMs: Long = 40L

    fun vibrateScanSuccess() {
        // If the device has no vibrator (or service unavailable), do nothing.
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // DEFAULT_AMPLITUDE keeps it consistent with platform expectations.
            v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(durationMs)
        }
    }
}



