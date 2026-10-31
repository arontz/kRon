package app.kron.lyrics.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    private fun releases(vararg tags: String, draft: Set<String> = emptySet()) =
        "[" + tags.joinToString(",") { """{"tag_name":"$it","draft":${it in draft}}""" } + "]"

    @Test
    fun comparesNumericallyNotAlphabetically() {
        assertTrue(compareVersions(parseVersion("0.10.0")!!, parseVersion("0.9.0")!!) > 0)
        assertEquals(0, compareVersions(parseVersion("1.0")!!, parseVersion("1.0.0")!!))
    }

    @Test
    fun parsesTagsAndRejectsJunk() {
        assertEquals(listOf(0, 2, 0), parseVersion("android-v0.2.0"))
        assertNull(parseVersion("0.2.0-beta"))
        assertNull(parseVersion("abc"))
        assertNull(parseVersion(""))
    }

    @Test
    fun picksHighestAndroidReleaseIgnoringWindowsAndDrafts() {
        val body = releases("v9.9.9", "android-v0.2.0", "android-v0.10.0", "android-v0.11.0", draft = setOf("android-v0.11.0"))
        assertEquals("android-v0.10.0", latestAndroidRelease(body)?.tag)
    }

    @Test
    fun emptyOrBrokenResponsesMeanNoReleases() {
        assertEquals(UpdateResult.NoReleases, evaluateUpdate("[]", "0.1.0"))
        assertEquals(UpdateResult.NoReleases, evaluateUpdate("""[{"tag_name":"v1.2.3"}]""", "0.1.0"))
        assertEquals(UpdateResult.NoReleases, evaluateUpdate("""{"message":"Not Found"}""", "0.1.0"))
        assertEquals(UpdateResult.NoReleases, evaluateUpdate("not json", "0.1.0"))
    }

    @Test
    fun reportsAvailableOrUpToDate() {
        val body = releases("android-v0.2.0")
        val r = evaluateUpdate(body, "0.1.0") as UpdateResult.Available
        assertEquals("0.2.0", r.release.version)
        assertEquals("https://github.com/arontz/kRon/releases/tag/android-v0.2.0", r.release.pageUrl)
        assertEquals(UpdateResult.UpToDate("0.2.0"), evaluateUpdate(body, "0.2.0"))
        assertEquals(UpdateResult.UpToDate("0.3.0"), evaluateUpdate(body, "0.3.0"))
    }
}
