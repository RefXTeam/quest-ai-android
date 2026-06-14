package com.chroniclequest.domain.usecase

import com.chroniclequest.domain.model.Quest
import com.chroniclequest.domain.model.QuestState
import com.chroniclequest.domain.model.VerificationMethod
import com.chroniclequest.domain.repository.QuestRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import javax.inject.Inject

/**
 * Turns a validated `giveUserQuest` tool-call payload into a persisted TRIGGERED
 * [Quest]. Missing/garbage fields are coerced into safe defaults and reward/target
 * values are clamped to the schema's stated ranges.
 */
class GenerateQuestFromToolCallUseCase @Inject constructor(
    private val questRepository: QuestRepository,
) {
    suspend operator fun invoke(
        args: JsonObject,
        rawJson: String,
        conversationSummary: String,
        now: Long,
    ): Quest {
        val title = args.string("title")?.takeIf { it.isNotBlank() } ?: "An Unexpected Quest"
        val description = args.string("description")
            ?: "A fresh challenge has appeared on your path."
        val method = VerificationMethod.fromOrNull(args.string("verificationMethod"))
            ?: VerificationMethod.USER_MANUAL
        val targetValue = args.int("targetValue").coerceAtLeast(1)
        val rewardExp = args.int("rewardExp").coerceIn(10, 100)
        val rewardGold = args.int("rewardGold").coerceIn(5, 50)
        // The model-supplied context summary is the few-shot fuel; fall back to the
        // caller's placeholder only if the model didn't provide one.
        val summary = args.string("contextSummary")?.takeIf { it.isNotBlank() }
            ?: conversationSummary

        val quest = Quest(
            id = 0,
            title = title,
            description = description,
            verificationMethod = method,
            targetValue = targetValue,
            rewardExp = rewardExp,
            rewardGold = rewardGold,
            state = QuestState.TRIGGERED,
            createdAt = now,
        )
        val id = questRepository.createTriggeredQuest(quest, summary, rawJson)
        return quest.copy(id = id)
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.int(key: String): Int =
        (this[key] as? JsonPrimitive)?.intOrNull
            ?: (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
            ?: 0
}
