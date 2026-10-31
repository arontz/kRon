package app.kron.lyrics.log

import app.kron.lyrics.model.PlaybackSnapshot
import app.kron.lyrics.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityLogTest {
    @Test
    fun newestFirstAndCapped() {
        var t = 0L
        val log = ActivityLog(capacity = 3) { ++t }
        (1..5).forEach { log.add(LogKind.NETWORK, "e$it") }
        assertEquals(listOf("e5", "e4", "e3"), log.entries.value.map { it.text })
    }

    @Test
    fun addIfChangedSkipsRepeats() {
        val log = ActivityLog()
        log.addIfChanged("a", LogKind.MEDIA, "1")
        log.addIfChanged("a", LogKind.MEDIA, "2")
        log.addIfChanged("b", LogKind.MEDIA, "3")
        log.addIfChanged("a", LogKind.MEDIA, "4")
        assertEquals(listOf("4", "3", "1"), log.entries.value.map { it.text })
    }

    @Test
    fun clearEmptiesAndForgetsLastKey() {
        val log = ActivityLog()
        log.addIfChanged("a", LogKind.MEDIA, "1")
        log.clear()
        assertEquals(0, log.entries.value.size)
        log.addIfChanged("a", LogKind.MEDIA, "again")
        assertEquals(1, log.entries.value.size)
    }

    @Test
    fun describesOnlyWhatIsUsed() {
        val s = PlaybackSnapshot(Track("Song", "Band", 213_000), 42_000, 1f, true, 0)
        assertEquals("com.app: \"Song\" by Band · 3:33 · playing", describeRead("com.app", s))
        val paused = s.copy(track = Track("Song", "", -1), playing = false)
        assertEquals("com.app: \"Song\" by unknown artist · paused", describeRead("com.app", paused))
        assertEquals(
            "com.app: media session without a title (nothing used)",
            describeRead("com.app", s.copy(track = null)),
        )
    }
}
