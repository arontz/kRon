package app.kron.lyrics.media

import android.content.ComponentName
import android.content.res.Configuration
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import app.kron.lyrics.log.LogKind
import app.kron.lyrics.log.activityLog
import app.kron.lyrics.history.PlayHistory
import app.kron.lyrics.history.PlayTracker
import app.kron.lyrics.history.PlayedSong
import app.kron.lyrics.log.describeRead
import app.kron.lyrics.model.PlaybackSnapshot
import app.kron.lyrics.overlay.AppVisibility
import app.kron.lyrics.overlay.OverlayHost
import app.kron.lyrics.overlay.hostShouldRun
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Enabled by the user under "Notification access". It only reads media-session metadata
 * (title, artist, duration, state, position); notification contents are never touched.
 */
class MediaListenerService : NotificationListenerService() {
    private var manager: MediaSessionManager? = null
    private var controllers: List<MediaController> = emptyList()
    private val host by lazy { OverlayHost(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val playTracker = PlayTracker()
    private var playJob: Job? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        track(list.orEmpty())
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) = refresh()
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onSessionDestroyed() = refresh()
    }

    override fun onListenerConnected() {
        ListenerConnection.connected = true
        activityLog.add(LogKind.SERVICE, "Notification access connected")
        val m = getSystemService(MediaSessionManager::class.java)
        manager = m
        val component = ComponentName(this, MediaListenerService::class.java)
        m.addOnActiveSessionsChangedListener(sessionsListener, component)
        track(m.getActiveSessions(component))
    }

    override fun onListenerDisconnected() {
        ListenerConnection.connected = false
        activityLog.add(LogKind.SERVICE, "Notification access disconnected")
        manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        release()
        NowPlayingStore.publish(NowPlayingStore.EMPTY)
        trackPlays(NowPlayingStore.EMPTY)
        host.shutdown()
    }

    private fun track(list: List<MediaController>) {
        release()
        controllers = list
        list.forEach { it.registerCallback(callback) }
        refresh()
    }

    private fun release() {
        controllers.forEach { it.unregisterCallback(callback) }
        controllers = emptyList()
    }

    private fun refresh() {
        val infos = controllers.map {
            val state = it.playbackState
            SessionInfo(it.packageName, state != null && isPlayingState(state.state), state?.lastPositionUpdateTime ?: 0)
        }
        val best = pickSession(infos)
        val controller = best?.let { controllers[infos.indexOf(it)] }
        val state = controller?.playbackState
        if (controller == null || state == null) {
            activityLog.addIfChanged("none", LogKind.MEDIA, "No active media session")
            NowPlayingStore.publish(NowPlayingStore.EMPTY)
            trackPlays(NowPlayingStore.EMPTY)
            return
        }
        val meta = controller.metadata
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val snapshot = toSnapshot(
            title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = artist,
            durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1,
            state = state.state,
            positionMs = state.position,
            speed = state.playbackSpeed,
            updateTimeMs = state.lastPositionUpdateTime,
        )
        val t = snapshot.track
        activityLog.addIfChanged(
            "${controller.packageName}|${t?.title}|${t?.artist}|${snapshot.playing}",
            LogKind.MEDIA,
            describeRead(controller.packageName, snapshot),
        )
        NowPlayingStore.publish(snapshot)
        trackPlays(snapshot)
        startHostIfNeeded()
    }

    /** Adds a song to the history once it has played for a while; skipping through songs adds nothing. */
    private fun trackPlays(s: PlaybackSnapshot) {
        playTracker.onSnapshot(s.track, s.playing, SystemClock.elapsedRealtime())
        playJob?.cancel()
        if (playTracker.remainingMs(SystemClock.elapsedRealtime()) == null) return
        playJob = scope.launch {
            while (true) {
                val remaining = playTracker.remainingMs(SystemClock.elapsedRealtime()) ?: return@launch
                if (remaining > 0) {
                    delay(remaining + 50)
                    continue
                }
                val track = playTracker.due(SystemClock.elapsedRealtime()) ?: return@launch
                PlayHistory.record(PlayedSong(track.title, track.artist, track.durationMs, System.currentTimeMillis()))
                return@launch
            }
        }
    }

    /**
     * The host runs the floating card while music plays, or the lyrics inside kRon while it is open (even paused,
     * and without the overlay permission). It shuts itself down once nobody could see it.
     */
    private fun startHostIfNeeded() {
        val s = NowPlayingStore.snapshot.value
        if (hostShouldRun(s.track != null, s.playing, Settings.canDrawOverlays(this), AppVisibility.isVisible)) {
            host.ensureRunning()
        }
    }

    override fun onCreate() {
        super.onCreate()
        PlayHistory.init(this)
        // Opening kRon is what starts the in-app lyrics when the host is not already running.
        scope.launch { AppVisibility.visible.collect { startHostIfNeeded() } }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        host.onConfigurationChanged()
    }

    override fun onDestroy() {
        scope.cancel()
        host.shutdown()
        super.onDestroy()
    }
}
