package app.kron.lyrics.log

import app.kron.lyrics.model.PlaybackSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class LogKind(val label: String) {
    MEDIA("Read"),
    NETWORK("Sent"),
    CACHE("Cache"),
    UPDATE("Update"),
}

data class LogEntry(val timeMs: Long, val kind: LogKind, val text: String)

/**
 * A short, in-memory record of everything kRon reads or sends, shown on the "Activity log" screen.
 * It is never written to disk and never goes to logcat; killing the app clears it. Newest entry first.
 */
class ActivityLog(
    private val capacity: Int = 200,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val state = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = state

    private var lastKey: String? = null // guarded by `this`

    /** Safe to call from any thread. */
    fun add(kind: LogKind, text: String) {
        val entry = LogEntry(now(), kind, text)
        state.update { (listOf(entry) + it).take(capacity) }
    }

    /** Adds [text] unless the previous [addIfChanged] call used the same [key]; players fire state changes constantly. */
    fun addIfChanged(key: String, kind: LogKind, text: String) {
        synchronized(this) {
            if (key == lastKey) return
            lastKey = key
        }
        add(kind, text)
    }

    fun clear() {
        synchronized(this) { lastKey = null }
        state.value = emptyList()
    }
}

/** The one log the whole app writes to. */
val activityLog = ActivityLog()

/** What the log says about a media-session read: only the fields kRon actually uses. */
fun describeRead(packageName: String, s: PlaybackSnapshot): String {
    val t = s.track ?: return "$packageName: media session without a title (nothing used)"
    val duration = if (t.durationMs > 0) " · ${t.durationMs / 60_000}:%02d".format(t.durationMs / 1_000 % 60) else ""
    val artist = if (t.artist.isBlank()) "unknown artist" else t.artist
    return "$packageName: \"${t.title}\" by $artist$duration · ${if (s.playing) "playing" else "paused"}"
}
