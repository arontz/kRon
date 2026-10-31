package app.kron.lyrics.lyrics

import app.kron.lyrics.log.LogKind
import app.kron.lyrics.log.activityLog
import app.kron.lyrics.model.LrcLine
import app.kron.lyrics.model.LyricsResult
import app.kron.lyrics.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Serves synced lyrics from disk when it can; only synced results are kept. A broken cache is ignored. */
class LyricsRepository(
    private val fetcher: LyricsFetcher,
    private val cache: LyricsCache,
) : LyricsFetcher {
    override suspend fun fetch(track: Track): LyricsResult {
        withContext(Dispatchers.IO) { runCatching { cache.get(track) }.getOrNull() }?.let { text ->
            val lines = parseLrc(text)
            if (lines.isNotEmpty()) {
                activityLog.add(LogKind.CACHE, "Lyrics for \"${track.title}\" came from this device, no network used")
                return LyricsResult.Synced(lines)
            }
        }
        val result = fetcher.fetch(track)
        if (result is LyricsResult.Synced) {
            withContext(Dispatchers.IO) { runCatching { cache.put(track, formatLrc(result.lines)) } }
        }
        return result
    }
}

/** The inverse of [parseLrc], at millisecond precision. */
internal fun formatLrc(lines: List<LrcLine>): String = lines.joinToString("\n") { l ->
    "[%02d:%02d.%03d]%s".format(l.timeMs / 60_000, l.timeMs / 1_000 % 60, l.timeMs % 1_000, l.text)
}
