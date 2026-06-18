package com.chroniclequest.presentation.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chroniclequest.domain.model.MonitorChannel
import com.chroniclequest.domain.model.PipelineEvent
import com.chroniclequest.domain.model.PipelineStage
import com.chroniclequest.presentation.theme.ArcanePurple
import com.chroniclequest.presentation.theme.DangerCrimson
import com.chroniclequest.presentation.theme.ExpEmerald
import com.chroniclequest.presentation.theme.EmotionPink
import com.chroniclequest.presentation.theme.NeonCyan
import com.chroniclequest.presentation.theme.QuestGold
import com.chroniclequest.presentation.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    onBack: () -> Unit,
    viewModel: MonitorViewModel = hiltViewModel(),
) {
    val events by viewModel.events.collectAsStateWithLifecycle()

    MonitorContent(
        onBack = onBack,
        onClear = viewModel::clear,
        webUrl = viewModel.webUrl,
        events = events,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonitorContent(
    onBack: () -> Unit,
    onClear: () -> Unit,
    webUrl: String?,
    events: List<PipelineEvent>,
) {
    // Phone view stays the high-level pipeline flow; the raw server request/response
    // lane (NETWORK) is shown on the wide web monitor's right column.
    val pipelineEvents = events.filter { it.channel == MonitorChannel.PIPELINE }
    androidx.compose.material3.Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("파이프라인 모니터") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "지우기")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        ) {
            item { WebUrlCard(webUrl) }
            if (pipelineEvents.isEmpty()) {
                item {
                    Text(
                        "에이전트를 시작하고 말을 걸면 파이프라인 이벤트가 실시간으로 흐릅니다.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 40.dp),
                    )
                }
            }
            items(pipelineEvents.asReversed()) { event -> EventRow(event) }
        }
    }
}

@Composable
private fun WebUrlCard(webUrl: String?) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("웹 모니터 (같은 Wi-Fi)", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
            Text(
                webUrl ?: "Wi-Fi 미연결 — 웹 모니터 주소를 확인할 수 없습니다",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                "발표 PC 브라우저에서 위 주소로 접속하면 동일 화면을 볼 수 있어요 (에이전트 실행 중).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EventRow(event: PipelineEvent) {
    val color = stageColor(event.stage)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(event.stage.emoji, style = MaterialTheme.typography.titleLarge)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        event.stage.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = color,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        timeFormat.format(event.timestamp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                    )
                }
                Text(
                    event.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp),
                )
                event.detail?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun stageColor(stage: PipelineStage): Color = when (stage) {
    PipelineStage.LISTENING, PipelineStage.VERIFY -> NeonCyan
    PipelineStage.AI_REQUEST, PipelineStage.AI_RESPONSE -> ArcanePurple
    PipelineStage.TOOL_CALL -> QuestGold
    PipelineStage.QUEST, PipelineStage.REWARD -> ExpEmerald
    PipelineStage.TURN, PipelineStage.COOLDOWN -> TextSecondary
    PipelineStage.EMOTION -> EmotionPink
    PipelineStage.ERROR -> DangerCrimson
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
