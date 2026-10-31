package app.kron.lyrics.overlay

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import app.kron.lyrics.lyrics.LrclibFetcher
import app.kron.lyrics.lyrics.LyricsCache
import app.kron.lyrics.lyrics.LyricsLoader
import app.kron.lyrics.lyrics.LyricsRepository
import app.kron.lyrics.lyrics.LyricsUiState
import app.kron.lyrics.lyrics.currentIndex
import app.kron.lyrics.media.NowPlayingStore
import app.kron.lyrics.model.PlaybackSnapshot
import app.kron.lyrics.playback.PositionClock
import app.kron.lyrics.playback.cardVisible
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Runs the lyrics card for [app.kron.lyrics.media.MediaListenerService], which the system keeps bound, so no
 * foreground service is needed. It exists only while a song plays: it shuts itself down once the card has
 * faded after a pause, when nothing is playing, or when the overlay permission is gone.
 */
class OverlayHost(private val context: Context) {
    private var scope: CoroutineScope? = null
    private var controller: OverlayController? = null
    private var fading = false
    private var latest: PlaybackSnapshot = NowPlayingStore.EMPTY
    private val clock = PositionClock()

    /** Starts the card if it is not running; a card that is mid-fade is replaced by a fresh one. */
    fun ensureRunning() {
        if (scope != null && !fading) return
        shutdown()
        start()
    }

    fun shutdown() {
        controller?.release()
        scope?.cancel()
        controller = null
        scope = null
        fading = false
    }

    fun onConfigurationChanged() {
        controller?.onConfigurationChanged()
    }

    private fun start() {
        val handler = CoroutineExceptionHandler { _, e ->
            Log.e("kron", "The lyrics card failed", e)
            shutdown()
        }
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main + handler)
        val c = OverlayController(context, CardPositionStore(context.getSharedPreferences("card", Context.MODE_PRIVATE)))
        val loader = LyricsLoader(s, LyricsRepository(LrclibFetcher(), LyricsCache(File(context.cacheDir, "lyrics"))))
        scope = s
        controller = c

        s.launch {
            NowPlayingStore.snapshot.collect { snap ->
                latest = snap
                clock.set(snap.positionMs, snap.speed, snap.updatedAtMs)
                loader.load(snap.track)
            }
        }
        s.launch {
            while (true) {
                render(c, loader)
                delay(if (latest.playing) 100 else 500)
            }
        }
    }

    private fun render(c: OverlayController, loader: LyricsLoader) {
        if (fading) return
        val now = SystemClock.elapsedRealtime()
        val snap = latest
        val track = snap.track
        if (track == null || !Settings.canDrawOverlays(context)) return shutdown()
        if (!cardVisible(snap, now)) {
            fading = true
            return c.hide { shutdown() }
        }

        val state = loader.state.value
        val lines = if (state is LyricsUiState.Ready) {
            visibleLines(state.lines, currentIndex(state.lines, clock.positionAt(now)))
        } else {
            CardLines("", "", "")
        }
        c.update(CardContent(track.title, track.artist, state, lines))
        c.show()
    }
}
