package dev.ujhhgtg.via.ui.widgets

import android.graphics.Canvas
import android.graphics.Interpolator
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AnimationUtils
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.ui.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** q4.a fast-scroll delegate, shared by original u0 RecyclerView and q4.b ScrollView. */
class ViaFastScroller(private val target: Target) {
    interface Target {
        val scrollingView: View
        fun scrollRange(): Int
        fun scrollExtent(): Int
        fun scrollOffset(): Int
        fun forwardTouch(event: MotionEvent)
    }
    private val view get() = target.scrollingView
    private val context get() = view.context
    private val resources get() = view.resources
    private val bounds = Rect(0, 0, context.dp(24f), context.dp(64f))
    private val minimumHeight = context.dp(64f)
    private val rtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    private val touchSlop = context.dp(4f).toFloat()
    private var dragY = 0f
    private var dragging = false
    private var moved = false
    private var state = 0
    private var fadeStart = 0L
    private val fadeDuration = ViewConfiguration.getScrollBarFadeDuration()
    private val alpha = FloatArray(1)
    private val interpolator = Interpolator(1, 2)
    private val drawable = thumb()
    private val fade = Runnable {
        val now = AnimationUtils.currentAnimationTimeMillis()
        if (now >= fadeStart) {
            interpolator.setKeyFrame(0, now.toInt(), floatArrayOf(255f))
            interpolator.setKeyFrame(1, now.toInt() + fadeDuration, floatArrayOf(0f))
            state = 2
            view.invalidate()
        }
    }

    private fun thumb(): StateListDrawable {
        val radius = context.dp(999f).toFloat()
        val radii = if (rtl) floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)
            else floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
        fun layer(color: Int, inset: Int) = LayerDrawable(arrayOf(GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadii = radii; setColor(color)
        })).apply { setLayerInset(0, if (rtl) 0 else inset, 0, if (rtl) inset else 0, 0) }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), layer(settingsColor(context, dev.ujhhgtg.via.R.attr.viaAccentColor, 0xff0099e5.toInt()), context.dp(18f)))
            addState(intArrayOf(), layer(0x80808080.toInt(), context.dp(19f)))
        }
    }

    fun awaken(): Boolean {
        view.postInvalidateOnAnimation()
        if (dragging) return false
        var duration = 1500L
        if (state == 0) duration = max(750L, duration)
        fadeStart = AnimationUtils.currentAnimationTimeMillis() + duration
        state = 1
        view.removeCallbacks(fade)
        view.postDelayed(fade, fadeStart - AnimationUtils.currentAnimationTimeMillis())
        return false
    }

    fun draw(canvas: Canvas) {
        var animate = false
        if (dragging) drawable.alpha = 255
        else {
            if (state == 0) return
            if (state == 2) {
                animate = true
                if (interpolator.timeToValues(alpha) == Interpolator.Result.FREEZE_END) state = 0
                else drawable.alpha = alpha[0].roundToInt()
            } else drawable.alpha = 255
        }
        if (position(0)) {
            drawable.setBounds(bounds.left + view.scrollX, bounds.top + view.scrollY, bounds.right + view.scrollX, bounds.bottom + view.scrollY)
            drawable.draw(canvas)
        }
        if (animate) view.invalidate()
    }

    private fun pressed(value: Boolean) {
        drawable.state = if (value) intArrayOf(android.R.attr.state_pressed) else intArrayOf()
        view.invalidate()
    }

    fun touch(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val y = event.y
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (state == 0) { dragging = false; return false }
                if (!dragging) {
                    moved = false
                    position(0)
                    if (y >= bounds.top && y <= bounds.bottom && event.x >= bounds.left && event.x <= bounds.right) {
                        dragging = true
                        dragY = y
                        target.forwardTouch(event)
                        val cancel = MotionEvent.obtain(event)
                        cancel.action = MotionEvent.ACTION_CANCEL
                        target.forwardTouch(cancel)
                        cancel.recycle()
                        pressed(true)
                        position(0)
                        view.removeCallbacks(fade)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                val delta = (y - dragY).roundToInt()
                if (delta != 0) {
                    if (moved) { position(delta); dragY = y }
                    else if (abs(delta) > touchSlop) {
                        position((if (delta > 0) delta - touchSlop else delta + touchSlop).toInt())
                        moved = true; dragY = y
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                pressed(false); dragging = false; awaken()
                if (action == MotionEvent.ACTION_UP && !moved) {
                    val down = MotionEvent.obtain(event)
                    down.action = MotionEvent.ACTION_DOWN
                    target.forwardTouch(down)
                    target.forwardTouch(event)
                    down.recycle()
                }
            }
        }
        if (!dragging) return false
        view.invalidate()
        view.parent.requestDisallowInterceptTouchEvent(true)
        return true
    }

    private fun position(delta: Int): Boolean {
        val range = target.scrollRange()
        if (range <= 0) return false
        val offset = target.scrollOffset()
        val extent = target.scrollExtent()
        val scrollable = range - extent
        if (scrollable <= view.height) return false
        val scrollFraction = offset.toFloat() / scrollable
        val extentFraction = extent.toFloat() / range
        val thumbWidth = bounds.width()
        if (rtl) { bounds.left = 0; bounds.right = thumbWidth }
        else { bounds.right = view.width; bounds.left = bounds.right - thumbWidth }
        val thumbHeight = max(minimumHeight, (min(extentFraction, .25f) * view.height).roundToInt())
        bounds.bottom = bounds.top + thumbHeight
        val track = view.height - thumbHeight
        val top = (scrollFraction * track).roundToInt()
        bounds.offsetTo(bounds.left, top)
        if (delta == 0) return true
        val targetTop = (top + delta).coerceIn(0, track)
        view.scrollBy(0, (scrollable * (targetTop.toFloat() / track)).roundToInt() - offset)
        return true
    }
}

