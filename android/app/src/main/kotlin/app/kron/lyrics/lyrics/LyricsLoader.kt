package app.kron.lyrics.lyrics

import app.kron.lyrics.model.LrcLine
import app.kron.lyrics.model.LyricsResult
import app.kron.lyrics.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface LyricsUiState {
    data object Idle : LyricsUiState
    data object Loading : LyricsUiState
    data class Ready(val lines: List<LrcLine>) : LyricsUiState
    data object Missing : LyricsUiState
}

/** Loads lyrics for the current track; a response for a track that is no longer current is dropped. */
class LyricsLoader(
    private val scope: CoroutineScope,
    private val fetcher: LyricsFetcher,
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
            val result = fetcher.fetch(track)
            if (current != track) return@launch
            _state.value = when (result) {
                is LyricsResult.Synced -> LyricsUiState.Ready(result.lines)
                else -> LyricsUiState.Missing
            }
        }
    }
}
