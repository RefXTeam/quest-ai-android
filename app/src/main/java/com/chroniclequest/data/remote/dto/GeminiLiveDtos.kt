package com.chroniclequest.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/* ---------------------------------------------------------------------------
 * Outbound (client → server) frames for the BidiGenerateContent Live API.
 * ------------------------------------------------------------------------- */

@Serializable
data class BidiSetupMessage(val setup: SetupPayload)

@Serializable
data class SetupPayload(
    val model: String,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null,
    val tools: List<Tool>? = null,
)

@Serializable
data class GenerationConfig(
    val responseModalities: List<String> = listOf("TEXT"),
    val temperature: Double? = null,
)

@Serializable
data class Content(
    val parts: List<Part>,
    val role: String? = null,
)

@Serializable
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null,
)

@Serializable
data class InlineData(
    val mimeType: String,
    val data: String,
)

@Serializable
data class Tool(val functionDeclarations: List<FunctionDeclaration>)

@Serializable
data class FunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: Schema,
)

@Serializable
data class Schema(
    val type: String,
    val properties: Map<String, PropertySchema>? = null,
    val required: List<String>? = null,
)

@Serializable
data class PropertySchema(
    val type: String,
    val description: String? = null,
    val enum: List<String>? = null,
)

@Serializable
data class RealtimeInputMessage(val realtimeInput: RealtimeInput)

@Serializable
data class RealtimeInput(val mediaChunks: List<MediaChunk>)

@Serializable
data class MediaChunk(
    val mimeType: String,
    val data: String,
)

@Serializable
data class ToolResponseMessage(val toolResponse: ToolResponsePayload)

@Serializable
data class ToolResponsePayload(val functionResponses: List<FunctionResponse>)

@Serializable
data class FunctionResponse(
    val id: String? = null,
    val name: String,
    val response: JsonObject,
)

/* ---------------------------------------------------------------------------
 * Inbound (server → client) frames. We parse the relevant sub-objects; unknown
 * keys are ignored by the configured Json.
 * ------------------------------------------------------------------------- */

@Serializable
data class ToolCall(
    val functionCalls: List<FunctionCall> = emptyList(),
)

@Serializable
data class FunctionCall(
    val id: String? = null,
    val name: String,
    val args: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class ServerContent(
    val turnComplete: Boolean = false,
    val interrupted: Boolean = false,
)

/** Marker for the `setupComplete` handshake frame. */
@Serializable
class SetupComplete
