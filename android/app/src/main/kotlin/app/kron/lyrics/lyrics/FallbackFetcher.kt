package app.kron.lyrics.lyrics

import app.kron.lyrics.model.LyricsResult
import app.kron.lyrics.model.Track

/**
 * Asks [primary] first and only asks [backup] when the primary has no synced lyrics. The better answer wins:
 * synced, then unsynced, then "not found", and an error only when both failed.
 */
class FallbackFetcher(
    private val primary: LyricsFetcher,
    private val backup: LyricsFetcher,
) : LyricsFetcher {
    override suspend fun fetch(track: Track): LyricsResult {
        val first = primary.fetch(track)
        if (first is LyricsResult.Synced) return first
        val second = backup.fetch(track)
        return if (rank(second) > rank(first)) second else first
    }

    private fun rank(r: LyricsResult) = when (r) {
        is LyricsResult.Synced -> 3
        LyricsResult.NotSynced -> 2
        LyricsResult.NotFound -> 1
        LyricsResult.Error -> 0
    }
}
