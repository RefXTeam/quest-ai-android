package com.chroniclequest.data.analytics

import com.chroniclequest.data.local.dao.QuestLogDao
import com.chroniclequest.data.local.entity.QuestLogEntity
import com.chroniclequest.domain.model.QuestState
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the full quest log and aggregates it into a [FlywheelExport]: COMPLETED
 * quests are "successful" matches; DISMISSED/EXPIRED are "failed". The result is
 * the payload exported for offline few-shot prompt tuning.
 */
@Singleton
class FlywheelExporter @Inject constructor(
    private val questLogDao: QuestLogDao,
    private val json: Json,
) {
    suspend fun buildExport(): FlywheelExport {
        val logs = questLogDao.getAllForExport()
        val totals = ReactionTotals(
            triggered = logs.count { it.state == QuestState.TRIGGERED },
            accepted = logs.count { it.state == QuestState.ACCEPTED },
            dismissed = logs.count { it.state == QuestState.DISMISSED },
            expired = logs.count { it.state == QuestState.EXPIRED },
            completed = logs.count { it.state == QuestState.COMPLETED },
        )
        val successful = logs.filter { it.state == QuestState.COMPLETED }.map { it.toSample() }
        val failed = logs
            .filter { it.state == QuestState.DISMISSED || it.state == QuestState.EXPIRED }
            .map { it.toSample() }

        val resolved = successful.size + failed.size
        val successRate = if (resolved == 0) 0.0 else successful.size.toDouble() / resolved

        return FlywheelExport(
            totals = totals,
            successRate = successRate,
            successful = successful,
            failed = failed,
        )
    }

    suspend fun buildExportJson(): String {
        val pretty = Json(from = json) { prettyPrint = true }
        return pretty.encodeToString(FlywheelExport.serializer(), buildExport())
    }

    private fun QuestLogEntity.toSample() = QuestSample(
        questId = id,
        timestamp = timestamp,
        conversationSummary = conversationSummary,
        generatedQuestJson = generatedQuestJson,
        title = title,
        verificationMethod = verificationMethod.name,
        reactionState = state.name,
    )
}
