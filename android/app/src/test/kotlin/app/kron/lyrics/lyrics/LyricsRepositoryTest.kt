package app.kron.lyrics.lyrics

import app.kron.lyrics.model.LrcLine
import app.kron.lyrics.model.LyricsResult
import app.kron.lyrics.model.Track
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LyricsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val track = Track("Song", "Artist", 200_000)

    private class CountingFetcher(val result: LyricsResult) : LyricsFetcher {
        var calls = 0
        override suspend fun fetch(track: Track): LyricsResult {
            calls++
            return result
        }
    }

    @Test
    fun syncedResultIsCachedAndServedWithoutFetching() = runTest {
        val lines = listOf(LrcLine(1_234, "one"), LrcLine(65_007, "two"))
        val fetcher = CountingFetcher(LyricsResult.Synced(lines))
        val repo = LyricsRepository(fetcher, LyricsCache(folder.newFolder("c")))

        assertEquals(LyricsResult.Synced(lines), repo.fetch(track))
        assertEquals(LyricsResult.Synced(lines), repo.fetch(track))
        assertEquals(1, fetcher.calls)
    }

    @Test
    fun missesAreNotCached() = runTest {
        val fetcher = CountingFetcher(LyricsResult.NotFound)
        val repo = LyricsRepository(fetcher, LyricsCache(folder.newFolder("c")))

        repo.fetch(track)
        repo.fetch(track)
        assertEquals(2, fetcher.calls)
    }
}
