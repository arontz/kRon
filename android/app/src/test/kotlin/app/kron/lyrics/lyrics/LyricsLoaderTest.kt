package app.kron.lyrics.lyrics

import app.kron.lyrics.model.LrcLine
import app.kron.lyrics.model.LyricsResult
import app.kron.lyrics.model.Track
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
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

    /** Returns [results] in order, then repeats the last one; counts how often it was asked. */
    private class ScriptedFetcher(private vararg val results: LyricsResult) : LyricsFetcher {
        var calls = 0
        override suspend fun fetch(track: Track): LyricsResult = results[minOf(calls++, results.size - 1)]
    }

    @Test
    fun aServiceErrorIsUnavailableNotMissing() = runTest {
        val loader = LyricsLoader(backgroundScope, ScriptedFetcher(LyricsResult.Error))
        loader.load(a)
        runCurrent()
        assertEquals(LyricsUiState.Unavailable, loader.state.value)
    }

    @Test
    fun anUnavailableServiceIsRetriedUntilItAnswers() = runTest {
        val fetcher = ScriptedFetcher(LyricsResult.Error, LyricsResult.Error, LyricsResult.Synced(linesA))
        val loader = LyricsLoader(backgroundScope, fetcher, retryDelaysMs = listOf(15_000, 30_000))
        loader.load(a)
        runCurrent()
        assertEquals(1, fetcher.calls)

        advanceTimeBy(14_999); runCurrent()
        assertEquals(1, fetcher.calls)
        advanceTimeBy(1); runCurrent()
        assertEquals(2, fetcher.calls)
        assertEquals(LyricsUiState.Unavailable, loader.state.value)

        advanceTimeBy(30_000); runCurrent()
        assertEquals(3, fetcher.calls)
        assertEquals(LyricsUiState.Ready(linesA), loader.state.value)
    }

    @Test
    fun retriesKeepTheLastDelayOnceTheListRunsOut() = runTest {
        val fetcher = ScriptedFetcher(LyricsResult.Error)
        val loader = LyricsLoader(backgroundScope, fetcher, retryDelaysMs = listOf(10, 20))
        loader.load(a)
        runCurrent()
        advanceTimeBy(10 + 20 + 20); runCurrent()
        assertEquals(4, fetcher.calls)
    }

    @Test
    fun noLyricsExistIsNeverRetried() = runTest {
        for (result in listOf(LyricsResult.NotFound, LyricsResult.NotSynced)) {
            val fetcher = ScriptedFetcher(result)
            val loader = LyricsLoader(backgroundScope, fetcher, retryDelaysMs = listOf(1_000))
            loader.load(a)
            runCurrent()
            advanceTimeBy(600_000); runCurrent()
            assertEquals(1, fetcher.calls)
            assertEquals(LyricsUiState.Missing, loader.state.value)
        }
    }

    @Test
    fun skippingToAnotherTrackStopsTheOldTracksRetries() = runTest {
        val failing = ScriptedFetcher(LyricsResult.Error)
        val loader = LyricsLoader(backgroundScope, object : LyricsFetcher {
            override suspend fun fetch(track: Track): LyricsResult =
                if (track == a) failing.fetch(track) else LyricsResult.Synced(linesB)
        }, retryDelaysMs = listOf(1_000))
        loader.load(a)
        runCurrent()
        loader.load(b)
        runCurrent()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, failing.calls)
        assertEquals(LyricsUiState.Ready(linesB), loader.state.value)
    }

    @Test
    fun everyMissIsMissing() = runTest {
        for (result in listOf(LyricsResult.NotFound, LyricsResult.NotSynced)) {
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
    fun aFetcherThatThrowsIsUnavailableInsteadOfCrashing() = runTest {
        val loader = LyricsLoader(backgroundScope, object : LyricsFetcher {
            override suspend fun fetch(track: Track): LyricsResult = throw IllegalStateException("disk full")
        })
        loader.load(a)
        runCurrent()
        assertEquals(LyricsUiState.Unavailable, loader.state.value)
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
