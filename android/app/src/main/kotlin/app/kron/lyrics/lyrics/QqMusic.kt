package app.kron.lyrics.lyrics

import app.kron.lyrics.model.LyricsResult
import app.kron.lyrics.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.net.URLEncoder
import kotlin.math.abs

data class QqHit(val mid: String, val title: String, val artist: String, val album: String, val durationSec: Int)

fun parseQqSearch(body: String): List<QqHit> {
    val root = try {
        Json.parseToJsonElement(body)
    } catch (_: Exception) {
        return emptyList()
    }
    val list = (((root as? JsonObject)?.get("data") as? JsonObject)?.get("song") as? JsonObject)?.get("list") as? JsonArray
        ?: return emptyList()
    return list.mapNotNull { item ->
        val o = item as? JsonObject ?: return@mapNotNull null
        val mid = (o["songmid"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val artist = (o["singer"] as? JsonArray)
            ?.mapNotNull { ((it as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull }
            ?.joinToString(", ").orEmpty()
        QqHit(
            mid = mid,
            title = (o["songname"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            artist = artist,
            album = (o["albumname"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            durationSec = (o["interval"] as? JsonPrimitive)?.intOrNull ?: 0,
        )
    }
}

private val BRACKETED = Regex("""[(\[（【].*?[)\]）】]""")

private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

/** The title without "(Live)", "[Remastered]" and similar, so a bracketed tag does not hide a match. */
private fun baseTitle(s: String) = norm(s.replace(BRACKETED, ""))

private const val MAX_DURATION_GAP_SEC = 15

/**
 * The search result that is this song, or null. The title must match (ignoring bracketed tags), a known artist
 * must overlap, and when both durations are known they must be within 15 s, so a live take of a different length
 * is not chosen: its lyrics would be out of step with the audio. Closer durations win.
 */
fun pickQqHit(hits: List<QqHit>, track: Track): QqHit? {
    val wantTitle = baseTitle(track.title)
    val wantArtist = norm(track.artist)
    if (wantTitle.isEmpty()) return null
    return hits.mapNotNull { h ->
        if (baseTitle(h.title) != wantTitle) return@mapNotNull null
        var score = if (norm(h.title) == norm(track.title)) 100 else 90
        if (wantArtist.isNotEmpty()) {
            val a = norm(h.artist)
            if (a.isEmpty() || !(a.contains(wantArtist) || wantArtist.contains(a))) return@mapNotNull null
        }
        if (track.durationMs > 0 && h.durationSec > 0) {
            val gap = abs(track.durationMs / 1000 - h.durationSec).toInt()
            if (gap > MAX_DURATION_GAP_SEC) return@mapNotNull null
            score -= gap
        }
        h to score
    }.maxByOrNull { it.second }?.first
}

/** The LRC text from a lyric response, with QQ's HTML escapes decoded, or null when there is none. */
fun parseQqLyric(body: String): String? {
    val root = try {
        Json.parseToJsonElement(body)
    } catch (_: Exception) {
        return null
    }
    val raw = ((root as? JsonObject)?.get("lyric") as? JsonPrimitive)?.contentOrNull ?: return null
    return decodeEntities(raw).takeIf { it.isNotBlank() }
}

private val ENTITY = Regex("""&(#x[0-9a-fA-F]+|#[0-9]+|apos|quot|lt|gt|amp);""")

/** QQ sends lyrics with HTML entities, even the colons and dots of the timestamps (`&#58;`, `&#46;`). */
fun decodeEntities(s: String): String = ENTITY.replace(s) { m ->
    when (val e = m.groupValues[1]) {
        "apos" -> "'"
        "quot" -> "\""
        "lt" -> "<"
        "gt" -> ">"
        "amp" -> "&"
        else -> {
            val code = if (e[1] == 'x') e.substring(2).toIntOrNull(16) else e.substring(1).toIntOrNull()
            code?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: m.value
        }
    }
}

/** QQ Music's unofficial lyrics endpoints, the same ones the Windows app uses. */
class QqMusicFetcher(private val baseUrl: String = "https://c.y.qq.com") : LyricsFetcher {
    override suspend fun fetch(track: Track): LyricsResult = withContext(Dispatchers.IO) {
        val query = listOf(track.artist, track.title).filter { it.isNotBlank() }.joinToString(" ")
        val search = httpGet("$baseUrl/soso/fcgi-bin/client_search_cp?w=${enc(query)}&format=json&n=15&p=1", HEADERS)
        if (search == null || search.first != 200) return@withContext LyricsResult.Error
        val hit = pickQqHit(parseQqSearch(search.second), track) ?: return@withContext LyricsResult.NotFound

        val lyric = httpGet("$baseUrl/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=${enc(hit.mid)}&format=json&nobase64=1", HEADERS)
        if (lyric == null || lyric.first != 200) return@withContext LyricsResult.Error
        val text = parseQqLyric(lyric.second) ?: return@withContext LyricsResult.NotFound
        val lines = parseLrc(text)
        if (lines.isNotEmpty()) LyricsResult.Synced(lines) else LyricsResult.NotSynced
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private companion object {
        val HEADERS = mapOf("Referer" to "https://y.qq.com/")
    }
}
