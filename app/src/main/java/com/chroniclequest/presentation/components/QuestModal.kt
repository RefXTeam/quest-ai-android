package com.chroniclequest.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chroniclequest.domain.model.Quest
import com.chroniclequest.presentation.theme.NeonCyan
import com.chroniclequest.presentation.theme.QuestGold
import com.chroniclequest.presentation.theme.glowBorder

/**
 * The "A quest has appeared!" modal. Animates in with a scale+fade, sits over a
 * scrim, and offers Accept / Dismiss. Shown whenever [quest] is non-null.
 */
@Composable
fun QuestModal(
    quest: Quest?,
    onAccept: (Long) -> Unit,
    onDismiss: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = quest != null,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = quest != null,
                enter = scaleIn(tween(280), initialScale = 0.8f) + fadeIn(tween(280)),
                exit = scaleOut(tween(200), targetScale = 0.85f) + fadeOut(tween(200)),
            ) {
                quest?.let { QuestModalCard(it, onAccept, onDismiss) }
            }
        }
    }
}

@Composable
private fun QuestModalCard(
    quest: Quest,
    onAccept: (Long) -> Unit,
    onDismiss: (Long) -> Unit,
) {
    Surface(
        modifier = Modifier
            .padding(28.dp)
            .glowBorder(NeonCyan, cornerRadius = 24.dp, width = 2.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = quest.category?.let { "⚔️  새로운 ${it.emoji} ${it.label} 퀘스트" }
                    ?: "⚔️  새로운 퀘스트 등장",
                style = MaterialTheme.typography.labelLarge,
                color = QuestGold,
            )
            Text(
                text = quest.title,
                style = MaterialTheme.typography.headlineMedium,
                color = NeonCyan,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = quest.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "보상: +${quest.rewardExp} 경험치 · +${quest.rewardGold} 골드",
                style = MaterialTheme.typography.titleLarge,
                color = QuestGold,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = { onDismiss(quest.id) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("나중에")
                }
                Button(
                    onClick = { onAccept(quest.id) },
                    modifier = Modifier.weight(1.6f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("퀘스트 수락", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
