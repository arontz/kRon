package app.kron.lyrics.ui

import app.kron.lyrics.media.ListenerStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupIssuesTest {
    @Test
    fun everythingWorkingMeansNoIssues() {
        assertEquals(emptyList<SetupIssue>(), setupIssues(ListenerStatus.Connected, reconnectHelp = false, overlayGranted = true))
    }

    @Test
    fun missingNotificationAccessIsAnIssue() {
        assertEquals(
            listOf(SetupIssue.NotificationAccess),
            setupIssues(ListenerStatus.NotGranted, reconnectHelp = false, overlayGranted = true),
        )
    }

    @Test
    fun aListenerThatIsOnButNotConnectedIsOnlyAnIssueOnceTheHelpAppears() {
        assertEquals(emptyList<SetupIssue>(), setupIssues(ListenerStatus.NotConnected, reconnectHelp = false, overlayGranted = true))
        assertEquals(
            listOf(SetupIssue.Reconnect),
            setupIssues(ListenerStatus.NotConnected, reconnectHelp = true, overlayGranted = true),
        )
    }

    @Test
    fun missingOverlayPermissionIsAnIssue() {
        assertEquals(listOf(SetupIssue.Overlay), setupIssues(ListenerStatus.Connected, reconnectHelp = false, overlayGranted = false))
    }

    @Test
    fun issuesComeInTheOrderTheUserShouldFixThem() {
        assertEquals(
            listOf(SetupIssue.NotificationAccess, SetupIssue.Overlay),
            setupIssues(ListenerStatus.NotGranted, reconnectHelp = false, overlayGranted = false),
        )
        assertEquals(
            listOf(SetupIssue.Reconnect, SetupIssue.Overlay),
            setupIssues(ListenerStatus.NotConnected, reconnectHelp = true, overlayGranted = false),
        )
    }
}
