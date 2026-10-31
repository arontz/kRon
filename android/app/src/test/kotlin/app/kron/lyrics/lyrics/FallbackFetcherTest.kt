package app.kron.lyrics.lyrics

import app.kron.lyrics.model.LrcLine
import app.kron.lyrics.model.LyricsResult
import app.kron.lyrics.model.Track
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FallbackFetcherTest {
    private val track = Track("Song", "Artist", 200_000)
    private val first = LyricsResult.Synced(listOf(LrcLine(1, "from the first")))
    private val second = LyricsResult.Synced(listOf(LrcLine(2, "from the second")))

    private class Fake(val result: LyricsResult) : LyricsFetcher {
        var calls = 0
        override suspend fun fetch(track: Track): LyricsResult {
            calls++
            return result
        }
    }

    private suspend fun run(a: LyricsResult, b: LyricsResult): Triple<LyricsResult, Int, Int> {
        val fa = Fake(a)
        val fb = Fake(b)
        val result = FallbackFetcher(fa, fb).fetch(track)
        return Triple(result, fa.calls, fb.calls)
    }

    @Test
    fun syncedLyricsFromTheMainSourceAreUsedWithoutAskingTheBackup() = runTest {
        assertEquals(Triple<LyricsResult, Int, Int>(first, 1, 0), run(first, second))
    }

    @Test
    fun theBackupIsAskedWhenTheMainSourceHasNothingUseful() = runTest {
        for (miss in listOf(LyricsResult.NotFound, LyricsResult.NotSynced, LyricsResult.Error)) {
            assertEquals(Triple<LyricsResult, Int, Int>(second, 1, 1), run(miss, second))
        }
    }

    @Test
    fun syncedBeatsUnsynced() = runTest {
        assertEquals(second, run(LyricsResult.NotSynced, second).first)
    }

    @Test
    fun unsyncedBeatsNotFound() = runTest {
        assertEquals(LyricsResult.NotSynced, run(LyricsResult.NotSynced, LyricsResult.NotFound).first)
        assertEquals(LyricsResult.NotSynced, run(LyricsResult.NotFound, LyricsResult.NotSynced).first)
    }

    @Test
    fun oneAnsweredNotFoundIsEnoughEvenIfTheOtherFailed() = runTest {
        assertEquals(LyricsResult.NotFound, run(LyricsResult.Error, LyricsResult.NotFound).first)
        assertEquals(LyricsResult.NotFound, run(LyricsResult.NotFound, LyricsResult.Error).first)
    }

    @Test
    fun onlyWhenBothFailIsItAnError() = runTest {
        assertEquals(LyricsResult.Error, run(LyricsResult.Error, LyricsResult.Error).first)
    }
}
