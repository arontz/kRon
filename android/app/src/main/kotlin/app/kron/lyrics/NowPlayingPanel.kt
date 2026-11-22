package app.kron.lyrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kron.lyrics.lyrics.LyricsUiState
import app.kron.lyrics.model.PlaybackSnapshot
import app.kron.lyrics.overlay.CardContent

/**
 * The lyrics shown inside kRon while it is open, in place of the floating card. [content] is what the host is
 * working out; when there is none (nothing started it yet) the title and artist still come from [nowPlaying].
 */
@Composable
fun NowPlayingPanel(content: CardContent?, nowPlaying: PlaybackSnapshot) {
    val track = nowPlaying.track
    val title = content?.title ?: track?.title
    if (title == null) {
        Text("Play a song in any music app and its lyrics show here.", fontSize = 13.sp)
        return
    }
    val artist = content?.artist ?: track?.artist.orEmpty()
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        if (artist.isNotBlank()) Text(artist, fontSize = 13.sp)
        when (val state = content?.state) {
            is LyricsUiState.Ready -> {
                val faded = LocalContentColor.current.copy(alpha = 0.6f)
                // A blank line keeps its height so the block does not jump at the first and last lines.
                Text(content.lines.previous.ifEmpty { " " }, fontSize = 16.sp, color = faded)
                Text(
                    content.lines.current.ifEmpty { " " },
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(content.lines.next.ifEmpty { " " }, fontSize = 16.sp, color = faded)
            }
            LyricsUiState.Missing -> Text("No lyrics found for this song.")
            LyricsUiState.Unavailable -> Text("The lyrics service is down. kRon keeps trying.")
            LyricsUiState.Loading -> Text("Finding lyrics…")
            LyricsUiState.Idle, null -> Text(if (nowPlaying.playing) "Finding lyrics…" else "Paused. Press play to see the lyrics.")
        }
    }
}
