package com.chroniclequest.data.audio

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Wraps raw PCM 16-bit mono samples in a minimal WAV container so they can be
 * sent as `audio/wav` inlineData to the REST generateContent endpoint (which,
 * unlike the Live API, expects a container format rather than a raw PCM stream).
 */
object WavEncoder {

    private const val HEADER_SIZE = 44

    fun pcmToWavBytes(samples: ShortArray, sampleRate: Int = AudioConfig.SAMPLE_RATE): ByteArray {
        val dataSize = samples.size * 2
        val out = ByteArrayOutputStream(HEADER_SIZE + dataSize)

        fun writeString(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun writeIntLE(v: Int) = out.write(
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array(),
        )
        fun writeShortLE(v: Int) = out.write(
            ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array(),
        )

        val byteRate = sampleRate * 2 // mono, 16-bit
        writeString("RIFF")
        writeIntLE(36 + dataSize)
        writeString("WAVE")
        writeString("fmt ")
        writeIntLE(16)            // PCM fmt chunk size
        writeShortLE(1)           // audio format = PCM
        writeShortLE(1)           // channels = mono
        writeIntLE(sampleRate)
        writeIntLE(byteRate)
        writeShortLE(2)           // block align
        writeShortLE(16)          // bits per sample
        writeString("data")
        writeIntLE(dataSize)
        out.write(PcmUtils.shortsToLittleEndianBytes(samples))

        return out.toByteArray()
    }

    fun pcmToWavBase64(samples: ShortArray, sampleRate: Int = AudioConfig.SAMPLE_RATE): String =
        Base64.encodeToString(pcmToWavBytes(samples, sampleRate), Base64.NO_WRAP)
}
