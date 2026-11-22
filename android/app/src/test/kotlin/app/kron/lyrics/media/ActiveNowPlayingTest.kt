package app.kron.lyrics.media

import app.kron.lyrics.model.PlaybackSnapshot
import app.kron.lyrics.model.Track
import app.kron.lyrics.recognition.Recognition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveNowPlayingTest {
    private val none = PlaybackSnapshot(null, 0, 0f, false, 0)
    private val session = PlaybackSnapshot(Track("From phone", "A", 200_000), 1_000, 1f, true, 10)
    private val heard = RecognizedSong(PlaybackSnapshot(Track("Heard nearby", "B", 0), 5_000, 1f, true, 20), expiresAtMs = 100)

    @Test
    fun aSongPlayingOnThePhoneWinsOverAHeardOne() {
        assertEquals(session, activeSnapshot(session, heard, nowMs = 50))
    }

    @Test
    fun aHeardSongIsShownWhenNothingPlaysOnThePhone() {
        assertEquals(heard.snapshot, activeSnapshot(none, heard, nowMs = 50))
    }

    @Test
    fun aHeardSongExpiresAtItsTime() {
        assertEquals(none, activeSnapshot(none, heard, nowMs = 100))
        assertEquals(none, activeSnapshot(none, heard, nowMs = 500))
    }

    @Test
    fun withNothingAtAllThereIsNothing() {
        assertEquals(none, activeSnapshot(none, null, nowMs = 0))
    }

    @Test
    fun aSongStartingOnThePhoneClearsTheHeardOne() {
        assertNull(recognizedAfterSession(session, heard))
    }

    @Test
    fun anEmptySessionKeepsTheHeardOneAndNothingStaysNothing() {
        assertEquals(heard, recognizedAfterSession(none, heard))
        assertNull(recognizedAfterSession(none, null))
    }

    @Test
    fun aMatchBecomesAPlayingSnapshotCountingFromTheStartOfTheClip() {
        val snap = recognizedSnapshot(Recognition.Match("Warriors", "Imagine Dragons", "Album", 152_000), captureStartElapsedMs = 9_000)
        assertEquals(Track("Warriors", "Imagine Dragons", 0), snap.track)
        assertEquals(152_000L, snap.positionMs)
        assertEquals(1f, snap.speed, 0f)
        assertEquals(true, snap.playing)
        assertEquals(9_000L, snap.updatedAtMs)
    }

    @Test
    fun theHeardSongStaysForSevenMinutes() {
        assertEquals(7 * 60_000L, RECOGNIZED_TTL_MS)
    }
}
