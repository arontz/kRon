package io.github.arontz.kron.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import io.github.arontz.kron.MainActivity
import io.github.arontz.kron.R
import io.github.arontz.kron.media.NowPlayingStore
import io.github.arontz.kron.ui.KronTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps the pill on screen. It runs as a foreground service with a permanent notification, which is
 * what stops Android closing it while other apps are in front.
 */
class OverlayService : Service() {
    private lateinit var windows: WindowManager
    private var frame: DragFrame? = null
    private var owner: ServiceViewOwner? = null
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windows = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification())
        }
        if (frame == null && Settings.canDrawOverlays(this)) show()
        _running.value = true
        return START_STICKY
    }

    override fun onDestroy() {
        frame?.let { windows.removeView(it) }
        frame = null
        owner?.destroy()
        owner = null
        _running.value = false
        super.onDestroy()
    }

    private fun show() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        params.x = prefs.getInt(KEY_X, 0)
        params.y = prefs.getInt(KEY_Y, resources.displayMetrics.heightPixels / 4)

        val compose = ComposeView(this).apply {
            setContent {
                val nowPlaying by NowPlayingStore.state.collectAsState()
                KronTheme {
                    Pill(
                        nowPlaying = nowPlaying,
                        onOpen = ::openApp,
                        onPlayPause = NowPlayingStore::playPause,
                        onNext = NowPlayingStore::next,
                    )
                }
            }
        }
        val drag = DragFrame(
            this,
            onMove = { dx, dy ->
                params.x += dx
                params.y += dy
                frame?.let { windows.updateViewLayout(it, params) }
            },
            onRelease = ::snapToEdge,
        )
        drag.addView(compose)
        val lifecycle = ServiceViewOwner().also { it.attach(drag) }
        windows.addView(drag, params)
        frame = drag
        owner = lifecycle
    }

    /** After a drag the pill slides to the nearer side edge, like a chat bubble, and remembers where. */
    private fun snapToEdge() {
        val view = frame ?: return
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        params.x = if (params.x + view.width / 2 < screenW / 2) 0 else (screenW - view.width).coerceAtLeast(0)
        params.y = params.y.coerceIn(0, (screenH - view.height).coerceAtLeast(0))
        windows.updateViewLayout(view, params)
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putInt(KEY_X, params.x)
            putInt(KEY_Y, params.y)
        }
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun notification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_running), NotificationManager.IMPORTANCE_MIN).apply {
                description = getString(R.string.channel_running_description)
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_kron)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(open)
            .addAction(0, getString(R.string.notification_hide), stop)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL = "running"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "io.github.arontz.kron.STOP_PILL"
        private const val PREFS = "overlay"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
