package app.kron.lyrics.recognition.shazam

import app.kron.lyrics.recognition.Recognition
import app.kron.lyrics.recognition.RecognitionError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Reads the answer of Shazam's recognition endpoint. A match has `matches[0].offset`, the second of the song where the
 * recorded clip starts (29.98 for a clip cut at 30 s), and `track.title` and `track.subtitle` (the artist). The shape
 * was seen on a real answer; Shazam documents none of this, so everything else is read leniently.
 */
fun parseShazam(body: String): Recognition {
    val root = try {
        Json.parseToJsonElement(body) as? JsonObject
    } catch (_: Exception) {
        null
    } ?: return Recognition.Failed(RecognitionError.Other)

    val matches = when (val m = root["matches"]) {
        null -> return Recognition.NoMatch
        is JsonArray -> m
        else -> return Recognition.Failed(RecognitionError.Other)
    }
    val first = matches.firstOrNull() ?: return Recognition.NoMatch
    val match = first as? JsonObject ?: return Recognition.Failed(RecognitionError.Other)

    val track = root["track"] as? JsonObject ?: return Recognition.NoMatch
    fun text(key: String) = (track[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    val title = text("title")
    if (title.isEmpty()) return Recognition.NoMatch

    val offsetSeconds = (match["offset"] as? JsonPrimitive)?.doubleOrNull
    val positionMs = if (offsetSeconds != null && offsetSeconds.isFinite() && offsetSeconds > 0) Math.round(offsetSeconds * 1000) else 0L
    return Recognition.Match(title, text("subtitle"), album = "", positionMs = positionMs)
}
