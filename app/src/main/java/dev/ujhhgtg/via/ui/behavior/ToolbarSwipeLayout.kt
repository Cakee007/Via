package dev.ujhhgtg.via.ui.behavior

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper

/** com.tuyafeng.support.widget.a, using the original AndroidX ViewDragHelper. */
@SuppressLint("ViewConstructor")
class ToolbarSwipeLayout(context: Context, private val onSwipe: (Float, Boolean) -> Unit) : FrameLayout(context) {
    var dragDistance = (84f * resources.displayMetrics.density + .5f).toInt()
    var draggable = true
    var interceptParentTouch = false
    private var fraction = 0f
    private val rtl = resources.configuration.layoutDirection == LAYOUT_DIRECTION_RTL
    private val drag: ViewDragHelper = ViewDragHelper.create(this, object : ViewDragHelper.Callback() {
        override fun tryCaptureView(child: View, pointerId: Int) = true
        override fun getViewHorizontalDragRange(child: View) = 1
        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int =
            if (left - paddingLeft > dragDistance) dragDistance + paddingLeft else maxOf(left, paddingLeft - dragDistance)
        override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) {
            super.onViewPositionChanged(changedView, left, top, dx, dy)
            fraction = (left - paddingLeft).toFloat() / dragDistance
            onSwipe(if (rtl) -fraction else fraction, false)
            invalidate()
        }
        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            onSwipe(if (rtl) -fraction else fraction, true)
            drag.settleCapturedViewAt(paddingLeft, paddingTop)
            invalidate()
        }
    })

    override fun computeScroll() {
        super.computeScroll()
        if (drag.continueSettling(true)) ViewCompat.postInvalidateOnAnimation(this)
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (interceptParentTouch) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) parent.requestDisallowInterceptTouchEvent(true)
            else if (event.actionMasked == MotionEvent.ACTION_UP) parent.requestDisallowInterceptTouchEvent(false)
        }
        return super.dispatchTouchEvent(event)
    }
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
        if (!draggable) super.onInterceptTouchEvent(event) else drag.shouldInterceptTouchEvent(event)
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!draggable) return super.onTouchEvent(event)
        drag.processTouchEvent(event)
        return true
    }
}
