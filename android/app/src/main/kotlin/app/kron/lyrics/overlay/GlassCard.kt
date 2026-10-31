package app.kron.lyrics.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kron.lyrics.lyrics.LyricsUiState
import app.kron.lyrics.model.LrcLine

private val Accent = Color(0xFF7FE3C4)
private val Shadowed = TextStyle(shadow = Shadow(Color(0x73000000), Offset(0f, 1f), 4f))

/**
 * The floating lyrics card. With [blurAvailable] the background is a light see-through tint and the window
 * blurs what is behind it; without it the tint is darker so the text stays readable.
 */
@Composable
fun GlassCard(
    title: String,
    artist: String,
    state: LyricsUiState,
    lines: CardLines,
    blurAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val tint = if (blurAvailable) Color.White.copy(alpha = 0.16f) else Color(0xCC121420)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(tint, shape)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)), shape)
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Text(
            text = if (artist.isBlank()) title else "$title · $artist",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = Shadowed,
        )
        when (state) {
            is LyricsUiState.Ready -> {
                Side(lines.previous)
                Text(
                    text = lines.current,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 2.dp),
                    style = Shadowed,
                )
                Side(lines.next)
            }
            LyricsUiState.Missing -> Side("No lyrics found")
            LyricsUiState.Loading, LyricsUiState.Idle -> Unit
        }
    }
}

@Composable
private fun Side(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.7f),
        fontSize = 10.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = Shadowed,
    )
}

private val previewLines = CardLines("The line that came before", "The line being sung now", "The line coming next")
private val previewState = LyricsUiState.Ready(listOf(LrcLine(0, "x")))

@Preview(showBackground = true, backgroundColor = 0xFF31748F, widthDp = 170)
@Composable
private fun GlassCardBlurPreview() =
    GlassCard("Song title", "Artist", previewState, previewLines, blurAvailable = true)

@Preview(showBackground = true, backgroundColor = 0xFFF6C177, widthDp = 170)
@Composable
private fun GlassCardFallbackPreview() =
    GlassCard("Song title", "Artist", previewState, previewLines, blurAvailable = false)
