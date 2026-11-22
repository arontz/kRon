package app.kron.lyrics.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WavReadTest {
    @Test
    fun aWavWrittenByTheRecorderIsReadBack() {
        val pcm = byteArrayOf(1, 0, -1, -1, 0, 1, 0, -128) // 1, -1, 256, -32768
        val read = readWav(pcm16ToWav(pcm, 44_100))!!
        assertEquals(44_100, read.sampleRate)
        assertEquals(listOf<Short>(1, -1, 256, -32768), read.samples.toList())
    }

    @Test
    fun anOddTrailingByteIsIgnored() {
        val read = readWav(pcm16ToWav(byteArrayOf(5, 0, 9), 16_000))!!
        assertEquals(listOf<Short>(5), read.samples.toList())
    }

    @Test
    fun somethingThatIsNotAWavIsNull() {
        assertNull(readWav(ByteArray(0)))
        assertNull(readWav(ByteArray(20)))
        assertNull(readWav("RIFFxxxxNOPEfmt ".toByteArray()))
    }
}
