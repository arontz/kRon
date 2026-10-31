package app.kron.lyrics.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import app.kron.lyrics.log.LogKind
import app.kron.lyrics.log.activityLog

/**
 * Runs right after kRon is updated. Android normally reconnects notification access by itself, so this only looks
 * again after a grace period and, if it is still not connected, switches the listener off and on once.
 */
class RebindReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        activityLog.add(LogKind.SERVICE, "kRon was updated, checking that notification access reconnects")
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val granted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
                if (shouldRequestRebind(granted, ListenerConnection.connected, ListenerConnection.rebindTried)) {
                    ListenerConnection.rebindTried = true
                    activityLog.add(LogKind.SERVICE, "Still not connected after the update, switching the listener off and on")
                    requestListenerRebind(context)
                } else {
                    activityLog.add(LogKind.SERVICE, if (ListenerConnection.connected) "Connected after the update" else "Nothing to reconnect")
                }
            } finally {
                pending.finish()
            }
        }, RECONNECT_GRACE_MS)
    }
}
