package io.github.arontz.kron.media

import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.core.graphics.scale

/**
 * Android only hands out other apps' media sessions to an enabled notification listener, so this
 * service exists for that alone. It never reads, stores or sends notification content.
 */
class MediaListenerService : NotificationListenerService() {
    private val main = Handler(Looper.getMainLooper())
    private var sessions: MediaSessionManager? = null
    private var controllers: List<MediaController> = emptyList()
    private var active: MediaController? = null

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        track(list.orEmpty())
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            // Another session may have started playing; switch to it if so.
            if (state?.state != PlaybackState.STATE_PLAYING) pick() else refresh()
        }
        override fun onSessionDestroyed() = pick()
    }

    override fun onListenerConnected() {
        val manager = getSystemService(MediaSessionManager::class.java) ?: return
        sessions = manager
        val me = ComponentName(this, MediaListenerService::class.java)
        try {
            manager.addOnActiveSessionsChangedListener(sessionsChanged, me, main)
            track(manager.getActiveSessions(me))
            NowPlayingStore.setConnected(true)
        } catch (_: SecurityException) {
            NowPlayingStore.setConnected(false)
        }
    }

    override fun onListenerDisconnected() {
        sessions?.removeOnActiveSessionsChangedListener(sessionsChanged)
        sessions = null
        controllers.forEach { it.unregisterCallback(callback) }
        controllers = emptyList()
        active = null
        NowPlayingStore.setConnected(false)
        requestRebind(ComponentName(this, MediaListenerService::class.java))
    }

    private fun track(list: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(callback) }
        controllers = list
        list.forEach { it.registerCallback(callback, main) }
        pick()
    }

    /** Prefer whichever session is playing; otherwise keep the one we had, or the most recent. */
    private fun pick() {
        active = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: active?.takeIf { a -> controllers.any { it.sessionToken == a.sessionToken } }
            ?: controllers.firstOrNull()
        refresh()
    }

    private fun refresh() {
        val c = active
        val meta = c?.metadata
        if (c == null || meta == null) {
            NowPlayingStore.publish(c, null)
            return
        }
        val state = c.playbackState
        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty()
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty()
        val art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        NowPlayingStore.publish(
            c,
            NowPlaying(
                packageName = c.packageName,
                title = title,
                artist = artist,
                album = meta.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
                art = art?.let(::thumbnail),
                durationMs = meta.getLong(MediaMetadata.METADATA_KEY_DURATION),
                isPlaying = state?.state == PlaybackState.STATE_PLAYING,
                positionMs = state?.position ?: 0,
                reportedAt = state?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
                speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            ),
        )
    }

    /** Players often send full-size cover art; the pill needs a small one. */
    private fun thumbnail(bitmap: Bitmap): Bitmap {
        val size = 128
        if (bitmap.width <= size && bitmap.height <= size) return bitmap
        val scale = size.toFloat() / maxOf(bitmap.width, bitmap.height)
        return bitmap.scale((bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1))
    }
}
