package com.chroniclequest.domain.model

/**
 * Lifecycle of a quest, doubling as the implicit-feedback signal recorded in the
 * flywheel log (Component E).
 */
enum class QuestState {
    /** The LLM proposed it and it was surfaced to the user. */
    TRIGGERED,

    /** User tapped "Accept" — verification is now armed. */
    ACCEPTED,

    /** User swiped it away / declined. */
    DISMISSED,

    /** Accepted but the verification window elapsed without success. */
    EXPIRED,

    /** Verified complete; rewards granted. */
    COMPLETED;

    val isTerminal: Boolean
        get() = this == DISMISSED || this == EXPIRED || this == COMPLETED
}
