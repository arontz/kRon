package app.kron.lyrics.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenerRebindTest {
    @Test
    fun grantedButStillNotConnectedAsksOnce() {
        assertTrue(shouldRequestRebind(granted = true, connected = false, alreadyTried = false))
    }

    @Test
    fun neverAsksTwiceInTheSameProcess() {
        assertFalse(shouldRequestRebind(granted = true, connected = false, alreadyTried = true))
    }

    @Test
    fun alreadyConnectedNeedsNothing() {
        assertFalse(shouldRequestRebind(granted = true, connected = true, alreadyTried = false))
    }

    @Test
    fun withoutAccessThereIsNothingToRebind() {
        assertFalse(shouldRequestRebind(granted = false, connected = false, alreadyTried = false))
        assertFalse(shouldRequestRebind(granted = false, connected = true, alreadyTried = false))
    }
}
