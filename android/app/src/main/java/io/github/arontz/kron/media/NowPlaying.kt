package io.github.arontz.kron.media

import android.graphics.Bitmap
import android.os.SystemClock

/** What is playing right now, as the player last reported it. */
data class NowPlaying(
    val packageName: String,
    val title: String,
    val artist: String,
    val album: String,
    val art: Bitmap?,
    val durationMs: Long,
    val isPlaying: Boolean,
    /** Position at [reportedAt], from the player's last playback-state update. */
    val positionMs: Long,
    /** SystemClock.elapsedRealtime() of that update. */
    val reportedAt: Long,
    val speed: Float,
) {
    /**
     * Players report position only now and then, so between reports it is estimated from the last one,
     * the same way the Windows app runs its own clock between timeline updates.
     */
    fun positionAt(now: Long = SystemClock.elapsedRealtime()): Long {
        if (!isPlaying) return positionMs
        val estimate = positionMs + ((now - reportedAt) * speed).toLong()
        return if (durationMs > 0) estimate.coerceIn(0, durationMs) else estimate.coerceAtLeast(0)
    }
}
