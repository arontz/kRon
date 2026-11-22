package app.kron.lyrics.ui

import app.kron.lyrics.media.ListenerStatus

/** Something the user still has to fix before lyrics work. */
enum class SetupIssue { NotificationAccess, Reconnect, Overlay }

/**
 * What the Home screen should ask the user to fix, in the order to fix it. A listener that is on but not yet
 * connected only counts once the reconnect help has appeared, so a normal start never flashes a problem.
 */
fun setupIssues(status: ListenerStatus, reconnectHelp: Boolean, overlayGranted: Boolean): List<SetupIssue> = buildList {
    if (status == ListenerStatus.NotGranted) add(SetupIssue.NotificationAccess)
    if (status == ListenerStatus.NotConnected && reconnectHelp) add(SetupIssue.Reconnect)
    if (!overlayGranted) add(SetupIssue.Overlay)
}
