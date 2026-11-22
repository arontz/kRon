package app.kron.lyrics.recognition.shazam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureEncodingTest {
    @Test
    fun theEncoderReproducesTheReferenceSignatureExactly() {
        for (name in ReferenceVectors.names) {
            val fingerprint = Fingerprint(sampleRateHz = 16_000, sampleCount = 64_000, peaksByBand = ReferenceVectors.peaks(name))
            assertEquals(name, ReferenceVectors.uri(name), fingerprint.toDataUri())
        }
    }

    @Test
    fun theUriIsADataUriWithTheShazamSignatureType() {
        val fingerprint = Fingerprint(16_000, 64_000, ReferenceVectors.peaks("sweep"))
        assertTrue(fingerprint.toDataUri().startsWith("data:audio/vnd.shazam.sig;base64,"))
    }

    @Test
    fun aGapOf255FramesOrMoreIsWrittenOutInFull() {
        // The reference writes a delta that does not fit one byte as an escape byte plus the whole frame number.
        val peaks = mapOf(FrequencyBand.Hz520To1450 to listOf(FrequencyPeak(10, 100, 2000), FrequencyPeak(400, 100, 2000)))
        val bytes = Fingerprint(16_000, 64_000, peaks).encode()
        // header (48) + body marker and length (8) + band tag and length (8) + peaks
        val peakBytes = bytes.copyOfRange(48 + 8 + 8, bytes.size)
        assertEquals(10, peakBytes[0].toInt()) // first peak: delta 10
        assertEquals(0xff, peakBytes[5].toInt() and 0xff) // second peak: escape
        assertEquals(400, (peakBytes[6].toInt() and 0xff) or ((peakBytes[7].toInt() and 0xff) shl 8)) // then the frame, little-endian
    }

    @Test
    fun theDurationIsTheSamplesOverTheRateInMilliseconds() {
        assertEquals(4_000, Fingerprint(16_000, 64_000, emptyMap()).durationMs())
        assertEquals(250, Fingerprint(16_000, 4_016, emptyMap()).durationMs()) // truncated, as the reference reports it
    }

    @Test
    fun aSignatureWithNoPeaksStillHasAHeader() {
        assertEquals(48 + 8, Fingerprint(16_000, 16_000, emptyMap()).encode().size)
    }
}
