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
import java.net.URLEncoder

interface LyricsFetcher {
    suspend fun fetch(track: Track): LyricsResult
}

private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

/** Parses the body of LRCLIB's `/api/get`. */
fun parseLrclib(body: String): LyricsResult {
    val root = try {
        Json.parseToJsonElement(body)
    } catch (_: Exception) {
        return LyricsResult.Error
    }
    return when (root) {
        is JsonObject -> resultOf(root)
        is JsonArray -> LyricsResult.NotFound
        else -> LyricsResult.Error // a bare word is not an LRCLIB response
    }
}

/** Parses the body of LRCLIB's `/api/search`: the first entry with synced lyrics wins. */
fun parseLrclibSearch(body: String): LyricsResult {
    val root = try {
        Json.parseToJsonElement(body)
    } catch (_: Exception) {
        return LyricsResult.Error
    }
    if (root !is JsonArray) return if (root is JsonObject) LyricsResult.NotFound else LyricsResult.Error
    val results = root.mapNotNull { it as? JsonObject }.map(::resultOf)
    return results.firstOrNull { it is LyricsResult.Synced }
        ?: results.firstOrNull { it is LyricsResult.NotSynced }
        ?: LyricsResult.NotFound
}

private fun resultOf(o: JsonObject): LyricsResult {
    o.text("syncedLyrics")?.let { synced ->
        val lines = parseLrc(synced)
        if (lines.isNotEmpty()) return LyricsResult.Synced(lines)
    }
    return if (o.text("plainLyrics") != null) LyricsResult.NotSynced else LyricsResult.NotFound
}

class LrclibFetcher(private val baseUrl: String = "https://lrclib.net") : LyricsFetcher {
    override suspend fun fetch(track: Track): LyricsResult = withContext(Dispatchers.IO) {
        val base = "track_name=${enc(track.title)}&artist_name=${enc(track.artist)}"
        val direct = get("$baseUrl/api/get?$base" + if (track.durationMs > 0) "&duration=${track.durationMs / 1000}" else "")
        val first = when (direct?.first) {
            200 -> parseLrclib(direct.second)
            404 -> LyricsResult.NotFound
            else -> return@withContext LyricsResult.Error
        }
        if (first is LyricsResult.Synced) return@withContext first

        val search = get("$baseUrl/api/search?$base") ?: return@withContext first.orError()
        if (search.first != 200) return@withContext first.orError()
        val found = parseLrclibSearch(search.second)
        if (found is LyricsResult.NotFound) first else found
    }

    private fun LyricsResult.orError() = if (this is LyricsResult.NotSynced) this else LyricsResult.Error

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): Pair<Int, String>? = httpGet(url)
}
