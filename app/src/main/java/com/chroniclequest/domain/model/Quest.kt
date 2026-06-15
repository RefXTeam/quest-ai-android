package com.chroniclequest.domain.model

/**
 * A single RPG quest, produced from a Gemini `giveUserQuest` tool call.
 *
 * @param id stable local id (also the QuestLog row id)
 * @param title urgent, epic RPG quest title
 * @param description contextual justification + actionable guide
 * @param verificationMethod how completion is proven
 * @param targetValue units depend on [verificationMethod] (minutes, steps, …)
 * @param rewardExp 10–100 EXP on completion
 * @param rewardGold 5–50 gold on completion
 * @param state current lifecycle state
 * @param createdAt epoch millis when triggered
 * @param acceptedAt epoch millis when accepted (null until then)
 * @param deadlineAt epoch millis the verification window closes (null until accepted)
 * @param category optional category from `triggerDynamicQuest` (null for legacy `giveUserQuest`)
 */
data class Quest(
    val id: Long,
    val title: String,
    val description: String,
    val verificationMethod: VerificationMethod,
    val targetValue: Int,
    val rewardExp: Int,
    val rewardGold: Int,
    val state: QuestState,
    val createdAt: Long,
    val acceptedAt: Long? = null,
    val deadlineAt: Long? = null,
    val category: QuestCategory? = null,
)
