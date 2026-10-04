package dev.ujhhgtg.via.ui.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView

/** q4.b with the z8.r3.h thumb configuration used by the text editor. */
class FastScrollView(context: Context) : ScrollView(context), ViaFastScroller.Target {
    private var fastScroll: ViaFastScroller? = null
    init { isVerticalScrollBarEnabled = false; fastScroll = ViaFastScroller(this) }
    override val scrollingView: View get() = this
    override fun scrollRange() = computeVerticalScrollRange()
    override fun scrollExtent() = computeVerticalScrollExtent()
    override fun scrollOffset() = computeVerticalScrollOffset()
    override fun forwardTouch(event: MotionEvent) { super.onTouchEvent(event) }
    override fun awakenScrollBars(): Boolean = fastScroll?.awaken() ?: false
    override fun dispatchDraw(canvas: Canvas) { super.dispatchDraw(canvas); fastScroll?.draw(canvas) }
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
        (event.actionMasked == MotionEvent.ACTION_DOWN && fastScroll?.touch(event) == true) || super.onInterceptTouchEvent(event)
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = fastScroll?.touch(event) == true || super.onTouchEvent(event)
}
