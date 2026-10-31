package app.kron.lyrics.media

import android.media.session.PlaybackState
import app.kron.lyrics.model.PlaybackSnapshot
import app.kron.lyrics.model.Track

data class SessionInfo(val packageName: String, val playing: Boolean, val lastUpdateMs: Long)

/** A session that is playing wins, then the most recently updated one. */
fun pickSession(candidates: List<SessionInfo>): SessionInfo? =
    candidates.filter { it.playing }.maxByOrNull { it.lastUpdateMs }
        ?: candidates.maxByOrNull { it.lastUpdateMs }

fun isPlayingState(state: Int): Boolean =
    state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING

fun toSnapshot(
    title: String?,
    artist: String?,
    durationMs: Long,
    state: Int,
    positionMs: Long,
    speed: Float,
    updateTimeMs: Long,
): PlaybackSnapshot {
    val playing = isPlayingState(state)
    return PlaybackSnapshot(
        track = title?.takeIf { it.isNotBlank() }?.let { Track(it, artist.orEmpty(), durationMs) },
        positionMs = positionMs,
        speed = when {
            !playing -> 0f
            speed == 0f -> 1f
            else -> speed
        },
        playing = playing,
        updatedAtMs = updateTimeMs,
    )
}
