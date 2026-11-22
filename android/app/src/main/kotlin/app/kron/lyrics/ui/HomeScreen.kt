package app.kron.lyrics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kron.lyrics.ListenSlot
import app.kron.lyrics.NowPlayingPanel

/** Setup status (only what still needs fixing), the lyrics of what is playing, and the keep-screen-on switch. */
@Composable
fun HomeScreen(state: MainState, actions: MainActions) {
    val issues = setupIssues(state.listenerStatus, state.reconnectHelp, state.overlayGranted)
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("kRon", fontSize = 28.sp, fontWeight = FontWeight.Bold)

        if (issues.isEmpty()) {
            Text(
                "All set. Play a song in any music app: the lyrics card floats over your apps, and shows here while kRon is open.",
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            )
        }
        issues.forEach { issue -> IssueCard(issue, actions) }

        NowPlayingPanel(state.liveLyrics, state.nowPlaying)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Keep the screen on", fontWeight = FontWeight.SemiBold)
                Text("The screen won't turn off while kRon is open and in front. Other apps are not affected.", fontSize = 13.sp)
            }
            Switch(checked = state.keepScreenOn, onCheckedChange = actions.onKeepScreenOnChange)
        }
        ListenSlot()
    }
}

@Composable
private fun IssueCard(issue: SetupIssue, actions: MainActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (issue) {
            SetupIssue.NotificationAccess -> {
                Text("Turn on notification access", fontWeight = FontWeight.SemiBold)
                Text("Lets kRon see which song is playing. It only reads the song's title, artist and position.", fontSize = 13.sp)
                Button(onClick = actions.onOpenListener) { Text("Open notification access") }
                // An APK installed from a browser or file manager is "sideloaded": Android 13+ greys out the switch
                // until the user allows restricted settings in App info.
                Text(
                    "If the switch is greyed out: open App info, tap the three dots at the top right, choose " +
                        "\"Allow restricted settings\", then come back and try again.",
                    fontSize = 13.sp,
                )
                Button(onClick = actions.onOpenAppInfo) { Text("Open App info") }
            }
            SetupIssue.Reconnect -> {
                Text("Notification access isn't connected", fontWeight = FontWeight.SemiBold)
                // On some phones Android does not reconnect an enabled listener after the app is updated, and only
                // switching the permission off and on makes it bind again.
                Text(
                    "It is on, but Android has not connected kRon to it yet, so no lyrics will show. This can happen " +
                        "after an update. Open the setting, switch kRon off, then on again.",
                    fontSize = 13.sp,
                )
                Button(onClick = actions.onOpenListener) { Text("Open notification access") }
            }
            SetupIssue.Overlay -> {
                Text("Allow display over other apps", fontWeight = FontWeight.SemiBold)
                Text("Lets kRon float the lyrics card over the app you are using. The lyrics still show here without it.", fontSize = 13.sp)
                Button(onClick = actions.onOpenOverlay) { Text("Grant permission") }
            }
        }
    }
}
