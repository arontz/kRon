package app.kron.lyrics.debug

import android.app.Activity
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.LinearLayout

/** Debug-only stand-in for a music app: publishes a media session and plays no audio. */
class FakePlayerActivity : Activity() {
    private lateinit var session: MediaSession
    private var positionMs = 0L
    private var startedAtMs = 0L
    private var playing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = MediaSession(this, "kron-fake").apply {
            setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "Never Gonna Give You Up")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "Rick Astley")
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, 213_000)
                    .build(),
            )
            isActive = true
        }
        publish(PlaybackState.STATE_PAUSED)

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(Button(context).apply { text = "Play"; setOnClickListener { play() } })
                addView(Button(context).apply { text = "Pause"; setOnClickListener { pause() } })
            },
        )
    }

    override fun onDestroy() {
        session.release()
        super.onDestroy()
    }

    private fun play() {
        if (playing) return
        playing = true
        startedAtMs = SystemClock.elapsedRealtime()
        publish(PlaybackState.STATE_PLAYING)
    }

    private fun pause() {
        if (!playing) return
        positionMs += SystemClock.elapsedRealtime() - startedAtMs
        playing = false
        publish(PlaybackState.STATE_PAUSED)
    }

    private fun publish(state: Int) {
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)
                .setState(state, positionMs, if (state == PlaybackState.STATE_PLAYING) 1f else 0f, SystemClock.elapsedRealtime())
                .build(),
        )
    }
}
