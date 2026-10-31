package app.kron.lyrics.lyrics

import app.kron.lyrics.model.LrcLine
import app.kron.lyrics.model.LyricsResult
import app.kron.lyrics.model.Track
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsLoaderTest {
    private val a = Track("A", "x", 1000)
    private val b = Track("B", "x", 1000)
    private val linesA = listOf(LrcLine(1, "from a"))
    private val linesB = listOf(LrcLine(2, "from b"))

    private class DeferredFetcher : LyricsFetcher {
        val pending = HashMap<String, CompletableDeferred<LyricsResult>>()
        fun on(track: Track) = pending.getOrPut(track.title) { CompletableDeferred() }
        override suspend fun fetch(track: Track): LyricsResult = on(track).await()
    }

    @Test
    fun noTrackIsIdle() = runTest {
        val loader = LyricsLoader(backgroundScope, DeferredFetcher())
        loader.load(null)
        runCurrent()
        assertEquals(LyricsUiState.Idle, loader.state.value)
    }

    @Test
    fun loadingThenReady() = runTest {
        val fetcher = DeferredFetcher()
        val loader = LyricsLoader(backgroundScope, fetcher)
        loader.load(a)
        runCurrent()
        assertEquals(LyricsUiState.Loading, loader.state.value)
        fetcher.on(a).complete(LyricsResult.Synced(linesA))
        runCurrent()
        assertEquals(LyricsUiState.Ready(linesA), loader.state.value)
    }

    @Test
    fun everyMissIsMissing() = runTest {
        for (result in listOf(LyricsResult.NotFound, LyricsResult.NotSynced, LyricsResult.Error)) {
            val fetcher = DeferredFetcher()
            val loader = LyricsLoader(backgroundScope, fetcher)
            loader.load(a)
            runCurrent()
            fetcher.on(a).complete(result)
            runCurrent()
            assertEquals(LyricsUiState.Missing, loader.state.value)
        }
    }

    @Test
    fun staleResponseNeverReplacesTheCurrentTrack() = runTest {
        val fetcher = DeferredFetcher()
        val loader = LyricsLoader(backgroundScope, fetcher)
        loader.load(a)
        loader.load(b)
        runCurrent()
        fetcher.on(b).complete(LyricsResult.Synced(linesB))
        runCurrent()
        fetcher.on(a).complete(LyricsResult.Synced(linesA))
        runCurrent()
        assertEquals(LyricsUiState.Ready(linesB), loader.state.value)
    }

    @Test
    fun clearingTheTrackDropsAnInFlightResponse() = runTest {
        val fetcher = DeferredFetcher()
        val loader = LyricsLoader(backgroundScope, fetcher)
        loader.load(a)
        runCurrent()
        loader.load(null)
        fetcher.on(a).complete(LyricsResult.Synced(linesA))
        runCurrent()
        assertEquals(LyricsUiState.Idle, loader.state.value)
    }
}
