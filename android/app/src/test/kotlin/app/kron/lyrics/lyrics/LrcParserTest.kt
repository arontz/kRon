package app.kron.lyrics.lyrics

import app.kron.lyrics.model.LrcLine
import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {
    @Test
    fun parsesMinutesSecondsAndFraction() {
        assertEquals(listOf(LrcLine(12340, "Hello")), parseLrc("[00:12.34]Hello"))
        assertEquals(listOf(LrcLine(62000, "x")), parseLrc("[01:02]x"))
        assertEquals(listOf(LrcLine(10500, "a")), parseLrc("[00:10.5]a"))
        assertEquals(listOf(LrcLine(10123, "a")), parseLrc("[00:10.123]a"))
    }

    @Test
    fun expandsMultipleTimestamps() {
        assertEquals(
            listOf(LrcLine(1000, "La"), LrcLine(5000, "La")),
            parseLrc("[00:01.00][00:05.00]La"),
        )
    }

    @Test
    fun sortsOutOfOrderLines() {
        assertEquals(
            listOf(LrcLine(1000, "first"), LrcLine(5000, "second")),
            parseLrc("[00:05.00]second\n[00:01.00]first"),
        )
    }

    @Test
    fun ignoresTagLinesAndGarbage() {
        assertEquals(
            listOf(LrcLine(1000, "ok")),
            parseLrc("[ar:Someone]\n[ti:Song]\nnot a lyric\n[00:01.00]ok"),
        )
    }

    @Test
    fun keepsBlankTextLines() {
        assertEquals(listOf(LrcLine(3000, "")), parseLrc("[00:03.00]"))
    }

    @Test
    fun handlesWindowsLineEndings() {
        assertEquals(
            listOf(LrcLine(1000, "a"), LrcLine(2000, "b")),
            parseLrc("[00:01.00]a\r\n[00:02.00]b\r\n"),
        )
    }

    @Test
    fun overlongTimestampIsSkippedNotCrashed() {
        assertEquals(
            listOf(LrcLine(1000, "ok")),
            parseLrc("[99999999999999999999:00]bad\n[00:01.00]ok"),
        )
    }

    @Test
    fun currentIndexCases() {
        val lines = listOf(LrcLine(1000, "a"), LrcLine(5000, "b"), LrcLine(9000, "c"))
        assertEquals(-1, currentIndex(lines, 999))
        assertEquals(0, currentIndex(lines, 1000))
        assertEquals(1, currentIndex(lines, 7000))
        assertEquals(2, currentIndex(lines, 99_000))
        assertEquals(-1, currentIndex(emptyList(), 5000))
    }
}
