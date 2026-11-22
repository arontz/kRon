package app.kron.lyrics.history

import app.kron.lyrics.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayTrackerTest {
    private val a = Track("A", "x", 200_000)
    private val b = Track("B", "x", 180_000)
    private fun tracker() = PlayTracker(thresholdMs = 10_000)

    @Test
    fun aSongCountsAfterTenSecondsOfPlaying() {
        val t = tracker()
        t.onSnapshot(a, playing = true, now = 1_000)
        assertEquals(10_000L, t.remainingMs(now = 1_000))
        assertNull(t.due(now = 10_999))
        assertEquals(a, t.due(now = 11_000))
    }

    @Test
    fun pausingStartsTheCountAgain() {
        val t = tracker()
        t.onSnapshot(a, true, now = 0)
        t.onSnapshot(a, false, now = 6_000)
        assertNull(t.remainingMs(now = 7_000))
        assertNull(t.due(now = 20_000))
        t.onSnapshot(a, true, now = 20_000)
        assertNull(t.due(now = 29_999))
        assertEquals(a, t.due(now = 30_000))
    }

    @Test
    fun skippingAwayBeforeTenSecondsNeverCounts() {
        val t = tracker()
        t.onSnapshot(a, true, now = 0)
        t.onSnapshot(b, true, now = 5_000)
        assertNull(t.due(now = 10_000))
        assertEquals(b, t.due(now = 15_000))
    }

    @Test
    fun aSongIsCountedOnlyOnce() {
        val t = tracker()
        t.onSnapshot(a, true, now = 0)
        assertEquals(a, t.due(now = 10_000))
        assertNull(t.due(now = 20_000))
        assertNull(t.remainingMs(now = 20_000))
    }

    @Test
    fun pausingAndResumingACountedSongDoesNotCountItAgain() {
        val t = tracker()
        t.onSnapshot(a, true, now = 0)
        t.due(now = 10_000)
        t.onSnapshot(a, false, now = 11_000)
        t.onSnapshot(a, true, now = 12_000)
        assertNull(t.due(now = 60_000))
    }

    @Test
    fun repeatingTheSameSongDoesNotCountItAgain() {
        val t = tracker()
        t.onSnapshot(a, true, now = 0)
        t.due(now = 10_000)
        t.onSnapshot(a, true, now = 200_000) // the same track restarted
        assertNull(t.due(now = 300_000))
    }

    @Test
    fun comingBackToASongAfterAnotherCountsItAgain() {
        val t = tracker()
        t.onSnapshot(a, true, now = 0)
        t.due(now = 10_000)
        t.onSnapshot(b, true, now = 20_000)
        t.due(now = 30_000)
        t.onSnapshot(a, true, now = 40_000)
        assertEquals(a, t.due(now = 50_000))
    }

    @Test
    fun aPausedSongIsNeverDueAndNoTrackResetsEverything() {
        val t = tracker()
        t.onSnapshot(a, false, now = 0)
        assertNull(t.due(now = 100_000))
        t.onSnapshot(null, false, now = 100_000)
        assertNull(t.remainingMs(now = 100_000))
        t.onSnapshot(a, true, now = 110_000)
        assertEquals(a, t.due(now = 120_000))
    }

    @Test
    fun remainingShrinksAsTimePasses() {
        val t = tracker()
        t.onSnapshot(a, true, now = 0)
        assertEquals(4_000L, t.remainingMs(now = 6_000))
        assertEquals(0L, t.remainingMs(now = 15_000))
    }
}
