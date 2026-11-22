package app.kron.lyrics.history

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayHistoryTest {
    private fun song(title: String, artist: String = "A", at: Long = 0) = PlayedSong(title, artist, 200_000, at)

    @Test
    fun newestSongGoesFirst() {
        val list = addPlay(listOf(song("one", at = 1)), song("two", at = 2))
        assertEquals(listOf("two", "one"), list.map { it.title })
    }

    @Test
    fun playingASongAgainMovesItToTheTopWithTheNewTime() {
        val start = listOf(song("c", at = 3), song("b", at = 2), song("a", at = 1))
        val list = addPlay(start, song("a", at = 9))
        assertEquals(listOf("a", "c", "b"), list.map { it.title })
        assertEquals(9L, list.first().playedAtMs)
        assertEquals(3, list.size)
    }

    @Test
    fun sameSongIsMatchedIgnoringCaseAndSpaces() {
        val list = addPlay(listOf(song("One More Light", "Linkin Park", 1)), song("  one more light ", "LINKIN PARK", 2))
        assertEquals(1, list.size)
        assertEquals(2L, list.first().playedAtMs)
    }

    @Test
    fun theSameTitleByAnotherArtistIsAnotherSong() {
        val list = addPlay(listOf(song("Hello", "Adele", 1)), song("Hello", "Lionel", 2))
        assertEquals(2, list.size)
    }

    @Test
    fun theOldestSongsFallOffTheEnd() {
        var list = emptyList<PlayedSong>()
        for (i in 1..5) list = addPlay(list, song("s$i", at = i.toLong()), cap = 3)
        assertEquals(listOf("s5", "s4", "s3"), list.map { it.title })
    }

    @Test
    fun songsRecordedBeforeTheFileFinishedLoadingStayOnTopWithoutDuplicates() {
        val recorded = listOf(song("new", at = 9), song("dup", at = 8))
        val loaded = listOf(song("dup", at = 1), song("old", at = 0))
        assertEquals(listOf("new", "dup", "old"), mergeLoaded(recorded, loaded).map { it.title })
        assertEquals(8L, mergeLoaded(recorded, loaded)[1].playedAtMs)
    }

    @Test
    fun mergingStillRespectsTheCap() {
        val loaded = (1..5).map { song("l$it", at = it.toLong()) }
        assertEquals(3, mergeLoaded(listOf(song("new", at = 9)), loaded, cap = 3).size)
    }

    @Test
    fun theListSurvivesBeingSavedAndLoaded() {
        val list = listOf(song("a", at = 1), song("b", "Bee", 2))
        assertEquals(list, decodeHistory(encodeHistory(list)))
    }

    @Test
    fun unknownAndMissingFieldsDoNotBreakLoading() {
        val text = """[{"title":"x","artist":"y","playedAtMs":5,"somethingNew":"later version"},{"title":"only a title"}]"""
        val list = decodeHistory(text)
        assertEquals(listOf("x", "only a title"), list.map { it.title })
        assertEquals(5L, list[0].playedAtMs)
        assertEquals("", list[1].artist)
    }

    @Test
    fun oneBrokenEntryIsSkippedNotTheWholeList() {
        val list = decodeHistory("""[{"title":"good"},{"noTitle":true},"junk",{"title":"also good"}]""")
        assertEquals(listOf("good", "also good"), list.map { it.title })
    }

    @Test
    fun aSongRepeatedInTheFileLoadsOnceSoTheListCanNeverHaveDuplicateKeys() {
        val text = """[{"title":"Same","artist":"A","playedAtMs":9},{"title":" same ","artist":"a","playedAtMs":1},{"title":"Other"}]"""
        val list = decodeHistory(text)
        assertEquals(listOf("Same", "Other"), list.map { it.title })
        assertEquals(9L, list.first().playedAtMs)
    }

    @Test
    fun anOldFileThatStillHasAPlayerFieldLoads() {
        assertEquals(listOf("x"), decodeHistory("""[{"title":"x","player":"com.old.app"}]""").map { it.title })
    }

    @Test
    fun unreadableTextGivesAnEmptyList() {
        assertEquals(emptyList<PlayedSong>(), decodeHistory(null))
        assertEquals(emptyList<PlayedSong>(), decodeHistory(""))
        assertEquals(emptyList<PlayedSong>(), decodeHistory("not json"))
        assertEquals(emptyList<PlayedSong>(), decodeHistory("""{"title":"not a list"}"""))
    }

    @Test
    fun playedAgoReadsNaturally() {
        val now = 10_000_000_000L
        val min = 60_000L
        assertEquals("just now", playedAgo(now, now - 30_000))
        assertEquals("5 min ago", playedAgo(now, now - 5 * min))
        assertEquals("1 h ago", playedAgo(now, now - 60 * min))
        assertEquals("3 h ago", playedAgo(now, now - 3 * 60 * min))
        assertEquals("yesterday", playedAgo(now, now - 30 * 60 * min))
        assertEquals("4 days ago", playedAgo(now, now - 4 * 24 * 60 * min))
        assertEquals("just now", playedAgo(now, now + 5_000))
    }
}
