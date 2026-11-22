package app.kron.lyrics.recognition.shazam

import app.kron.lyrics.lyrics.httpPostJson
import app.kron.lyrics.recognition.Recognition
import app.kron.lyrics.recognition.RecognitionError
import app.kron.lyrics.recognition.SongRecognizer
import app.kron.lyrics.recognition.readWav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Recognizes a clip through Shazam's own recognition endpoint, with no account and no token. Experimental: the
 * endpoint is not a public API, so Shazam can change or block it at any time. The clip is turned into a fingerprint on
 * this phone and only the fingerprint is sent, never the audio. kRon says who it is in the User-Agent, and nothing is
 * done to hide that. A 429 is reported, not retried.
 */
class ShazamClient(private val baseUrl: String = "https://amp.shazam.com") : SongRecognizer {
    override val needsToken = false

    override suspend fun recognize(wav: ByteArray, token: String): Recognition = withContext(Dispatchers.Default) {
        val audio = readWav(wav) ?: return@withContext Recognition.Failed(RecognitionError.Other)
        val fingerprint = fingerprintSamples(resampleTo16k(audio.samples, audio.sampleRate))

        val body = buildJsonObject {
            put("timezone", "UTC") // not the phone's own time zone
            put("signature", buildJsonObject {
                put("uri", fingerprint.toDataUri())
                put("samplems", fingerprint.durationMs())
            })
            put("timestamp", System.currentTimeMillis())
            put("context", buildJsonObject {})
            put("geolocation", buildJsonObject {})
        }.toString()

        val url = "$baseUrl/discovery/v5/en/US/android/-/tag/${UUID.randomUUID().toString().uppercase()}/" +
            "${UUID.randomUUID().toString().uppercase()}?sync=true&webv3=true&sampling=true&connected=" +
            "&shazamapiversion=v3&sharehub=true&hubv5minorversion=v5.1&hidelb=true&video=v3"
        val label = "POST ${baseUrl.removePrefix("https://")} · fingerprint of the clip (${body.length / 1024 + 1} KB), not the audio"

        val response = withContext(Dispatchers.IO) { httpPostJson(url, body, label) }
            ?: return@withContext Recognition.Failed(RecognitionError.Network)
        when (response.first) {
            in 200..299 -> parseShazam(response.second)
            429 -> Recognition.Failed(RecognitionError.RateLimited)
            else -> Recognition.Failed(RecognitionError.Other)
        }
    }
}
