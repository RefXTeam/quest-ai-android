package com.chroniclequest.presentation.home

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.HearingDisabled
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chroniclequest.domain.model.Quest
import com.chroniclequest.presentation.components.ConfettiCanvas
import com.chroniclequest.presentation.components.QuestCard
import com.chroniclequest.presentation.components.QuestModal
import com.chroniclequest.presentation.components.StatHud
import com.chroniclequest.presentation.narration.rememberQuestNarrator
import com.chroniclequest.service.BatteryOptimization
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDebug: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val narrator = rememberQuestNarrator()
    var confettiTrigger by remember { mutableIntStateOf(0) }

    // Narrate a newly-surfaced quest; stop the voice once the user acts (the
    // pending quest clears to null on accept/dismiss).
    LaunchedEffect(state.pendingQuest?.id) {
        val quest = state.pendingQuest
        if (quest != null) {
            narrator.announceQuest(quest.title, quest.description)
        } else {
            narrator.stop()
        }
    }

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    val permissionState = rememberMultiplePermissionsState(requiredPermissions) { result ->
        if (result.values.all { it }) {
            // Keep the agent alive in the background past OEM battery killers.
            BatteryOptimization.requestIgnore(context)
            viewModel.onPermissionsGranted()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                HomeEffect.QuestTriggerHaptic ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                is HomeEffect.CelebrateCompletion -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    confettiTrigger++
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            "퀘스트 완료! +${effect.gainedExp} 경험치 · +${effect.gainedGold} 골드",
                        )
                    }
                }
                is HomeEffect.ShowMessage ->
                    scope.launch { snackbarHostState.showSnackbar(effect.text) }
                HomeEffect.RequestPermissions ->
                    permissionState.launchMultiplePermissionRequest()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("용사님 퀘스트") },
                actions = {
                    IconButton(onClick = onOpenDebug) {
                        Icon(Icons.Filled.Insights, contentDescription = "최적화 패널")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        floatingActionButton = {
            ListeningFab(
                isListening = state.isListening,
                onClick = {
                    if (state.isListening) {
                        viewModel.onIntent(HomeIntent.ToggleListening)
                    } else if (permissionState.allPermissionsGranted) {
                        BatteryOptimization.requestIgnore(context)
                        viewModel.onIntent(HomeIntent.ToggleListening)
                    } else {
                        permissionState.launchMultiplePermissionRequest()
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            HomeContent(
                state = state,
                padding = padding,
                onCompleteManual = { viewModel.onIntent(HomeIntent.CompleteManual(it, null)) },
            )
            QuestModal(
                quest = state.pendingQuest,
                onAccept = { viewModel.onIntent(HomeIntent.AcceptQuest(it)) },
                onDismiss = { viewModel.onIntent(HomeIntent.DismissQuest(it)) },
            )
            ConfettiCanvas(trigger = confettiTrigger)
        }
    }
}

@Composable
private fun ListeningFab(isListening: Boolean, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = if (isListening) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (isListening) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Icon(
            imageVector = if (isListening) Icons.Filled.Hearing else Icons.Filled.HearingDisabled,
            contentDescription = null,
        )
        Text(if (isListening) "  감지 중" else "  에이전트 시작")
    }
}

@Composable
private fun HomeContent(
    state: HomeState,
    padding: PaddingValues,
    onCompleteManual: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        item { StatHud(stats = state.stats) }
        item {
            Text(
                text = "진행 중인 퀘스트",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (state.activeQuests.isEmpty()) {
            item { EmptyQuestState(state.isListening) }
        } else {
            items(state.activeQuests, key = { it.quest.id }) { uiModel ->
                QuestCard(uiModel = uiModel, onCompleteManual = onCompleteManual)
            }
        }
    }
}

@Composable
private fun EmptyQuestState(isListening: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 48.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (isListening) "🜂" else "🜨",
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            text = if (isListening) {
                "에이전트가 주변을 듣고 있어요.\n일상 속에서 퀘스트가 나타납니다."
            } else {
                "에이전트를 시작해 당신의 모험을 시작하세요."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
