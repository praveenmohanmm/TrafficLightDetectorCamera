package com.poodlesoft.trafficlightdetector

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock

/**
 * Plays an audible alert tone when a traffic pole is detected.
 * Uses a 2-second cooldown to avoid repeating the tone on every frame.
 */
class SoundAlertManager {

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, VOLUME_PERCENT)
    private var lastAlertAt: Long = 0L

    /**
     * Plays a short beep if the cooldown has elapsed since the last alert.
     */
    fun playAlert() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAlertAt >= COOLDOWN_MS) {
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, TONE_DURATION_MS)
            lastAlertAt = now
        }
    }

    fun release() {
        toneGenerator.release()
    }

    companion object {
        private const val VOLUME_PERCENT = 100
        private const val TONE_DURATION_MS = 600
        private const val COOLDOWN_MS = 2_000L
    }
}
