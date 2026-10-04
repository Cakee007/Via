package dev.ujhhgtg.via.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.graphics.withSave
import androidx.customview.widget.ViewDragHelper
import androidx.fragment.app.Fragment
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.withSign

/** Kotlin port of com.tuyafeng.support.widget.y, including its predictive-back path. */
class SwipeBackLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : FrameLayout(context, attrs, defStyle) {
    interface Listener {
        fun onProgress(state: Int, progress: Float)
        fun canStart(edge: Int): Boolean
        fun onStateChanged(state: Int)
    }

    private var threshold = .46f
    private val dragHelper = ViewDragHelper.create(this, .18f, DragCallback())
    private var progress = 0f
    private var dimFraction = 0f
    private var content: View? = null
    private var trackedEdges = 0
    private var gesturesEnabled = true
    private var activePointer = 0
    private var activeEdge = 0
    private var dimAlpha = 64
    private var predictive = false
    private val listeners = mutableListOf<Listener>()
    private var fragmentListener: FragmentListener? = null

    init { setEdgeOrientation(if (resources.configuration.layoutDirection == LAYOUT_DIRECTION_RTL) ViewDragHelper.EDGE_RIGHT else ViewDragHelper.EDGE_LEFT) }

    fun addListener(listener: Listener) { listeners += listener }
    fun attach(fragment: Fragment, view: View) {
        addView(view)
        content = view
        fragmentListener = FragmentListener(fragment).also(::addListener)
    }

    private inner class DragCallback : ViewDragHelper.Callback() {
        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int) = when {
            activeEdge and ViewDragHelper.EDGE_LEFT != 0 -> min(child.width, max(left, 0))
            activeEdge and ViewDragHelper.EDGE_RIGHT != 0 -> min(0, max(left, -child.width))
            else -> 0
        }
        override fun getViewHorizontalDragRange(child: View) = 1
        override fun onEdgeTouched(edgeFlags: Int, pointerId: Int) {
            super.onEdgeTouched(edgeFlags, pointerId)
            if (trackedEdges and edgeFlags != 0) { activeEdge = edgeFlags; activePointer = pointerId }
        }
        override fun onViewDragStateChanged(state: Int) {
            super.onViewDragStateChanged(state)
            if (state == ViewDragHelper.STATE_IDLE) activePointer = -1
            listeners.forEach { it.onStateChanged(state) }
        }
        override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) {
            super.onViewPositionChanged(changedView, left, top, dx, dy)
            if (activeEdge and ViewDragHelper.EDGE_LEFT != 0) progress = abs(left.toFloat() / width)
            else if (activeEdge and ViewDragHelper.EDGE_RIGHT != 0) progress = abs(left.toFloat() / requireNotNull(content).width)
            invalidate()
            listeners.forEach { it.onProgress(dragHelper.viewDragState, progress) }
        }
        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            var finish = progress > threshold
            val direction = when {
                activeEdge and ViewDragHelper.EDGE_LEFT != 0 -> {
                    finish = finish || (xvel > 300f && xvel > abs(yvel) && progress > threshold * 2f / 5f)
                    1
                }
                activeEdge and ViewDragHelper.EDGE_RIGHT != 0 -> {
                    finish = finish || (xvel < -300f && xvel < -abs(yvel) && progress > threshold * 2f / 5f)
                    -1
                }
                else -> 0
            }
            if (dragHelper.settleCapturedViewAt(if (finish) direction * (releasedChild.width + 10) else 0, releasedChild.top)) postInvalidateOnAnimation()
        }
        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            if (child.translationX != 0f || pointerId != activePointer || !dragHelper.isEdgeTouched(trackedEdges, pointerId)) return false
            if (trackedEdges and ViewDragHelper.EDGE_LEFT == ViewDragHelper.EDGE_LEFT && dragHelper.isEdgeTouched(ViewDragHelper.EDGE_LEFT, pointerId)) activeEdge = ViewDragHelper.EDGE_LEFT
            else if (trackedEdges and ViewDragHelper.EDGE_RIGHT == ViewDragHelper.EDGE_RIGHT && dragHelper.isEdgeTouched(ViewDragHelper.EDGE_RIGHT, pointerId)) activeEdge = ViewDragHelper.EDGE_RIGHT
            return listeners.all { it.canStart(activeEdge) }
        }
    }

    private class FragmentListener(private val fragment: Fragment) : Listener {
        private var previous: Fragment? = null
        private var previousOffset = 0f
        private var completed = false

        override fun onProgress(state: Int, progress: Float) {
            if (completed) return
            if (progress >= 1f) { finish(); return }
            val previous = previous ?: return
            val view = previous.view ?: return
            if (previous.isVisible) { completed = true; resetTranslation(view) }
            else {
                if (view.visibility != VISIBLE) view.visibility = VISIBLE
                view.translationX = previousOffset * (1f - progress)
            }
        }

        override fun canStart(edge: Int): Boolean {
            if (!fragment.isVisible || fragment.parentFragmentManager.isStateSaved) return false
            previous?.let { previous ->
                previous.view?.let { view ->
                    if (view.visibility != VISIBLE) { view.translationX = previousOffset; view.visibility =
                        VISIBLE
                    }
                }
                return true
            }
            val fragments = fragment.parentFragmentManager.fragments
            if (fragments.size <= 1) return false
            for (index in fragments.indexOf(fragment) - 1 downTo 0) {
                val candidate = fragments[index]
                val view = candidate.view ?: continue
                view.visibility = VISIBLE
                val distance = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 66f, view.resources.displayMetrics) * if (view.resources.configuration.layoutDirection == LAYOUT_DIRECTION_RTL) 1 else -1
                previousOffset = max(view.width * .3f, abs(distance)).withSign(distance)
                previous = candidate
                break
            }
            return previous != null
        }

        override fun onStateChanged(state: Int) {
            val previous = previous ?: return
            val view = previous.view ?: return
            if (completed || state != ViewDragHelper.STATE_IDLE) return
            if (previous.isVisible) resetTranslation(view)
            else { view.visibility = GONE; view.translationX = 0f }
        }

        fun finish() {
            if (completed) return
            completed = true
            previous?.view?.let { it.animate().cancel(); it.translationX = 0f }
            if (fragment.isDetached || fragment.parentFragmentManager.isStateSaved) return
            fragment.parentFragmentManager.popBackStack()
        }

        private fun resetTranslation(view: View) {
            if (view.translationX == 0f) return
            view.animate().translationX(0f).setDuration(120L).setInterpolator(DecelerateInterpolator()).start()
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
        if (predictive || (!gesturesEnabled && dragHelper.viewDragState == ViewDragHelper.STATE_IDLE)) super.onInterceptTouchEvent(event)
        else dragHelper.shouldInterceptTouchEvent(event)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (predictive || (!gesturesEnabled && dragHelper.viewDragState == ViewDragHelper.STATE_IDLE)) return super.onTouchEvent(event)
        dragHelper.processTouchEvent(event)
        return true
    }

    override fun computeScroll() {
        dimFraction = (1f - progress).coerceIn(0f, 1f)
        if (dragHelper.continueSettling(true)) postInvalidateOnAnimation()
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (dimAlpha == 0) return super.drawChild(canvas, child, drawingTime)
        val result = super.drawChild(canvas, child, drawingTime)
        if (child === content && dimFraction > 0f && (predictive || dragHelper.viewDragState != ViewDragHelper.STATE_IDLE)) {
            val color = (dimAlpha * dimFraction).toInt() shl 24
            canvas.withSave {
                val left = child.left + child.translationX
                val right = child.right + child.translationX
                if (activeEdge and ViewDragHelper.EDGE_LEFT != 0) canvas.clipRect(
                    0f,
                    0f,
                    left,
                    height.toFloat()
                )
                else if (activeEdge and ViewDragHelper.EDGE_RIGHT != 0) canvas.clipRect(
                    right,
                    0f,
                    this@SwipeBackLayout.right.toFloat(),
                    height.toFloat()
                )
                canvas.drawColor(color)
            }
        }
        return result
    }

    fun setEdgeOrientation(edges: Int) { trackedEdges = edges; dragHelper.setEdgeTrackingEnabled(edges) }
    fun setEdgeSize(size: Int) {
        dragHelper.edgeSize = when {
            size > 0 -> size
            size == -2 -> resources.displayMetrics.widthPixels
            size == -1 -> resources.displayMetrics.widthPixels / 2
            else -> context.dp(20f)
        }
    }
    fun setGestureEnabled(enabled: Boolean) { gesturesEnabled = enabled }
    fun isGestureEnabled() = gesturesEnabled
    fun setScrollThresholdSize(size: Float) {
        require(!(size < 0f)) { "Threshold size should be greater than 0" }
        threshold = min(size / resources.displayMetrics.widthPixels, .46f)
    }

    fun startPredictiveBack(): Boolean {
        val listener = fragmentListener ?: return false
        val view = content ?: return false
        if (predictive || view.translationX != 0f || dragHelper.viewDragState != ViewDragHelper.STATE_IDLE) return false
        activeEdge = ViewDragHelper.EDGE_LEFT
        if (!listener.canStart(ViewDragHelper.EDGE_LEFT)) return false
        predictive = true
        updatePredictiveBack(0f)
        return true
    }
    fun updatePredictiveBack(value: Float) {
        val view = content ?: return
        if (!predictive) return
        progress = value.coerceIn(0f, .999f)
        view.translationX = view.width * progress
        dimFraction = 1f - progress
        fragmentListener?.onProgress(ViewDragHelper.STATE_DRAGGING, progress)
        invalidate()
    }
    fun cancelPredictiveBack() { if (predictive && content != null) settlePredictiveBack(false) }
    fun finishPredictiveBack() { if (predictive) settlePredictiveBack(true) }
    private fun settlePredictiveBack(finish: Boolean) {
        val view = requireNotNull(content)
        val left = view.left + view.translationX.roundToInt()
        view.translationX = 0f
        view.offsetLeftAndRight(left - view.left)
        predictive = false
        if (dragHelper.smoothSlideViewTo(view, if (finish) view.width + 10 else 0, view.top)) postInvalidateOnAnimation()
        else if (finish) fragmentListener?.finish() else fragmentListener?.onStateChanged(ViewDragHelper.STATE_IDLE)
    }
}
