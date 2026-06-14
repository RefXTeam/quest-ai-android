package com.chroniclequest.service

import android.util.Log
import com.chroniclequest.domain.AmbientEventBus
import com.chroniclequest.domain.AmbientSignal
import com.chroniclequest.domain.model.Quest
import com.chroniclequest.domain.model.QuestProgress
import com.chroniclequest.domain.model.QuestState
import com.chroniclequest.domain.model.VerificationMethod
import com.chroniclequest.domain.repository.QuestRepository
import com.chroniclequest.domain.usecase.CompleteQuestUseCase
import com.chroniclequest.service.verification.MediaPlayVerifier
import com.chroniclequest.service.verification.QuestVerifier
import com.chroniclequest.service.verification.ScreenOffVerifier
import com.chroniclequest.service.verification.StepCountVerifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Component C — central dispatch for quest verification. Binds an accepted quest
 * to the verifier matching its [VerificationMethod], grants rewards on success
 * (Component D celebration via the bus), and marks it EXPIRED if the deadline
 * passes first. USER_MANUAL quests have no background verifier — they complete
 * through the in-app check-in button.
 */
@Singleton
class QuestVerificationManager @Inject constructor(
    private val completeQuest: CompleteQuestUseCase,
    private val questRepository: QuestRepository,
    private val eventBus: AmbientEventBus,
    screenOffVerifier: ScreenOffVerifier,
    stepCountVerifier: StepCountVerifier,
    mediaPlayVerifier: MediaPlayVerifier,
) {
    private val scope = CoroutineScope(SupervisorJob())
    private val armed = ConcurrentHashMap<Long, Quest>()
    private val expiryJobs = ConcurrentHashMap<Long, Job>()

    private val verifiers: Map<VerificationMethod, QuestVerifier> = listOf(
        screenOffVerifier, stepCountVerifier, mediaPlayVerifier,
    ).associateBy { it.method }

    fun arm(quest: Quest) {
        armed[quest.id] = quest
        verifiers[quest.verificationMethod]?.start(quest) { id -> reportCompleted(id) }
        scheduleExpiry(quest)
        Log.d(TAG, "Armed quest #${quest.id} via ${quest.verificationMethod}")
    }

    fun disarm(questId: Long) {
        armed.remove(questId)
        expiryJobs.remove(questId)?.cancel()
        verifiers.values.forEach { it.stop(questId) }
    }

    fun armedQuests(): Collection<Quest> = armed.values

    /** Current verification progress for an armed quest, or null if none. */
    fun progressOf(questId: Long, now: Long): QuestProgress? {
        val quest = armed[questId] ?: return null
        return verifiers[quest.verificationMethod]?.progress(questId, now)
    }

    /** Re-arm accepted-but-unverified quests after a process/service restart. */
    fun restoreArmed() {
        scope.launch {
            questRepository.getAcceptedQuests().forEach { quest ->
                if (!armed.containsKey(quest.id)) arm(quest)
            }
        }
    }

    private fun reportCompleted(questId: Long) {
        scope.launch {
            val result = completeQuest(questId) ?: return@launch
            disarm(questId)
            eventBus.emit(AmbientSignal.QuestCompleted(result.gainedExp, result.gainedGold))
            Log.d(TAG, "Quest #$questId verified complete")
        }
    }

    private fun scheduleExpiry(quest: Quest) {
        val deadline = quest.deadlineAt ?: return
        val remaining = deadline - System.currentTimeMillis()
        if (remaining <= 0) {
            expire(quest.id)
            return
        }
        expiryJobs[quest.id] = scope.launch {
            delay(remaining)
            expire(quest.id)
        }
    }

    private fun expire(questId: Long) {
        scope.launch {
            val quest = questRepository.getQuest(questId) ?: return@launch
            if (quest.state == QuestState.ACCEPTED) {
                questRepository.setState(questId, QuestState.EXPIRED)
                Log.d(TAG, "Quest #$questId expired")
            }
            disarm(questId)
        }
    }

    companion object {
        private const val TAG = "QuestVerificationManager"
    }
}
