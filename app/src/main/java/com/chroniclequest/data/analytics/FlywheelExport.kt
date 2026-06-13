package com.chroniclequest.data.analytics

import kotlinx.serialization.Serializable

/**
 * Structured payload for offline few-shot prompt engineering (Component E). Splits
 * the quest log into successful vs failed matches so prompts can be tuned toward
 * what users actually accept and complete.
 */
@Serializable
data class FlywheelExport(
    val schemaVersion: Int = 1,
    val totals: ReactionTotals,
    val successRate: Double,
    val successful: List<QuestSample>,
    val failed: List<QuestSample>,
)

@Serializable
data class ReactionTotals(
    val triggered: Int,
    val accepted: Int,
    val dismissed: Int,
    val expired: Int,
    val completed: Int,
)

@Serializable
data class QuestSample(
    val questId: Long,
    val timestamp: Long,
    val conversationSummary: String,
    val generatedQuestJson: String,
    val title: String,
    val verificationMethod: String,
    val reactionState: String,
)
