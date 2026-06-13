package com.chroniclequest.service

import android.util.Log
import com.chroniclequest.BuildConfig
import com.chroniclequest.data.audio.AudioConfig
import com.chroniclequest.data.audio.WavEncoder
import com.chroniclequest.data.remote.GeminiEvent
import com.chroniclequest.data.remote.GeminiRestClient
import com.chroniclequest.data.remote.GeminiTools
import com.chroniclequest.domain.AmbientEventBus
import com.chroniclequest.domain.AmbientSignal
import com.chroniclequest.domain.engine.CooldownEngine
import com.chroniclequest.domain.engine.SilenceDetector
import com.chroniclequest.domain.usecase.GenerateQuestFromToolCallUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Component B orchestration — REST fallback variant (the configured key has no
 * Live/bidi access). Voiced audio accumulates locally; when the 3-second silence
 * detector closes a turn, the buffered audio is sent to `generateContent` as one
 * WAV clip and any returned tool calls become quests — still gated by the
 * 20-minute cooldown regardless of how eager the model is.
 */
@Singleton
class AmbientQuestPipeline @Inject constructor(
    private val restClient: GeminiRestClient,
    private val silenceDetector: SilenceDetector,
    private val cooldownEngine: CooldownEngine,
    private val generateQuest: GenerateQuestFromToolCallUseCase,
    private val eventBus: AmbientEventBus,
) : AmbientPipeline {

    private var scope: CoroutineScope? = null
    private val bufferLock = Any()
    private val buffer = ArrayList<Short>(AudioConfig.SAMPLE_RATE * 4)
    private var keyMissingNotified = false

    override fun start(scope: CoroutineScope) {
        this.scope = scope
        silenceDetector.reset()
        clearBuffer()
        if (BuildConfig.GEMINI_API_KEY.isBlank() && !keyMissingNotified) {
            keyMissingNotified = true
            eventBus.emit(AmbientSignal.AgentError("GEMINI_API_KEY is empty — add it to local.properties."))
        }
        Log.d(TAG, "Pipeline started (REST mode, model=${BuildConfig.GEMINI_REST_MODEL})")
    }

    override fun onVoicedChunk(chunk: ShortArray) {
        appendToBuffer(chunk)
        silenceDetector.onVoiced(System.currentTimeMillis())
    }

    override fun onSilenceChunk() {
        if (silenceDetector.onSilence(System.currentTimeMillis())) {
            Log.d(TAG, "Turn complete (3s pause) — evaluating")
            evaluateTurn()
        }
    }

    override fun stop() {
        clearBuffer()
        silenceDetector.reset()
        scope = null
        Log.d(TAG, "Pipeline stopped")
    }

    private fun evaluateTurn() {
        val activeScope = scope ?: return
        val samples = drainBuffer()
        if (samples.size < MIN_SAMPLES) {
            Log.d(TAG, "Turn too short (${samples.size} samples) — skipped")
            return
        }
        if (BuildConfig.GEMINI_API_KEY.isBlank()) return

        activeScope.launch {
            runCatching {
                restClient.evaluateTurn(
                    wavBase64 = WavEncoder.pcmToWavBase64(samples),
                    model = BuildConfig.GEMINI_REST_MODEL,
                    apiKey = BuildConfig.GEMINI_API_KEY,
                )
            }.onSuccess { calls ->
                Log.d(TAG, "Turn evaluated: ${calls.size} tool call(s)")
                calls.forEach(::onToolCall)
            }.onFailure { e ->
                Log.e(TAG, "Turn evaluation failed", e)
                eventBus.emit(AmbientSignal.AgentError(e.message ?: "Agent request failed"))
            }
        }
    }

    private fun onToolCall(event: GeminiEvent.ToolCallReceived) {
        when (event.name) {
            GeminiTools.GIVE_USER_QUEST -> handleQuestToolCall(event)
            GeminiTools.SEND_INSIGHT_TIP -> handleInsightTip(event)
            else -> Log.w(TAG, "Unknown tool call: ${event.name}")
        }
    }

    private fun handleQuestToolCall(event: GeminiEvent.ToolCallReceived) {
        val now = System.currentTimeMillis()
        if (!cooldownEngine.canTrigger(now)) {
            val mins = cooldownEngine.millisUntilReady(now) / 60_000
            Log.d(TAG, "Quest suppressed by cooldown (~$mins min left)")
            return
        }
        val activeScope = scope ?: return
        activeScope.launch {
            runCatching {
                generateQuest(
                    args = event.args,
                    rawJson = event.args.toString(),
                    conversationSummary = "Ambient context @ $now",
                    now = now,
                )
            }.onSuccess { quest ->
                cooldownEngine.markTriggered(now)
                Log.d(TAG, "Quest created: ${quest.title} (#${quest.id})")
            }.onFailure { e ->
                Log.e(TAG, "Failed to create quest", e)
            }
        }
    }

    private fun handleInsightTip(event: GeminiEvent.ToolCallReceived) {
        val message = event.args.stringValue("message") ?: return
        eventBus.emit(AmbientSignal.InsightTip(message))
        Log.d(TAG, "Insight tip: $message")
    }

    /* ----- audio buffer (capped ring of recent voiced samples) ----- */

    private fun appendToBuffer(chunk: ShortArray) = synchronized(bufferLock) {
        for (s in chunk) buffer.add(s)
        val overflow = buffer.size - MAX_SAMPLES
        if (overflow > 0) buffer.subList(0, overflow).clear()
    }

    private fun drainBuffer(): ShortArray = synchronized(bufferLock) {
        val out = buffer.toShortArray()
        buffer.clear()
        out
    }

    private fun clearBuffer() = synchronized(bufferLock) { buffer.clear() }

    private fun JsonObject.stringValue(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    companion object {
        private const val TAG = "AmbientQuestPipeline"

        /** Cap the buffer at ~30 s so a long monologue can't grow unbounded. */
        private val MAX_SAMPLES = AudioConfig.SAMPLE_RATE * 30

        /** Need at least ~0.6 s of voiced audio to bother evaluating a turn. */
        private val MIN_SAMPLES = (AudioConfig.SAMPLE_RATE * 0.6).toInt()
    }
}
