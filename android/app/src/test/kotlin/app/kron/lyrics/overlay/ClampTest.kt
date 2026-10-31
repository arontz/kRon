package app.kron.lyrics.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class ClampTest {
    private fun clamp(x: Int, y: Int, cardW: Int = 400, cardH: Int = 200, screenW: Int = 1080, screenH: Int = 2400) =
        clampToScreen(Pos(x, y), cardW, cardH, screenW, screenH)

    @Test
    fun negativeCoordinatesGoToZero() {
        assertEquals(Pos(0, 0), clamp(-50, -10))
    }

    @Test
    fun pastTheRightEdgePinsToTheEdge() {
        assertEquals(Pos(680, 100), clamp(900, 100))
    }

    @Test
    fun pastTheBottomPinsToTheBottom() {
        assertEquals(Pos(100, 2200), clamp(100, 2300))
    }

    @Test
    fun inBoundsIsUnchanged() {
        assertEquals(Pos(100, 300), clamp(100, 300))
    }

    @Test
    fun cardWiderThanTheScreenPinsToZero() {
        assertEquals(Pos(0, 50), clamp(30, 50, cardW = 1200))
    }

    @Test
    fun rotationToLandscapePullsTheCardBackInside() {
        // saved low in portrait (y 2200), then the screen becomes 2400 wide and 1080 tall
        assertEquals(Pos(680, 880), clamp(680, 2200, screenW = 2400, screenH = 1080))
    }
}
