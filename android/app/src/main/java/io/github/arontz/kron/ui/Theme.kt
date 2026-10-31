package io.github.arontz.kron.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The same colours as the Windows app and the website.
object Kron {
    val Ground = Color(0xFF0F1218)
    val Ground2 = Color(0xFF151A23)
    val Ground3 = Color(0xFF1C222E)
    val Ink = Color(0xFFF3F5F9)
    val Soft = Color(0xFFB9C0CF)
    val Accent = Color(0xFF7FE0C6)
    val AccentInk = Color(0xFF0A2820)
}

@Composable
fun KronTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Kron.Ground,
            surface = Kron.Ground2,
            primary = Kron.Accent,
            onPrimary = Kron.AccentInk,
            onBackground = Kron.Ink,
            onSurface = Kron.Ink,
        ),
        content = content,
    )
}
