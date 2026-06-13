package com.chroniclequest.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide bus for transient, non-persisted signals from the background
 * pipeline to the UI. Persisted quests flow through Room instead; this carries
 * the ephemeral stuff (insight tips, agent errors).
 */
@Singleton
class AmbientEventBus @Inject constructor() {
    private val _signals = MutableSharedFlow<AmbientSignal>(extraBufferCapacity = 16)
    val signals: SharedFlow<AmbientSignal> = _signals.asSharedFlow()

    fun emit(signal: AmbientSignal) {
        _signals.tryEmit(signal)
    }
}

sealed interface AmbientSignal {
    data class InsightTip(val message: String) : AmbientSignal
    data class AgentError(val message: String) : AmbientSignal

    /** A quest was verified complete in the background — UI should celebrate. */
    data class QuestCompleted(val gainedExp: Int, val gainedGold: Int) : AmbientSignal
}
