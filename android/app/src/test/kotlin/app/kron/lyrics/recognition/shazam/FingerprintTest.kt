package app.kron.lyrics.recognition.shazam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The Kotlin port is checked against shazamio-core's own answers for the same samples. Both do their arithmetic in
 * single-precision floating point, but their FFTs are different programs, so the last bit of a value can differ; this
 * is how closely the two must still agree.
 */
class FingerprintTest {
    private data class Agreement(val reference: Int, val matched: Int, val found: Int, val exact: Int)

    private fun compare(name: String): Agreement {
        val reference = ReferenceVectors.peaks(name)
        val found = fingerprintSamples(ReferenceVectors.wavSamples(name)).peaksByBand
        var total = 0
        var matched = 0
        var exact = 0
        for ((band, refPeaks) in reference) {
            val byFrame = (found[band] ?: emptyList()).groupBy { it.frame }
            for (r in refPeaks) {
                total++
                val candidate = byFrame[r.frame]?.firstOrNull { abs(it.refinedBin - r.refinedBin) <= 1 && abs(it.magnitude - r.magnitude) <= 2 }
                if (candidate != null) matched++
                if (candidate != null && candidate.refinedBin == r.refinedBin && candidate.magnitude == r.magnitude) exact++
            }
        }
        return Agreement(total, matched, found.values.sumOf { it.size }, exact)
    }

    @Test
    fun theReferenceFindsPeaksInBothSignals() {
        for (name in ReferenceVectors.names) assertTrue(name, ReferenceVectors.peaks(name).values.sumOf { it.size } > 50)
    }

    @Test
    fun almostEveryReferencePeakIsFoundWithTheSameFrameAndNearlyTheSameValues() {
        for (name in ReferenceVectors.names) {
            val a = compare(name)
            println("$name: reference ${a.reference} peaks, ${a.matched} matched, ${a.exact} exactly, port found ${a.found}")
            assertTrue("$name: only ${a.matched} of ${a.reference} peaks matched", a.matched >= a.reference * 0.98)
        }
    }

    @Test
    fun thePortDoesNotInventPeaks() {
        for (name in ReferenceVectors.names) {
            val a = compare(name)
            assertTrue("$name: the port found ${a.found} peaks, the reference ${a.reference}", a.found <= a.reference * 1.02 + 1)
        }
    }

    @Test
    fun silenceHasNoPeaksAndAShortInputIsFine() {
        assertEquals(0, fingerprintSamples(ShortArray(16_000)).peaksByBand.values.sumOf { it.size })
        assertEquals(0, fingerprintSamples(ShortArray(100)).peaksByBand.values.sumOf { it.size })
        assertEquals(0, fingerprintSamples(ShortArray(0)).peaksByBand.values.sumOf { it.size })
    }

    @Test
    fun theSignatureRecordsTheSampleCountAndRate() {
        val f = fingerprintSamples(ShortArray(16_000))
        assertEquals(16_000, f.sampleCount)
        assertEquals(16_000, f.sampleRateHz)
    }

    @Test
    fun peaksOfABandComeInFrameOrderSoTheEncoderCanDeltaThem() {
        val f = fingerprintSamples(ReferenceVectors.wavSamples("mix"))
        for ((_, peaks) in f.peaksByBand) assertEquals(peaks.sortedBy { it.frame }.map { it.frame }, peaks.map { it.frame })
    }
}
