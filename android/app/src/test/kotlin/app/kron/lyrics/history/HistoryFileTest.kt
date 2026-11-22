package app.kron.lyrics.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HistoryFileTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val songs = listOf(PlayedSong("a", "x", 1, 2, "p"), PlayedSong("b", "y", 3, 4, "q"))

    @Test
    fun writtenSongsAreReadBack() {
        val file = HistoryFile(folder.root.resolve("history.json"))
        file.write(songs)
        assertEquals(songs, file.read())
    }

    @Test
    fun aMissingFileIsAnEmptyList() {
        assertEquals(emptyList<PlayedSong>(), HistoryFile(folder.root.resolve("none.json")).read())
    }

    @Test
    fun aCorruptFileIsAnEmptyListNotACrash() {
        val f = folder.newFile("history.json")
        f.writeText("{{ not json")
        assertEquals(emptyList<PlayedSong>(), HistoryFile(f).read())
    }

    @Test
    fun writingReplacesTheOldListAndLeavesNoTempFile() {
        val f = folder.root.resolve("history.json")
        val file = HistoryFile(f)
        file.write(songs)
        file.write(songs.take(1))
        assertEquals(songs.take(1), file.read())
        assertFalse(folder.root.resolve("history.json.tmp").exists())
    }

    @Test
    fun theFolderIsCreatedWhenMissing() {
        val file = HistoryFile(folder.root.resolve("a/b/history.json"))
        file.write(songs)
        assertEquals(songs, file.read())
    }
}
