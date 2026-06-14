package com.chroniclequest.service.verification

import com.chroniclequest.domain.model.Quest
import com.chroniclequest.domain.model.QuestProgress
import com.chroniclequest.domain.model.VerificationMethod

/**
 * Binds a quest to a single device feature and reports back when the goal is met.
 * One verifier instance handles all armed quests of its [method].
 */
interface QuestVerifier {
    val method: VerificationMethod

    /** Begin watching [quest]; call [onComplete] with the quest id when satisfied. */
    fun start(quest: Quest, onComplete: (Long) -> Unit)

    /** Stop watching a quest (accepted→completed/dismissed/expired). */
    fun stop(questId: Long)

    /**
     * Current verification progress for [questId], or null if this method has no
     * measurable progress (e.g. media-play / manual). [now] is epoch millis.
     */
    fun progress(questId: Long, now: Long): QuestProgress? = null
}
