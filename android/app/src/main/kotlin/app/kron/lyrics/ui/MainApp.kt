package app.kron.lyrics.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.kron.lyrics.LogScreen
import app.kron.lyrics.history.PlayedSong
import app.kron.lyrics.log.LogEntry
import app.kron.lyrics.media.ListenerStatus
import app.kron.lyrics.model.PlaybackSnapshot
import app.kron.lyrics.overlay.CardContent
import app.kron.lyrics.update.UpdateResult

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Lyrics("Lyrics", Icons.AutoMirrored.Filled.List),
    Settings("Settings", Icons.Filled.Settings),
}

/** Everything the screens show; [MainActivity] owns it and rebuilds it as things change. */
class MainState(
    val listenerStatus: ListenerStatus,
    val reconnectHelp: Boolean,
    val overlayGranted: Boolean,
    val liveLyrics: CardContent?,
    val nowPlaying: PlaybackSnapshot,
    val keepScreenOn: Boolean,
    val history: List<PlayedSong>,
    val logEntries: List<LogEntry>,
    val versionName: String,
    val checking: Boolean,
    val updateResult: UpdateResult?,
) {
    val listenerGranted: Boolean get() = listenerStatus != ListenerStatus.NotGranted
}

class MainActions(
    val onOpenListener: () -> Unit,
    val onOpenOverlay: () -> Unit,
    val onOpenAppInfo: () -> Unit,
    val onKeepScreenOnChange: (Boolean) -> Unit,
    val onCheckUpdate: () -> Unit,
    val onOpenDownload: (String) -> Unit,
    val onClearHistory: () -> Unit,
    val onClearLog: () -> Unit,
)

/**
 * The three tabs. The Activity log (opened from Settings) and a song's lyrics (opened from the Lyrics tab) are
 * screens inside their tabs, so Back leaves them first and then returns to Home before it leaves the app.
 */
@Composable
fun MainApp(state: MainState, actions: MainActions) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var showLog by rememberSaveable { mutableStateOf(false) }
    var openedSong by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = showLog || openedSong != null || tab != Tab.Home) {
        when {
            showLog -> showLog = false
            openedSong != null -> openedSong = null
            else -> tab = Tab.Home
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = {
                            tab = t
                            showLog = false
                            openedSong = null
                        },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            when (tab) {
                Tab.Home -> HomeScreen(state, actions)
                Tab.Lyrics -> HistoryScreen(
                    history = state.history,
                    openedSong = openedSong,
                    onOpenSong = { openedSong = it },
                    onCloseSong = { openedSong = null },
                    onClearHistory = actions.onClearHistory,
                )
                Tab.Settings ->
                    if (showLog) LogScreen(state.logEntries, actions.onClearLog, onBack = { showLog = false })
                    else SettingsScreen(state, actions, onOpenLog = { showLog = true })
            }
        }
    }
}
