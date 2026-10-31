package io.github.arontz.kron.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Tiny hand-drawn media glyphs, so the app doesn't pull in an icon library for three shapes.

@Composable
fun PlayGlyph(color: Color, size: Dp = 16.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width
    val h = this.size.height
    drawPath(Path().apply {
        moveTo(w * 0.22f, h * 0.12f)
        lineTo(w * 0.88f, h * 0.5f)
        lineTo(w * 0.22f, h * 0.88f)
        close()
    }, color)
}

@Composable
fun PauseGlyph(color: Color, size: Dp = 16.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width
    val h = this.size.height
    drawRect(color, Offset(w * 0.2f, h * 0.12f), Size(w * 0.2f, h * 0.76f))
    drawRect(color, Offset(w * 0.6f, h * 0.12f), Size(w * 0.2f, h * 0.76f))
}

@Composable
fun NextGlyph(color: Color, size: Dp = 16.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width
    val h = this.size.height
    drawPath(Path().apply {
        moveTo(w * 0.14f, h * 0.14f)
        lineTo(w * 0.66f, h * 0.5f)
        lineTo(w * 0.14f, h * 0.86f)
        close()
    }, color)
    drawRect(color, Offset(w * 0.7f, h * 0.14f), Size(w * 0.16f, h * 0.72f))
}
