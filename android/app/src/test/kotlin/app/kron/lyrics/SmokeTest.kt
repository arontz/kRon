package app.kron.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test
    fun appIdIsStable() = assertEquals("app.kron.lyrics", BuildConfig.APPLICATION_ID)
}
