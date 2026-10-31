package app.kron.lyrics.playback

import app.kron.lyrics.model.PlaybackSnapshot

/** Extrapolates the playback position between the updates a media session reports. */
class PositionClock {
    private var positionMs = 0L
    private var speed = 0f
    private var atMs = 0L

    fun set(positionMs: Long, speed: Float, atMs: Long) {
        this.positionMs = positionMs
        this.speed = speed
        this.atMs = atMs
    }

    fun positionAt(nowMs: Long): Long {
        if (speed == 0f) return positionMs.coerceAtLeast(0)
        return (positionMs + ((nowMs - atMs) * speed).toLong()).coerceAtLeast(0)
    }
}

fun cardVisible(s: PlaybackSnapshot, nowMs: Long, hideAfterPauseMs: Long = 10_000): Boolean = when {
    s.track == null -> false
    s.playing -> true
    else -> nowMs < s.updatedAtMs + hideAfterPauseMs
}
