package com.chroniclequest.service

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.chroniclequest.data.audio.AudioCaptureManager
import com.chroniclequest.data.audio.VadProcessor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import javax.inject.Inject

/**
 * Component A — the always-on ambient capture engine.
 *
 * Runs as a `microphone` foreground service: captures PCM, applies the local VAD
 * gate, and (Step 4) forwards voiced chunks to the Gemini Live pipeline. Releases
 * the mic automatically when it loses audio focus — e.g. an incoming phone call.
 */
@AndroidEntryPoint
class AmbientAudioService : LifecycleService() {

    @Inject lateinit var audioCaptureManager: AudioCaptureManager
    @Inject lateinit var vadProcessor: VadProcessor
    @Inject lateinit var ambientPipeline: AmbientPipeline
    @Inject lateinit var verificationManager: QuestVerificationManager
    @Inject lateinit var webServer: MonitorWebServer

    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var captureJob: Job? = null
    private var hasFocus = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            // Transient loss = a phone/VoIP call or nav prompt grabbed audio.
            // Pause the mic (spec: release on call) and resume when focus returns.
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Transient focus loss (call?) — pausing capture")
                hasFocus = false
                stopCapture()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus regained — resuming capture")
                hasFocus = true
                startCapture()
            }
            // Permanent loss (another app started media) or duck: keep capturing so
            // ambient detection survives in the background while music/video plays.
            else -> {
                Log.d(TAG, "Focus change $change — keeping ambient capture alive")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelfSafely()
                return START_NOT_STICKY
            }
            else -> startListening()
        }
        return START_STICKY
    }

    private fun startListening() {
        startAsForeground()
        _isRunning.value = true
        verificationManager.restoreArmed()
        webServer.start()
        ambientPipeline.start(lifecycleScope)
        if (requestAudioFocus()) {
            hasFocus = true
            startCapture()
        }
    }

    private fun startAsForeground() {
        val notification = ServiceNotifications.buildForegroundNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                ServiceNotifications.FOREGROUND_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(ServiceNotifications.FOREGROUND_ID, notification)
        }
    }

    private fun startCapture() {
        if (captureJob?.isActive == true) return
        captureJob = audioCaptureManager.pcmChunks()
            .onEach { chunk ->
                if (vadProcessor.isVoiced(chunk)) {
                    ambientPipeline.onVoicedChunk(chunk)
                } else {
                    ambientPipeline.onSilenceChunk()
                }
            }
            .catch { e -> Log.e(TAG, "Capture flow error", e) }
            .launchIn(lifecycleScope)
    }

    private fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
    }

    private fun requestAudioFocus(): Boolean {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = request
        val result = audioManager.requestAudioFocus(request)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun stopSelfSafely() {
        stopCapture()
        abandonAudioFocus()
        ambientPipeline.stop()
        webServer.stop()
        _isRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopCapture()
        abandonAudioFocus()
        ambientPipeline.stop()
        webServer.stop()
        _isRunning.value = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AmbientAudioService"
        const val ACTION_START = "com.chroniclequest.action.START_LISTENING"
        const val ACTION_STOP = "com.chroniclequest.action.STOP_LISTENING"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun startIntent(context: Context) = Intent(context, AmbientAudioService::class.java).apply {
            action = ACTION_START
        }

        fun stopIntent(context: Context) = Intent(context, AmbientAudioService::class.java).apply {
            action = ACTION_STOP
        }
    }
}
