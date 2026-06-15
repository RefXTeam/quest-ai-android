package com.chroniclequest.domain.model

import kotlinx.serialization.Serializable

/** One step in the ambient → AI → quest pipeline, shown live on the monitor screen + web. */
@Serializable
data class PipelineEvent(
    val timestamp: Long,
    val stage: PipelineStage,
    val message: String,
    val detail: String? = null,
)

/** Pipeline steps, each with a Korean label + emoji for the demo monitor. */
@Serializable
enum class PipelineStage(val label: String, val emoji: String) {
    LISTENING("감지", "🎙️"),
    TURN("턴 종료", "⏸️"),
    AI_REQUEST("AI 요청", "📤"),
    AI_RESPONSE("AI 응답", "📥"),
    TOOL_CALL("함수 호출", "⚡"),
    COOLDOWN("쿨다운", "⏳"),
    QUEST("퀘스트 생성", "⚔️"),
    VERIFY("검증", "🎯"),
    REWARD("보상", "🎉"),
    ERROR("오류", "⚠️"),
}
