package app.kron.lyrics.recognition

import org.junit.Assert.assertEquals
import org.junit.Test

class RecognizerChoiceTest {
    @Test
    fun aSavedChoiceIsReadBack() {
        assertEquals(RecognizerChoice.Shazam, recognizerChoiceFrom("Shazam"))
        assertEquals(RecognizerChoice.Audd, recognizerChoiceFrom("Audd"))
    }

    @Test
    fun anythingMissingOrUnknownMeansAudd() {
        assertEquals(RecognizerChoice.Audd, recognizerChoiceFrom(null))
        assertEquals(RecognizerChoice.Audd, recognizerChoiceFrom(""))
        assertEquals(RecognizerChoice.Audd, recognizerChoiceFrom("shazam")) // names are exact; an odd value is not trusted
        assertEquals(RecognizerChoice.Audd, recognizerChoiceFrom("SomethingNew"))
    }
}
