package com.chroniclequest.data.remote

import android.util.Log
import com.chroniclequest.data.audio.PcmUtils
import com.chroniclequest.data.remote.dto.BidiSetupMessage
import com.chroniclequest.data.remote.dto.FunctionResponse
import com.chroniclequest.data.remote.dto.MediaChunk
import com.chroniclequest.data.remote.dto.RealtimeInput
import com.chroniclequest.data.remote.dto.RealtimeInputMessage
import com.chroniclequest.data.remote.dto.ToolCall
import com.chroniclequest.data.remote.dto.ToolResponseMessage
import com.chroniclequest.data.remote.dto.ToolResponsePayload
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Component B — persistent bidirectional WebSocket to the Gemini Live API
 * (BidiGenerateContent). On open it sends the agent `setup`; thereafter it streams
 * base64 PCM and surfaces server `toolCall`s as [GeminiEvent]s.
 */
@Singleton
class GeminiLiveClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private val _events = MutableSharedFlow<GeminiEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<GeminiEvent> = _events.asSharedFlow()

    private var webSocket: WebSocket? = null
    private val connected = AtomicBoolean(false)
    val isConnected: Boolean get() = connected.get()

    fun connect(apiKey: String, model: String) {
        if (webSocket != null) return
        if (apiKey.isBlank()) {
            _events.tryEmit(GeminiEvent.Failure(IllegalStateException(MISSING_KEY_MESSAGE)))
            return
        }
        val url = "$BASE_URL?key=$apiKey"
        val request = Request.Builder().url(url).build()
        webSocket = okHttpClient.newWebSocket(request, listener(model))
    }

    fun sendAudioChunk(pcm: ShortArray) {
        val socket = webSocket ?: return
        if (!connected.get()) return
        val message = RealtimeInputMessage(
            realtimeInput = RealtimeInput(
                mediaChunks = listOf(
                    MediaChunk(
                        mimeType = "audio/pcm;rate=16000",
                        data = PcmUtils.shortsToBase64(pcm),
                    ),
                ),
            ),
        )
        socket.send(json.encodeToString(RealtimeInputMessage.serializer(), message))
    }

    /** Acknowledge a tool call so the model knows it was handled. */
    fun sendToolResponse(id: String?, name: String) {
        val socket = webSocket ?: return
        val response = ToolResponseMessage(
            toolResponse = ToolResponsePayload(
                functionResponses = listOf(
                    FunctionResponse(
                        id = id,
                        name = name,
                        response = buildJsonObject { put("result", "accepted") },
                    ),
                ),
            ),
        )
        socket.send(json.encodeToString(ToolResponseMessage.serializer(), response))
    }

    fun disconnect() {
        connected.set(false)
        webSocket?.close(NORMAL_CLOSURE, "client disconnect")
        webSocket = null
    }

    private fun listener(model: String) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket open — sending setup")
            connected.set(true)
            val setup = BidiSetupMessage(setup = GeminiAgentConfig.buildSetup(model))
            webSocket.send(json.encodeToString(BidiSetupMessage.serializer(), setup))
            _events.tryEmit(GeminiEvent.Connected)
        }

        override fun onMessage(webSocket: WebSocket, text: String) = handleServerFrame(text)

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) =
            handleServerFrame(bytes.utf8())

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing $code $reason")
            connected.set(false)
            this@GeminiLiveClient.webSocket = null
            _events.tryEmit(GeminiEvent.Closed(code, reason))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure", t)
            connected.set(false)
            this@GeminiLiveClient.webSocket = null
            _events.tryEmit(GeminiEvent.Failure(t))
        }
    }

    private fun handleServerFrame(raw: String) {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        when {
            root.containsKey("setupComplete") -> {
                Log.d(TAG, "Setup complete")
                _events.tryEmit(GeminiEvent.SetupComplete)
            }
            root.containsKey("toolCall") -> {
                val toolCall = runCatching {
                    json.decodeFromJsonElement(ToolCall.serializer(), root.getValue("toolCall"))
                }.getOrNull() ?: return
                toolCall.functionCalls.forEach { call ->
                    _events.tryEmit(GeminiEvent.ToolCallReceived(call.id, call.name, call.args))
                }
            }
            root.containsKey("serverContent") -> {
                val content = root.getValue("serverContent").jsonObject
                if (content["turnComplete"]?.toString() == "true") {
                    _events.tryEmit(GeminiEvent.TurnComplete)
                }
            }
        }
    }

    companion object {
        private const val TAG = "GeminiLiveClient"
        private const val BASE_URL =
            "wss://generativelanguage.googleapis.com/ws/" +
                "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private const val NORMAL_CLOSURE = 1000
        const val MISSING_KEY_MESSAGE =
            "GEMINI_API_KEY is empty. Add it to local.properties to enable the AI agent."
    }
}
