package com.chroniclequest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.chroniclequest.data.local.entity.QuestLogEntity
import com.chroniclequest.domain.model.QuestState
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestLogDao {

    @Insert
    suspend fun insert(entity: QuestLogEntity): Long

    @Update
    suspend fun update(entity: QuestLogEntity)

    @Query("SELECT * FROM quest_log WHERE id = :id")
    suspend fun getById(id: Long): QuestLogEntity?

    @Query("UPDATE quest_log SET state = :state WHERE id = :id")
    suspend fun updateState(id: Long, state: QuestState)

    @Query(
        "UPDATE quest_log SET state = :state, acceptedAt = :acceptedAt, deadlineAt = :deadlineAt WHERE id = :id",
    )
    suspend fun markAccepted(id: Long, state: QuestState, acceptedAt: Long, deadlineAt: Long)

    /** Active = surfaced or accepted but not yet terminal. Drives the live UI. */
    @Query(
        "SELECT * FROM quest_log WHERE state IN ('TRIGGERED','ACCEPTED') ORDER BY timestamp DESC",
    )
    fun observeActive(): Flow<List<QuestLogEntity>>

    @Query("SELECT * FROM quest_log ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<QuestLogEntity>>

    /** Full history for the flywheel exporter (Component E). */
    @Query("SELECT * FROM quest_log ORDER BY timestamp ASC")
    suspend fun getAllForExport(): List<QuestLogEntity>

    @Query("SELECT COUNT(*) FROM quest_log WHERE state = :state")
    suspend fun countByState(state: QuestState): Int

    @Query("SELECT * FROM quest_log WHERE state = :state ORDER BY timestamp DESC")
    suspend fun getByState(state: QuestState): List<QuestLogEntity>

    @Query("SELECT * FROM quest_log WHERE state IN (:states) ORDER BY timestamp DESC")
    suspend fun getByStates(states: List<QuestState>): List<QuestLogEntity>
}
