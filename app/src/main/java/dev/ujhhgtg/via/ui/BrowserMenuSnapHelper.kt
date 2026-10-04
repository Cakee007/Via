package dev.ujhhgtg.via.ui

import android.util.DisplayMetrics
import android.view.View
import android.view.animation.Interpolator
import android.widget.Scroller
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import kotlin.math.abs
import kotlin.math.max

/** Kotlin translation of i8.a, the original page snap implementation. */
internal class BrowserMenuSnapHelper(private val maximumPages: Int) : SnapHelper() {
    interface Listener { fun onAligned(position: Int); fun onTarget(position: Int) }
    var listener: Listener? = null
    private lateinit var recycler: RecyclerView
    private lateinit var orientation: OrientationHelper
    private lateinit var fling: Scroller
    private var reverse = false
    private var itemsPerPage = 0
    private var maximumItems = 0
    private var itemSize = 0
    private var previousFirst = -1
    private val interpolation = Interpolator { value -> val offset = value - 1f; offset * offset * offset + 1f }

    override fun attachToRecyclerView(recyclerView: RecyclerView?) {
        if (recyclerView != null) {
            recycler = recyclerView
            val manager = recycler.layoutManager as? LinearLayoutManager ?: error("RecyclerView must have a layout manager")
            if (manager.canScrollHorizontally()) {
                orientation = OrientationHelper.createHorizontalHelper(manager)
                reverse = recycler.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
            } else {
                check(manager.canScrollVertically()) { "RecyclerView must be scrollable" }
                orientation = OrientationHelper.createVerticalHelper(manager)
                reverse = false
            }
            fling = Scroller(recycler.context, interpolation)
            measurePage(manager)
        }
        super.attachToRecyclerView(recyclerView)
    }

    override fun calculateDistanceToFinalSnap(manager: RecyclerView.LayoutManager, targetView: View): IntArray {
        val distance = IntArray(2)
        val offset = if (reverse) orientation.getDecoratedEnd(targetView) - recycler.width else orientation.getDecoratedStart(targetView)
        if (manager.canScrollHorizontally()) distance[0] = offset
        if (manager.canScrollVertically()) distance[1] = offset
        if (distance[0] == 0 && distance[1] == 0) listener?.onAligned(manager.getPosition(targetView))
        else listener?.onTarget(manager.getPosition(targetView))
        return distance
    }

    override fun findSnapView(manager: RecyclerView.LayoutManager): View? {
        val linear = manager as LinearLayoutManager
        val first = linear.findFirstVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION) return null
        measurePage(manager)
        val target = if (first >= previousFirst) {
            val completelyVisible = linear.findFirstCompletelyVisibleItemPosition()
            if (completelyVisible == RecyclerView.NO_POSITION || completelyVisible % itemsPerPage != 0)
                pageStart(itemsPerPage + first) else completelyVisible
        } else {
            val start = pageStart(first)
            if (linear.findViewByPosition(start) == null) {
                val distance = distanceToPosition(linear, start)
                recycler.smoothScrollBy(distance[0], distance[1], interpolation)
            }
            start
        }
        previousFirst = first
        return linear.findViewByPosition(target)
    }

    override fun findTargetSnapPosition(manager: RecyclerView.LayoutManager, velocityX: Int, velocityY: Int): Int {
        val linear = manager as LinearLayoutManager
        measurePage(manager)
        fling.fling(0, 0, velocityX, velocityY, Int.MIN_VALUE, Int.MAX_VALUE, Int.MIN_VALUE, Int.MAX_VALUE)
        return when {
            velocityX != 0 -> flingTarget(linear, fling.finalX, itemSize)
            velocityY != 0 -> flingTarget(linear, fling.finalY, itemSize)
            else -> RecyclerView.NO_POSITION
        }
    }

    override fun createScroller(layoutManager: RecyclerView.LayoutManager): RecyclerView.SmoothScroller? {
        if (layoutManager !is RecyclerView.SmoothScroller.ScrollVectorProvider) return null
        return object : LinearSmoothScroller(recycler.context) {
            override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float = 50f / displayMetrics.densityDpi
            override fun onTargetFound(targetView: View, state: RecyclerView.State, action: Action) {
                val distance = calculateDistanceToFinalSnap(recycler.layoutManager!!, targetView)
                val duration = calculateTimeForDeceleration(max(abs(distance[0]), abs(distance[1])))
                if (duration > 0) action.update(distance[0], distance[1], duration, interpolation)
            }
        }
    }

    private fun distanceToPosition(manager: LinearLayoutManager, position: Int): IntArray {
        val distance = IntArray(2)
        val first = manager.findFirstVisibleItemPosition()
        if (manager.canScrollHorizontally() && position <= first) {
            distance[0] = if (reverse) orientation.getDecoratedEnd(manager.findViewByPosition(manager.findLastVisibleItemPosition())!!) + (first - position) * itemSize
                else orientation.getDecoratedStart(manager.findViewByPosition(first)!!) - (first - position) * itemSize
        }
        if (manager.canScrollVertically() && position <= first)
            distance[1] = (manager.findViewByPosition(first)?.top ?: 0) - (first - position) * itemSize
        return distance
    }

    private fun flingTarget(manager: LinearLayoutManager, distance: Int, size: Int): Int {
        var items = pageStart(abs(distance) / size + itemsPerPage - 1)
        if (items < itemsPerPage) items = itemsPerPage else if (items > maximumItems) items = maximumItems
        if (distance < 0) items *= -1
        if (reverse) items *= -1
        val useFirst = if (reverse) distance < 0 else distance >= 0
        return pageStart(if (useFirst) manager.findFirstVisibleItemPosition() else manager.findLastVisibleItemPosition()) + items
    }

    private fun measurePage(manager: RecyclerView.LayoutManager) {
        val child = manager.getChildAt(0)
        if (itemSize == 0 && child != null) {
            val span = if (manager is GridLayoutManager) manager.spanCount else 1
            if (manager.canScrollHorizontally()) { itemSize = child.width; itemsPerPage = span * (recycler.width / itemSize) }
            else if (manager.canScrollVertically()) { itemSize = child.height; itemsPerPage = span * (recycler.height / itemSize) }
            maximumItems = itemsPerPage * maximumPages
        }
    }
    private fun pageStart(position: Int) = position - position % itemsPerPage
}
