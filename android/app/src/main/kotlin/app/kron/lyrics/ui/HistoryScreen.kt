package app.kron.lyrics.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kron.lyrics.history.PlayedSong
import app.kron.lyrics.history.playedAgo
import app.kron.lyrics.lyrics.LyricsCache
import app.kron.lyrics.lyrics.parseLrc
import app.kron.lyrics.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Identifies a song in the list; the same match [app.kron.lyrics.history.addPlay] uses. */
private fun idOf(s: PlayedSong) = "${s.artist.trim().lowercase()}|${s.title.trim().lowercase()}"

/** The songs the user has played, newest first. Tapping one shows its lyrics if they are saved on this device. */
@Composable
fun HistoryScreen(
    history: List<PlayedSong>,
    openedSong: String?,
    onOpenSong: (String) -> Unit,
    onCloseSong: () -> Unit,
    onClearHistory: () -> Unit,
) {
    val opened = openedSong?.let { id -> history.firstOrNull { idOf(it) == id } }
    if (opened != null) {
        SavedLyrics(opened, onCloseSong)
        return
    }

    var confirmClear by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Lyrics", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Songs you've played. Tap one to read its lyrics.", fontSize = 13.sp)
        if (history.isEmpty()) {
            Text("Songs show up here once they've played for 10 seconds.")
        } else {
            val now = System.currentTimeMillis()
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(history, key = { idOf(it) }) { song ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSong(idOf(song)) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(song.title, fontWeight = FontWeight.SemiBold)
                        val artist = song.artist.ifBlank { "Unknown artist" }
                        Text("$artist · ${playedAgo(now, song.playedAtMs)}", fontSize = 13.sp)
                    }
                }
            }
            OutlinedButton(onClick = { confirmClear = true }) { Text("Clear history") }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear your history?") },
            text = { Text("This removes the list of songs you've played from this phone. Your music apps aren't affected.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearHistory()
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

/** A song's lyrics as saved on this device. Nothing is fetched here: lyrics are saved when the song plays. */
@Composable
private fun SavedLyrics(song: PlayedSong, onBack: () -> Unit) {
    val context = LocalContext.current
    var lines by remember(song) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(song) {
        lines = withContext(Dispatchers.IO) {
            runCatching { LyricsCache(File(context.cacheDir, "lyrics")).get(Track(song.title, song.artist, song.durationMs)) }
                .getOrNull()
                ?.let { text -> parseLrc(text).map { it.text }.filter { it.isNotBlank() } }
        }
    }
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = onBack) { Text("Back") }
        Text(song.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (song.artist.isNotBlank()) Text(song.artist, fontSize = 13.sp)
        val saved = lines
        when {
            saved == null -> Text("Loading…", fontSize = 13.sp)
            saved.isEmpty() -> Text("Lyrics aren't saved for this song yet. Play it with kRon open to get them.")
            else -> Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                saved.forEach { Text(it, fontSize = 16.sp) }
            }
        }
    }
}
