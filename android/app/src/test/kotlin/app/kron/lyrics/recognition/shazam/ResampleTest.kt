package app.kron.lyrics.recognition.shazam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class ResampleTest {
    private fun tone(rate: Int, hz: Double, seconds: Double, amplitude: Int = 10_000) =
        ShortArray((rate * seconds).toInt()) { (amplitude * sin(2 * PI * hz * it / rate)).toInt().toShort() }

    private fun rms(s: ShortArray, from: Int = 0, to: Int = s.size): Double {
        var sum = 0.0
        for (i in from until to) sum += s[i].toDouble() * s[i]
        return sqrt(sum / (to - from))
    }

    @Test
    fun theLengthFollowsTheRates() {
        assertEquals(160_000, resampleTo16k(ShortArray(441_000), 44_100).size)
        assertEquals(160_000, resampleTo16k(ShortArray(480_000), 48_000).size)
    }

    @Test
    fun sixteenKilohertzInputIsReturnedUnchanged() {
        val input = tone(16_000, 1_000.0, 1.0)
        assertTrue(input.contentEquals(resampleTo16k(input, 16_000)))
    }

    @Test
    fun aToneInTheMiddleOfTheBandKeepsItsLoudness() {
        val out = resampleTo16k(tone(44_100, 1_000.0, 2.0), 44_100)
        val inLoudness = 10_000 / sqrt(2.0)
        // skip the first and last 50 ms, where the filter has less to look at
        assertEquals(inLoudness, rms(out, 800, out.size - 800), inLoudness * 0.03)
    }

    @Test
    fun aToneAboveTheNewNyquistIsStronglyAttenuatedInsteadOfFoldingBack() {
        // 12 kHz cannot exist at 16 kHz (limit 8 kHz); without a low-pass it would fold down to 4 kHz.
        val out = resampleTo16k(tone(44_100, 12_000.0, 2.0), 44_100)
        val inLoudness = 10_000 / sqrt(2.0)
        assertTrue("left ${rms(out, 800, out.size - 800)} of $inLoudness", rms(out, 800, out.size - 800) < inLoudness * 0.03)
    }

    @Test
    fun theToneStaysAtTheSameFrequency() {
        val out = resampleTo16k(tone(44_100, 1_000.0, 1.0), 44_100)
        // 1 kHz at 16 kHz repeats every 16 samples; compare a sample with the one a period later
        for (i in 1_000 until 1_200) assertTrue(abs(out[i] - out[i + 16]) < 400)
    }

    @Test
    fun emptyAndTinyInputsAreFine() {
        assertEquals(0, resampleTo16k(ShortArray(0), 44_100).size)
        assertEquals(0, resampleTo16k(ShortArray(1), 44_100).size)
    }
}
