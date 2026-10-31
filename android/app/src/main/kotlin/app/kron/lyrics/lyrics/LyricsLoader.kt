package app.kron.lyrics.lyrics

import app.kron.lyrics.model.LrcLine
import app.kron.lyrics.model.LyricsResult
import app.kron.lyrics.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface LyricsUiState {
    data object Idle : LyricsUiState
    data object Loading : LyricsUiState
    data class Ready(val lines: List<LrcLine>) : LyricsUiState

    /** The lyrics services answered, and none has lyrics for this song. */
    data object Missing : LyricsUiState

    /** The lyrics services could not be reached or failed, so there is no answer yet; the loader keeps retrying. */
    data object Unavailable : LyricsUiState
}

/** 15 s, 30 s, 1 min, 2 min, then every 5 min. */
val DEFAULT_RETRY_DELAYS_MS = listOf(15_000L, 30_000L, 60_000L, 120_000L, 300_000L)

/**
 * Loads lyrics for the current track; a response for a track that is no longer current is dropped. While the
 * services are unavailable it retries with growing pauses, the last delay repeating, until one answers.
 */
class LyricsLoader(
    private val scope: CoroutineScope,
    private val fetcher: LyricsFetcher,
    private val retryDelaysMs: List<Long> = DEFAULT_RETRY_DELAYS_MS,
) {
    private val _state = MutableStateFlow<LyricsUiState>(LyricsUiState.Idle)
    val state: StateFlow<LyricsUiState> = _state

    private var job: Job? = null
    private var current: Track? = null

    fun load(track: Track?) {
        if (track != null && track == current) return
        job?.cancel()
        current = track
        if (track == null) {
            _state.value = LyricsUiState.Idle
            return
        }
        _state.value = LyricsUiState.Loading
        job = scope.launch {
            var attempt = 0
            while (true) {
                val result = try {
                    fetcher.fetch(track)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    LyricsResult.Error // lyrics are a nicety; a failure must never take the app down
                }
                if (current != track) return@launch
                when (result) {
                    is LyricsResult.Synced -> {
                        _state.value = LyricsUiState.Ready(result.lines)
                        return@launch
                    }
                    LyricsResult.NotFound, LyricsResult.NotSynced -> {
                        _state.value = LyricsUiState.Missing
                        return@launch
                    }
                    LyricsResult.Error -> _state.value = LyricsUiState.Unavailable
                }
                delay(retryDelaysMs.getOrElse(attempt++) { retryDelaysMs.last() })
            }
        }
    }
}
