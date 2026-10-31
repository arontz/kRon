package app.kron.lyrics.overlay

import app.kron.lyrics.model.LrcLine
import org.junit.Assert.assertEquals
import org.junit.Test

class CardLayoutTest {
    private val lines = listOf(LrcLine(1000, "one"), LrcLine(2000, "two"), LrcLine(3000, "three"))

    @Test
    fun middleIndexShowsBothNeighbours() {
        assertEquals(CardLines("one", "two", "three"), visibleLines(lines, 1))
    }

    @Test
    fun beforeTheFirstLineShowsOnlyTheNextOne() {
        assertEquals(CardLines("", "", "one"), visibleLines(lines, -1))
    }

    @Test
    fun firstLineHasNoPrevious() {
        assertEquals(CardLines("", "one", "two"), visibleLines(lines, 0))
    }

    @Test
    fun lastLineHasNoNext() {
        assertEquals(CardLines("two", "three", ""), visibleLines(lines, 2))
    }

    @Test
    fun singleLineList() {
        assertEquals(CardLines("", "solo", ""), visibleLines(listOf(LrcLine(0, "solo")), 0))
    }

    @Test
    fun emptyListIsAllBlank() {
        assertEquals(CardLines("", "", ""), visibleLines(emptyList(), -1))
    }

    @Test
    fun widthIsFortyFivePercentOfTheScreen() {
        assertEquals(486, cardWidthPx(1080))
        assertEquals(405, cardWidthPx(900))
    }
}
