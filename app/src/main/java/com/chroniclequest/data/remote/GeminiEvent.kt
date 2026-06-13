package com.chroniclequest.data.remote

import kotlinx.serialization.json.JsonObject

/** High-level events surfaced from the Gemini Live WebSocket. */
sealed interface GeminiEvent {
    data object Connected : GeminiEvent
    data object SetupComplete : GeminiEvent

    /** A server-issued function call — the only way the silent agent acts. */
    data class ToolCallReceived(
        val id: String?,
        val name: String,
        val args: JsonObject,
    ) : GeminiEvent

    data object TurnComplete : GeminiEvent
    data class Closed(val code: Int, val reason: String) : GeminiEvent
    data class Failure(val error: Throwable) : GeminiEvent
}
