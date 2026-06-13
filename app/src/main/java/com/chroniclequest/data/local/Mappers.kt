package com.chroniclequest.data.local

import com.chroniclequest.data.local.entity.QuestLogEntity
import com.chroniclequest.data.local.entity.UserStatsEntity
import com.chroniclequest.domain.model.Quest
import com.chroniclequest.domain.model.UserStats

fun QuestLogEntity.toDomain(): Quest = Quest(
    id = id,
    title = title,
    description = description,
    verificationMethod = verificationMethod,
    targetValue = targetValue,
    rewardExp = rewardExp,
    rewardGold = rewardGold,
    state = state,
    createdAt = timestamp,
    acceptedAt = acceptedAt,
    deadlineAt = deadlineAt,
)

fun UserStatsEntity.toDomain(): UserStats = UserStats(
    level = level,
    totalExp = totalExp,
    gold = gold,
)

fun UserStats.toEntity(): UserStatsEntity = UserStatsEntity(
    level = level,
    totalExp = totalExp,
    gold = gold,
)
