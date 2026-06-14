package com.chroniclequest.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chroniclequest.domain.AmbientEventBus
import com.chroniclequest.domain.AmbientSignal
import com.chroniclequest.domain.model.QuestState
import com.chroniclequest.domain.repository.QuestRepository
import com.chroniclequest.domain.repository.UserStatsRepository
import com.chroniclequest.domain.usecase.AcceptQuestUseCase
import com.chroniclequest.domain.usecase.CompleteQuestUseCase
import com.chroniclequest.service.AmbientAudioService
import com.chroniclequest.service.AmbientServiceController
import com.chroniclequest.service.QuestVerificationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val questRepository: QuestRepository,
    userStatsRepository: UserStatsRepository,
    private val acceptQuest: AcceptQuestUseCase,
    private val completeQuest: CompleteQuestUseCase,
    private val verificationManager: QuestVerificationManager,
    private val serviceController: AmbientServiceController,
    eventBus: AmbientEventBus,
) : ViewModel() {

    private val isListening = MutableStateFlow(false)

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    // 1-second tick to drive deadline countdowns and live verification progress.
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    val state: StateFlow<HomeState> = combine(
        userStatsRepository.observeStats(),
        questRepository.observeActiveQuests(),
        AmbientAudioService.isRunning,
        ticker,
    ) { stats, quests, running, now ->
        HomeState(
            stats = stats,
            activeQuests = quests.map { quest ->
                QuestUiModel(
                    quest = quest,
                    remainingMillis = quest.deadlineAt?.let { (it - now).coerceAtLeast(0) },
                    windowMillis = if (quest.acceptedAt != null && quest.deadlineAt != null) {
                        quest.deadlineAt - quest.acceptedAt
                    } else {
                        null
                    },
                    progress = if (quest.state == QuestState.ACCEPTED) {
                        verificationManager.progressOf(quest.id, now)
                    } else {
                        null
                    },
                )
            },
            pendingQuest = quests.firstOrNull { it.state == QuestState.TRIGGERED },
            isListening = running,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeState(),
    )

    init {
        // Haptic buzz whenever a brand-new quest surfaces.
        viewModelScope.launch {
            state.map { it.pendingQuest?.id }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { _effects.send(HomeEffect.QuestTriggerHaptic) }
        }
        // Surface ephemeral pipeline signals (tips, agent errors).
        viewModelScope.launch {
            eventBus.signals.collect { signal ->
                when (signal) {
                    is AmbientSignal.InsightTip -> _effects.send(HomeEffect.ShowMessage(signal.message))
                    is AmbientSignal.AgentError -> _effects.send(HomeEffect.ShowMessage(signal.message))
                    is AmbientSignal.QuestCompleted -> _effects.send(
                        HomeEffect.CelebrateCompletion(signal.gainedExp, signal.gainedGold),
                    )
                }
            }
        }
    }

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.ToggleListening -> toggleListening()
            is HomeIntent.AcceptQuest -> onAccept(intent.questId)
            is HomeIntent.DismissQuest -> onDismiss(intent.questId)
            is HomeIntent.CompleteManual -> onCompleteManual(intent.questId)
        }
    }

    private fun toggleListening() {
        if (isListening.value || AmbientAudioService.isRunning.value) {
            serviceController.stop()
            isListening.value = false
        } else {
            // The screen gates this on permissions before we reach here.
            serviceController.start()
            isListening.value = true
        }
    }

    /** Called by the screen once RECORD_AUDIO + notifications are granted. */
    fun onPermissionsGranted() {
        serviceController.start()
        isListening.value = true
    }

    private fun onAccept(questId: Long) {
        viewModelScope.launch {
            val accepted = acceptQuest(questId, System.currentTimeMillis()) ?: return@launch
            verificationManager.arm(accepted)
        }
    }

    private fun onDismiss(questId: Long) {
        viewModelScope.launch {
            verificationManager.disarm(questId)
            questRepository.setState(questId, QuestState.DISMISSED)
        }
    }

    private fun onCompleteManual(questId: Long) {
        viewModelScope.launch {
            verificationManager.disarm(questId)
            val result = completeQuest(questId) ?: return@launch
            _effects.send(
                HomeEffect.CelebrateCompletion(result.gainedExp, result.gainedGold),
            )
        }
    }
}
