package io.github.arontz.kron

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.arontz.kron.media.NowPlayingStore
import io.github.arontz.kron.overlay.OverlayService
import io.github.arontz.kron.overlay.Pill
import io.github.arontz.kron.ui.Kron
import io.github.arontz.kron.ui.KronTheme
import io.github.arontz.kron.ui.Setup
import io.github.arontz.kron.ui.Step

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KronTheme { Home() } }
    }
}

@Composable
private fun Home() {
    val context = LocalContext.current
    // Bumped whenever the app comes back from Settings, so every step re-checks itself.
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { checks++ }
    val running by OverlayService.running.collectAsState()
    val nowPlaying by NowPlayingStore.state.collectAsState()

    val done = remember(checks) { Step.entries.associateWith { Setup.isDone(context, it) } }
    val canShow = done.getValue(Step.Overlay) && done.getValue(Step.NotificationAccess)

    fun open(intent: Intent?) {
        if (intent == null) return
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Setup.appInfoIntent(context))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Kron.Ground)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = buildAnnotatedString {
                append("k")
                withStyle(SpanStyle(color = Kron.Accent)) { append("Ron") }
            },
            color = Kron.Ink,
            fontSize = 40.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text("Lyrics on top of any app. First, a few switches.", color = Kron.Soft, fontSize = 17.sp)

        StepCard(
            title = "Display over other apps",
            why = "So the pill can float on top of whatever you're using.",
            done = done.getValue(Step.Overlay),
            onTurnOn = { open(Setup.settingsIntent(context, Step.Overlay)) },
        )
        StepCard(
            title = "Notification access",
            why = "Android only shares what's playing with apps that have this. kRon reads the music player, never your messages or other notifications.",
            done = done.getValue(Step.NotificationAccess),
            onTurnOn = { open(Setup.settingsIntent(context, Step.NotificationAccess)) },
            help = "Switch greyed out? Android 13 and later block this for apps installed from an APK. " +
                "Open App info, tap ⋮ at the top right, choose Allow restricted settings, then try again.",
            onHelp = { open(Setup.appInfoIntent(context)) },
        )
        if (Build.VERSION.SDK_INT >= 33) {
            StepCard(
                title = "Notifications",
                why = "kRon shows one quiet notification while the pill is on. Android needs it to keep kRon running.",
                done = done.getValue(Step.Notifications),
                onTurnOn = { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
        }
        StepCard(
            title = "Keep running in the background",
            why = "Recommended. Some phones close apps to save battery, which makes the pill disappear.",
            done = done.getValue(Step.Battery),
            onTurnOn = { open(Setup.settingsIntent(context, Step.Battery)) },
        )

        Text("Preview", color = Kron.Soft, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        Pill(nowPlaying, onOpen = {}, onPlayPause = NowPlayingStore::playPause, onNext = NowPlayingStore::next)

        Button(
            onClick = { if (running) OverlayService.stop(context) else OverlayService.start(context) },
            enabled = canShow || running,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Kron.Accent, contentColor = Kron.AccentInk),
        ) {
            Text(if (running) "Hide the pill" else "Show the pill", fontWeight = FontWeight.SemiBold)
        }
        if (!canShow && !running) {
            Text(
                "Turn on the first two switches to show the pill.",
                color = Kron.Soft,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun StepCard(
    title: String,
    why: String,
    done: Boolean,
    onTurnOn: () -> Unit,
    help: String? = null,
    onHelp: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Kron.Ground2)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = Kron.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (done) {
                Text("On", color = Kron.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            } else {
                OutlinedButton(onClick = onTurnOn) { Text("Turn on", color = Kron.Ink) }
            }
        }
        Text(why, color = Kron.Soft, fontSize = 14.sp)
        if (!done && help != null && onHelp != null) {
            Text(help, color = Kron.Soft, fontSize = 13.sp)
            TextButton(onClick = onHelp) { Text("Open App info", color = Kron.Accent) }
        }
    }
}
