package app.kron.lyrics.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultipartTest {
    private fun latin1(b: ByteArray) = String(b, Charsets.ISO_8859_1)

    @Test
    fun theBodyHasFieldsThenTheFileThenTheClosingBoundary() {
        val file = byteArrayOf(0, 1, 2, 3, 127, -1, -128)
        val body = multipartBody("BOUND", mapOf("api_token" to "secret"), "file", "listen.wav", "audio/wav", file)
        val text = latin1(body)

        val expectedHead = "--BOUND\r\nContent-Disposition: form-data; name=\"api_token\"\r\n\r\nsecret\r\n" +
            "--BOUND\r\nContent-Disposition: form-data; name=\"file\"; filename=\"listen.wav\"\r\n" +
            "Content-Type: audio/wav\r\n\r\n"
        val expectedTail = "\r\n--BOUND--\r\n"
        assertTrue(text.startsWith(expectedHead))
        assertTrue(text.endsWith(expectedTail))
        assertEquals(expectedHead.length + file.size + expectedTail.length, body.size)
    }

    @Test
    fun theFileBytesAreCopiedExactly() {
        val file = ByteArray(300) { it.toByte() }
        val body = multipartBody("B", emptyMap(), "file", "a.wav", "audio/wav", file)
        val start = latin1(body).indexOf("\r\n\r\n") + 4
        assertTrue(file.contentEquals(body.copyOfRange(start, start + file.size)))
    }

    @Test
    fun severalFieldsAreEachTheirOwnPart() {
        val body = latin1(multipartBody("B", linkedMapOf("a" to "1", "b" to "2"), "file", "x.wav", "audio/wav", ByteArray(0)))
        assertEquals(3, Regex("--B\r\n").findAll(body).count())
        assertTrue(body.contains("name=\"a\"\r\n\r\n1\r\n"))
        assertTrue(body.contains("name=\"b\"\r\n\r\n2\r\n"))
    }
}
