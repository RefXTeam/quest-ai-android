package com.chroniclequest.domain.repository

import com.chroniclequest.domain.model.UserStats
import kotlinx.coroutines.flow.Flow

interface UserStatsRepository {

    fun observeStats(): Flow<UserStats>

    /**
     * Grant rewards on quest completion, rolling EXP into levels.
     * @return the updated stats (with possible level-up).
     */
    suspend fun awardRewards(exp: Int, gold: Int): UserStats
}
