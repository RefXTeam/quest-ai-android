package com.chroniclequest.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.chroniclequest.data.local.entity.UserStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserStatsDao {

    @Query("SELECT * FROM user_stats WHERE id = ${UserStatsEntity.SINGLETON_ID}")
    fun observe(): Flow<UserStatsEntity?>

    @Query("SELECT * FROM user_stats WHERE id = ${UserStatsEntity.SINGLETON_ID}")
    suspend fun get(): UserStatsEntity?

    @Upsert
    suspend fun upsert(entity: UserStatsEntity)

    @Query(
        "INSERT OR IGNORE INTO user_stats (id, level, totalExp, gold) " +
            "VALUES (${UserStatsEntity.SINGLETON_ID}, 1, 0, 0)",
    )
    suspend fun ensureSeeded()
}
