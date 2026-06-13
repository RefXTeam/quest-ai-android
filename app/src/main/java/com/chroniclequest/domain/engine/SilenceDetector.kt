package com.chroniclequest.domain.engine

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks conversational turn boundaries. The pipeline reports voiced vs silent
 * chunks; after [silenceWindowMs] of continuous silence following speech, the
 * turn is considered complete and a final quest evaluation may run. This prevents
 * interrupting ongoing speech mid-thought.
 *
 * Time is injected via [now] so the detector is deterministic and testable.
 */
@Singleton
class SilenceDetector @Inject constructor() {

    private var lastVoiceAt: Long = 0
    private var hadVoiceSinceReset: Boolean = false
    private var turnAlreadyClosed: Boolean = false

    fun onVoiced(now: Long) {
        lastVoiceAt = now
        hadVoiceSinceReset = true
        turnAlreadyClosed = false
    }

    /**
     * @return true exactly once when a 3-second pause closes a turn that actually
     * contained speech. Subsequent silent chunks return false until new speech.
     */
    fun onSilence(now: Long, silenceWindowMs: Long = DEFAULT_SILENCE_WINDOW_MS): Boolean {
        if (!hadVoiceSinceReset || turnAlreadyClosed) return false
        if (now - lastVoiceAt >= silenceWindowMs) {
            turnAlreadyClosed = true
            hadVoiceSinceReset = false
            return true
        }
        return false
    }

    fun reset() {
        hadVoiceSinceReset = false
        turnAlreadyClosed = false
        lastVoiceAt = 0
    }

    companion object {
        const val DEFAULT_SILENCE_WINDOW_MS = 3_000L
    }
}
