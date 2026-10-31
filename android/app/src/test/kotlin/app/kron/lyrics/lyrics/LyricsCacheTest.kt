package app.kron.lyrics.lyrics

import app.kron.lyrics.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LyricsCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val track = Track("Song", "Artist", 200_000)

    @Test
    fun putThenGetReturnsTheText() {
        val cache = LyricsCache(folder.newFolder("c"))
        cache.put(track, "[00:01.000]hi")
        assertEquals("[00:01.000]hi", cache.get(track))
    }

    @Test
    fun otherTrackIsAMiss() {
        val cache = LyricsCache(folder.newFolder("c"))
        cache.put(track, "x")
        assertNull(cache.get(Track("Other", "Artist", 200_000)))
    }

    @Test
    fun differentDurationIsASeparateEntry() {
        val cache = LyricsCache(folder.newFolder("c"))
        cache.put(track, "x")
        assertNull(cache.get(track.copy(durationMs = 201_000)))
    }

    @Test
    fun keyIgnoresCase() {
        val cache = LyricsCache(folder.newFolder("c"))
        cache.put(track, "x")
        assertEquals("x", cache.get(Track("song", "ARTIST", 200_000)))
    }

    @Test
    fun missingDirectoryIsCreated() {
        val cache = LyricsCache(folder.root.resolve("a/b"))
        cache.put(track, "x")
        assertEquals("x", cache.get(track))
    }
}
