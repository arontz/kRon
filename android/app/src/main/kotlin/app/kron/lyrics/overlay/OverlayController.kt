package app.kron.lyrics.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.kron.lyrics.lyrics.LyricsUiState

data class CardContent(val title: String, val artist: String, val state: LyricsUiState, val lines: CardLines)

/**
 * One card-sized overlay window, so touches anywhere else reach the app underneath. The card is a see-through
 * tint; there is deliberately no blur-behind flag, which would blur the whole screen rather than the card.
 */
class OverlayController(
    private val context: Context,
    private val positions: CardPositionStore,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var owner: OverlayLifecycleOwner? = null
    private var root: View? = null
    private var fadingRoot: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var content by mutableStateOf<CardContent?>(null)

    fun update(next: CardContent) {
        content = next
    }

    fun show() {
        if (root != null) return
        val (screenW, screenH) = screenSize()
        val w = cardWidthPx(screenW)
        val lp = WindowManager.LayoutParams(
            w,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val start = clampToScreen(positions.load() ?: Pos((screenW - w) / 2, screenH / 4), w, 0, screenW, screenH)
            x = start.x
            y = start.y
        }

        val lifecycle = OverlayLifecycleOwner().also { it.start() }
        val card = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { content?.let { c -> GlassCard(c.title, c.artist, c.state, c.lines) } }
        }
        val frame = DragFrame().apply {
            alpha = 0f
            addView(card)
            for (v in listOf<View>(this, card)) {
                v.setViewTreeLifecycleOwner(lifecycle)
                v.setViewTreeSavedStateRegistryOwner(lifecycle)
                v.setViewTreeViewModelStoreOwner(lifecycle)
            }
        }

        try {
            wm.addView(frame, lp)
        } catch (e: RuntimeException) {
            // e.g. the overlay permission was revoked between the check and now
            Log.w("kron", "Could not add the lyrics card", e)
            lifecycle.stop()
            return
        }
        owner = lifecycle
        root = frame
        params = lp
        frame.animate().alpha(1f).setDuration(200).start()
    }

    /** Fades the card out and removes it; [onGone] runs once it is off screen. */
    fun hide(onGone: () -> Unit = {}) {
        val v = root ?: return onGone()
        val lifecycle = owner
        root = null
        owner = null
        fadingRoot = v
        v.animate().alpha(0f).setDuration(300).withEndAction {
            fadingRoot = null
            runCatching { wm.removeView(v) }
            lifecycle?.stop()
            onGone()
        }.start()
    }

    /** Removes the card at once, including one that is mid-fade, for when the host is going away. */
    fun release() {
        for (v in listOfNotNull(root, fadingRoot)) {
            v.animate().cancel()
            runCatching { wm.removeView(v) }
        }
        root = null
        fadingRoot = null
        owner?.stop()
        owner = null
    }

    /** Re-fits the card to the screen, e.g. after a rotation. */
    fun onConfigurationChanged() {
        val v = root ?: return
        val lp = params ?: return
        val (screenW, screenH) = screenSize()
        lp.width = cardWidthPx(screenW)
        val p = clampToScreen(Pos(lp.x, lp.y), lp.width, v.height, screenW, screenH)
        lp.x = p.x
        lp.y = p.y
        runCatching { wm.updateViewLayout(v, lp) }
    }

    private fun screenSize(): Pair<Int, Int> {
        val b = wm.currentWindowMetrics.bounds
        return b.width() to b.height()
    }

    /** Holds the card and turns a press-and-drag on it into window moves, measured in screen coordinates. */
    private inner class DragFrame : FrameLayout(context) {
        private var start = Pos(0, 0)
        private var downX = 0f
        private var downY = 0f

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val lp = params ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    start = Pos(lp.x, lp.y)
                    downX = event.rawX
                    downY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    val (screenW, screenH) = screenSize()
                    val p = clampToScreen(dragPosition(start, downX, downY, event.rawX, event.rawY), lp.width, height, screenW, screenH)
                    lp.x = p.x
                    lp.y = p.y
                    runCatching { wm.updateViewLayout(this, lp) }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> positions.save(Pos(lp.x, lp.y))
            }
            return true
        }
    }
}
