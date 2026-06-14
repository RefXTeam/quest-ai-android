package com.chroniclequest.domain.model

/**
 * Real-time verification progress for an accepted quest (e.g. steps walked,
 * minutes the screen has stayed off). [current] and [target] are in the unit
 * implied by the quest's [VerificationMethod].
 */
data class QuestProgress(
    val current: Int,
    val target: Int,
) {
    val fraction: Float
        get() = if (target <= 0) 0f else (current.toFloat() / target).coerceIn(0f, 1f)
}
