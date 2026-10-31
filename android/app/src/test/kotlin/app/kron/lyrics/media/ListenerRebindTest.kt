package app.kron.lyrics.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenerRebindTest {
    @Test
    fun grantedButNotConnectedAsksForARebind() {
        assertTrue(shouldRequestRebind(granted = true, connected = false))
    }

    @Test
    fun alreadyConnectedNeedsNothing() {
        assertFalse(shouldRequestRebind(granted = true, connected = true))
    }

    @Test
    fun withoutAccessThereIsNothingToRebind() {
        assertFalse(shouldRequestRebind(granted = false, connected = false))
        assertFalse(shouldRequestRebind(granted = false, connected = true))
    }
}
