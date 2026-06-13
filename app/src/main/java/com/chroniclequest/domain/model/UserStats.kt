package com.chroniclequest.domain.model

/**
 * Aggregate RPG progression shown in the HUD. EXP rolls over into levels via
 * [expForLevel]; [expIntoLevel] / [expForNextLevel] drive the progress bar.
 */
data class UserStats(
    val level: Int = 1,
    val totalExp: Int = 0,
    val gold: Int = 0,
) {
    val expIntoLevel: Int
        get() = totalExp - cumulativeExpForLevel(level)

    val expForNextLevel: Int
        get() = expForLevel(level)

    val levelProgress: Float
        get() = (expIntoLevel.toFloat() / expForNextLevel).coerceIn(0f, 1f)

    companion object {
        /** EXP required to clear [level] (gently scaling curve). */
        fun expForLevel(level: Int): Int = 100 + (level - 1) * 50

        /** Total EXP accumulated to *reach* [level]. */
        fun cumulativeExpForLevel(level: Int): Int =
            (1 until level).sumOf { expForLevel(it) }

        /** Resolve the level/overflow for a given [totalExp]. */
        fun levelForTotalExp(totalExp: Int): Int {
            var level = 1
            while (totalExp >= cumulativeExpForLevel(level + 1)) level++
            return level
        }
    }
}
