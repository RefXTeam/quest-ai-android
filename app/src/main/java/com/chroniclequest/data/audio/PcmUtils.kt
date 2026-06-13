package com.chroniclequest.data.audio

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** PCM 16-bit conversions for the wire format Gemini expects (LE, base64). */
object PcmUtils {

    /** Pack 16-bit samples into little-endian bytes. */
    fun shortsToLittleEndianBytes(samples: ShortArray): ByteArray {
        val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) buffer.putShort(s)
        return buffer.array()
    }

    fun shortsToBase64(samples: ShortArray): String =
        Base64.encodeToString(shortsToLittleEndianBytes(samples), Base64.NO_WRAP)
}
