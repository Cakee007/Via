package dev.ujhhgtg.via.ui.behavior

import android.animation.ObjectAnimator
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max

/** mark.via.common.widget.v0; preview is the small edge arrow, never the WebView. */
class PageGestureController(
    private val preview: View,
    private val canStart: () -> Boolean,
    private val signal: () -> Int,
    private val onGesture: (Int) -> Unit,
    private val onPreview: (View, Int) -> Unit,
) {
    private var tracking = false
    private var previousX = 0
    private var previousY = 0
    private var downY = 0
    private var movedX = 0
    private var movedY = 0
    private var axis = -1
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var previewPosition = 0
    private var previewStart = 0
    private var signals = 0
    private var lastTime = 0L
    private val density = preview.resources.displayMetrics.density
    private val verticalUnit = (12 * density + .5f).toInt()
    private val directionGuard = (4 * density + .5f).toInt()
    private val previewWidth get() = preview.width
    private val threshold get() = previewWidth * 4 / 5

    init { preview.visibility = View.VISIBLE }

    fun reset() { viewportWidth = 0; viewportHeight = 0; preview.translationX = 0f }

    /** Signal bits: 1 back, 2 forward, 4 hide toolbars, 8 show toolbars. Always returns false. */
    fun onTouch(view: View, event: MotionEvent): Boolean {
        if (viewportWidth == 0 && viewportHeight == 0) { viewportWidth = view.width; viewportHeight = view.height }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                signals = signal(); tracking = false
                previousX = event.rawX.toInt(); previousY = event.rawY.toInt(); downY = previousY
                movedX = 0; movedY = 0; axis = -1
                preview.alpha = 1f; preview.translationX = 0f
                lastTime = SystemClock.elapsedRealtime()
            }
            MotionEvent.ACTION_MOVE -> if (tracking) {
                var dx = (event.rawX - previousX).toInt()
                var dy = (event.rawY - previousY).toInt()
                previousX = event.rawX.toInt(); previousY = event.rawY.toInt()
                if (axis == -1) {
                    val now = SystemClock.elapsedRealtime()
                    val elapsed = max(1L, now - lastTime).toFloat()
                    val vx = dx / elapsed; val vy = dy / elapsed
                    lastTime = now
                    val direction = if (dx > 0) 1 else 2
                    axis = if (abs(vx) > abs(vy) && abs(vx) > .5f && abs(dx) > abs(dy) + directionGuard && hasSignal(direction)) 1 else 0
                    if (axis == 1) {
                        previewStart = if (dx > 0) -10 else viewportWidth + previewWidth + 10
                        onPreview(preview, direction)
                        previewPosition = previewStart; preview.translationX = previewPosition.toFloat()
                    }
                }
                if (axis == 1) dx = dx * 8 / 10
                if (movedX + dx > threshold && dx > 0) dx = threshold - movedX
                else if (movedX + dx < -threshold && dx < 0) dx = -threshold - movedX
                if (movedX > 0 && movedX + dx <= 0 || movedX < 0 && movedX + dx >= 0) dx = 0
                if (axis == 1) {
                    dy = 0; previewPosition += dx; preview.translationX = previewPosition.toFloat()
                }
                movedX += dx; movedY += dy
            } else {
                tracking = canStart(); movedX = 0; movedY = 0
            }
            MotionEvent.ACTION_UP -> {
                tracking = false
                if (axis == 1) {
                    val amount = threshold
                    if (abs(movedX) < amount) {
                        val fraction = if (amount == 0) 0f else abs(movedX).toFloat() / amount
                        ObjectAnimator.ofFloat(preview, "translationX", previewPosition.toFloat(), previewStart.toFloat()).apply {
                            duration = (fraction * 300).toLong(); start()
                        }
                    } else {
                        ObjectAnimator.ofFloat(preview, "alpha", 1f, 0f).apply { duration = 300; start() }
                        if (movedX >= amount) onGesture(1) else if (movedX <= -amount) onGesture(2)
                    }
                } else {
                    val dy = event.rawY.toInt() - downY
                    if (abs(dy) > verticalUnit * 3) {
                        val direction = if (dy > 0) 8 else 4
                        if (hasSignal(direction)) onGesture(direction)
                    }
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (axis == 1) preview.translationX = 0f
                axis = 0
            }
        }
        return false
    }

    private fun hasSignal(mask: Int) = signals and mask == mask
}
