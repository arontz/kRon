package app.kron.lyrics.history

import app.kron.lyrics.model.Track

const val PLAYED_AFTER_MS = 10_000L

/**
 * Decides when a song counts as played: after it has played continuously for [thresholdMs]. Pausing or changing
 * song starts the count again, so songs that are skipped through never land in the history. A song counts once
 * until another song has played in between. Time is passed in, so this is plain logic with no clock of its own.
 */
class PlayTracker(private val thresholdMs: Long = PLAYED_AFTER_MS) {
    private var current: Track? = null
    private var playingSince: Long? = null
    private var counted = false

    fun onSnapshot(track: Track?, playing: Boolean, now: Long) {
        if (track != current) {
            current = track
            counted = false
            playingSince = if (track != null && playing) now else null
            return
        }
        if (track == null) return
        if (playing) {
            if (playingSince == null) playingSince = now
        } else {
            playingSince = null
        }
    }

    /** How long until the current song counts, or null when nothing is counting down. */
    fun remainingMs(now: Long): Long? {
        val since = playingSince
        if (current == null || counted || since == null) return null
        return (thresholdMs - (now - since)).coerceAtLeast(0)
    }

    /** The song that has just counted as played, once; otherwise null. */
    fun due(now: Long): Track? {
        if (remainingMs(now) != 0L) return null
        counted = true
        return current
    }
}
