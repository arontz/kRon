package app.kron.lyrics

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import app.kron.lyrics.log.LogEntry
import app.kron.lyrics.log.LogKind
import app.kron.lyrics.log.activityLog
import app.kron.lyrics.media.ListenerConnection
import app.kron.lyrics.media.ListenerStatus
import app.kron.lyrics.media.RECONNECT_GRACE_MS
import app.kron.lyrics.media.listenerStatus
import app.kron.lyrics.media.requestListenerRebind
import app.kron.lyrics.media.shouldRequestRebind
import app.kron.lyrics.update.UpdateChecker
import app.kron.lyrics.update.UpdateResult
import app.kron.lyrics.update.updateMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var listenerGranted by mutableStateOf(false)
    private var overlayGranted by mutableStateOf(false)
    private var showLog by mutableStateOf(false)
    private var checking by mutableStateOf(false)
    private var updateResult by mutableStateOf<UpdateResult?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val scope = rememberCoroutineScope()
                    // The help appears only if Android still has not connected kRon after a few seconds, so a normal
                    // start (the service connects within a moment) never flashes it.
                    val connected by ListenerConnection.state.collectAsState()
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
                    if (showLog) {
                        BackHandler { showLog = false }
                        val entries by activityLog.entries.collectAsState()
                        LogScreen(entries = entries, onClear = activityLog::clear, onBack = { showLog = false })
                    } else SetupScreen(
                        listenerGranted = listenerGranted,
                        showReconnectHelp = reconnectHelp,
                        overlayGranted = overlayGranted,
                        onOpenListener = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                        onOpenOverlay = {
                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                        },
                        onOpenAppInfo = {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                        },
                        versionName = BuildConfig.VERSION_NAME,
                        checking = checking,
                        updateResult = updateResult,
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
                        onOpenLog = { showLog = true },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        listenerGranted = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        overlayGranted = Settings.canDrawOverlays(this)
    }
}

@Composable
fun SetupScreen(
    listenerGranted: Boolean,
    showReconnectHelp: Boolean,
    overlayGranted: Boolean,
    onOpenListener: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenAppInfo: () -> Unit,
    versionName: String,
    checking: Boolean,
    updateResult: UpdateResult?,
    onCheckUpdate: () -> Unit,
    onOpenDownload: (String) -> Unit,
    onOpenLog: () -> Unit,
) {
    Column(
        modifier = Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("kRon", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("Lyrics for whatever is playing, floating over your other apps.")
        ListenSlot()
        PermissionRow(
            title = "Notification access",
            explanation = "Lets kRon see which song is playing. It only reads the song's title, artist and position.",
            granted = listenerGranted,
            onOpen = onOpenListener,
        )
        if (!listenerGranted) {
            // An APK installed from a browser or file manager is "sideloaded": Android 13+ greys out the switch
            // until the user allows restricted settings in App info.
            Text(
                "If the Notification access switch is greyed out: open App info, tap the three dots at the top " +
                    "right, choose \"Allow restricted settings\", then come back and try again.",
                fontSize = 13.sp,
            )
            Button(onClick = onOpenAppInfo) { Text("Open App info") }
        }
        if (showReconnectHelp) {
            // On some phones Android does not reconnect an enabled listener after the app is updated, and only
            // switching the permission off and on makes it bind again.
            Text(
                "Notification access is on, but Android has not connected kRon to it yet, so no lyrics will show. " +
                    "This can happen after an update. Open the setting, switch kRon off, then on again.",
                fontSize = 13.sp,
            )
            Button(onClick = onOpenListener) { Text("Open notification access") }
        }
        PermissionRow(
            title = "Display over other apps",
            explanation = "Lets kRon show the lyrics card on top of the app you are using.",
            granted = overlayGranted,
            onOpen = onOpenOverlay,
        )
        Spacer(Modifier.height(8.dp))
        if (listenerGranted && overlayGranted) {
            Text("All set. Play a song in any music app and the lyrics card appears.")
        }
        Spacer(Modifier.height(8.dp))
        Text("What kRon reads and sends", fontWeight = FontWeight.SemiBold)
        Text(
            "It reads the title, artist, length and play/pause state of the active media session, and nothing from " +
                "your notifications. To get lyrics it sends the title and artist to QQ Music (c.y.qq.com), and, only " +
                "if QQ Music has none, the title, artist and length to lrclib.net. It contacts api.github.com only " +
                "when you tap \"Check for updates\". It stores only lyrics cached on this device and where you " +
                "dragged the card.",
            fontSize = 13.sp,
        )
        OutlinedButton(onClick = onOpenLog) { Text("Activity log") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onCheckUpdate, enabled = !checking) { Text(if (checking) "Checking…" else "Check for updates") }
            Text("v$versionName", fontSize = 13.sp)
        }
        if (updateResult != null) {
            Text(updateMessage(updateResult), fontSize = 13.sp)
            if (updateResult is UpdateResult.Available) {
                Button(onClick = { onOpenDownload(updateResult.release.pageUrl) }) { Text("Open download page") }
            }
        }
    }
}

@Composable
fun LogScreen(entries: List<LogEntry>, onClear: () -> Unit, onBack: () -> Unit) {
    val clock = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Column(modifier = Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

@Composable
private fun PermissionRow(title: String, explanation: String, granted: Boolean, onOpen: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(if (granted) "Granted" else "Not granted", fontSize = 13.sp)
            }
            Button(onClick = onOpen) { Text(if (granted) "Review" else "Grant") }
        }
        Text(explanation, fontSize = 13.sp)
    }
}
