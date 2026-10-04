package dev.ujhhgtg.via.settings

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.ui.dp

/** n8.b, default range mode: long-press/checkbox-edge selection with edge auto-scroll. */
internal class SettingsRangeSelection(
    context: Context,
    private val selectable: (Int) -> Boolean,
    private val checked: (Int) -> Boolean,
    private val update: (Int, Boolean) -> Unit,
    private val changed: (Boolean) -> Unit,
) : RecyclerView.SimpleOnItemTouchListener() {
    private val edge = context.dp(56f)
    private val side = context.dp(48f) * if (context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) 1 else -1
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())
    private var recycler: RecyclerView? = null
    private var active = false
    private var start = -1
    private var last = -1
    private var minimum = -1
    private var maximum = -1
    private var initialY = 0f
    private var scroll = 0
    private val previous = mutableMapOf<Int, Boolean>()
    private val scrollTask = object : Runnable {
        override fun run() { if (scroll != 0) { recycler?.scrollBy(0, scroll); handler.postDelayed(this, 25L) } }
    }
    private fun sideContains(view: View, x: Float) = if (side > 0) x < side else x > view.measuredWidth + side
    private fun position(view: RecyclerView, event: MotionEvent): Int = view.findChildViewUnder(event.x, event.y)?.let(view::getChildAdapterPosition) ?: -1
    fun start(position: Int): Boolean {
        if (active) return false
        last = -1; minimum = -1; maximum = -1; scroll = 0; handler.removeCallbacks(scrollTask)
        if (!selectable(position)) { finish(); start = -1; return false }
        update(position, !checked(position))
        previous.clear(); active = true; changed(true)
        start = position; last = position
        return true
    }
    fun finish() {
        if (active) { previous.clear(); active = false; changed(false) }
        scroll = 0; handler.removeCallbacks(scrollTask)
    }
    override fun onInterceptTouchEvent(rv: RecyclerView, event: MotionEvent): Boolean {
        if (rv.adapter?.itemCount == 0) return false
        if (!active) {
            if (event.action == MotionEvent.ACTION_DOWN) initialY = if (sideContains(rv, event.x)) event.y else -1f
            else if (initialY >= 0 && event.action == MotionEvent.ACTION_MOVE && kotlin.math.abs(event.y - initialY) > slop) {
                val position = position(rv, event)
                if (position == -1 || !start(position)) initialY = -1f
            }
        }
        if (!active) return false
        recycler = rv
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) finish()
        return true
    }
    override fun onTouchEvent(rv: RecyclerView, event: MotionEvent) {
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) { finish(); return }
        if (event.action != MotionEvent.ACTION_MOVE) return
        val y = event.y
        val speed = when {
            y <= edge -> -minOf(edge.toFloat(), edge - y).toInt() / 2
            y >= rv.measuredHeight - edge -> minOf(edge.toFloat(), y - (rv.measuredHeight - edge)).toInt() / 2
            else -> 0
        }
        if (speed != 0 && scroll == 0) handler.postDelayed(scrollTask, 25L)
        if (speed == 0) handler.removeCallbacks(scrollTask)
        scroll = speed
        val position = position(rv, event)
        if (position == -1 || position == last || initialY > 0f && !sideContains(rv, event.x)) return
        last = position
        minimum = if (minimum == -1) position else minOf(minimum, position)
        maximum = if (maximum == -1) position else maxOf(maximum, position)
        if (start == last) {
            for (index in minimum..maximum) if (index != start) update(index, previous[index] == true && selectable(index))
            minimum = last; maximum = last
            return
        }
        val value = checked(start)
        val low = minOf(start, last); val high = maxOf(start, last)
        for (index in low..high) {
            previous.putIfAbsent(index, checked(index)); update(index, value && selectable(index))
        }
        if (minimum >= 0) for (index in minimum until low) update(index, previous[index] == true && selectable(index))
        if (maximum >= 0) for (index in high + 1..maximum) update(index, previous[index] == true && selectable(index))
    }
}
