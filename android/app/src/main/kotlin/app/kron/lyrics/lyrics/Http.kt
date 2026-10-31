package app.kron.lyrics.lyrics

import app.kron.lyrics.log.LogKind
import app.kron.lyrics.log.activityLog
import java.net.HttpURLConnection
import java.net.URL

/**
 * A GET that never throws. Returns the status code and body, or null when the network failed.
 * Every request is written to the Activity log, since that screen shows what kRon sends.
 */
internal fun httpGet(url: String, headers: Map<String, String> = emptyMap()): Pair<Int, String>? = try {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 10_000
    c.readTimeout = 10_000
    c.setRequestProperty("User-Agent", "kRon-Android/0.1 (https://github.com/arontz/kRon)")
    headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
    try {
        val code = c.responseCode
        activityLog.add(LogKind.NETWORK, "GET ${url.removePrefix("https://")} → HTTP $code")
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
    } finally {
        c.disconnect()
    }
} catch (_: Exception) {
    activityLog.add(LogKind.NETWORK, "GET ${url.removePrefix("https://")} → no connection")
    null // IOException, but also anything HttpURLConnection throws for odd responses
}
