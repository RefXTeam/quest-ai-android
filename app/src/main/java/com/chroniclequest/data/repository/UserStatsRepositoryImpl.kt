package com.chroniclequest.data.repository

import com.chroniclequest.data.local.dao.UserStatsDao
import com.chroniclequest.data.local.entity.UserStatsEntity
import com.chroniclequest.data.local.toDomain
import com.chroniclequest.domain.model.UserStats
import com.chroniclequest.domain.repository.UserStatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserStatsRepositoryImpl @Inject constructor(
    private val userStatsDao: UserStatsDao,
) : UserStatsRepository {

    // Serialize award operations so concurrent completions don't lose EXP/gold.
    private val awardMutex = Mutex()

    override fun observeStats(): Flow<UserStats> =
        userStatsDao.observe().map { it?.toDomain() ?: UserStats() }

    override suspend fun awardRewards(exp: Int, gold: Int): UserStats = awardMutex.withLock {
        userStatsDao.ensureSeeded()
        val current = userStatsDao.get() ?: UserStatsEntity()
        val newTotalExp = current.totalExp + exp
        val updated = UserStatsEntity(
            level = UserStats.levelForTotalExp(newTotalExp),
            totalExp = newTotalExp,
            gold = current.gold + gold,
        )
        userStatsDao.upsert(updated)
        updated.toDomain()
    }
}
