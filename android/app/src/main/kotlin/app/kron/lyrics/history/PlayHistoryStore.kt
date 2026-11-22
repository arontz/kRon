package app.kron.lyrics.history

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * The single owner of the list of songs played. The listener service records into it and the screens read it.
 * It is kept on this device only (the manifest turns cloud backup off) and all file work happens off the main thread.
 */
object PlayHistory {
    private val flow = MutableStateFlow<List<PlayedSong>>(emptyList())
    val songs: StateFlow<List<PlayedSong>> = flow

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    @Volatile
    private var file: HistoryFile? = null

    fun init(context: Context) {
        if (file != null) return
        val f = HistoryFile(File(context.applicationContext.filesDir, "history.json"))
        file = f
        scope.launch { flow.update { mergeLoaded(it, f.read()) } }
    }

    fun record(song: PlayedSong) {
        flow.update { addPlay(it, song) }
        persist()
    }

    fun clear() {
        flow.value = emptyList()
        persist()
    }

    private fun persist() {
        scope.launch {
            // Always write the latest list, so two quick saves can never leave the older one on disk.
            writeLock.withLock { runCatching { file?.write(flow.value) } }
        }
    }
}
