package com.chroniclequest.presentation.home

import com.chroniclequest.domain.model.Quest
import com.chroniclequest.domain.model.QuestProgress
import com.chroniclequest.domain.model.UserStats

/**
 * A quest plus its live, time-derived display data (recomputed every second).
 *
 * @param remainingMillis ms until the verification deadline (null if not accepted)
 * @param windowMillis total verification window (acceptedAt → deadlineAt)
 * @param progress current verification progress (null if method has no measurable progress)
 */
data class QuestUiModel(
    val quest: Quest,
    val remainingMillis: Long? = null,
    val windowMillis: Long? = null,
    val progress: QuestProgress? = null,
)

/** MVI Model — the full render state of the home screen. */
data class HomeState(
    val stats: UserStats = UserStats(),
    val activeQuests: List<QuestUiModel> = emptyList(),
    /** Quest currently shown in the trigger modal (newest TRIGGERED), or null. */
    val pendingQuest: Quest? = null,
    val isListening: Boolean = false,
    val isLoading: Boolean = true,
)

/** MVI Intent — user/system actions the ViewModel reduces. */
sealed interface HomeIntent {
    data object ToggleListening : HomeIntent
    data class AcceptQuest(val questId: Long) : HomeIntent
    data class DismissQuest(val questId: Long) : HomeIntent
    data class CompleteManual(val questId: Long, val reflection: String?) : HomeIntent
}

/** MVI Effect — one-shot side effects (not part of render state). */
sealed interface HomeEffect {
    /** Fire haptic feedback when a new quest appears. */
    data object QuestTriggerHaptic : HomeEffect

    /** Play the XP-confetti burst for a completed quest. */
    data class CelebrateCompletion(val gainedExp: Int, val gainedGold: Int) : HomeEffect

    data class ShowMessage(val text: String) : HomeEffect

    /** Request the RECORD_AUDIO / notification permissions before starting the service. */
    data object RequestPermissions : HomeEffect
}
