package app.kron.lyrics.model

import kotlinx.coroutines.flow.StateFlow

data class LrcLine(val timeMs: Long, val text: String)

/** [durationMs] <= 0 means the duration is unknown. */
data class Track(val title: String, val artist: String, val durationMs: Long)

sealed interface LyricsResult {
    data class Synced(val lines: List<LrcLine>) : LyricsResult
    data object NotSynced : LyricsResult
    data object NotFound : LyricsResult
    data object Error : LyricsResult
}

/** [updatedAtMs] is on the `SystemClock.elapsedRealtime()` timeline. */
data class PlaybackSnapshot(
    val track: Track?,
    val positionMs: Long,
    val speed: Float,
    val playing: Boolean,
    val updatedAtMs: Long,
)

/** Where the card learns what is playing; media sessions today, song recognition later. */
interface NowPlayingSource {
    val snapshot: StateFlow<PlaybackSnapshot>
}
