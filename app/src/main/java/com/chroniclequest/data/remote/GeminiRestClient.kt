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
    ): List<GeminiEvent.ToolCallReceived> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IllegalStateException(GeminiLiveClient.MISSING_KEY_MESSAGE)
        }
        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    role = "user",
                    parts = listOf(
                        Part(
                            inlineData = InlineData(
                                mimeType = "audio/wav",
                                data = wavBase64,
                            ),
                        ),
                    ),
                ),
            ),
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

        restHttpClient.newCall(httpRequest).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.w(TAG, "generateContent HTTP ${response.code}: ${payload.take(200)}")
                return@withContext emptyList()
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
