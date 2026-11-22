// Ported to Kotlin from shazamio-core 2.1.0, src/fingerprinting/signature_format.rs
// (https://github.com/shazamio/shazamio-core). Copyright (c) 2024 dotX12, MIT licence: see android/THIRD-PARTY-NOTICES.md.
package app.kron.lyrics.recognition.shazam

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.CRC32

private const val DATA_URI_PREFIX = "data:audio/vnd.shazam.sig;base64,"

private val HEADER_MAGIC = 0xcafe2580L.toInt()
private val HEADER_SECOND_MAGIC = 0x94119c00L.toInt()
private const val HEADER_FIXED_VALUE = 0x007c0000
private const val SAMPLE_RATE_ID_SHIFT = 27
private const val BODY_MAGIC = 0x40000000
private const val BAND_MARKER_BASE = 0x60030040

/** A frame delta that does not fit one byte is written as this byte followed by the whole frame number. */
private const val FRAME_ESCAPE = 0xff

class FrequencyPeak(
    val frame: Int,
    /** An unsigned 16-bit value. */
    val magnitude: Int,
    /** An unsigned 16-bit value, in 1/64 of an FFT bin. */
    val refinedBin: Int,
)

enum class FrequencyBand(val id: Int) {
    Hz250To520(0),
    Hz520To1450(1),
    Hz1450To3500(2),
    Hz3500To5500(3),
    ;

    companion object {
        fun fromId(id: Int): FrequencyBand = entries.first { it.id == id }

        fun containing(frequencyHz: Int): FrequencyBand? = when (frequencyHz) {
            in 250..519 -> Hz250To520
            in 520..1449 -> Hz520To1450
            in 1450..3499 -> Hz1450To3500
            in 3500..5500 -> Hz3500To5500
            else -> null
        }
    }
}

private fun sampleRateId(rate: Int): Int = when (rate) {
    8000 -> 1
    11025 -> 2
    16000 -> 3
    32000 -> 4
    44100 -> 5
    48000 -> 6
    else -> throw IllegalArgumentException("Invalid sample rate $rate for a signature")
}

private fun encodePeaks(peaks: List<FrequencyPeak>): ByteArray {
    val out = ByteBuffer.allocate(peaks.size * 9).order(ByteOrder.LITTLE_ENDIAN)
    var previousFrame = 0
    for (peak in peaks) {
        var delta = peak.frame - previousFrame
        require(delta >= 0) { "the peaks of a band are in frame order" }
        if (delta >= FRAME_ESCAPE) {
            out.put(FRAME_ESCAPE.toByte())
            out.putInt(peak.frame)
            delta = 0
        }
        out.put(delta.toByte())
        out.putShort(peak.magnitude.toShort())
        out.putShort(peak.refinedBin.toShort())
        previousFrame = peak.frame
    }
    return out.array().copyOf(out.position())
}

/** What the fingerprint of a clip holds: its peaks, grouped by frequency band, each band's list in frame order. */
class Fingerprint(
    val sampleRateHz: Int,
    val sampleCount: Int,
    val peaksByBand: Map<FrequencyBand, List<FrequencyPeak>>,
) {
    /** Truncated in single precision, as the reference reports it. */
    fun durationMs(): Int = (sampleCount.toFloat() / sampleRateHz.toFloat() * 1000f).toInt()

    fun encode(): ByteArray {
        var body = ByteArray(0)
        for ((band, peaks) in peaksByBand.entries.sortedBy { it.key.id }) {
            val encoded = encodePeaks(peaks)
            val head = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(BAND_MARKER_BASE + band.id).putInt(encoded.size).array()
            body += head + encoded
            body = body.copyOf((body.size + 3) / 4 * 4) // padded with zeros to a multiple of 4
        }

        // The body opens with its own magic and length, and both lengths count them.
        val bodyLength = body.size + 8
        // The format pads the sample count by 240 ms.
        val paddedSampleCount = sampleCount + sampleRateHz * 24 / 100

        val words = intArrayOf(
            HEADER_MAGIC, 0, bodyLength, HEADER_SECOND_MAGIC, 0, 0, 0, sampleRateId(sampleRateHz) shl SAMPLE_RATE_ID_SHIFT,
            0, 0, paddedSampleCount, HEADER_FIXED_VALUE, BODY_MAGIC, bodyLength,
        )
        val signature = ByteBuffer.allocate(words.size * 4 + body.size).order(ByteOrder.LITTLE_ENDIAN)
        words.forEach { signature.putInt(it) }
        signature.put(body)

        val bytes = signature.array()
        val checksum = CRC32().apply { update(bytes, 8, bytes.size - 8) }.value.toInt()
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(4, checksum)
        return bytes
    }

    fun toDataUri(): String = DATA_URI_PREFIX + Base64.getEncoder().encodeToString(encode())
}
