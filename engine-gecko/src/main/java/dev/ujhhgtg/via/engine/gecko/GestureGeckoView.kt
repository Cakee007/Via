package dev.ujhhgtg.via.engine.gecko

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.PanZoomController

/**
 * Reports, per touch sequence, whether the page can still scroll horizontally, so the host's page swipe
 * starts only at a horizontal edge (WebView derives the same from onOverScrolled).
 */
internal class GestureGeckoView(context: Context) : GeckoView(context) {
    private var scrollable = PanZoomController.SCROLLABLE_FLAG_NONE
    private var handledByContent = false

    var lastTouchRawX = 0; private set
    var lastTouchRawY = 0; private set

    /** True when this touch sequence can't scroll the page horizontally in at least one direction. */
    val edgeGestureReady: Boolean
        get() = !handledByContent && (scrollable and HORIZONTAL) != HORIZONTAL

    fun canScrollPageHorizontally(direction: Int): Boolean = when {
        direction < 0 -> scrollable and PanZoomController.SCROLLABLE_FLAG_LEFT != 0
        direction > 0 -> scrollable and PanZoomController.SCROLLABLE_FLAG_RIGHT != 0
        else -> false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        lastTouchRawX = event.rawX.toInt()
        lastTouchRawY = event.rawY.toInt()
        if (event.actionMasked != MotionEvent.ACTION_DOWN || session == null) return super.onTouchEvent(event)
        requestFocus()
        // Until Gecko answers, assume the page scrolls, so no swipe starts on stale state.
        scrollable = HORIZONTAL
        handledByContent = false
        onTouchEventForDetailResult(event).accept { detail ->
            if (detail == null) return@accept
            scrollable = detail.scrollableDirections()
            handledByContent = detail.handledResult() == PanZoomController.INPUT_RESULT_HANDLED_CONTENT
        }
        return true
    }

    private companion object {
        const val HORIZONTAL = PanZoomController.SCROLLABLE_FLAG_LEFT or PanZoomController.SCROLLABLE_FLAG_RIGHT
    }
}
