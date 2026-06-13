package com.chroniclequest.domain.usecase

import com.chroniclequest.domain.model.Quest
import com.chroniclequest.domain.model.VerificationMethod
import com.chroniclequest.domain.repository.QuestRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Accepts a quest and arms its verification window. The deadline is derived from
 * the verification method + target so each quest type gets a sensible time box.
 */
class AcceptQuestUseCase @Inject constructor(
    private val questRepository: QuestRepository,
) {
    suspend operator fun invoke(questId: Long, now: Long): Quest? {
        val quest = questRepository.getQuest(questId) ?: return null
        val deadline = now + windowMillis(quest)
        questRepository.acceptQuest(questId, deadline)
        return quest.copy(deadlineAt = deadline, acceptedAt = now)
    }

    private fun windowMillis(quest: Quest): Long = when (quest.verificationMethod) {
        // Give the full target plus a grace buffer to keep the screen off.
        VerificationMethod.SCREEN_OFF ->
            TimeUnit.MINUTES.toMillis((quest.targetValue + 5).toLong())
        VerificationMethod.STEP_COUNT -> TimeUnit.HOURS.toMillis(2)
        VerificationMethod.MEDIA_PLAY -> TimeUnit.MINUTES.toMillis(30)
        VerificationMethod.USER_MANUAL -> TimeUnit.HOURS.toMillis(6)
    }
}
