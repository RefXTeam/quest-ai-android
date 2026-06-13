package com.chroniclequest.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Single-row table (id is always [SINGLETON_ID]) holding RPG progression. */
@Entity(tableName = "user_stats")
data class UserStatsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val level: Int = 1,
    val totalExp: Int = 0,
    val gold: Int = 0,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
