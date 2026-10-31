package app.kron.lyrics.playback

import app.kron.lyrics.model.PlaybackSnapshot
import app.kron.lyrics.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionClockTest {
    @Test
    fun advancesWhilePlaying() {
        val c = PositionClock()
        c.set(10_000, 1f, atMs = 1_000)
        assertEquals(12_500, c.positionAt(3_500))
    }

    @Test
    fun respectsSpeed() {
        val c = PositionClock()
        c.set(10_000, 2f, atMs = 1_000)
        assertEquals(12_000, c.positionAt(2_000))
    }

    @Test
    fun frozenWhenPaused() {
        val c = PositionClock()
        c.set(10_000, 0f, atMs = 1_000)
        assertEquals(10_000, c.positionAt(1_000))
        assertEquals(10_000, c.positionAt(99_000))
    }

    @Test
    fun neverNegative() {
        val c = PositionClock()
        c.set(0, 1f, atMs = 5_000)
        assertEquals(0, c.positionAt(4_000))
    }

    private val track = Track("t", "a", 1000)

    private fun snap(track: Track? = this.track, playing: Boolean, updatedAtMs: Long = 0) =
        PlaybackSnapshot(track, 0, if (playing) 1f else 0f, playing, updatedAtMs)

    @Test
    fun visibleWhilePlaying() {
        assertTrue(cardVisible(snap(playing = true), nowMs = 1_000_000))
    }

    @Test
    fun visibleUntilTenSecondsAfterPause() {
        val paused = snap(playing = false, updatedAtMs = 0)
        assertTrue(cardVisible(paused, nowMs = 9_999))
        assertFalse(cardVisible(paused, nowMs = 10_000))
    }

    @Test
    fun hiddenWhenNoTrack() {
        assertFalse(cardVisible(snap(track = null, playing = true), nowMs = 0))
    }
}
