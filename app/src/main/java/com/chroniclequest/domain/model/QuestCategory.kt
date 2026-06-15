package com.chroniclequest.domain.model

/**
 * Quest category from the `triggerDynamicQuest` universal function. Optional —
 * legacy `giveUserQuest` produces quests with a null category.
 */
enum class QuestCategory(val label: String, val emoji: String) {
    HEALTH("건강", "🏃"),
    STUDY("학습", "📚"),
    REST("휴식", "🌙"),
    SOCIAL("소셜", "💬");

    companion object {
        fun fromOrNull(raw: String?): QuestCategory? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}
