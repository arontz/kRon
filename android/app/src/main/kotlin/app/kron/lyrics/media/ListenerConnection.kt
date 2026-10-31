package app.kron.lyrics.media

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether [MediaListenerService] is currently bound by the system. It is false in a freshly started process. */
object ListenerConnection {
    private val flow = MutableStateFlow(false)

    /** For the setup screen, so it can react the moment the service connects. */
    val state: StateFlow<Boolean> = flow

    var connected: Boolean
        get() = flow.value
        set(value) {
            flow.value = value
        }
}

enum class ListenerStatus {
    /** The user has not switched notification access on for kRon. */
    NotGranted,

    /** On, and Android has connected kRon to it: lyrics work. */
    Connected,

    /** On, but Android has not connected kRon to it. Seen after an app update on some phones. */
    NotConnected,
}

fun listenerStatus(granted: Boolean, connected: Boolean): ListenerStatus = when {
    !granted -> ListenerStatus.NotGranted
    connected -> ListenerStatus.Connected
    else -> ListenerStatus.NotConnected
}

/**
 * After an app update Android sometimes leaves an enabled notification listener unbound until the user switches
 * the permission off and on. Asking for a rebind when access is granted but the service is not connected is the
 * attempt to avoid that.
 */
fun shouldRequestRebind(granted: Boolean, connected: Boolean): Boolean = granted && !connected

/**
 * Switches the listener component off and on again. That is a package change, which makes Android re-bind every
 * listener the user has approved. `NotificationListenerService.requestRebind` is deliberately not used: it only
 * undoes a `requestUnbind`, so for a listener that was never unbound it does nothing (seen in the Activity log:
 * three requests, no connection).
 */
fun requestListenerRebind(context: Context) {
    val component = ComponentName(context, MediaListenerService::class.java)
    val pm = context.packageManager
    pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
}
