package app.kron.lyrics.history

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

/**
 * A song the user listened to: what the Lyrics tab shows, plus the length that finds its saved lyrics. Every field
 * after the title has a default, so a file written by an older or newer
 * version of kRon still loads; a later version can add, say, where the entry came from without breaking old files.
 */
@Serializable
data class PlayedSong(
    val title: String,
    val artist: String = "",
    val durationMs: Long = 0,
    val playedAtMs: Long = 0,
)

const val HISTORY_CAP = 100

private fun key(s: PlayedSong) = "${s.artist.trim().lowercase()}|${s.title.trim().lowercase()}"

/** Puts [song] first. If it was in the list already (same title and artist) that entry moves up; the oldest fall off. */
fun addPlay(list: List<PlayedSong>, song: PlayedSong, cap: Int = HISTORY_CAP): List<PlayedSong> {
    val k = key(song)
    return (listOf(song) + list.filter { key(it) != k }).take(cap)
}

/** The songs [current] already holds (recorded while the file was still loading) stay on top; [loaded] follows. */
fun mergeLoaded(current: List<PlayedSong>, loaded: List<PlayedSong>, cap: Int = HISTORY_CAP): List<PlayedSong> {
    val have = current.map(::key).toSet()
    return (current + loaded.filter { key(it) !in have }).take(cap)
}

private val json = Json { ignoreUnknownKeys = true }

fun encodeHistory(list: List<PlayedSong>): String = json.encodeToString(ListSerializer(PlayedSong.serializer()), list)

/** Reads a saved list, skipping entries that cannot be read. Anything unreadable gives an empty list, never a crash. */
fun decodeHistory(text: String?): List<PlayedSong> {
    if (text.isNullOrBlank()) return emptyList()
    val root = try {
        json.parseToJsonElement(text)
    } catch (_: Exception) {
        return emptyList()
    }
    val array = root as? JsonArray ?: return emptyList()
    // One entry per song: the list screen uses the song as its key, and two the same would crash it.
    return array.mapNotNull { runCatching { json.decodeFromJsonElement(PlayedSong.serializer(), it) }.getOrNull() }.distinctBy(::key)
}

/** "just now", "5 min ago", "3 h ago", "yesterday", "4 days ago". */
fun playedAgo(nowMs: Long, thenMs: Long): String {
    val diff = nowMs - thenMs
    val minutes = diff / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "just now"
        hours < 1 -> "$minutes min ago"
        days < 1 -> "$hours h ago"
        days == 1L -> "yesterday"
        else -> "$days days ago"
    }
}
