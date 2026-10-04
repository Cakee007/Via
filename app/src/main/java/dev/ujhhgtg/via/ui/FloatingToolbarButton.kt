package dev.ujhhgtg.via.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.skins.SkinResources
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * c8.s6.Y8/ta/ab/T8 with mark.via.common.widget.t0: the "press to hide/show" fullscreen button.
 * A tap reveals the toolbars; a swipe triggers back (left), forward (right), tabs (up) or reload
 * (down); a long press drags it to one of the snapped positions stored in the `fab` preference.
 */
@SuppressLint("ViewConstructor")
class FloatingToolbarButton(
    context: Context,
    private val preferences: BrowserPreferences,
    private val onTap: () -> Unit,
    private val onSwipe: (Int) -> Unit,
) : ImageView(context) {
    private val margin = resources.getDimensionPixelSize(R.dimen.floating_toolbar_button_margin)
    private val size = resources.getDimensionPixelSize(R.dimen.floating_toolbar_button_size)
    private val slop = dp(6)
    private val threshold = dp(30)
    private var shownIcon = -1
    var shown = false
        private set

    init {
        isClickable = true; isFocusable = true
        id = generateViewId()
        setIcon(0)
        setColorFilter(-1)
        background = StateListDrawable().apply {
            fun oval(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
            addState(intArrayOf(-android.R.attr.state_focused, android.R.attr.state_enabled, -android.R.attr.state_pressed), oval(0x99333333.toInt()))
            addState(intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed), oval(0x99666666.toInt()))
        }
        contentDescription = context.getString(R.string.toast_fullscreen_off)
        val padding = dp(10)
        setPadding(padding, padding, padding, padding)
        elevation = dp(12).toFloat()
        visibility = GONE
        setOnTouchListener(DragHelper())
    }

    /** s6.ta: 42dp, 36dp from the edges, placed by the stored gravity bits. */
    fun applyPosition() {
        val stored = preferences.fabSize
        var gravity = when {
            stored and 3 == 3 -> Gravity.START
            stored and 5 == 5 -> Gravity.END
            else -> Gravity.CENTER_HORIZONTAL
        }
        gravity = gravity or if (stored and 0x50 == 0x50) Gravity.BOTTOM else Gravity.CENTER_VERTICAL
        layoutParams = FrameLayout.LayoutParams(size, size, gravity).apply { setMargins(margin, margin, margin, margin) }
    }

    /** s6.ab */
    fun show() {
        if (shown) return
        shown = true
        animate().cancel()
        animate().withStartAction { alpha = 0f; visibility = VISIBLE }.translationY(0f).alpha(1f).setDuration(160L).start()
    }

    /** s6.T8 */
    fun hide(animated: Boolean) {
        if (!shown) return
        shown = false
        animate().cancel()
        if (animated) animate().translationY(dp(32).toFloat()).alpha(0f).setDuration(160L)
            .withEndAction { visibility = GONE }.start()
        else visibility = GONE
    }

    /** t0.c.c: the icon previews the swipe direction. */
    private fun setIcon(direction: Int) {
        val (icon, key) = when (direction) {
            0 -> R.drawable.menu to "ic_menu"
            1 -> R.drawable.chevron_left to "ic_back"
            2 -> R.drawable.chevron_right to "ic_forward"
            4 -> R.drawable.tab_square to "ic_tabs"
            8 -> R.drawable.reload to "ic_reload"
            else -> return
        }
        if (shownIcon == icon) return
        shownIcon = icon
        setImageDrawable(SkinResources.drawable(context, icon, key))
    }

    /** t0.c.e: snap to the left/right third or the bottom-centre, and persist the gravity. */
    private fun snap(x: Int, y: Int): IntArray {
        val parent = parent as View
        val height = parent.height; val width = parent.width
        val third = width / 3
        val result = IntArray(2)
        var gravity: Int
        val lowerBand = height / 4 * 3 - margin
        when {
            x < third -> { result[0] = margin; gravity = 3 }
            x > third * 2 -> { result[0] = width - this.width - margin; gravity = 5 }
            y >= lowerBand -> { result[0] = (width - this.width) / 2; gravity = 0 }
            x < width / 2 -> { result[0] = margin; gravity = 3 }
            else -> { result[0] = width - this.width - margin; gravity = 5 }
        }
        if (gravity != 0 && y < lowerBand) result[1] = (height - this.height) / 2
        else { result[1] = height - this.height - margin; gravity = gravity or 80 }
        preferences.fabSize = gravity
        return result
    }

    private inner class DragHelper : OnTouchListener {
        private var dragging = false
        private var axis = 0
        private var lastX = 0
        private var lastY = 0
        private var downAt = 0L
        private var alphaBefore = 1f
        private var direction = 0
        private val enterDrag = Runnable {
            if (axis == 0) {
                dragging = true
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                alpha = .5f
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = false
                    postDelayed(enterDrag, 300L)
                    alphaBefore = alpha
                    lastX = event.rawX.toInt(); lastY = event.rawY.toInt()
                    downAt = SystemClock.elapsedRealtime()
                }
                MotionEvent.ACTION_MOVE -> move(event)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { performClick(); release() }
            }
            return true
        }

        private fun move(event: MotionEvent) {
            val dx = (event.rawX - lastX).toInt(); val dy = (event.rawY - lastY).toInt()
            lastX = event.rawX.toInt(); lastY = event.rawY.toInt()
            if (dragging) { translationX += dx; translationY += dy; return }
            if (axis == 0 && (abs(dx) > slop || abs(dy) > slop)) {
                axis = if (abs(dx) > abs(dy)) 3 else 12
                removeCallbacks(enterDrag)
            }
            if (axis == 0) return
            var offsetX = 0; var offsetY = 0
            if (axis and 3 != 0) offsetX = (translationX + dx * .75f).toInt().let {
                if (axis and 1 == 0) max(0, it) else if (axis and 2 == 0) min(0, it) else it
            } else offsetY = (translationY + dy * .75f).toInt().let {
                if (axis and 4 == 0) max(0, it) else if (axis and 8 == 0) min(0, it) else it
            }
            translationX = offsetX.toFloat(); translationY = offsetY.toFloat()
            direction = when {
                abs(offsetX) > threshold -> if (offsetX > 0) 2 else 1
                abs(offsetY) > threshold -> if (offsetY > 0) 8 else 4
                else -> 0
            }
            setIcon(direction)
        }

        private fun release() {
            val fromX = translationX.toInt(); val fromY = translationY.toInt()
            val toX: Int; val toY: Int
            if (dragging) {
                alpha = alphaBefore
                val target = snap(lastX, lastY)
                toX = target[0] - left - fromX; toY = target[1] - top - fromY
            } else {
                toX = -fromX; toY = -fromY
                removeCallbacks(enterDrag)
            }
            val duration = (min(1f, max(abs(toY), abs(toX)) / (slop * 30f)) * 150f + 100f).toLong()
            ValueAnimator.ofFloat(0f, 1f).apply {
                addUpdateListener { translationX = toX * (it.animatedValue as Float) + fromX; translationY = toY * (it.animatedValue as Float) + fromY }
                addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(animation: Animator) = finish() })
                interpolator = AccelerateInterpolator()
                this.duration = duration
            }.start()
        }

        /** t0.j */
        private fun finish() {
            if (dragging) {
                translationX = 0f; translationY = 0f
                applyPosition()
                return
            }
            if (direction != 0) {
                onSwipe(direction)
                direction = 0
                setIcon(0)
            } else if (axis == 0 && SystemClock.elapsedRealtime() - downAt < 300L) onTap()
            axis = 0
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

    companion object {
        /** s6.Y8 adds the button to the browser canvas once, on first use. */
        fun attach(parent: ViewGroup, button: FloatingToolbarButton) {
            if (button.parent != null) return
            button.applyPosition()
            parent.addView(button)
        }
    }
}
