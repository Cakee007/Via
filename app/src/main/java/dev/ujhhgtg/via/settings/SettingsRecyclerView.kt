package dev.ujhhgtg.via.settings

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.widget.EdgeEffect
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.ui.widgets.ViaFastScroller
import kotlin.math.min

/** mark.via.common.widget.u0, q4.a and z8.r3's list configuration. */
class SettingsRecyclerView(context: Context) : RecyclerView(context), ViaFastScroller.Target {
    private var fastScroll: ViaFastScroller? = null

    init {
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        fastScroll = ViaFastScroller(this)
        edgeEffectFactory = StretchEdgeEffectFactory()
    }

    override fun awakenScrollBars(): Boolean = fastScroll?.awaken() ?: false
    override fun dispatchDraw(canvas: Canvas) { super.dispatchDraw(canvas); fastScroll?.draw(canvas) }
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
        (event.actionMasked == MotionEvent.ACTION_DOWN && fastScroll?.touch(event) == true) || super.onInterceptTouchEvent(event)
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = fastScroll?.touch(event) == true || super.onTouchEvent(event)
    override fun forwardTouch(event: MotionEvent) { super.onTouchEvent(event) }
    override val scrollingView: View get() = this
    override fun scrollRange() = computeVerticalScrollRange()
    override fun scrollExtent() = computeVerticalScrollExtent()
    override fun scrollOffset() = computeVerticalScrollOffset()

    /** d6.a: spring stretch instead of a platform glow. */
    class StretchEdgeEffectFactory : EdgeEffectFactory() {
        override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect = object : EdgeEffect(view.context) {
            private var animation: SpringAnimation? = null
            private var pulled = false
            private val horizontal get() = direction == DIRECTION_LEFT || direction == DIRECTION_RIGHT
            // dynamicanimation stays at the original's 1.0.0: 1.1 divides the frame time by the system
            // Animator scale, which d6.a's springs never did.
            private fun spring() = SpringAnimation(view, if (horizontal) DynamicAnimation.SCALE_X else DynamicAnimation.SCALE_Y).apply {
                // c0.b uses 1/256 for scale; the library default is .002.
                setMinimumVisibleChange(1f / 256f)
                spring = SpringForce().setFinalPosition(1f).setDampingRatio(.75f).setStiffness(200f)
            }
            private fun scale() = if (horizontal) view.scaleX else view.scaleY
            private fun edge() = if (horizontal) { if (view.pivotX == 0f) DIRECTION_LEFT else DIRECTION_RIGHT }
                else { if (view.pivotY == 0f) DIRECTION_TOP else DIRECTION_BOTTOM }
            private fun pivot() {
                if (horizontal) view.pivotX = if (direction == DIRECTION_LEFT) 0f else view.width.toFloat()
                else view.pivotY = if (direction == DIRECTION_TOP) 0f else view.height.toFloat()
            }
            private fun pull(delta: Float) {
                val scale = scale()
                if (!pulled && scale == 1f) { pulled = true; pivot() }
                val change = (if (edge() != direction) -(scale - 1f) else 1.05f - scale) / .05f * delta * .15f
                if (change != 0f) {
                    val value = (scale + change).coerceIn(.5f, 1.8f)
                    if (horizontal) view.scaleX = value else view.scaleY = value
                }
                animation?.cancel()
            }
            override fun draw(canvas: Canvas) = false
            override fun isFinished() = animation?.isRunning != true
            // The original RecyclerView 1.2.1 used onPull only. Android 12's
            // platform distance never advances with draw=false; exposing it to
            // newer RecyclerView would consume every DOWN after an overscroll.
            override fun getDistance() = 0f
            override fun onPullDistance(deltaDistance: Float, displacement: Float): Float {
                onPull(deltaDistance, displacement)
                return deltaDistance
            }
            override fun onAbsorb(velocity: Int) {
                super.onAbsorb(velocity); pivot()
                val change = min(.05f, velocity / 20000f * .25f)
                animation?.cancel()
                animation = spring().also { it.setStartValue(change + 1f); it.start() }
            }
            override fun onPull(deltaDistance: Float) { super.onPull(deltaDistance); pull(deltaDistance) }
            override fun onPull(deltaDistance: Float, displacement: Float) { super.onPull(deltaDistance, displacement); pull(deltaDistance) }
            override fun onRelease() {
                super.onRelease(); pulled = false
                if (scale() != 1f) animation = spring().also { it.start() }
            }
        }
    }
}
