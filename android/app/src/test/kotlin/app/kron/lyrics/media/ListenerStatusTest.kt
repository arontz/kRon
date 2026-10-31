package app.kron.lyrics.media

import org.junit.Assert.assertEquals
import org.junit.Test

class ListenerStatusTest {
    @Test
    fun withoutAccessItIsNotGrantedWhateverTheConnection() {
        assertEquals(ListenerStatus.NotGranted, listenerStatus(granted = false, connected = false))
        assertEquals(ListenerStatus.NotGranted, listenerStatus(granted = false, connected = true))
    }

    @Test
    fun grantedAndConnectedIsWorking() {
        assertEquals(ListenerStatus.Connected, listenerStatus(granted = true, connected = true))
    }

    @Test
    fun grantedButNotConnectedIsTheStuckStateAfterAnUpdate() {
        assertEquals(ListenerStatus.NotConnected, listenerStatus(granted = true, connected = false))
    }
}
