package app.kron.lyrics

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import app.kron.lyrics.history.PlayHistory
import app.kron.lyrics.log.LogEntry
import app.kron.lyrics.log.LogKind
import app.kron.lyrics.log.activityLog
import app.kron.lyrics.media.ListenerConnection
import app.kron.lyrics.media.ListenerStatus
import app.kron.lyrics.media.NowPlayingStore
import app.kron.lyrics.media.RECONNECT_GRACE_MS
import app.kron.lyrics.media.listenerStatus
import app.kron.lyrics.media.requestListenerRebind
import app.kron.lyrics.media.shouldRequestRebind
import app.kron.lyrics.overlay.AppVisibility
import app.kron.lyrics.overlay.LiveLyrics
import app.kron.lyrics.ui.MainActions
import app.kron.lyrics.ui.MainApp
import app.kron.lyrics.ui.MainState
import app.kron.lyrics.update.UpdateChecker
import app.kron.lyrics.update.UpdateResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var listenerGranted by mutableStateOf(false)
    private var overlayGranted by mutableStateOf(false)
    private var checking by mutableStateOf(false)
    private var updateResult by mutableStateOf<UpdateResult?>(null)
    private var keepScreenOn by mutableStateOf(false)
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    private fun applyKeepScreenOn() {
        if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, false)
        applyKeepScreenOn()
        PlayHistory.init(this)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val scope = rememberCoroutineScope()
                    // The help appears only if Android still has not connected kRon after a few seconds, so a normal
                    // start (the service connects within a moment) never flashes it.
                    val connected by ListenerConnection.state.collectAsState()
                    val liveLyrics by LiveLyrics.content.collectAsState()
                    val nowPlaying by NowPlayingStore.snapshot.collectAsState()
                    val history by PlayHistory.songs.collectAsState()
                    val logEntries by activityLog.entries.collectAsState()
                    val status = listenerStatus(listenerGranted, connected)
                    var reconnectHelp by remember { mutableStateOf(false) }
                    LaunchedEffect(status) {
                        reconnectHelp = false
                        if (status != ListenerStatus.NotConnected) return@LaunchedEffect
                        delay(RECONNECT_GRACE_MS) // Android usually connects the listener by itself within a moment
                        if (ListenerConnection.connected) return@LaunchedEffect
                        if (shouldRequestRebind(listenerGranted, ListenerConnection.connected, ListenerConnection.rebindTried)) {
                            ListenerConnection.rebindTried = true
                            activityLog.add(LogKind.SERVICE, "Notification access is on but still not connected, switching the listener off and on")
                            requestListenerRebind(this@MainActivity)
                            delay(RECONNECT_GRACE_MS)
                            if (ListenerConnection.connected) return@LaunchedEffect
                        }
                        reconnectHelp = true
                    }

                    MainApp(
                        state = MainState(
                            listenerStatus = status,
                            reconnectHelp = reconnectHelp,
                            overlayGranted = overlayGranted,
                            liveLyrics = liveLyrics,
                            nowPlaying = nowPlaying,
                            keepScreenOn = keepScreenOn,
                            history = history,
                            logEntries = logEntries,
                            versionName = BuildConfig.VERSION_NAME,
                            checking = checking,
                            updateResult = updateResult,
                        ),
                        actions = MainActions(
                            onOpenListener = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                            onOpenOverlay = {
                                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                            },
                            onOpenAppInfo = {
                                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                            },
                            onKeepScreenOnChange = { on ->
                                keepScreenOn = on
                                prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, on).apply()
                                applyKeepScreenOn()
                            },
                            onCheckUpdate = {
                                if (!checking) {
                                    checking = true
                                    scope.launch {
                                        updateResult = UpdateChecker(BuildConfig.VERSION_NAME).check()
                                        checking = false
                                    }
                                }
                            },
                            onOpenDownload = { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                            onClearHistory = PlayHistory::clear,
                            onClearLog = activityLog::clear,
                        ),
                    )
                }
            }
        }
    }

    // Visible, not merely resumed: while kRon is on screen the lyrics show here and the floating card steps aside.
    override fun onStart() {
        super.onStart()
        AppVisibility.isVisible = true
    }

    override fun onStop() {
        // Rotating the phone recreates this screen (onStop, then onStart); staying "visible" through that keeps the
        // floating card from flashing on and off.
        if (!isChangingConfigurations) AppVisibility.isVisible = false
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        listenerGranted = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        overlayGranted = Settings.canDrawOverlays(this)
    }

    private companion object {
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    }
}

@Composable
fun LogScreen(entries: List<LogEntry>, onClear: () -> Unit, onBack: () -> Unit) {
    val clock = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Activity log", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Everything kRon read or sent since it started, newest first. Kept in memory only; it is gone when the app closes.",
            fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            OutlinedButton(onClick = onClear) { Text("Clear") }
        }
        if (entries.isEmpty()) Text("Nothing yet. Play a song in any music app.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries) { e ->
                Column {
                    Text("${clock.format(Date(e.timeMs))} · ${e.kind.label}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(e.text, fontSize = 13.sp)
                }
            }
        }
    }
}

/** Reserved for the Listen button of the song-recognition phase. */
@Composable
fun ListenSlot() = Unit
