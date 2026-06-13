package com.chroniclequest.domain.usecase

import com.chroniclequest.domain.model.Quest
import com.chroniclequest.domain.model.QuestState
import com.chroniclequest.domain.model.UserStats
import com.chroniclequest.domain.repository.QuestRepository
import com.chroniclequest.domain.repository.UserStatsRepository
import javax.inject.Inject

data class CompletionResult(
    val quest: Quest,
    val updatedStats: UserStats,
    val gainedExp: Int,
    val gainedGold: Int,
)

/** Marks a quest COMPLETED (idempotently) and grants its EXP/Gold rewards. */
class CompleteQuestUseCase @Inject constructor(
    private val questRepository: QuestRepository,
    private val userStatsRepository: UserStatsRepository,
) {
    suspend operator fun invoke(questId: Long): CompletionResult? {
        val quest = questRepository.getQuest(questId) ?: return null
        if (quest.state == QuestState.COMPLETED) return null
        questRepository.setState(questId, QuestState.COMPLETED)
        val stats = userStatsRepository.awardRewards(quest.rewardExp, quest.rewardGold)
        return CompletionResult(
            quest = quest,
            updatedStats = stats,
            gainedExp = quest.rewardExp,
            gainedGold = quest.rewardGold,
        )
    }
}
