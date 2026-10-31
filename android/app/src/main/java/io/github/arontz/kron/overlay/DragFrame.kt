package io.github.arontz.kron.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Holds the pill's ComposeView. Taps go through to the buttons inside; once a finger moves past the
 * touch slop the frame takes over and moves the whole window. Raw screen coordinates are used because
 * the view itself moves with the window while dragging.
 */
@SuppressLint("ViewConstructor")
class DragFrame(
    context: Context,
    private val onMove: (dx: Int, dy: Int) -> Unit,
    private val onRelease: () -> Unit,
) : FrameLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX; downY = ev.rawY
                lastX = ev.rawX; lastY = ev.rawY
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && (abs(ev.rawX - downX) > slop || abs(ev.rawY - downY) > slop)) {
                    dragging = true
                }
            }
        }
        return dragging
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                dragging = true
                onMove((ev.rawX - lastX).toInt(), (ev.rawY - lastY).toInt())
                lastX = ev.rawX; lastY = ev.rawY
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) onRelease()
                dragging = false
            }
        }
        return true
    }
}
