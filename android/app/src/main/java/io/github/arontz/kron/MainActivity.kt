package io.github.arontz.kron

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The same colours as the Windows app and the website.
private val Ground = Color(0xFF0F1218)
private val Ink = Color(0xFFF3F5F9)
private val Soft = Color(0xFFB9C0CF)
private val Accent = Color(0xFF7FE0C6)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Ground, primary = Accent)) {
                Home(installedVersion())
            }
        }
    }
}

/** Reads the version from the installed package, so no BuildConfig is needed. */
private fun ComponentActivity.installedVersion(): String =
    packageManager.getPackageInfo(packageName, 0).versionName ?: ""

@Composable
private fun Home(version: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ground)
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = buildAnnotatedString {
                append("k")
                withStyle(SpanStyle(color = Accent)) { append("Ron") }
            },
            color = Ink,
            fontSize = 40.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text("Lyrics on top of any app. Coming soon.", color = Soft, fontSize = 17.sp)
        Text("Version $version", color = Soft, fontSize = 14.sp)
    }
}

@Preview
@Composable
private fun HomePreview() = Home("0.0.0")
