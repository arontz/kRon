package app.kron.lyrics.recognition

import org.junit.Assert.assertEquals
import org.junit.Test

class HeardMessageTest {
    @Test
    fun theMessageNamesTheSongAndWhereTheClipStarted() {
        val m = Recognition.Match("Warriors", "Imagine Dragons", "Album", 152_000)
        assertEquals("AudD recognized \"Warriors\" by Imagine Dragons, clip started at 2:32", heardLogMessage(m))
    }

    @Test
    fun aPercentSignOrOtherFormatCharactersInTheTitleOrArtistAreJustText() {
        val m = Recognition.Match("100% Pure Love %s %d", "AC%DC", "", 5_000)
        assertEquals("AudD recognized \"100% Pure Love %s %d\" by AC%DC, clip started at 0:05", heardLogMessage(m))
    }

    @Test
    fun anUnknownArtistAndLongPositionsReadWell() {
        val m = Recognition.Match("Song", "", "", 3_752_000)
        assertEquals("AudD recognized \"Song\" by unknown artist, clip started at 62:32", heardLogMessage(m))
    }
}
