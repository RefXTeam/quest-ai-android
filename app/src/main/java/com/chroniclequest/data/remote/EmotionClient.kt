package com.chroniclequest.data.remote

import android.util.Log
import com.chroniclequest.data.remote.dto.EmotionResponse
import com.chroniclequest.domain.PipelineMonitor
import com.chroniclequest.domain.model.EmotionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calls the magovoice voice-emotion API. No API key required; accepts our WAV.
 * Best-effort: returns null on any failure so the quest pipeline keeps going.
 */
@Singleton
class EmotionClient @Inject constructor(
    okHttpClient: OkHttpClient,
    private val json: Json,
    private val monitor: PipelineMonitor,
) {
    private val client: OkHttpClient = okHttpClient.newBuilder()
        .callTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun analyze(wav: ByteArray): EmotionResult? = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "turn.wav", wav.toRequestBody(WAV_MEDIA))
            .addFormDataPart("content_id", "")
            .addFormDataPart("out_dir", "exp/emotion_recognition")
            .build()
        val request = Request.Builder().url(URL).post(body).build()

        monitor.netRequest(
            target = "magovoice",
            line = "POST emotion_recognition/v1/run?is_speech=false",
            body = "multipart/form-data\n  • file: turn.wav (~${wav.size / 1024}KB)\n" +
                "  • content_id: \"\"\n  • out_dir: exp/emotion_recognition",
        )

        runCatching {
            client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                monitor.netResponse(
                    target = "magovoice",
                    line = "HTTP ${response.code}",
                    body = payload.ifBlank { "(빈 응답)" }.take(1000),
                    ok = response.isSuccessful,
                )
                if (!response.isSuccessful) {
                    Log.w(TAG, "emotion HTTP ${response.code}: ${payload.take(160)}")
                    return@use null
                }
                val parsed = json.decodeFromString(EmotionResponse.serializer(), payload)
                val result = parsed.content?.result
                val best = result?.bestEmotion?.takeIf { it.isNotBlank() }
                if (result == null || best == null) null
                else EmotionResult(bestEmotion = best, scores = result.emotion)
            }
        }.getOrElse {
            Log.w(TAG, "emotion analysis failed", it)
            null
        }
    }

    private companion object {
        const val TAG = "EmotionClient"
        const val URL =
            "https://api.magovoice.com/emotion_recognition/v1/run?is_speech=false"
        val WAV_MEDIA = "audio/wav".toMediaType()
    }
}
