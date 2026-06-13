package com.chroniclequest.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.chroniclequest.data.local.dao.QuestLogDao
import com.chroniclequest.data.local.dao.UserStatsDao
import com.chroniclequest.data.local.entity.QuestLogEntity
import com.chroniclequest.data.local.entity.UserStatsEntity

@Database(
    entities = [QuestLogEntity::class, UserStatsEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questLogDao(): QuestLogDao
    abstract fun userStatsDao(): UserStatsDao

    companion object {
        const val NAME = "chronicle_quest.db"
    }
}
