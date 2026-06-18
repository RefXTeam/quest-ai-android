package com.chroniclequest.domain.model

/**
 * Voice emotion analysis result (magovoice). [scores] maps emotion → 0–100;
 * [bestEmotion] is the dominant one. Fed into the Gemini prompt so quests can
 * empathize with the user's mood.
 */
data class EmotionResult(
    val bestEmotion: String,
    val scores: Map<String, Int>,
) {
    /** Korean one-liner injected into the Gemini request. */
    fun toPromptHint(): String {
        val best = koreanLabel(bestEmotion)
        val detail = scores.entries
            .sortedByDescending { it.value }
            .joinToString(" · ") { "${koreanLabel(it.key)} ${it.value}" }
        return "음성 감정 분석 — 우세 감정: $best (점수: $detail)"
    }

    val bestKorean: String get() = koreanLabel(bestEmotion)

    private fun koreanLabel(raw: String): String = when (raw.uppercase()) {
        "HAPPINESS" -> "기쁨"
        "NEUTRAL" -> "중립"
        "ANGRY" -> "분노"
        "SADNESS" -> "슬픔"
        "SURPRISE" -> "놀람"
        else -> raw
    }
}
