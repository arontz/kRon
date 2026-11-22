package app.kron.lyrics.media

import android.os.SystemClock
import app.kron.lyrics.model.NowPlayingSource
import app.kron.lyrics.model.PlaybackSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * What is playing, from two places: the media session ([MediaListenerService] publishes into it) and a song the
 * microphone recognized. [snapshot] is the combination ([activeSnapshot]), so a session update never wipes a heard song.
 */
object NowPlayingStore : NowPlayingSource {
    val EMPTY = PlaybackSnapshot(track = null, positionMs = 0, speed = 0f, playing = false, updatedAtMs = 0)

    private var session = EMPTY
    private var recognized: RecognizedSong? = null
    private val flow = MutableStateFlow(EMPTY)
    override val snapshot: StateFlow<PlaybackSnapshot> = flow

    private val heardFlow = MutableStateFlow(false)

    /** Whether a heard song is what is being shown, so it can always be dismissed, even after the screen is recreated. */
    val heardSongActive: StateFlow<Boolean> = heardFlow

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var expiry: Job? = null

    @Synchronized
    fun publish(s: PlaybackSnapshot) {
        session = s
        recognized = recognizedAfterSession(s, recognized)
        update()
    }

    /** A song the microphone recognized; shown until it expires, until [clearRecognized], or until the phone plays something. */
    @Synchronized
    fun publishRecognized(snap: PlaybackSnapshot) {
        recognized = RecognizedSong(snap, SystemClock.elapsedRealtime() + RECOGNIZED_TTL_MS)
        update()
        expiry?.cancel()
        expiry = scope.launch {
            delay(RECOGNIZED_TTL_MS)
            update()
        }
    }

    @Synchronized
    fun clearRecognized() {
        recognized = null
        expiry?.cancel()
        update()
    }

    @Synchronized
    private fun update() {
        val now = SystemClock.elapsedRealtime()
        flow.value = activeSnapshot(session, recognized, now)
        heardFlow.value = heardSongShown(session, recognized, now)
    }
}
