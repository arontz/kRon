package io.github.arontz.kron.media

import android.media.session.MediaController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The one place the rest of the app reads now playing from and sends media commands through. */
object NowPlayingStore {
    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state.asStateFlow()

    /** Whether the notification listener is connected, i.e. notification access is on and working. */
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    @Volatile
    private var controller: MediaController? = null

    internal fun publish(controller: MediaController?, nowPlaying: NowPlaying?) {
        this.controller = controller
        _state.value = nowPlaying
    }

    internal fun setConnected(value: Boolean) {
        _connected.value = value
        if (!value) publish(null, null)
    }

    fun playPause() {
        val c = controller ?: return
        if (_state.value?.isPlaying == true) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }
}
