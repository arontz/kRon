package app.kron.lyrics.recognition.shazam

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TAPS = 24
private const val CUTOFF_HZ = 7_500.0

/**
 * Converts mono 16-bit samples to the 16 kHz the signature is made at. A Hann-windowed sinc low-pass at 7.5 kHz comes
 * first, so anything above the new limit of 8 kHz is removed instead of folding back down into the audible range.
 */
fun resampleTo16k(pcm: ShortArray, fromRate: Int): ShortArray {
    if (fromRate == SIGNATURE_RATE_HZ) return pcm
    val outLength = (pcm.size.toLong() * SIGNATURE_RATE_HZ / fromRate).toInt()
    val out = ShortArray(outLength)
    val cutoff = CUTOFF_HZ / fromRate // cycles per input sample
    // The filter only depends on how far an output sample sits between two input samples, and only a few distinct
    // positions ever occur (160 for 44.1 kHz), so each is worked out once.
    val kernels = HashMap<Int, DoubleArray>()

    for (n in 0 until outLength) {
        val position = n.toLong() * fromRate // in 1/16000 of an input sample
        val whole = (position / SIGNATURE_RATE_HZ).toInt()
        val fraction = (position % SIGNATURE_RATE_HZ).toInt()
        val kernel = kernels.getOrPut(fraction) {
            DoubleArray(2 * TAPS) { i ->
                val x = (-TAPS + 1 + i) - fraction.toDouble() / SIGNATURE_RATE_HZ
                val sinc = if (abs(x) < 1e-9) 2 * cutoff else sin(2 * PI * cutoff * x) / (PI * x)
                sinc * (0.5 + 0.5 * cos(PI * x / TAPS))
            }
        }
        var sum = 0.0
        var weight = 0.0
        for (i in kernel.indices) {
            val index = whole - TAPS + 1 + i
            if (index < 0 || index >= pcm.size) continue
            sum += pcm[index] * kernel[i]
            weight += kernel[i]
        }
        val value = if (abs(weight) > 1e-9) sum / weight else 0.0
        out[n] = value.roundToInt().coerceIn(-32768, 32767).toShort()
    }
    return out
}
