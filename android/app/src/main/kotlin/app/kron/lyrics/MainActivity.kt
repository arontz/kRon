package app.kron.lyrics

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var listenerGranted by mutableStateOf(false)
    private var overlayGranted by mutableStateOf(false)

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SetupScreen(
                        listenerGranted = listenerGranted,
                        overlayGranted = overlayGranted,
                        onOpenListener = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                        onOpenOverlay = {
                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        listenerGranted = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        overlayGranted = Settings.canDrawOverlays(this)
    }
}

@Composable
fun SetupScreen(
    listenerGranted: Boolean,
    overlayGranted: Boolean,
    onOpenListener: () -> Unit,
    onOpenOverlay: () -> Unit,
) {
    Column(
        modifier = Modifier.safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("kRon", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("Lyrics for whatever is playing, floating over your other apps.")
        ListenSlot()
        PermissionRow(
            title = "Notification access",
            explanation = "Lets kRon see which song is playing. It only reads the song's title, artist and position.",
            granted = listenerGranted,
            onOpen = onOpenListener,
        )
        PermissionRow(
            title = "Display over other apps",
            explanation = "Lets kRon show the lyrics card on top of the app you are using.",
            granted = overlayGranted,
            onOpen = onOpenOverlay,
        )
        Spacer(Modifier.height(8.dp))
        if (listenerGranted && overlayGranted) {
            Text("All set. Play a song in any music app and the lyrics card appears.")
        }
    }
}

/** Reserved for the Listen button of the song-recognition phase. */
@Composable
fun ListenSlot() = Unit

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
