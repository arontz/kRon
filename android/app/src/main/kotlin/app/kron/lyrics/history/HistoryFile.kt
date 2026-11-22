package app.kron.lyrics.history

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** The history on disk. Written to a temporary file and moved into place, so a crash mid-write cannot corrupt it. */
class HistoryFile(private val file: File) {
    fun read(): List<PlayedSong> =
        decodeHistory(runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull())

    fun write(songs: List<PlayedSong>) {
        file.absoluteFile.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(encodeHistory(songs))
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
