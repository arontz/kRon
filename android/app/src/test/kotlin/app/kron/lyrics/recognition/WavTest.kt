package app.kron.lyrics.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavTest {
    private fun text(b: ByteArray, at: Int) = String(b, at, 4, Charsets.US_ASCII)
    private fun le(b: ByteArray) = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)

    @Test
    fun theHeaderDescribesMonoSixteenBitAudio() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val wav = pcm16ToWav(pcm, sampleRate = 44_100)
        val buf = le(wav)

        assertEquals(44 + 4, wav.size)
        assertEquals("RIFF", text(wav, 0))
        assertEquals(36 + 4, buf.getInt(4))
        assertEquals("WAVE", text(wav, 8))
        assertEquals("fmt ", text(wav, 12))
        assertEquals(16, buf.getInt(16))
        assertEquals(1, buf.getShort(20).toInt()) // plain PCM
        assertEquals(1, buf.getShort(22).toInt()) // mono
        assertEquals(44_100, buf.getInt(24))
        assertEquals(88_200, buf.getInt(28)) // bytes per second
        assertEquals(2, buf.getShort(32).toInt()) // bytes per frame
        assertEquals(16, buf.getShort(34).toInt())
        assertEquals("data", text(wav, 36))
        assertEquals(4, buf.getInt(40))
    }

    @Test
    fun theAudioFollowsTheHeaderUnchanged() {
        val pcm = ByteArray(1000) { (it % 251).toByte() }
        val wav = pcm16ToWav(pcm, 16_000)
        assertTrue(pcm.contentEquals(wav.copyOfRange(44, wav.size)))
    }

    @Test
    fun noAudioStillGivesAValidHeader() {
        val wav = pcm16ToWav(ByteArray(0), 44_100)
        assertEquals(44, wav.size)
        assertEquals(0, le(wav).getInt(40))
    }

    private fun samples(vararg s: Int): ByteArray {
        val b = ByteBuffer.allocate(s.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        s.forEach { b.putShort(it.toShort()) }
        return b.array()
    }

    @Test
    fun silenceHasNoLevel() {
        assertEquals(0f, rmsLevel(samples(0, 0, 0, 0), 8), 0f)
        assertEquals(0f, rmsLevel(ByteArray(0), 0), 0f)
    }

    @Test
    fun aLoudSquareWaveIsNearFullLevel() {
        assertEquals(1f, rmsLevel(samples(32_767, -32_768, 32_767, -32_768), 8), 0.01f)
    }

    @Test
    fun halfAmplitudeIsAboutHalfTheLevel() {
        assertEquals(0.5f, rmsLevel(samples(16_384, -16_384, 16_384, -16_384), 8), 0.01f)
    }

    @Test
    fun onlyTheBytesReadAreCountedAndAnOddByteIsIgnored() {
        val buf = samples(16_384, 16_384, 32_767, 32_767)
        assertEquals(0.5f, rmsLevel(buf, 4), 0.01f)
        assertEquals(0.5f, rmsLevel(buf, 5), 0.01f)
    }
}
