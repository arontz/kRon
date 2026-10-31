package app.kron.lyrics.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Runs only while a song is playing (started by [app.kron.lyrics.media.MediaListenerService]) and stops itself
 * when the card has faded out, nothing is playing, or the overlay permission is gone.
 */
class OverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val clock = PositionClock()
    private lateinit var controller: OverlayController
    private lateinit var loader: LyricsLoader
    private var latest: PlaybackSnapshot = NowPlayingStore.EMPTY
    private var fading = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        controller = OverlayController(this, CardPositionStore(getSharedPreferences("card", MODE_PRIVATE)))
        loader = LyricsLoader(scope, LyricsRepository(LrclibFetcher(), LyricsCache(File(cacheDir, "lyrics"))))

        scope.launch {
            NowPlayingStore.snapshot.collect { s ->
                latest = s
                clock.set(s.positionMs, s.speed, s.updatedAtMs)
                loader.load(s.track)
            }
        }
        scope.launch {
            while (true) {
                render()
                delay(if (latest.playing) 100 else 500)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller.onConfigurationChanged()
    }

    override fun onDestroy() {
        controller.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun render() {
        if (fading) return
        val now = SystemClock.elapsedRealtime()
        val s = latest
        val track = s.track
        if (!Settings.canDrawOverlays(this)) return stopSelf()
        if (track == null) return stopSelf()
        if (!cardVisible(s, now)) {
            fading = true
            return controller.hide { stopSelf() }
        }

        val state = loader.state.value
        val lines = if (state is LyricsUiState.Ready) {
            visibleLines(state.lines, currentIndex(state.lines, clock.positionAt(now)))
        } else {
            CardLines("", "", "")
        }
        controller.update(CardContent(track.title, track.artist, state, lines))
        controller.show()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Lyrics overlay", NotificationManager.IMPORTANCE_LOW),
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("kRon")
            .setContentText("Showing lyrics")
            .setOngoing(true)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "overlay"
        const val NOTIFICATION_ID = 1
    }
}
