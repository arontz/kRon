// Ported to Kotlin from shazamio-core 2.1.0, src/fingerprinting/algorithm.rs
// (https://github.com/shazamio/shazamio-core). Copyright (c) 2024 dotX12, MIT licence: see android/THIRD-PARTY-NOTICES.md.
package app.kron.lyrics.recognition.shazam

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin

/** The rate the signature is made at and declares. */
const val SIGNATURE_RATE_HZ = 16_000

private const val FRAME_LENGTH = 2048
private const val HOP_LENGTH = 128
private const val BIN_COUNT = FRAME_LENGTH / 2 + 1
private const val HISTORY_LENGTH = 256

// A frame is judged this many hops after it was computed, once every later frame its neighbourhood needs exists; its
// neighbours are read around an older reference frame.
private const val PEAK_LAG = 45
private const val REFERENCE_LAG = 48

// The neighbourhood a peak must beat. A spread value at `bin - 1` already covers bins `bin - 1` to `bin + 1`, so the
// reference bin is the one below the candidate.
private val FREQUENCY_NEIGHBOUR_DISTANCES = intArrayOf(2, 3, 6, 9)
private val TIME_NEIGHBOUR_DISTANCES = intArrayOf(4, 7, 14, 21, 28, 35, 42)

// How many hops back a new frame's spread values are carried.
private val SPREAD_BACK_LAGS = intArrayOf(1, 3, 6)

private const val FIRST_PEAK_BIN = 10
private const val LAST_PEAK_BIN = 1014
private const val MIN_PEAK_POWER = 1f / 64f

// `refinedBin` counts in 1/64 of a bin, and one bin is 8000 / 1024 Hz, so a refined bin is 125 / 1024 Hz.
private const val REFINED_BIN_HZ_NUMERATOR = 125
private const val REFINED_BIN_HZ_DENOMINATOR = 1024

private class Frame(val power: FloatArray, val spread: FloatArray)

/** Real-input FFT of [FRAME_LENGTH] points, done as a complex radix-2 transform. */
private class Fft(private val n: Int) {
    private val cosTable = DoubleArray(n / 2) { cos(2 * PI * it / n) }
    private val sinTable = DoubleArray(n / 2) { -sin(2 * PI * it / n) }
    private val reversed = IntArray(n).also { r ->
        val bits = Integer.numberOfTrailingZeros(n)
        for (i in 0 until n) r[i] = Integer.reverse(i) ushr (32 - bits)
    }

    /** Transforms [re] in place; [im] must be zero on entry and holds the imaginary parts on return. */
    fun forward(re: DoubleArray, im: DoubleArray) {
        for (i in 0 until n) {
            val j = reversed[i]
            if (j > i) {
                val t = re[i]; re[i] = re[j]; re[j] = t
            }
        }
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            for (start in 0 until n step size) {
                for (k in 0 until half) {
                    val wr = cosTable[k * step]
                    val wi = sinTable[k * step]
                    val a = start + k
                    val b = a + half
                    val tr = re[b] * wr - im[b] * wi
                    val ti = re[b] * wi + im[b] * wr
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                }
            }
            size *= 2
        }
    }
}

/** A Hann window whose zero ends fall just outside the frame, so no sample inside it is weighted to nothing. */
private fun hannWindow(): FloatArray {
    val period = (FRAME_LENGTH + 1).toDouble()
    return FloatArray(FRAME_LENGTH) { val s = sin(PI * (it + 1) / period); (s * s).toFloat() }
}

/** The scale the signature format stores a peak's magnitude in. */
private fun logMagnitude(power: Float): Float = max(ln(power.toDouble()).toFloat(), 1f / 64f) * 1477.3f + 6144.0f

/** Fits a parabola through the peak and its two neighbours and returns its vertex, in 1/64 of a bin. */
private fun refineBin(bin: Int, below: Float, peak: Float, above: Float): Int {
    val curvature = peak * 2.0f - below - above
    val vertexOffset = (above - below) * 32.0f / curvature
    return (bin * 64 + vertexOffset.toInt()) and 0xffff
}

private class Spectrogram {
    private val fft = Fft(FRAME_LENGTH)
    private val window = hannWindow()
    private val samples = ShortArray(FRAME_LENGTH)
    private val re = DoubleArray(FRAME_LENGTH)
    private val im = DoubleArray(FRAME_LENGTH)
    private val history = Array(HISTORY_LENGTH) { Frame(FloatArray(BIN_COUNT), FloatArray(BIN_COUNT)) }
    private var count = 0
    val peaks = HashMap<FrequencyBand, MutableList<FrequencyPeak>>()

    // The slots are zeroed up front and a lag may reach one not written yet, which reads as silence.
    private fun slot(lag: Int) = (count + HISTORY_LENGTH - 1 - lag) % HISTORY_LENGTH

    private fun ago(lag: Int) = history[slot(lag)]

    fun pushHop(hop: ShortArray, offset: Int) {
        System.arraycopy(samples, HOP_LENGTH, samples, 0, FRAME_LENGTH - HOP_LENGTH)
        System.arraycopy(hop, offset, samples, FRAME_LENGTH - HOP_LENGTH, HOP_LENGTH)

        val power = powerSpectrum()
        val spread = power.copyOf()
        for (bin in 0 until BIN_COUNT - 2) spread[bin] = max(max(power[bin], power[bin + 1]), power[bin + 2])

        count++
        for (lag in SPREAD_BACK_LAGS) {
            val earlier = ago(lag).spread
            for (i in 0 until BIN_COUNT) if (spread[i] > earlier[i]) earlier[i] = spread[i]
        }
        history[slot(0)] = Frame(power, spread)

        if (count > PEAK_LAG) pickPeaks()
    }

    private fun powerSpectrum(): FloatArray {
        for (i in 0 until FRAME_LENGTH) {
            re[i] = (samples[i].toFloat() * window[i]).toDouble()
            im[i] = 0.0
        }
        fft.forward(re, im)
        return FloatArray(BIN_COUNT) { max((re[it] * re[it] + im[it] * im[it]).toFloat() / 131072.0f, 1e-10f) }
    }

    private fun pickPeaks() {
        val frameIndex = count - 1 - PEAK_LAG
        val candidate = ago(PEAK_LAG).power
        val reference = ago(REFERENCE_LAG).spread
        val timeNeighbours = ArrayList<FloatArray>(TIME_NEIGHBOUR_DISTANCES.size * 2)
        for (distance in TIME_NEIGHBOUR_DISTANCES) {
            timeNeighbours += ago(REFERENCE_LAG - distance).spread
            timeNeighbours += ago(REFERENCE_LAG + distance).spread
        }

        for (bin in FIRST_PEAK_BIN..LAST_PEAK_BIN) {
            val power = candidate[bin]
            val below = bin - 1
            if (power < MIN_PEAK_POWER || power < reference[below]) continue

            var beatsFrequencyNeighbours = true
            for (distance in FREQUENCY_NEIGHBOUR_DISTANCES) {
                if (!(power > reference[below - distance] && power > reference[below + distance])) {
                    beatsFrequencyNeighbours = false
                    break
                }
            }
            if (!beatsFrequencyNeighbours) continue
            if (timeNeighbours.any { !(power > it[below]) }) continue

            val magnitude = logMagnitude(power)
            val refinedBin = refineBin(bin, logMagnitude(candidate[bin - 1]), magnitude, logMagnitude(candidate[bin + 1]))
            val frequencyHz = refinedBin * REFINED_BIN_HZ_NUMERATOR / REFINED_BIN_HZ_DENOMINATOR
            val band = FrequencyBand.containing(frequencyHz) ?: continue

            peaks.getOrPut(band) { ArrayList() } += FrequencyPeak(frameIndex, magnitude.toInt().coerceIn(0, 0xffff), refinedBin)
        }
    }
}

/** The fingerprint of 16 kHz mono 16-bit samples. A trailing part of a hop is dropped, as frames advance a whole hop at a time. */
fun fingerprintSamples(samples: ShortArray): Fingerprint {
    val spectrogram = Spectrogram()
    var offset = 0
    while (offset + HOP_LENGTH <= samples.size) {
        spectrogram.pushHop(samples, offset)
        offset += HOP_LENGTH
    }
    return Fingerprint(SIGNATURE_RATE_HZ, samples.size, spectrogram.peaks)
}
