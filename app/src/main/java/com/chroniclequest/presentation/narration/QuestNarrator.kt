package com.chroniclequest.presentation.narration

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Speaks newly-triggered quests aloud in a calm, trustworthy Korean female voice
 * ("용사님, …"). Wraps Android [TextToSpeech]; the engine initializes async, so a
 * quest requested before init is queued and spoken once ready.
 */
class QuestNarrator(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureVoice()
                ready = true
                pending?.let { speakNow(it) }
                pending = null
            } else {
                Log.w(TAG, "TTS init failed: $status")
            }
        }
    }

    /** Announce a quest. Replaces any in-progress narration. */
    fun announceQuest(title: String, description: String) {
        val text = "용사님. $title. $description"
        if (ready) speakNow(text) else pending = text
    }

    /** Stop speaking immediately (called when the user acts / the modal closes). */
    fun stop() {
        pending = null
        tts?.stop()
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }

    private fun speakNow(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    private fun configureVoice() {
        val engine = tts ?: return
        engine.language = Locale.KOREAN
        // Calm, grounded delivery for a trustworthy narrator.
        engine.setPitch(0.92f)
        engine.setSpeechRate(0.96f)
        pickKoreanFemaleVoice(engine)?.let { engine.voice = it }
    }

    /**
     * Prefer an offline Korean voice that looks female by name; fall back to any
     * Korean voice. Android's [Voice] API exposes no gender field, so most engines'
     * default ko-KR voice (which is already female) is a safe fallback.
     */
    private fun pickKoreanFemaleVoice(engine: TextToSpeech): Voice? {
        val korean = runCatching {
            engine.voices?.filter { it.locale.language == Locale.KOREAN.language }
        }.getOrNull().orEmpty()
        if (korean.isEmpty()) return null

        val femaleHint = korean.firstOrNull { v ->
            val n = v.name.lowercase()
            n.contains("female") || n.contains("-f-") || Regex("-x-[a-z]{2}f").containsMatchIn(n)
        }
        val offline = korean.firstOrNull { !it.isNetworkConnectionRequired }
        return femaleHint ?: offline ?: korean.minByOrNull { it.name }
    }

    companion object {
        private const val TAG = "QuestNarrator"
        private const val UTTERANCE_ID = "quest_narration"
    }
}

/** Lifecycle-aware [QuestNarrator] that shuts the engine down on disposal. */
@Composable
fun rememberQuestNarrator(): QuestNarrator {
    val context = LocalContext.current
    val narrator = remember { QuestNarrator(context) }
    DisposableEffect(Unit) {
        onDispose { narrator.shutdown() }
    }
    return narrator
}
