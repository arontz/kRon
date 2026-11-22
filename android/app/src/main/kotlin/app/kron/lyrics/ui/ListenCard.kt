package app.kron.lyrics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kron.lyrics.media.ListenerStatus
import app.kron.lyrics.recognition.ListenState
import app.kron.lyrics.recognition.RecognitionError

/** The Listen button and what happens after it is tapped. */
@Composable
fun ListenCard(state: MainState, actions: MainActions, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Listen", fontWeight = FontWeight.SemiBold)
        when (val s = state.listenState) {
            ListenState.Idle -> {
                Text(
                    "Identify a song playing nearby, on a speaker, a TV or the radio. When you tap, about 10 seconds " +
                        "of microphone audio is sent to AudD to find it.",
                    fontSize = 13.sp,
                )
                Button(onClick = actions.onListen) { Text("Listen") }
            }
            ListenState.NeedsToken -> {
                Text("Listening needs a free AudD token. Get one and paste it in Settings.", fontSize = 13.sp)
                Button(onClick = onOpenSettings) { Text("Open Settings") }
            }
            ListenState.NeedsPermission ->
                Text("Allow the microphone in the box that just appeared, then it starts listening.", fontSize = 13.sp)
            ListenState.PermissionDenied -> {
                Text("The microphone is off for kRon. Allow it in App info, then tap Listen again.", fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = actions.onOpenAppInfo) { Text("Open App info") }
                    OutlinedButton(onClick = actions.onListen) { Text("Listen") }
                }
            }
            is ListenState.Listening -> {
                Text("Listening… hold the phone near the music.", fontSize = 13.sp)
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                if (s.progress > 0.3f && s.level < 0.02f) Text("I can barely hear anything. Move closer.", fontSize = 13.sp)
                OutlinedButton(onClick = actions.onCancelListen) { Text("Cancel") }
            }
            ListenState.Identifying -> Text("Looking it up…", fontSize = 13.sp)
            is ListenState.Found -> {
                Text("Found: ${s.match.title}" + if (s.match.artist.isNotBlank()) " by ${s.match.artist}" else "", fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        !state.heardSongActive -> "Music is playing on this phone, so the lyrics follow that. The song is in your Lyrics list."
                        state.listenerStatus != ListenerStatus.Connected ->
                            "Turn on notification access to see its lyrics. The song is in your Lyrics list."
                        else -> "Its lyrics show above. It's in your Lyrics list too."
                    },
                    fontSize = 13.sp,
                )
                OutlinedButton(onClick = actions.onListen) { Text("Listen again") }
            }
            ListenState.NoMatch -> {
                Text("Couldn't recognize that. Move closer to the music and try again.", fontSize = 13.sp)
                Button(onClick = actions.onListen) { Text("Listen") }
            }
            ListenState.MicUnavailable -> {
                Text("Couldn't use the microphone. Another app may be using it.", fontSize = 13.sp)
                Button(onClick = actions.onListen) { Text("Listen") }
            }
            is ListenState.Failed -> {
                Text(failureMessage(s.error), fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = actions.onListen) { Text("Listen") }
                    if (s.error == RecognitionError.InvalidToken) OutlinedButton(onClick = onOpenSettings) { Text("Open Settings") }
                }
            }
        }
        // A heard song stays on show for a few minutes, so it can always be dismissed, whatever the card says.
        val busy = state.listenState is ListenState.Listening || state.listenState is ListenState.Identifying
        if (state.heardSongActive && !busy) {
            OutlinedButton(onClick = actions.onStopShowing) { Text("Stop showing the heard song") }
        }
    }
}

private fun failureMessage(error: RecognitionError) = when (error) {
    RecognitionError.InvalidToken -> "AudD didn't accept your token. Check it in Settings."
    RecognitionError.LimitReached -> "Your free AudD requests are used up. Add credit on audd.io, or try again later."
    RecognitionError.Network -> "Couldn't reach AudD. Check your connection and try again."
    RecognitionError.Other -> "Something went wrong with the lookup. Try again."
}
