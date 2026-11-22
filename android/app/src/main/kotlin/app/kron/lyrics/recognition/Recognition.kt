package app.kron.lyrics.recognition

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class RecognitionError {
    /** The saved token was refused. */
    InvalidToken,

    /** The free requests, or the paid ones, are used up. */
    LimitReached,

    /** The service is refusing requests for now (HTTP 429); trying again later may work. */
    RateLimited,

    /** The service could not be reached. */
    Network,

    Other,
}

sealed interface Recognition {
    /** [positionMs] is where in the song the recorded clip was taken, as the service reports it. */
    data class Match(val title: String, val artist: String, val album: String, val positionMs: Long) : Recognition

    data object NoMatch : Recognition

    data class Failed(val error: RecognitionError) : Recognition
}

/**
 * Reads AudD's answer. The success shape is the one in its docs; the error and no-match shapes are not documented, so
 * they are read leniently and anything odd becomes a plain failure instead of an exception.
 */
fun parseAudd(body: String): Recognition {
    val root = try {
        Json.parseToJsonElement(body) as? JsonObject
    } catch (_: Exception) {
        null
    } ?: return Recognition.Failed(RecognitionError.Other)

    if ((root["status"] as? JsonPrimitive)?.contentOrNull == "error") return Recognition.Failed(errorKind(root["error"]))

    val result = when (val r = root["result"]) {
        null, JsonNull -> return Recognition.NoMatch
        is JsonArray -> r.firstOrNull() as? JsonObject ?: return if (r.isEmpty()) Recognition.NoMatch else Recognition.Failed(RecognitionError.Other)
        is JsonObject -> r
        else -> return Recognition.Failed(RecognitionError.Other)
    }
    fun text(key: String) = (result[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    val title = text("title")
    if (title.isEmpty()) return Recognition.NoMatch
    return Recognition.Match(title, text("artist"), text("album"), parseTimecode(text("timecode")) ?: 0L)
}

private fun errorKind(error: Any?): RecognitionError {
    val code = ((error as? JsonObject)?.get("error_code") as? JsonPrimitive)?.contentOrNull
        ?.trim()?.removePrefix("#")?.toIntOrNull()
    return when (code) {
        900 -> RecognitionError.InvalidToken
        901 -> RecognitionError.LimitReached
        else -> RecognitionError.Other
    }
}

/** "02:32" or "1:02:32" to milliseconds; null for anything else. */
fun parseTimecode(s: String): Long? {
    val parts = s.trim().split(':')
    if (parts.size !in 2..3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
    val n = parts.map { it.toLongOrNull() ?: return null }
    val seconds = if (n.size == 3) n[0] * 3_600 + n[1] * 60 + n[2] else n[0] * 60 + n[1]
    return seconds * 1_000
}
