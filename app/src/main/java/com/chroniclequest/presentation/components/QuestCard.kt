package com.chroniclequest.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chroniclequest.domain.model.Quest
import com.chroniclequest.domain.model.QuestState
import com.chroniclequest.domain.model.VerificationMethod
import com.chroniclequest.presentation.theme.ExpEmerald
import com.chroniclequest.presentation.theme.NeonCyan
import com.chroniclequest.presentation.theme.QuestGold

/** A quest as a list item: title, method badge, rewards, and a manual check-in button. */
@Composable
fun QuestCard(
    quest: Quest,
    onCompleteManual: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = quest.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false),
                )
                StatusPill(quest.state)
            }
            Text(
                text = quest.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MethodChip(quest.verificationMethod, quest.targetValue)
                RewardChip(quest.rewardExp, quest.rewardGold)
            }
            if (quest.state == QuestState.ACCEPTED &&
                quest.verificationMethod == VerificationMethod.USER_MANUAL
            ) {
                OutlinedButton(
                    onClick = { onCompleteManual(quest.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    Text("  인증하기 — 완료했어요")
                }
            }
        }
    }
}

@Composable
private fun StatusPill(state: QuestState) {
    val (label, color) = when (state) {
        QuestState.TRIGGERED -> "신규" to NeonCyan
        QuestState.ACCEPTED -> "진행 중" to ExpEmerald
        QuestState.COMPLETED -> "완료" to ExpEmerald
        QuestState.DISMISSED -> "무시됨" to MaterialTheme.colorScheme.onSurfaceVariant
        QuestState.EXPIRED -> "만료됨" to MaterialTheme.colorScheme.error
    }
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        colors = AssistChipDefaults.assistChipColors(
            disabledLabelColor = color,
            disabledContainerColor = color.copy(alpha = 0.12f),
        ),
    )
}

@Composable
private fun MethodChip(method: VerificationMethod, target: Int) {
    val text = when (method) {
        VerificationMethod.SCREEN_OFF -> "📵 화면 끄기 · ${target}분"
        VerificationMethod.STEP_COUNT -> "👟 걷기 · ${target}걸음"
        VerificationMethod.MEDIA_PLAY -> "🎵 미디어 재생"
        VerificationMethod.USER_MANUAL -> "✍️ 직접 인증"
    }
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(text) },
        colors = AssistChipDefaults.assistChipColors(
            disabledLabelColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
}

@Composable
private fun RewardChip(exp: Int, gold: Int) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text("+$exp 경험치 · +$gold 골드") },
        colors = AssistChipDefaults.assistChipColors(
            disabledLabelColor = QuestGold,
            disabledContainerColor = QuestGold.copy(alpha = 0.10f),
        ),
    )
}
