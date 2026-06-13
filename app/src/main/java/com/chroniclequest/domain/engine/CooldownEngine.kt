package com.chroniclequest.domain.engine

import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local rate-limiter that enforces a hard minimum gap between quest generations,
 * regardless of how often the LLM fires a tool call. Survives across pipeline
 * restarts within a process (state is in-memory by design — the cooldown is a
 * UX guard, not a security control).
 */
@Singleton
class CooldownEngine @Inject constructor() {

    private val lastQuestAt = AtomicLong(0)

    fun canTrigger(now: Long, cooldownMs: Long = DEFAULT_COOLDOWN_MS): Boolean {
        val last = lastQuestAt.get()
        return last == 0L || now - last >= cooldownMs
    }

    /** Record that a quest was just triggered, starting a fresh cooldown. */
    fun markTriggered(now: Long) {
        lastQuestAt.set(now)
    }

    fun millisUntilReady(now: Long, cooldownMs: Long = DEFAULT_COOLDOWN_MS): Long {
        val last = lastQuestAt.get()
        if (last == 0L) return 0
        return (cooldownMs - (now - last)).coerceAtLeast(0)
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS = 20 * 60 * 1_000L // 20 minutes
    }
}
