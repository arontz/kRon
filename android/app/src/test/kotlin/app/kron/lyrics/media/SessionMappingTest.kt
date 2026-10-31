package app.kron.lyrics.media

import android.media.session.PlaybackState
import app.kron.lyrics.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionMappingTest {
    private fun s(pkg: String, playing: Boolean, at: Long) = SessionInfo(pkg, playing, at)

    @Test
    fun playingBeatsANewerPausedSession() {
        val picked = pickSession(listOf(s("paused", false, 900), s("playing", true, 100)))
        assertEquals("playing", picked?.packageName)
    }

    @Test
    fun newestPlayingWins() {
        val picked = pickSession(listOf(s("old", true, 100), s("new", true, 200)))
        assertEquals("new", picked?.packageName)
    }

    @Test
    fun newestPausedWinsWhenNothingPlays() {
        val picked = pickSession(listOf(s("old", false, 100), s("new", false, 200)))
        assertEquals("new", picked?.packageName)
    }

    @Test
    fun noSessionsGivesNull() {
        assertNull(pickSession(emptyList()))
    }

    @Test
    fun playingSnapshotKeepsPositionAndSpeed() {
        val snap = toSnapshot("Song", "Artist", 200_000, PlaybackState.STATE_PLAYING, 42_000, 1f, 7_000)
        assertEquals(Track("Song", "Artist", 200_000), snap.track)
        assertTrue(snap.playing)
        assertEquals(1f, snap.speed, 0f)
        assertEquals(42_000, snap.positionMs)
        assertEquals(7_000, snap.updatedAtMs)
    }

    @Test
    fun pausedSnapshotHasZeroSpeed() {
        val snap = toSnapshot("Song", "Artist", 200_000, PlaybackState.STATE_PAUSED, 42_000, 1f, 7_000)
        assertFalse(snap.playing)
        assertEquals(0f, snap.speed, 0f)
    }

    @Test
    fun bufferingCountsAsPlaying() {
        val snap = toSnapshot("Song", "Artist", 200_000, PlaybackState.STATE_BUFFERING, 0, 1f, 0)
        assertTrue(snap.playing)
    }

    @Test
    fun playingWithZeroSpeedRunsAtNormalSpeed() {
        val snap = toSnapshot("Song", "Artist", 200_000, PlaybackState.STATE_PLAYING, 0, 0f, 0)
        assertEquals(1f, snap.speed, 0f)
    }

    @Test
    fun blankTitleMeansNoTrack() {
        assertNull(toSnapshot(null, "Artist", 1, PlaybackState.STATE_PLAYING, 0, 1f, 0).track)
        assertNull(toSnapshot("  ", "Artist", 1, PlaybackState.STATE_PLAYING, 0, 1f, 0).track)
    }

    @Test
    fun missingArtistBecomesEmptyAndUnknownDurationIsKept() {
        val track = toSnapshot("Song", null, -1, PlaybackState.STATE_PLAYING, 0, 1f, 0).track
        assertNotNull(track)
        assertEquals(Track("Song", "", -1), track)
    }
}
