package com.chroniclequest.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/* ---------------------------------------------------------------------------
 * REST generateContent request/response. Used by the fallback path when the API
 * key has no Live (bidiGenerateContent) access. Reuses [Content]/[Tool]/[Part].
 * ------------------------------------------------------------------------- */

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val tools: List<Tool>? = null,
    val toolConfig: ToolConfig? = null,
    val generationConfig: GenerationConfig? = null,
)

@Serializable
data class ToolConfig(val functionCallingConfig: FunctionCallingConfig)

@Serializable
data class FunctionCallingConfig(val mode: String)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate> = emptyList(),
)

@Serializable
data class Candidate(val content: ResponseContent? = null)

@Serializable
data class ResponseContent(val parts: List<ResponsePart> = emptyList())

@Serializable
data class ResponsePart(
    val text: String? = null,
    val functionCall: ResponseFunctionCall? = null,
)

@Serializable
data class ResponseFunctionCall(
    val name: String,
    val args: JsonObject = JsonObject(emptyMap()),
)
