package app.kron.lyrics.lyrics

import app.kron.lyrics.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QqMusicTest {
    private fun hit(mid: String, title: String, artist: String, seconds: Int = 200) =
        QqHit(mid, title, artist, "album", seconds)

    @Test
    fun searchResultsAreParsed() {
        val body = """{"code":0,"data":{"song":{"list":[
            {"songmid":"001abc","songname":"Never Gonna Give You Up","singer":[{"name":"Rick Astley"}],"albumname":"Whenever","interval":213},
            {"songmid":"002def","songname":"Duet","singer":[{"name":"A"},{"name":"B"}],"albumname":"","interval":180}
        ]}}}"""
        assertEquals(
            listOf(
                QqHit("001abc", "Never Gonna Give You Up", "Rick Astley", "Whenever", 213),
                QqHit("002def", "Duet", "A, B", "", 180),
            ),
            parseQqSearch(body),
        )
    }

    @Test
    fun anythingUnexpectedGivesNoHits() {
        assertEquals(emptyList<QqHit>(), parseQqSearch("""{"code":0,"data":{"song":{}}}"""))
        assertEquals(emptyList<QqHit>(), parseQqSearch("[]"))
        assertEquals(emptyList<QqHit>(), parseQqSearch("not json"))
        assertEquals(emptyList<QqHit>(), parseQqSearch("""{"data":{"song":{"list":[{"songname":"no mid"}]}}}"""))
    }

    @Test
    fun theStudioVersionBeatsALiveTakeOfTheSameSong() {
        val track = Track("Song", "Artist", 200_000)
        val hits = listOf(hit("live", "Song", "Artist", 260), hit("studio", "Song", "Artist", 201))
        assertEquals("studio", pickQqHit(hits, track)?.mid)
    }

    @Test
    fun matchingIgnoresCasePunctuationAndExtraArtists() {
        val track = Track("don't stop", "ARTIST", 0)
        assertEquals("a", pickQqHit(listOf(hit("a", "Don't Stop!", "Artist, Guest")), track)?.mid)
    }

    @Test
    fun aDifferentSongIsNeverPicked() {
        val track = Track("Song", "Artist", 200_000)
        assertNull(pickQqHit(listOf(hit("x", "Other Song", "Artist")), track))
        assertNull(pickQqHit(listOf(hit("y", "Song", "Someone Else")), track))
        assertNull(pickQqHit(emptyList(), track))
    }

    @Test
    fun withoutAnArtistTheTitleAloneDecides() {
        assertEquals("a", pickQqHit(listOf(hit("a", "Song", "Whoever")), Track("Song", "", -1))?.mid)
    }

    @Test
    fun anUnknownDurationDoesNotRuleAnyoneOut() {
        val hits = listOf(hit("a", "Song", "Artist", 999))
        assertEquals("a", pickQqHit(hits, Track("Song", "Artist", -1))?.mid)
    }

    @Test
    fun lyricTextIsDecodedFromQqsEscapes() {
        val body = """{"retcode":0,"lyric":"[00&#58;01&#46;00]one&#10;[00&#58;02&#46;00]it&apos;s &amp; two"}"""
        assertEquals("[00:01.00]one\n[00:02.00]it's & two", parseQqLyric(body))
    }

    @Test
    fun emptyOrBrokenLyricGivesNull() {
        assertNull(parseQqLyric("""{"lyric":""}"""))
        assertNull(parseQqLyric("""{"lyric":"   "}"""))
        assertNull(parseQqLyric("""{"retcode":-1}"""))
        assertNull(parseQqLyric("not json"))
    }

    @Test
    fun entitiesAreDecoded() {
        assertEquals("Don't & won't\nstop", decodeEntities("Don&apos;t &amp; won&#39;t&#10;stop"))
        assertEquals("a A b", decodeEntities("a &#x41; b"))
        assertEquals("&unknown; stays", decodeEntities("&unknown; stays"))
    }
}
