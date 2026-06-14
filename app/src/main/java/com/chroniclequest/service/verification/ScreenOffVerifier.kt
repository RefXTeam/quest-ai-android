package com.chroniclequest.service.verification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.chroniclequest.domain.model.Quest
import com.chroniclequest.domain.model.QuestProgress
import com.chroniclequest.domain.model.VerificationMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verifies a "keep the screen off for N minutes" quest. While any such quest is
 * armed we listen for [Intent.ACTION_SCREEN_OFF] / [Intent.ACTION_SCREEN_ON]:
 * screen-off starts a per-quest countdown; screen-on cancels it (the streak
 * broke) so the user must try again. When a countdown survives to its target the
 * quest completes.
 */
@Singleton
class ScreenOffVerifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : QuestVerifier {

    override val method = VerificationMethod.SCREEN_OFF

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val targets = ConcurrentHashMap<Long, Quest>()
    private val countdowns = ConcurrentHashMap<Long, Job>()
    private val callbacks = ConcurrentHashMap<Long, (Long) -> Unit>()
    // Epoch millis when the current screen-off streak began (absent = screen is on).
    private val streakStart = ConcurrentHashMap<Long, Long>()
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> startCountdowns()
                Intent.ACTION_SCREEN_ON -> cancelCountdowns()
            }
        }
    }

    override fun start(quest: Quest, onComplete: (Long) -> Unit) {
        targets[quest.id] = quest
        callbacks[quest.id] = onComplete
        ensureReceiver()
        Log.d(TAG, "Watching screen-off quest #${quest.id} (${quest.targetValue}m)")
    }

    override fun stop(questId: Long) {
        targets.remove(questId)
        callbacks.remove(questId)
        countdowns.remove(questId)?.cancel()
        streakStart.remove(questId)
        if (targets.isEmpty()) teardownReceiver()
    }

    override fun progress(questId: Long, now: Long): QuestProgress? {
        val quest = targets[questId] ?: return null
        val start = streakStart[questId]
        val minutesOff = if (start != null) ((now - start) / 60_000L).toInt() else 0
        return QuestProgress(current = minutesOff, target = quest.targetValue)
    }

    private fun startCountdowns() {
        val now = System.currentTimeMillis()
        targets.values.forEach { quest ->
            streakStart.putIfAbsent(quest.id, now)
            if (countdowns[quest.id]?.isActive == true) return@forEach
            countdowns[quest.id] = scope.launch {
                delay(TimeUnit.MINUTES.toMillis(quest.targetValue.toLong()))
                callbacks[quest.id]?.invoke(quest.id)
                stop(quest.id)
            }
        }
    }

    private fun cancelCountdowns() {
        countdowns.values.forEach { it.cancel() }
        countdowns.clear()
        streakStart.clear()
    }

    private fun ensureReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        context.registerReceiver(receiver, filter)
        receiverRegistered = true
    }

    private fun teardownReceiver() {
        if (!receiverRegistered) return
        runCatching { context.unregisterReceiver(receiver) }
        receiverRegistered = false
    }

    companion object {
        private const val TAG = "ScreenOffVerifier"
    }
}
