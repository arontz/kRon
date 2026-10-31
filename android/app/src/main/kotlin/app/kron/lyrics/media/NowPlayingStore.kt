package app.kron.lyrics.media

import app.kron.lyrics.model.NowPlayingSource
import app.kron.lyrics.model.PlaybackSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The media-session implementation of [NowPlayingSource]; [MediaListenerService] publishes into it. */
object NowPlayingStore : NowPlayingSource {
    val EMPTY = PlaybackSnapshot(track = null, positionMs = 0, speed = 0f, playing = false, updatedAtMs = 0)

    private val state = MutableStateFlow(EMPTY)
    override val snapshot: StateFlow<PlaybackSnapshot> = state

    fun publish(s: PlaybackSnapshot) {
        state.value = s
    }
}
