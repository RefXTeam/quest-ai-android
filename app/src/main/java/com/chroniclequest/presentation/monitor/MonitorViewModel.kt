package com.chroniclequest.presentation.monitor

import androidx.lifecycle.ViewModel
import com.chroniclequest.domain.PipelineMonitor
import com.chroniclequest.service.NetworkUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MonitorViewModel @Inject constructor(
    private val monitor: PipelineMonitor,
) : ViewModel() {

    val events = monitor.events

    /** Web-monitor URL (http://<wifi-ip>:8080) shown for the laptop browser. */
    val webUrl: String? = NetworkUtils.localIpv4()?.let { "http://$it:8080" }

    fun clear() = monitor.clear()
}
