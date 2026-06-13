package com.chroniclequest.presentation.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chroniclequest.data.analytics.FlywheelExporter
import com.chroniclequest.data.analytics.ReactionTotals
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DebugUiState(
    val loading: Boolean = true,
    val totals: ReactionTotals? = null,
    val successRate: Double = 0.0,
    val json: String = "",
)

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val exporter: FlywheelExporter,
) : ViewModel() {

    private val _state = MutableStateFlow(DebugUiState())
    val state: StateFlow<DebugUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val export = exporter.buildExport()
            val json = exporter.buildExportJson()
            _state.value = DebugUiState(
                loading = false,
                totals = export.totals,
                successRate = export.successRate,
                json = json,
            )
        }
    }
}
