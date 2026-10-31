package app.kron.lyrics.overlay

import app.kron.lyrics.model.LrcLine
import kotlin.math.roundToInt

data class CardLines(val previous: String, val current: String, val next: String)

/** The three lines the card shows around [index]; an index of -1 means the song has not reached its first line. */
fun visibleLines(lines: List<LrcLine>, index: Int): CardLines = CardLines(
    previous = lines.getOrNull(index - 1)?.text.orEmpty(),
    current = lines.getOrNull(index)?.text.orEmpty(),
    next = lines.getOrNull(index + 1)?.text.orEmpty(),
)

fun cardWidthPx(screenWidthPx: Int): Int = (screenWidthPx * 0.45).roundToInt()

/** Top-left corner of the card window, in screen pixels. */
data class Pos(val x: Int, val y: Int)

/**
 * Where the card goes while dragged. Both touch points are raw screen coordinates, because the window itself
 * moves under the finger and view-relative coordinates would shift with it.
 */
fun dragPosition(start: Pos, downX: Float, downY: Float, nowX: Float, nowY: Float): Pos =
    Pos(start.x + (nowX - downX).roundToInt(), start.y + (nowY - downY).roundToInt())

/** Keeps the whole card on screen; a card larger than the screen pins to 0. */
fun clampToScreen(p: Pos, cardW: Int, cardH: Int, screenW: Int, screenH: Int): Pos = Pos(
    x = p.x.coerceIn(0, (screenW - cardW).coerceAtLeast(0)),
    y = p.y.coerceIn(0, (screenH - cardH).coerceAtLeast(0)),
)
