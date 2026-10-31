package app.kron.lyrics.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/** Runs right after kRon is updated and reconnects notification access without the user opening the app. */
class RebindReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val granted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        if (shouldRequestRebind(granted, ListenerConnection.connected)) requestListenerRebind(context)
    }
}
