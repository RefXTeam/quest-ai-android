package com.chroniclequest.data.audio

import android.annotation.SuppressLint
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps [AudioRecord] and emits fixed-size PCM 16-bit chunks as a cold [Flow].
 * The recorder is created/started when collection begins and fully released when
 * collection stops or the scope is cancelled (e.g. on a phone-call interruption).
 *
 * Caller MUST hold RECORD_AUDIO permission before collecting.
 */
@Singleton
class AudioCaptureManager @Inject constructor() {

    @SuppressLint("MissingPermission")
    fun pcmChunks(): Flow<ShortArray> = callbackFlow {
        val minBuffer = AudioRecord.getMinBufferSize(
            AudioConfig.SAMPLE_RATE,
            AudioConfig.CHANNEL_CONFIG,
            AudioConfig.AUDIO_FORMAT,
        )
        val bufferSize = maxOf(minBuffer, AudioConfig.SAMPLES_PER_CHUNK * 2)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            AudioConfig.SAMPLE_RATE,
            AudioConfig.CHANNEL_CONFIG,
            AudioConfig.AUDIO_FORMAT,
            bufferSize,
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            close(IllegalStateException("AudioRecord failed to initialize"))
            return@callbackFlow
        }

        val chunk = ShortArray(AudioConfig.SAMPLES_PER_CHUNK)
        recorder.startRecording()
        Log.d(TAG, "AudioRecord started (buffer=$bufferSize)")

        try {
            while (coroutineContext.isActive) {
                val read = recorder.read(chunk, 0, chunk.size)
                if (read > 0) {
                    // Copy so downstream isn't racing the reused buffer.
                    trySend(chunk.copyOf(read))
                } else if (read < 0) {
                    Log.w(TAG, "AudioRecord.read error=$read")
                    break
                }
            }
        } finally {
            runCatching {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            }
            recorder.release()
            Log.d(TAG, "AudioRecord released")
        }

        awaitClose {
            runCatching {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            }
            recorder.release()
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        private const val TAG = "AudioCaptureManager"
    }
}
