package com.chroniclequest.data.remote

import com.chroniclequest.data.remote.dto.Content
import com.chroniclequest.data.remote.dto.FunctionDeclaration
import com.chroniclequest.data.remote.dto.GenerationConfig
import com.chroniclequest.data.remote.dto.Part
import com.chroniclequest.data.remote.dto.PropertySchema
import com.chroniclequest.data.remote.dto.Schema
import com.chroniclequest.data.remote.dto.SetupPayload
import com.chroniclequest.data.remote.dto.Tool

/** Tool names, kept in one place so the parser and schema stay in sync. */
object GeminiTools {
    const val TRIGGER_DYNAMIC_QUEST = "triggerDynamicQuest"
    const val GIVE_USER_QUEST = "giveUserQuest"
    const val SEND_INSIGHT_TIP = "sendInsightTip"
}

/**
 * Builds the BidiGenerateContent `setup` payload: the silent-agent system prompt,
 * TEXT-only response modality (the agent must never speak), and the two tools.
 */
object GeminiAgentConfig {

    private const val SYSTEM_INSTRUCTION = """
You are an invisible ambient RPG Game Master embedded in the user's life.
The user speaks KOREAN. Do NOT speak, narrate, or output any audio or text to the
user directly. Silently listen to the user's ambient context and conversation.
Only ever respond by calling a tool:
 - PREFER `triggerDynamicQuest` for any quest — it classifies the quest with a
   `category` (HEALTH/STUDY/REST/SOCIAL) and picks a `targetSensor`.
 - `giveUserQuest` is a legacy alternative for simple cases (no category).
 - Call `sendInsightTip` for a lighter, non-trackable observation or encouragement.
Be sparing: most of the time you should call no tool at all. Prefer silence over
noise. When you do create a quest, make the title urgent and epic, and choose a
sensor that genuinely fits the situation.

LANGUAGE: Write every user-facing string — quest `title`, `description`, and the
insight `message` — in natural KOREAN (한국어). Keep titles short and epic.

targetValue UNITS are strict and depend on the sensor:
 - SCREEN_OFF: targetValue is in MINUTES (e.g. 20, 30 — never seconds; keep it 5–120).
 - STEP_COUNT: targetValue is a number of STEPS (e.g. 500, 2000).
 - USER_CHECK / MEDIA_PLAY / USER_MANUAL: targetValue is 1.
"""

    fun buildSetup(model: String, fewShot: String? = null): SetupPayload = SetupPayload(
        model = model,
        generationConfig = GenerationConfig(
            responseModalities = listOf("TEXT"),
            temperature = 0.8,
        ),
        systemInstruction = systemInstruction(fewShot),
        tools = tools(),
    )

    /**
     * Shared between the Live setup and the REST fallback request. [fewShot], when
     * present, appends on-device success/failure examples so the agent self-improves
     * from the user's own past reactions (no external training).
     */
    fun systemInstruction(fewShot: String? = null): Content {
        val base = SYSTEM_INSTRUCTION.trim()
        val text = if (fewShot.isNullOrBlank()) base else "$base\n\n$fewShot"
        return Content(parts = listOf(Part(text = text)))
    }

    fun tools(): List<Tool> = listOf(
        Tool(
            functionDeclarations = listOf(
                triggerDynamicQuest(),
                giveUserQuest(),
                sendInsightTip(),
            ),
        ),
    )

    /** Universal dynamic quest function — the agent fills category/sensor/target at runtime. */
    private fun triggerDynamicQuest() = FunctionDeclaration(
        name = GeminiTools.TRIGGER_DYNAMIC_QUEST,
        description = "유저의 상황에 맞춰 실시간으로 가변적인 RPG 미션을 동적으로 생성하고 하달한다.",
        parameters = Schema(
            type = "OBJECT",
            properties = mapOf(
                "category" to PropertySchema(
                    type = "STRING",
                    description = "퀘스트 분류",
                    enum = listOf("HEALTH", "STUDY", "REST", "SOCIAL"),
                ),
                "title" to PropertySchema("STRING", "RPG 스타일의 몰입감 있는 퀘스트 제목"),
                "description" to PropertySchema(
                    "STRING",
                    "유저 상황에 공감하며 행동을 유도하는 가이드라인",
                ),
                "targetSensor" to PropertySchema(
                    type = "STRING",
                    description = "완료 검증 방식",
                    enum = listOf("SCREEN_OFF", "STEP_COUNT", "USER_CHECK"),
                ),
                "targetValue" to PropertySchema("INTEGER", "목표 시간(분) 또는 목표 걸음 수"),
                "rewardExp" to PropertySchema("INTEGER", "10~100 사이의 경험치"),
                "rewardGold" to PropertySchema("INTEGER", "5~50 사이의 골드"),
                "contextSummary" to PropertySchema(
                    "STRING",
                    "퀘스트를 유발한 당시 상황·대화의 한국어 한 줄 요약 (피드백 학습용)",
                ),
            ),
            required = listOf(
                "category", "title", "description", "targetSensor",
                "targetValue", "rewardExp", "rewardGold", "contextSummary",
            ),
        ),
    )

    private fun giveUserQuest() = FunctionDeclaration(
        name = GeminiTools.GIVE_USER_QUEST,
        description = "Triggered when the user encounters a real-world situation that can be " +
            "solved via a trackable micro-action.",
        parameters = Schema(
            type = "OBJECT",
            properties = mapOf(
                "title" to PropertySchema("STRING", "Urgent, epic RPG quest title"),
                "description" to PropertySchema(
                    "STRING",
                    "Contextual justification and actionable guide",
                ),
                "verificationMethod" to PropertySchema(
                    type = "STRING",
                    description = "How completion is proven",
                    enum = listOf("SCREEN_OFF", "STEP_COUNT", "MEDIA_PLAY", "USER_MANUAL"),
                ),
                "targetValue" to PropertySchema(
                    "INTEGER",
                    "e.g. minutes for screen off, steps for step count",
                ),
                "rewardExp" to PropertySchema("INTEGER", "Experience reward, 10-100"),
                "rewardGold" to PropertySchema("INTEGER", "Gold reward, 5-50"),
                "contextSummary" to PropertySchema(
                    "STRING",
                    "퀘스트를 유발한 당시 상황·대화의 한국어 한 줄 요약 (피드백 학습용)",
                ),
            ),
            required = listOf(
                "title", "description", "verificationMethod",
                "targetValue", "rewardExp", "rewardGold", "contextSummary",
            ),
        ),
    )

    private fun sendInsightTip() = FunctionDeclaration(
        name = GeminiTools.SEND_INSIGHT_TIP,
        description = "Send a short, non-trackable insight or encouragement when a full quest " +
            "isn't warranted.",
        parameters = Schema(
            type = "OBJECT",
            properties = mapOf(
                "message" to PropertySchema("STRING", "A concise, supportive insight (1-2 sentences)"),
            ),
            required = listOf("message"),
        ),
    )
}
