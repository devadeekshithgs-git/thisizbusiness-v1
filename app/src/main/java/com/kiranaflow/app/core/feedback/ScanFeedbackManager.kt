package com.kiranaflow.app.core.feedback

import android.content.Context
import android.media.AudioManager
import android.os.Vibrator
import com.kiranaflow.app.data.local.AppPrefs
import com.kiranaflow.app.data.local.AppPrefsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * Single entry point for successful scan feedback (haptic + beep).
 *
 * Why this design:
 * - Reusable across screens and future events (payment success, error buzz, etc.)
 * - No UI-thread blocking
 * - SoundPool is initialized once (via SoundController)
 * - Settings are cached from DataStore flow (no synchronous disk reads on scan)
 */
class ScanFeedbackManager(appContext: Context) {
    private val appContext = appContext.applicationContext

    private val prefsStore = AppPrefsStore(this.appContext)
    private val latestPrefs = AtomicReference(AppPrefs())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val haptics = HapticController(
        vibrator = runCatching { this.appContext.getSystemService(Vibrator::class.java) }.getOrNull()
    )

    private val sound = SoundController(
        appContext = this.appContext,
        audioManager = runCatching { this.appContext.getSystemService(AudioManager::class.java) }.getOrNull()
    )

    init {
        // Cache preference values in-memory to keep scan feedback ultra-low latency.
        scope.launch {
            prefsStore.prefs.collectLatest { latestPrefs.set(it) }
        }
    }

    /**
     * Public API. Call ONLY after:
     * - barcode decoded
     * - inventory lookup succeeded
     * - cart mutation succeeded (added / qty incremented)
     */
    fun onScanSuccess(context: Context) {
        // `context` param kept for ergonomic call-sites; internal uses applicationContext.
        val p = latestPrefs.get()

        if (p.scanVibrationEnabled) {
            runCatching { haptics.vibrateScanSuccess() }
        }
        if (p.scanBeepEnabled) {
            runCatching { sound.playScanSuccessBeep() }
        }
    }
}



