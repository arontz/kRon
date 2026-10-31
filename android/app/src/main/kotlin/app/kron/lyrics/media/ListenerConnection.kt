package app.kron.lyrics.media

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService

/** Whether [MediaListenerService] is currently bound by the system. It is false in a freshly started process. */
object ListenerConnection {
    @Volatile
    var connected = false
}

/**
 * After an app update Android sometimes leaves an enabled notification listener unbound until the user toggles
 * the permission. Asking for a rebind when access is granted but the service is not connected avoids that.
 */
fun shouldRequestRebind(granted: Boolean, connected: Boolean): Boolean = granted && !connected

fun requestListenerRebind(context: Context) {
    NotificationListenerService.requestRebind(ComponentName(context, MediaListenerService::class.java))
}
