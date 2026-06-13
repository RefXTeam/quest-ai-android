package com.chroniclequest.data.audio

import kotlin.math.log10
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local Root-Mean-Square based voice-activity gate. Keeps silent chunks off the
 * wire so we don't burn Gemini tokens / bandwidth on ambient quiet.
 */
@Singleton
class VadProcessor @Inject constructor() {

    /** Approximate loudness of a PCM chunk on a ~0–90 scale (see [AudioConfig]). */
    fun amplitudeDb(chunk: ShortArray): Double {
        if (chunk.isEmpty()) return 0.0
        var sumSquares = 0.0
        for (sample in chunk) {
            val s = sample.toDouble()
            sumSquares += s * s
        }
        val rms = sqrt(sumSquares / chunk.size)
        if (rms <= 0.0) return 0.0
        return 20.0 * log10(rms)
    }

    fun isVoiced(chunk: ShortArray, thresholdDb: Double = AudioConfig.VAD_THRESHOLD_DB): Boolean =
        amplitudeDb(chunk) >= thresholdDb
}
