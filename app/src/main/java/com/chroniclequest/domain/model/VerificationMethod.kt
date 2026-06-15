package com.chroniclequest.domain.model

/**
 * How a quest's completion is proven. Each value maps to a verifier inside the
 * QuestVerificationManager (Step 6).
 */
enum class VerificationMethod {
    /** Keep the screen off for [Quest.targetValue] minutes. */
    SCREEN_OFF,

    /** Walk [Quest.targetValue] steps, measured as a delta from quest start. */
    STEP_COUNT,

    /** Start media/music playback. */
    MEDIA_PLAY,

    /** User self-reports completion via an in-app check-in. */
    USER_MANUAL;

    companion object {
        fun fromOrNull(raw: String?): VerificationMethod? {
            val key = raw?.trim() ?: return null
            // triggerDynamicQuest uses USER_CHECK for the same concept as USER_MANUAL.
            if (key.equals("USER_CHECK", ignoreCase = true)) return USER_MANUAL
            return entries.firstOrNull { it.name.equals(key, ignoreCase = true) }
        }
    }
}
