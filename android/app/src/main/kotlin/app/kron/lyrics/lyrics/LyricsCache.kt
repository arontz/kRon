package app.kron.lyrics.lyrics

import app.kron.lyrics.model.Track
import java.io.File
import java.security.MessageDigest

/** Stores LRC text on disk, one file per track. */
class LyricsCache(private val dir: File) {
    fun get(track: Track): String? = file(track).takeIf { it.isFile }?.readText()

    fun put(track: Track, lrc: String) {
        dir.mkdirs()
        file(track).writeText(lrc)
    }

    private fun file(track: Track): File {
        val key = "${track.artist}|${track.title}|${track.durationMs}".lowercase()
        val hex = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$hex.lrc")
    }
}
