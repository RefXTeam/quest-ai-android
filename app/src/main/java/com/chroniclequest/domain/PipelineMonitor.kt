package com.chroniclequest.domain

import com.chroniclequest.domain.model.PipelineEvent
import com.chroniclequest.domain.model.PipelineStage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory, process-wide log of the live data flow (audio → Gemini → quest) for
 * the demo monitor screen. Each pipeline stage calls [log]; the monitor UI observes
 * [events]. Keeps only the most recent [MAX_EVENTS].
 */
@Singleton
class PipelineMonitor @Inject constructor() {

    private val _events = MutableStateFlow<List<PipelineEvent>>(emptyList())
    val events: StateFlow<List<PipelineEvent>> = _events.asStateFlow()

    fun log(stage: PipelineStage, message: String, detail: String? = null) {
        val event = PipelineEvent(System.currentTimeMillis(), stage, message, detail)
        _events.update { (it + event).takeLast(MAX_EVENTS) }
    }

    fun clear() {
        _events.value = emptyList()
    }

    private companion object {
        const val MAX_EVENTS = 200
    }
}
