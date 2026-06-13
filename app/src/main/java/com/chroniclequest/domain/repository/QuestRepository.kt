package com.chroniclequest.domain.repository

import com.chroniclequest.domain.model.Quest
import com.chroniclequest.domain.model.QuestState
import kotlinx.coroutines.flow.Flow

/**
 * Persistence + lifecycle for quests. Backed by the Room `quest_log` table, which
 * doubles as the flywheel log (Component E).
 */
interface QuestRepository {

    /** Live quests (TRIGGERED/ACCEPTED) for the home screen. */
    fun observeActiveQuests(): Flow<List<Quest>>

    /** Full history for logs/analytics. */
    fun observeAllQuests(): Flow<List<Quest>>

    /**
     * Persist a freshly-generated quest in the TRIGGERED state.
     * @return the assigned row id.
     */
    suspend fun createTriggeredQuest(
        quest: Quest,
        conversationSummary: String,
        generatedQuestJson: String,
    ): Long

    suspend fun getQuest(id: Long): Quest?

    /** Accepted-but-unverified quests, e.g. to re-arm verification after restart. */
    suspend fun getAcceptedQuests(): List<Quest>

    /** Arm verification: ACCEPTED + deadline = now + window. */
    suspend fun acceptQuest(id: Long, deadlineAt: Long)

    suspend fun setState(id: Long, state: QuestState)
}
