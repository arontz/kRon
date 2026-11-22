package app.kron.lyrics.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The success shape is from AudD's docs; the error and no-match shapes are not documented, so they are parsed leniently. */
class AuddParseTest {
    @Test
    fun aMatchGivesTitleArtistAlbumAndWhereTheClipWas() {
        val body = """{"status":"success","result":{"artist":"Imagine Dragons","title":"Warriors","album":"Warriors",
            "release_date":"2014-09-18","label":"Universal Music","timecode":"02:32","song_link":"https://lis.tn/Warriors"}}"""
        assertEquals(Recognition.Match("Warriors", "Imagine Dragons", "Warriors", 152_000), parseAudd(body))
    }

    @Test
    fun noMatchIsANullOrEmptyResult() {
        assertEquals(Recognition.NoMatch, parseAudd("""{"status":"success","result":null}"""))
        assertEquals(Recognition.NoMatch, parseAudd("""{"status":"success","result":[]}"""))
        assertEquals(Recognition.NoMatch, parseAudd("""{"status":"success"}"""))
    }

    @Test
    fun aResultWithoutATitleIsNotAMatch() {
        assertEquals(Recognition.NoMatch, parseAudd("""{"status":"success","result":{"artist":"x","timecode":"00:10"}}"""))
        assertEquals(Recognition.NoMatch, parseAudd("""{"status":"success","result":{"title":"  ","artist":"x"}}"""))
    }

    @Test
    fun aMissingOrBrokenTimecodeStartsAtZero() {
        assertEquals(0L, (parseAudd("""{"status":"success","result":{"title":"t"}}""") as Recognition.Match).positionMs)
        assertEquals(0L, (parseAudd("""{"status":"success","result":{"title":"t","timecode":"soon"}}""") as Recognition.Match).positionMs)
    }

    @Test
    fun timecodesAreMinutesAndSecondsOrHours() {
        assertEquals(152_000L, parseTimecode("02:32"))
        assertEquals(62_000L, parseTimecode("1:02"))
        assertEquals(3_752_000L, parseTimecode("1:02:32"))
        assertEquals(0L, parseTimecode("00:00"))
        assertNull(parseTimecode(""))
        assertNull(parseTimecode("abc"))
        assertNull(parseTimecode("1:xx"))
        assertNull(parseTimecode("1:2:3:4"))
        assertNull(parseTimecode("-1:05"))
    }

    @Test
    fun anInvalidTokenAndAnExhaustedAllowanceAreTold() {
        assertEquals(
            Recognition.Failed(RecognitionError.InvalidToken),
            parseAudd("""{"status":"error","error":{"error_code":900,"error_message":"Invalid API token"}}"""),
        )
        assertEquals(
            Recognition.Failed(RecognitionError.LimitReached),
            parseAudd("""{"status":"error","error":{"error_code":901,"error_message":"limit reached"}}"""),
        )
    }

    @Test
    fun theErrorCodeMayBeAStringOrWrittenWithAHash() {
        assertEquals(
            Recognition.Failed(RecognitionError.LimitReached),
            parseAudd("""{"status":"error","error":{"error_code":"901"}}"""),
        )
        assertEquals(
            Recognition.Failed(RecognitionError.InvalidToken),
            parseAudd("""{"status":"error","error":{"error_code":"#900"}}"""),
        )
    }

    @Test
    fun otherErrorsAndOddShapesAreAGenericFailureNeverACrash() {
        for (body in listOf(
            """{"status":"error","error":{"error_code":300,"error_message":"fingerprinting error"}}""",
            """{"status":"error"}""",
            """{"status":"error","error":"just a string"}""",
            "not json",
            "[]",
            "",
            """{"status":"success","result":"surprise"}""",
        )) {
            assertEquals(body, Recognition.Failed(RecognitionError.Other), parseAudd(body))
        }
    }
}
