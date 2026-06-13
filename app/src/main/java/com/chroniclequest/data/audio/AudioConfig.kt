package com.chroniclequest.data.audio

import android.media.AudioFormat

/** PCM capture parameters required by the Gemini Live API (16-bit, 16 kHz, mono). */
object AudioConfig {
    const val SAMPLE_RATE = 16_000
    const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    /** ~100 ms of audio per emitted chunk (1600 samples). */
    const val SAMPLES_PER_CHUNK = SAMPLE_RATE / 10

    /**
     * RMS amplitude gate. We approximate loudness as 20·log10(rms) which yields a
     * ~0–90 scale for 16-bit PCM; voiced speech in a normal room comfortably
     * clears 45. Chunks below this are dropped locally to save bandwidth/tokens.
     */
    const val VAD_THRESHOLD_DB = 45.0
}
