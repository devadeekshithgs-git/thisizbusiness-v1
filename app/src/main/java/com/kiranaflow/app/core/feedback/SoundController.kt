package com.kiranaflow.app.core.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.annotation.RawRes
import com.kiranaflow.app.R

/**
 * Sound controller for POS-style scan beeps.
 *
 * Requirements implemented:
 * - Uses SoundPool (not MediaPlayer)
 * - Loads once (process scoped)
 * - Respects system ringer mode (plays only in RINGER_MODE_NORMAL)
 */
internal class SoundController(
    private val appContext: Context,
    private val audioManager: AudioManager?
) {
    @Volatile private var soundPool: SoundPool? = null
    @Volatile private var soundId: Int = 0
    @Volatile private var loaded: Boolean = false

    private val initLock = Any()

    private fun ensureInitialized(@RawRes resId: Int = R.raw.beep) {
        if (soundPool != null) return
        synchronized(initLock) {
            if (soundPool != null) return

            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val sp = SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(attrs)
                .build()

            sp.setOnLoadCompleteListener { _, sampleId, status ->
                if (sampleId == soundId && status == 0) loaded = true
            }

            soundPool = sp
            loaded = false
            soundId = sp.load(appContext, resId, 1)
        }
    }

    fun playScanSuccessBeep() {
        // Respect system ringer mode: do NOT beep in silent/vibrate modes.
        val am = audioManager ?: return
        if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return

        ensureInitialized()
        val sp = soundPool ?: return
        if (!loaded || soundId == 0) return

        // No looping, normal rate, max volume.
        sp.play(soundId, 1f, 1f, 1, 0, 1f)
    }
}



