package app.kron.lyrics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kron.lyrics.recognition.RecognizerChoice
import app.kron.lyrics.update.UpdateResult
import app.kron.lyrics.update.updateMessage

/** Permissions, the Activity log, what kRon reads and sends, and updates. */
@Composable
fun SettingsScreen(state: MainState, actions: MainActions, onOpenLog: () -> Unit) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold)

        PermissionRow(
            title = "Notification access",
            explanation = "Lets kRon see which song is playing. It only reads the song's title, artist and position.",
            granted = state.listenerGranted,
            onOpen = actions.onOpenListener,
        )
        PermissionRow(
            title = "Display over other apps",
            explanation = "Lets kRon show the lyrics card on top of the app you are using.",
            granted = state.overlayGranted,
            onOpen = actions.onOpenOverlay,
        )

        AuddSection(state, actions)

        Text("What kRon reads and sends", fontWeight = FontWeight.SemiBold)
        Text(
            "It reads the title, artist, length and play/pause state of the active media session, and nothing from " +
                "your notifications. To get lyrics it sends the title and artist to QQ Music (c.y.qq.com), and, only " +
                "if QQ Music has none, the title, artist and length to lrclib.net. Only when you tap Listen, it " +
                "records about 10 seconds from the microphone. With AudD, that clip is sent to AudD (audd.io) to " +
                "find the song, and how long AudD keeps it is up to AudD. With the experimental Shazam option, the " +
                "clip is turned into a fingerprint on your phone and only the fingerprint (no audio, and no account) " +
                "is sent to Shazam, whose unofficial interface can change or block this at any time. Either way " +
                "kRon does not keep the recording. It contacts " +
                "api.github.com only when you tap \"Check for updates\". On this phone only, it keeps the lyrics it " +
                "has saved, a list of the songs you've played (title, artist, length and when), your AudD token if " +
                "you add one, where you dragged the card, and whether to keep the screen on. None of it is backed " +
                "up to the cloud.",
            fontSize = 13.sp,
        )
        OutlinedButton(onClick = onOpenLog) { Text("Activity log") }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = actions.onCheckUpdate, enabled = !state.checking) {
                Text(if (state.checking) "Checking…" else "Check for updates")
            }
            Text("v${state.versionName}", fontSize = 13.sp)
        }
        val result = state.updateResult
        if (result != null) {
            Text(updateMessage(result), fontSize = 13.sp)
            if (result is UpdateResult.Available) {
                Button(onClick = { actions.onOpenDownload(result.release.pageUrl) }) { Text("Open download page") }
            }
        }
    }
}

/** The AudD token the Listen button needs. It is the user's own, typed here, and stays on this phone. */
@Composable
private fun AuddSection(state: MainState, actions: MainActions) {
    var draft by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Song recognition", fontWeight = FontWeight.SemiBold)
        Text("Which service the Listen button on Home uses.", fontSize = 13.sp)
        RecognizerOption(
            selected = state.recognizerChoice == RecognizerChoice.Audd,
            title = "AudD",
            description = "Official and supported. Needs your own free token (the first 300 requests are free).",
            onSelect = { actions.onChooseRecognizer(RecognizerChoice.Audd) },
        )
        RecognizerOption(
            selected = state.recognizerChoice == RecognizerChoice.Shazam,
            title = "Shazam (experimental)",
            description = "Free, no account or token. It uses Shazam's unofficial interface, so it can stop working " +
                "at any time. Only a fingerprint of the clip, not the audio, is sent to Shazam, which Apple owns.",
            onSelect = { actions.onChooseRecognizer(RecognizerChoice.Shazam) },
        )
        Text("AudD token", fontWeight = FontWeight.SemiBold)
        Text(
            "Used only with AudD. Make a free account, copy your token and paste it here. It stays on this phone.",
            fontSize = 13.sp,
        )
        OutlinedButton(onClick = { actions.onOpenDownload("https://dashboard.audd.io") }) { Text("Get a free token") }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text(if (state.hasAuddToken) "Replace your token" else "AudD token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    actions.onSaveToken(draft)
                    draft = ""
                },
                enabled = draft.isNotBlank(),
            ) { Text("Save token") }
            if (state.hasAuddToken) OutlinedButton(onClick = actions.onClearToken) { Text("Remove token") }
        }
        Text(if (state.hasAuddToken) "A token is saved on this phone." else "No token yet.", fontSize = 13.sp)
    }
}

@Composable
private fun RecognizerOption(selected: Boolean, title: String, description: String, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, fontSize = 13.sp)
        }
    }
}

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
