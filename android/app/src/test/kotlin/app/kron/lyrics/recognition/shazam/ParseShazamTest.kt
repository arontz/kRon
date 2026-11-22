package app.kron.lyrics.recognition.shazam

import app.kron.lyrics.recognition.Recognition
import app.kron.lyrics.recognition.RecognitionError
import org.junit.Assert.assertEquals
import org.junit.Test

/** The match shape is the one Shazam's endpoint returned for a real clip; the other shapes are read leniently. */
class ParseShazamTest {
    private val match = """{"matches":[{"id":"209178774","offset":29.980880859,"timeskew":-0.000044,"frequencyskew":0}],
        "timestamp":1763900000000,"timezone":"UTC","tagid":"X","track":{"key":"209178774","title":"Sneaky Snitch","subtitle":"Kevin MacLeod"}}"""

    @Test
    fun aMatchGivesTitleArtistAndTheSecondOfTheSongWhereTheClipStarts() {
        assertEquals(Recognition.Match("Sneaky Snitch", "Kevin MacLeod", "", 29_981), parseShazam(match))
    }

    @Test
    fun noMatchesIsNoMatch() {
        assertEquals(Recognition.NoMatch, parseShazam("""{"matches":[],"location":{"accuracy":0.01},"tagid":"X"}"""))
        assertEquals(Recognition.NoMatch, parseShazam("""{"tagid":"X"}"""))
    }

    @Test
    fun aMatchWithoutATrackOrTitleIsNotUsable() {
        assertEquals(Recognition.NoMatch, parseShazam("""{"matches":[{"id":"1","offset":3.0}]}"""))
        assertEquals(Recognition.NoMatch, parseShazam("""{"matches":[{"id":"1","offset":3.0}],"track":{"title":"  "}}"""))
    }

    @Test
    fun aMissingOrOddOffsetStartsAtZeroAndAMissingArtistIsEmpty() {
        assertEquals(
            Recognition.Match("T", "", "", 0),
            parseShazam("""{"matches":[{"id":"1"}],"track":{"title":"T"}}"""),
        )
        assertEquals(
            Recognition.Match("T", "", "", 0),
            parseShazam("""{"matches":[{"id":"1","offset":"soon"}],"track":{"title":"T"}}"""),
        )
        assertEquals(
            Recognition.Match("T", "", "", 0),
            parseShazam("""{"matches":[{"id":"1","offset":-4.0}],"track":{"title":"T"}}"""),
        )
    }

    @Test
    fun anythingElseIsAGenericFailureNeverACrash() {
        for (body in listOf("not json", "[]", "", """{"matches":"surprise"}""", """{"matches":[1]}""")) {
            assertEquals(body, Recognition.Failed(RecognitionError.Other), parseShazam(body))
        }
    }
}
