package app.kron.lyrics.recognition

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Wraps raw 16-bit mono PCM in a WAV header, which is what the recognition service is sent. */
fun pcm16ToWav(pcm: ByteArray, sampleRate: Int): ByteArray {
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    header.put("RIFF".toByteArray(Charsets.US_ASCII))
    header.putInt(36 + pcm.size)
    header.put("WAVE".toByteArray(Charsets.US_ASCII))
    header.put("fmt ".toByteArray(Charsets.US_ASCII))
    header.putInt(16) // size of the format block
    header.putShort(1) // plain PCM
    header.putShort(1) // mono
    header.putInt(sampleRate)
    header.putInt(sampleRate * 2) // bytes per second
    header.putShort(2) // bytes per frame
    header.putShort(16) // bits per sample
    header.put("data".toByteArray(Charsets.US_ASCII))
    header.putInt(pcm.size)
    return header.array() + pcm
}

/** How loud the first [length] bytes of 16-bit little-endian PCM are, from 0 (silence) to 1 (full scale). */
fun rmsLevel(buffer: ByteArray, length: Int): Float {
    val samples = minOf(length, buffer.size) / 2
    if (samples == 0) return 0f
    val data = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
    var sum = 0.0
    for (i in 0 until samples) {
        val s = data.getShort(i * 2).toDouble()
        sum += s * s
    }
    return (sqrt(sum / samples) / 32_768.0).toFloat().coerceIn(0f, 1f)
}

class WavAudio(val sampleRate: Int, val samples: ShortArray)

/** Reads mono 16-bit PCM out of a WAV, or null when it is not one. An odd trailing byte is ignored. */
fun readWav(bytes: ByteArray): WavAudio? {
    if (bytes.size < 12 || String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF" || String(bytes, 8, 4, Charsets.US_ASCII) != "WAVE") return null
    val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    var sampleRate = 0
    var position = 12
    while (position + 8 <= bytes.size) {
        val id = String(bytes, position, 4, Charsets.US_ASCII)
        val size = data.getInt(position + 4)
        val body = position + 8
        if (size < 0) return null
        if (id == "fmt " && body + 8 <= bytes.size) sampleRate = data.getInt(body + 4)
        if (id == "data") {
            if (sampleRate <= 0) return null
            val end = minOf(bytes.size, body + size)
            val samples = ShortArray((end - body) / 2) { data.getShort(body + it * 2) }
            return WavAudio(sampleRate, samples)
        }
        position = body + size + (size and 1)
    }
    return null
}
