package app.kron.lyrics.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostPolicyTest {
    private fun decide(
        hasTrack: Boolean = true,
        canDraw: Boolean = true,
        appVisible: Boolean = false,
        playbackVisible: Boolean = true,
    ) = renderDecision(hasTrack, canDraw, appVisible, playbackVisible)

    @Test
    fun withoutATrackEverythingStops() {
        for (canDraw in listOf(true, false)) for (app in listOf(true, false)) for (vis in listOf(true, false)) {
            assertEquals(Render.Stop, decide(hasTrack = false, canDraw = canDraw, appVisible = app, playbackVisible = vis))
        }
    }

    @Test
    fun whileKronIsOpenTheCardIsHiddenButTheLyricsKeepRunning() {
        assertEquals(Render.RunCardHidden, decide(appVisible = true))
    }

    @Test
    fun whileKronIsOpenNeitherAPauseNorMissingOverlayPermissionStopsIt() {
        assertEquals(Render.RunCardHidden, decide(appVisible = true, playbackVisible = false))
        assertEquals(Render.RunCardHidden, decide(appVisible = true, canDraw = false))
        assertEquals(Render.RunCardHidden, decide(appVisible = true, canDraw = false, playbackVisible = false))
    }

    @Test
    fun withoutOverlayPermissionAndKronClosedThereIsNothingToShow() {
        assertEquals(Render.Stop, decide(canDraw = false))
    }

    @Test
    fun aLongPauseFadesTheCardThenStops() {
        assertEquals(Render.FadeThenStop, decide(playbackVisible = false))
    }

    @Test
    fun otherwiseTheCardShows() {
        assertEquals(Render.ShowCard, decide())
    }

    @Test
    fun theHostStartsForPlayingMusicWithOverlayPermission() {
        assertTrue(hostShouldRun(hasTrack = true, playing = true, canDraw = true, appVisible = false))
    }

    @Test
    fun theHostStartsWhenKronOpensEvenIfPausedOrWithoutOverlayPermission() {
        assertTrue(hostShouldRun(hasTrack = true, playing = false, canDraw = true, appVisible = true))
        assertTrue(hostShouldRun(hasTrack = true, playing = true, canDraw = false, appVisible = true))
    }

    @Test
    fun theHostDoesNotStartWhenNobodyCouldSeeIt() {
        assertFalse(hostShouldRun(hasTrack = true, playing = false, canDraw = true, appVisible = false))
        assertFalse(hostShouldRun(hasTrack = true, playing = true, canDraw = false, appVisible = false))
        assertFalse(hostShouldRun(hasTrack = false, playing = true, canDraw = true, appVisible = true))
    }
}
