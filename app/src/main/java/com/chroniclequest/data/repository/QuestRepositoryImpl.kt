package com.chroniclequest.data.repository

import com.chroniclequest.data.local.dao.QuestLogDao
import com.chroniclequest.data.local.entity.QuestLogEntity
import com.chroniclequest.data.local.toDomain
import com.chroniclequest.domain.model.Quest
import com.chroniclequest.domain.model.QuestState
import com.chroniclequest.domain.repository.QuestRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuestRepositoryImpl @Inject constructor(
    private val questLogDao: QuestLogDao,
) : QuestRepository {

    override fun observeActiveQuests(): Flow<List<Quest>> =
        questLogDao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeAllQuests(): Flow<List<Quest>> =
        questLogDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun createTriggeredQuest(
        quest: Quest,
        conversationSummary: String,
        generatedQuestJson: String,
    ): Long {
        val entity = QuestLogEntity(
            timestamp = quest.createdAt,
            conversationSummary = conversationSummary,
            generatedQuestJson = generatedQuestJson,
            title = quest.title,
            description = quest.description,
            verificationMethod = quest.verificationMethod,
            targetValue = quest.targetValue,
            rewardExp = quest.rewardExp,
            rewardGold = quest.rewardGold,
            state = QuestState.TRIGGERED,
        )
        return questLogDao.insert(entity)
    }

    override suspend fun getQuest(id: Long): Quest? =
        questLogDao.getById(id)?.toDomain()

    override suspend fun getAcceptedQuests(): List<Quest> =
        questLogDao.getByState(QuestState.ACCEPTED).map { it.toDomain() }

    override suspend fun acceptQuest(id: Long, deadlineAt: Long) {
        val now = System.currentTimeMillis()
        questLogDao.markAccepted(
            id = id,
            state = QuestState.ACCEPTED,
            acceptedAt = now,
            deadlineAt = deadlineAt,
        )
    }

    override suspend fun setState(id: Long, state: QuestState) {
        questLogDao.updateState(id, state)
    }
}
