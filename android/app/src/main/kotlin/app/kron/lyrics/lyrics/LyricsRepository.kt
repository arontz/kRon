package app.kron.lyrics.lyrics

import app.kron.lyrics.model.LrcLine
import app.kron.lyrics.model.LyricsResult
import app.kron.lyrics.model.Track

/** Serves synced lyrics from disk when it can; only synced results are kept. */
class LyricsRepository(
    private val fetcher: LyricsFetcher,
    private val cache: LyricsCache,
) : LyricsFetcher {
    override suspend fun fetch(track: Track): LyricsResult {
        cache.get(track)?.let { text ->
            val lines = parseLrc(text)
            if (lines.isNotEmpty()) return LyricsResult.Synced(lines)
        }
        val result = fetcher.fetch(track)
        if (result is LyricsResult.Synced) cache.put(track, formatLrc(result.lines))
        return result
    }
}

/** The inverse of [parseLrc], at millisecond precision. */
internal fun formatLrc(lines: List<LrcLine>): String = lines.joinToString("\n") { l ->
    "[%02d:%02d.%03d]%s".format(l.timeMs / 60_000, l.timeMs / 1_000 % 60, l.timeMs % 1_000, l.text)
}
