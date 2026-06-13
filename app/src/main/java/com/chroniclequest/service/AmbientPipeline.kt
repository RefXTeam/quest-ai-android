package com.chroniclequest.service

import kotlinx.coroutines.CoroutineScope

/**
 * Seam between the capture engine (Component A) and the AI brain (Component B).
 * The service feeds it VAD-gated audio; the implementation decides what to do
 * with it (stream to Gemini, detect silence boundaries, emit quests).
 */
interface AmbientPipeline {
    /** Called when the service enters the foreground. [scope] is the service lifecycle scope. */
    fun start(scope: CoroutineScope)

    /** A chunk that cleared the local VAD gate. */
    fun onVoicedChunk(chunk: ShortArray)

    /** A chunk below the VAD threshold (silence). */
    fun onSilenceChunk()

    /** Capture stopped — tear down any upstream connection. */
    fun stop()
}
