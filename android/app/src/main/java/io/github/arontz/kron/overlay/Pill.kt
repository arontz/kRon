package io.github.arontz.kron.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.arontz.kron.media.NowPlaying
import io.github.arontz.kron.ui.Kron
import io.github.arontz.kron.ui.NextGlyph
import io.github.arontz.kron.ui.PauseGlyph
import io.github.arontz.kron.ui.PlayGlyph

/** The floating pill: cover, song and artist, play/pause and next. Drag anywhere to move it. */
@Composable
fun Pill(
    nowPlaying: NowPlaying?,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .clip(RoundedCornerShape(50))
            .background(Kron.Ground2)
            .clickable(onClick = onOpen)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Cover(nowPlaying)
        Column(Modifier.weight(1f, fill = false).widthIn(min = 80.dp)) {
            Text(
                text = nowPlaying?.title?.ifBlank { null } ?: "Nothing playing",
                color = Kron.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = nowPlaying?.artist?.ifBlank { null } ?: "Play something in any app",
                color = Kron.Soft,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (nowPlaying != null) {
            RoundButton(if (nowPlaying.isPlaying) "Pause" else "Play", onPlayPause, accent = true) {
                if (nowPlaying.isPlaying) PauseGlyph(Kron.AccentInk, 14.dp) else PlayGlyph(Kron.AccentInk, 14.dp)
            }
            RoundButton("Next", onNext) { NextGlyph(Kron.Ink, 14.dp) }
        }
    }
}

@Composable
private fun Cover(nowPlaying: NowPlaying?) {
    val art = nowPlaying?.art
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(Kron.Ground3),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(36.dp))
        } else {
            Text("k", color = Kron.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RoundButton(label: String, onClick: () -> Unit, accent: Boolean = false, glyph: @Composable () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (accent) Kron.Accent else Kron.Ground3)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { glyph() }
}
