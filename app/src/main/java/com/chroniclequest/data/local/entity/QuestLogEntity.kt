package com.chroniclequest.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.chroniclequest.domain.model.QuestState
import com.chroniclequest.domain.model.VerificationMethod

/**
 * Single source of truth for a quest. Serves both as the live quest record the
 * UI renders/verifies against AND as the flywheel log (Component E): the
 * [conversationSummary] + [generatedQuestJson] + [state] tuple is what gets
 * exported for offline few-shot prompt tuning.
 */
@Entity(tableName = "quest_log")
data class QuestLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val conversationSummary: String,
    val generatedQuestJson: String,
    // Rendered quest fields
    val title: String,
    val description: String,
    val verificationMethod: VerificationMethod,
    val targetValue: Int,
    val rewardExp: Int,
    val rewardGold: Int,
    // Lifecycle / implicit feedback
    val state: QuestState,
    val acceptedAt: Long? = null,
    val deadlineAt: Long? = null,
)
