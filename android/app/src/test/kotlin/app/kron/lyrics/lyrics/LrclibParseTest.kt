package app.kron.lyrics.lyrics

import app.kron.lyrics.model.LrcLine
import app.kron.lyrics.model.LyricsResult
import org.junit.Assert.assertEquals
import org.junit.Test

class LrclibParseTest {
    @Test
    fun syncedBodyGivesSynced() {
        val body = """{"id":1,"syncedLyrics":"[00:01.00]one\n[00:02.00]two","plainLyrics":"one\ntwo"}"""
        assertEquals(
            LyricsResult.Synced(listOf(LrcLine(1000, "one"), LrcLine(2000, "two"))),
            parseLrclib(body),
        )
    }

    @Test
    fun plainOnlyGivesNotSynced() {
        assertEquals(LyricsResult.NotSynced, parseLrclib("""{"syncedLyrics":null,"plainLyrics":"just words"}"""))
        assertEquals(LyricsResult.NotSynced, parseLrclib("""{"syncedLyrics":"","plainLyrics":"just words"}"""))
    }

    @Test
    fun missingGivesNotFound() {
        assertEquals(LyricsResult.NotFound, parseLrclib("""{"statusCode":404,"name":"NotFound"}"""))
        assertEquals(LyricsResult.NotFound, parseLrclib("[]"))
    }

    @Test
    fun badJsonGivesError() {
        assertEquals(LyricsResult.Error, parseLrclib("not json"))
    }

    @Test
    fun searchPicksFirstSyncedResult() {
        val body = """[
            {"syncedLyrics":null,"plainLyrics":"plain only"},
            {"syncedLyrics":"[00:05.00]second","plainLyrics":"second"}
        ]"""
        assertEquals(LyricsResult.Synced(listOf(LrcLine(5000, "second"))), parseLrclibSearch(body))
    }

    @Test
    fun searchWithOnlyPlainGivesNotSynced() {
        assertEquals(
            LyricsResult.NotSynced,
            parseLrclibSearch("""[{"syncedLyrics":null,"plainLyrics":"words"}]"""),
        )
    }

    @Test
    fun searchWithNothingGivesNotFound() {
        assertEquals(LyricsResult.NotFound, parseLrclibSearch("[]"))
        assertEquals(LyricsResult.Error, parseLrclibSearch("oops"))
    }
}
