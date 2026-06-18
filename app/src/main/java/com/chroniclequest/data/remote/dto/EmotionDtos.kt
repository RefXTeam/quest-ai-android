package com.chroniclequest.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** magovoice emotion_recognition response (relevant fields only). */
@Serializable
data class EmotionResponse(
    val code: Int = 0,
    val message: String = "",
    val content: EmotionContent? = null,
)

@Serializable
data class EmotionContent(val result: EmotionResultDto? = null)

@Serializable
data class EmotionResultDto(
    val emotion: Map<String, Int> = emptyMap(),
    @SerialName("best_emotion") val bestEmotion: String? = null,
)
