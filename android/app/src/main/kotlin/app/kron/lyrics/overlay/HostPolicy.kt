package app.kron.lyrics.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What [OverlayHost] does on each tick. */
enum class Render {
    /** Nothing to show or no way to show it: shut the host down. */
    Stop,

    /** kRon is open, so the lyrics are shown inside it: remove the card at once but keep computing lyrics. */
    RunCardHidden,

    /** Paused for a while with kRon closed: fade the card out, then shut down. */
    FadeThenStop,

    ShowCard,
}

/**
 * While kRon is open the lyrics live in the app, so the card goes away and neither a pause nor a missing overlay
 * permission may stop the host. Otherwise the card shows only with the overlay permission and while playback is
 * recent enough.
 */
fun renderDecision(hasTrack: Boolean, canDrawOverlays: Boolean, appVisible: Boolean, playbackVisible: Boolean): Render = when {
    !hasTrack -> Render.Stop
    appVisible -> Render.RunCardHidden
    !canDrawOverlays -> Render.Stop
    !playbackVisible -> Render.FadeThenStop
    else -> Render.ShowCard
}

/** Whether the media-session service should start the host: for the card while playing, or for the app view. */
fun hostShouldRun(hasTrack: Boolean, playing: Boolean, canDraw: Boolean, appVisible: Boolean): Boolean =
    hasTrack && ((playing && canDraw) || appVisible)

/** Whether kRon's own screen is on screen, set by MainActivity from onStart and onStop. */
object AppVisibility {
    private val flow = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = flow

    var isVisible: Boolean
        get() = flow.value
        set(value) {
            flow.value = value
        }
}

/** The lines the host is currently working out, shared so kRon's screen can show them instead of the card. */
object LiveLyrics {
    private val flow = MutableStateFlow<CardContent?>(null)
    val content: StateFlow<CardContent?> = flow

    fun set(value: CardContent?) {
        flow.value = value
    }
}
