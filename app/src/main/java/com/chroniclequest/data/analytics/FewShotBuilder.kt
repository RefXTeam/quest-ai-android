package com.chroniclequest.data.analytics

import com.chroniclequest.data.local.dao.QuestLogDao
import com.chroniclequest.data.local.entity.QuestLogEntity
import com.chroniclequest.domain.model.QuestState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds an on-device few-shot block from the user's own quest history. COMPLETED
 * quests become positive examples, DISMISSED/EXPIRED become negative ones. Injected
 * into the system prompt each turn so the agent self-improves toward what *this*
 * user actually accepts — no external training, no data leaves the device.
 */
@Singleton
class FewShotBuilder @Inject constructor(
    private val questLogDao: QuestLogDao,
) {
    suspend fun build(
        maxSuccess: Int = 3,
        maxFailure: Int = 3,
    ): String? {
        // Single query (timestamp DESC) instead of three full scans.
        val rows = questLogDao.getByStates(
            listOf(QuestState.COMPLETED, QuestState.DISMISSED, QuestState.EXPIRED),
        ).filter { it.hasContext() }
        val success = rows.filter { it.state == QuestState.COMPLETED }.take(maxSuccess)
        val failure = rows
            .filter { it.state == QuestState.DISMISSED || it.state == QuestState.EXPIRED }
            .take(maxFailure)

        if (success.isEmpty() && failure.isEmpty()) return null

        return buildString {
            appendLine("참고: 이 사용자의 과거 반응 사례다. 성공 패턴은 살리고 실패 패턴은 피해서 더 잘 맞는 퀘스트를 제안하라.")
            success.forEach { q ->
                val cat = q.category?.let { "(${it.label}) " }.orEmpty()
                appendLine(
                    "[성공] ${cat}상황:\"${q.conversationSummary}\" → 제안: \"${q.title}\" " +
                        "(${q.verificationMethod}) → 사용자가 완료함",
                )
            }
            failure.forEach { q ->
                val cat = q.category?.let { "(${it.label}) " }.orEmpty()
                val reaction = if (q.state == QuestState.DISMISSED) "무시함" else "시간 내에 못 해서 실패함"
                appendLine(
                    "[실패] ${cat}상황:\"${q.conversationSummary}\" → 제안: \"${q.title}\" " +
                        "(${q.verificationMethod}) → 사용자가 $reaction",
                )
            }
        }.trim()
    }

    /** Skip rows whose summary is still the placeholder or empty. */
    private fun QuestLogEntity.hasContext(): Boolean =
        conversationSummary.isNotBlank() && !conversationSummary.startsWith("Ambient context")
}
