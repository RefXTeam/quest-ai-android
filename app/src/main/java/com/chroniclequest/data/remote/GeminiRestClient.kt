package com.chroniclequest.data.remote

import android.util.Log
import com.chroniclequest.data.remote.dto.Content
import com.chroniclequest.data.remote.dto.FunctionCallingConfig
import com.chroniclequest.data.remote.dto.GenerateContentRequest
import com.chroniclequest.data.remote.dto.GenerateContentResponse
import com.chroniclequest.data.remote.dto.GenerationConfig
import com.chroniclequest.data.remote.dto.InlineData
import com.chroniclequest.data.remote.dto.Part
import com.chroniclequest.data.remote.dto.ToolConfig
import com.chroniclequest.domain.PipelineMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * REST fallback for keys without Live (bidiGenerateContent) access. Instead of
 * streaming, it evaluates a completed conversational turn: the accumulated voiced
 * audio (WAV/base64) is POSTed to `generateContent` with the same system prompt
 * and tools, and any returned function calls are surfaced as [GeminiEvent]s.
 */
@Singleton
class GeminiRestClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val monitor: PipelineMonitor,
) {
    // The shared client has no read timeout (it's tuned for the long-lived Live
    // WebSocket). A turn evaluation is a bounded request, so cap it to avoid
    // hanging forever if the network stalls. newBuilder() reuses the connection pool.
    private val restHttpClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .callTimeout(40, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .build()
    }
    /**
     * @return the tool calls the model chose to emit for this turn (often empty —
     * the agent stays silent unless something actionable is detected).
     */
    suspend fun evaluateTurn(
        wavBase64: String,
        model: String,
        apiKey: String,
        fewShot: String? = null,
        emotionHint: String? = null,
    ): List<GeminiEvent.ToolCallReceived> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IllegalStateException(GeminiLiveClient.MISSING_KEY_MESSAGE)
        }
        val userParts = buildList {
            add(Part(inlineData = InlineData(mimeType = "audio/wav", data = wavBase64)))
            // Voice-emotion analysis context (magovoice), when available.
            if (!emotionHint.isNullOrBlank()) add(Part(text = emotionHint))
        }
        val request = GenerateContentRequest(
            contents = listOf(Content(role = "user", parts = userParts)),
            systemInstruction = GeminiAgentConfig.systemInstruction(fewShot),
            tools = GeminiAgentConfig.tools(),
            toolConfig = ToolConfig(FunctionCallingConfig(mode = "AUTO")),
            generationConfig = GenerationConfig(
                responseModalities = listOf("TEXT"),
                temperature = 0.8,
            ),
        )

        val body = json.encodeToString(GenerateContentRequest.serializer(), request)
            .toRequestBody(JSON_MEDIA)
        // Key goes in a header, not the URL, so it never lands in OkHttp request logs.
        val httpRequest = Request.Builder()
            .url("$BASE_URL/$model:generateContent")
            .addHeader("x-goog-api-key", apiKey)
            .post(body)
            .build()

        // Server-comm panel (right column): summarise the request without dumping the
        // multi-megabyte base64 audio. Header omitted so the API key is never shown.
        val audioKb = wavBase64.length * 3 / 4 / 1024
        monitor.netRequest(
            target = "Gemini",
            line = "POST $model:generateContent",
            body = buildString {
                appendLine("contents[0].role: user")
                appendLine("  • inlineData: audio/wav (~${audioKb}KB, base64 생략)")
                if (!emotionHint.isNullOrBlank()) appendLine("  • text: \"$emotionHint\"")
                appendLine("tools: triggerDynamicQuest, giveUserQuest, sendInsightTip")
                appendLine("toolConfig.functionCallingConfig.mode: AUTO")
                appendLine("generationConfig: TEXT, temperature 0.8")
                if (!fewShot.isNullOrBlank()) append("systemInstruction: 기본 + few-shot 주입")
                else append("systemInstruction: 기본")
            },
        )

        restHttpClient.newCall(httpRequest).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            monitor.netResponse(
                target = "Gemini",
                line = "HTTP ${response.code}",
                body = payload.ifBlank { "(빈 응답)" }.take(1400),
                ok = response.isSuccessful,
            )
            if (!response.isSuccessful) {
                Log.w(TAG, "generateContent HTTP ${response.code}: ${payload.take(200)}")
                // Surface as an error (not an empty "silent" response) so the monitor
                // shows the real reason instead of "함수 호출 없음".
                val reason = if (response.code == 429) {
                    "API 호출 한도 초과 (429) — 잠시 후 다시 시도해 주세요"
                } else {
                    "AI 요청 실패 (HTTP ${response.code})"
                }
                throw IllegalStateException(reason)
            }
            val parsed = runCatching {
                json.decodeFromString(GenerateContentResponse.serializer(), payload)
            }.getOrElse {
                Log.e(TAG, "Failed to parse generateContent response", it)
                return@withContext emptyList()
            }
            parsed.candidates
                .firstOrNull()
                ?.content
                ?.parts
                ?.mapNotNull { it.functionCall }
                ?.map { GeminiEvent.ToolCallReceived(id = null, name = it.name, args = it.args) }
                ?: emptyList()
        }
    }

    companion object {
        private const val TAG = "GeminiRestClient"
        private const val BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
