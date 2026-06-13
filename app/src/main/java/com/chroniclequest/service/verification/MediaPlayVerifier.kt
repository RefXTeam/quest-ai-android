package com.chroniclequest.service.verification

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.chroniclequest.domain.model.Quest
import com.chroniclequest.domain.model.VerificationMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verifies a "start playing media" quest. We poll [AudioManager.isMusicActive],
 * which flips true when any app drives the music stream — a permission-free proxy
 * for playback. (A richer [android.media.session.MediaSessionManager] read would
 * give per-app detail but requires notification-listener access; deferred.)
 */
@Singleton
class MediaPlayVerifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : QuestVerifier {

    override val method = VerificationMethod.MEDIA_PLAY

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val targets = ConcurrentHashMap<Long, Quest>()
    private val callbacks = ConcurrentHashMap<Long, (Long) -> Unit>()
    private var pollJob: Job? = null

    override fun start(quest: Quest, onComplete: (Long) -> Unit) {
        targets[quest.id] = quest
        callbacks[quest.id] = onComplete
        ensurePolling()
        Log.d(TAG, "Watching media-play quest #${quest.id}")
    }

    override fun stop(questId: Long) {
        targets.remove(questId)
        callbacks.remove(questId)
        if (targets.isEmpty()) {
            pollJob?.cancel()
            pollJob = null
        }
    }

    private fun ensurePolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive && targets.isNotEmpty()) {
                if (audioManager.isMusicActive) {
                    targets.keys.toList().forEach { id ->
                        callbacks[id]?.invoke(id)
                        stop(id)
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    companion object {
        private const val TAG = "MediaPlayVerifier"
        private const val POLL_INTERVAL_MS = 2_000L
    }
}
