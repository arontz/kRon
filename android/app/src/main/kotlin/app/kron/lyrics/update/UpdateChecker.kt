package app.kron.lyrics.update

import app.kron.lyrics.log.LogKind
import app.kron.lyrics.log.activityLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.net.HttpURLConnection
import java.net.URL

const val ANDROID_TAG_PREFIX = "android-v"
private const val REPO = "arontz/kRon"

data class ReleaseInfo(val version: String, val tag: String) {
    /** Built from the tag, never taken from the server's response. */
    val pageUrl: String get() = "https://github.com/$REPO/releases/tag/$tag"
}

sealed interface UpdateResult {
    data class UpToDate(val current: String) : UpdateResult
    data class Available(val release: ReleaseInfo) : UpdateResult
    data object NoReleases : UpdateResult
    data class Failed(val reason: String) : UpdateResult
}

/** "0.10.1" -> [0, 10, 1]; anything with a non-numeric part (e.g. "0.2.0-beta") -> null. */
fun parseVersion(s: String): List<Int>? {
    val parts = s.removePrefix(ANDROID_TAG_PREFIX).removePrefix("v").split('.')
    if (parts.isEmpty() || parts.size > 4) return null
    return parts.map { p -> p.takeIf { it.isNotEmpty() && it.all(Char::isDigit) && it.length <= 6 }?.toInt() ?: return null }
}

/** Numeric comparison, missing trailing parts count as 0 (so 0.10.0 > 0.9.0 and 1.0 == 1.0.0). */
fun compareVersions(a: List<Int>, b: List<Int>): Int {
    for (i in 0 until maxOf(a.size, b.size)) {
        val c = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
        if (c != 0) return c
    }
    return 0
}

/** The highest published (non-draft) `android-v*` release in a GitHub "list releases" response, or null. */
fun latestAndroidRelease(body: String): ReleaseInfo? {
    val root = try {
        Json.parseToJsonElement(body) as? JsonArray
    } catch (_: Exception) {
        null
    } ?: return null
    return root.asSequence()
        .mapNotNull { it as? JsonObject }
        .filter { (it["draft"] as? JsonPrimitive)?.booleanOrNull != true }
        .mapNotNull { o ->
            val tag = (o["tag_name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            if (!tag.startsWith(ANDROID_TAG_PREFIX)) return@mapNotNull null
            val v = parseVersion(tag) ?: return@mapNotNull null
            v to ReleaseInfo(tag.removePrefix(ANDROID_TAG_PREFIX), tag)
        }
        .maxWithOrNull { x, y -> compareVersions(x.first, y.first) }
        ?.second
}

/** Decides what to tell the user, given a 200 response body. */
fun evaluateUpdate(body: String, currentVersion: String): UpdateResult {
    val latest = latestAndroidRelease(body) ?: return UpdateResult.NoReleases
    val cur = parseVersion(currentVersion) ?: return UpdateResult.Failed("This build has an unusual version ($currentVersion).")
    val lat = parseVersion(latest.tag) ?: return UpdateResult.NoReleases
    return if (compareVersions(lat, cur) > 0) UpdateResult.Available(latest) else UpdateResult.UpToDate(currentVersion)
}

fun updateMessage(r: UpdateResult): String = when (r) {
    is UpdateResult.UpToDate -> "You are up to date (version ${r.current})."
    is UpdateResult.Available -> "Version ${r.release.version} is available."
    UpdateResult.NoReleases -> "No Android release has been published yet."
    is UpdateResult.Failed -> r.reason
}

/**
 * Asks GitHub for the repository's releases. Runs only when the user taps "Check for updates":
 * there is no background check. Nothing about the user or the device is sent besides the request itself.
 */
class UpdateChecker(
    private val currentVersion: String,
    private val baseUrl: String = "https://api.github.com",
) {
    suspend fun check(): UpdateResult = withContext(Dispatchers.IO) {
        val url = "$baseUrl/repos/$REPO/releases?per_page=30"
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.setRequestProperty("User-Agent", "kRon-Android/$currentVersion (https://github.com/$REPO)")
            c.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                val code = c.responseCode
                activityLog.add(LogKind.UPDATE, "GET api.github.com/repos/$REPO/releases → HTTP $code")
                when (code) {
                    200 -> evaluateUpdate(c.inputStream.bufferedReader().use { it.readText() }, currentVersion)
                    404 -> UpdateResult.NoReleases
                    403, 429 -> UpdateResult.Failed("GitHub is limiting requests right now. Try again in a few minutes.")
                    else -> UpdateResult.Failed("GitHub answered with an error ($code). Try again later.")
                }
            } finally {
                c.disconnect()
            }
        } catch (_: Exception) {
            activityLog.add(LogKind.UPDATE, "GET api.github.com/repos/$REPO/releases → no connection")
            UpdateResult.Failed("Could not reach GitHub. Check your connection.")
        }
    }
}
