package app.kron.lyrics.recognition

import app.kron.lyrics.lyrics.httpPostMultipart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Sends a recorded clip to AudD's standard endpoint (a multipart POST with the token and the audio file). */
class AuddClient(private val url: String = "https://api.audd.io/") : SongRecognizer {
    override val needsToken = true

    override suspend fun recognize(wav: ByteArray, token: String): Recognition = withContext(Dispatchers.IO) {
        val boundary = "kron" + UUID.randomUUID().toString().replace("-", "")
        val body = multipartBody(boundary, mapOf("api_token" to token), "file", "listen.wav", "audio/wav", wav)
        // The token is a form field, never part of the address or this log line.
        val label = "POST ${url.removePrefix("https://").trimEnd('/')} · ${wav.size / 1024} KB of microphone audio"
        val response = httpPostMultipart(url, boundary, body, label)
            ?: return@withContext Recognition.Failed(RecognitionError.Network)
        parseAudd(response.second)
    }
}
