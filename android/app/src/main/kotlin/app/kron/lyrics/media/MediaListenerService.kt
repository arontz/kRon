package app.kron.lyrics.media

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService

/**
 * Enabled by the user under "Notification access". It only reads media-session metadata
 * (title, artist, duration, state, position); notification contents are never touched.
 */
class MediaListenerService : NotificationListenerService() {
    private var manager: MediaSessionManager? = null
    private var controllers: List<MediaController> = emptyList()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        track(list.orEmpty())
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) = refresh()
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onSessionDestroyed() = refresh()
    }

    override fun onListenerConnected() {
        val m = getSystemService(MediaSessionManager::class.java)
        manager = m
        val component = ComponentName(this, MediaListenerService::class.java)
        m.addOnActiveSessionsChangedListener(sessionsListener, component)
        track(m.getActiveSessions(component))
    }

    override fun onListenerDisconnected() {
        manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        release()
        NowPlayingStore.publish(NowPlayingStore.EMPTY)
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
            NowPlayingStore.publish(NowPlayingStore.EMPTY)
            return
        }
        val meta = controller.metadata
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        NowPlayingStore.publish(
            toSnapshot(
                title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE),
                artist = artist,
                durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1,
                state = state.state,
                positionMs = state.position,
                speed = state.playbackSpeed,
                updateTimeMs = state.lastPositionUpdateTime,
            ),
        )
    }
}
