package app.kron.lyrics.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.kron.lyrics.lyrics.LyricsUiState
import java.util.function.Consumer
import kotlin.math.roundToInt

data class CardContent(val title: String, val artist: String, val state: LyricsUiState, val lines: CardLines)

/**
 * One card-sized overlay window, so touches anywhere else reach the app underneath. The window blurs what is
 * behind it while the system allows that, and follows the setting if it changes.
 */
class OverlayController(
    private val context: Context,
    private val positions: CardPositionStore,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val owner = OverlayLifecycleOwner()
    private var view: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null

    private var content by mutableStateOf<CardContent?>(null)
    private var blurEnabled by mutableStateOf(wm.isCrossWindowBlurEnabled)
    private val blurListener = Consumer<Boolean> { enabled ->
        blurEnabled = enabled
        params?.let { applyBlur(it) }
        view?.let { v -> params?.let { wm.updateViewLayout(v, it) } }
    }

    val isShowing: Boolean get() = view != null

    fun update(next: CardContent) {
        content = next
    }

    fun show() {
        if (view != null) return
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
            val start = positions.load() ?: Pos((screenW - w) / 2, screenH / 4)
            x = start.x
            y = start.y
        }
        applyBlur(lp)
        params = lp

        owner.start()
        val v = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            alpha = 0f
            setContent {
                content?.let { c ->
                    GlassCard(
                        title = c.title,
                        artist = c.artist,
                        state = c.state,
                        lines = c.lines,
                        blurAvailable = blurEnabled,
                        modifier = Modifier.pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = ::savePosition,
                                onDragCancel = ::savePosition,
                            ) { change, drag ->
                                change.consume()
                                move(drag.x.roundToInt(), drag.y.roundToInt())
                            }
                        },
                    )
                }
            }
        }
        view = v
        wm.addView(v, lp)
        wm.addCrossWindowBlurEnabledListener(context.mainExecutor, blurListener)
        v.animate().alpha(1f).setDuration(200).start()
    }

    /** Fades the card out and removes it; [onGone] runs once it is off screen. */
    fun hide(onGone: () -> Unit = {}) {
        val v = view ?: return onGone()
        view = null
        wm.removeCrossWindowBlurEnabledListener(blurListener)
        v.animate().alpha(0f).setDuration(300).withEndAction {
            runCatching { wm.removeView(v) }
            owner.stop()
            onGone()
        }.start()
    }

    /** Removes the card at once, for when the service is going away. */
    fun release() {
        val v = view
        view = null
        runCatching { wm.removeCrossWindowBlurEnabledListener(blurListener) }
        if (v != null) {
            v.animate().cancel()
            runCatching { wm.removeView(v) }
            owner.stop()
        }
    }

    /** Re-fits the card to the screen, e.g. after a rotation. */
    fun onConfigurationChanged() {
        val v = view ?: return
        val lp = params ?: return
        val (screenW, screenH) = screenSize()
        lp.width = cardWidthPx(screenW)
        val p = clampToScreen(Pos(lp.x, lp.y), lp.width, v.height, screenW, screenH)
        lp.x = p.x
        lp.y = p.y
        wm.updateViewLayout(v, lp)
    }

    private fun move(dx: Int, dy: Int) {
        val v = view ?: return
        val lp = params ?: return
        val (screenW, screenH) = screenSize()
        val p = clampToScreen(Pos(lp.x + dx, lp.y + dy), lp.width, v.height, screenW, screenH)
        lp.x = p.x
        lp.y = p.y
        wm.updateViewLayout(v, lp)
    }

    private fun savePosition() {
        params?.let { positions.save(Pos(it.x, it.y)) }
    }

    private fun applyBlur(lp: WindowManager.LayoutParams) {
        if (blurEnabled) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            lp.blurBehindRadius = BLUR_RADIUS_PX
        } else {
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            lp.blurBehindRadius = 0
        }
    }

    private fun screenSize(): Pair<Int, Int> {
        val b = wm.currentWindowMetrics.bounds
        return b.width() to b.height()
    }

    private companion object {
        const val BLUR_RADIUS_PX = 24
    }
}
