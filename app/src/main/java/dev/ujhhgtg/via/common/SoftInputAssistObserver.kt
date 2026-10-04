package dev.ujhhgtg.via.common

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/** Original mark.via.common.SoftInputAssistObserver and its k8.r implementation. */
class SoftInputAssistObserver(activity: Activity) : LifecycleEventObserver {
    private var content: ViewGroup? = activity.findViewById(android.R.id.content)
    private var view: View? = content!!.getChildAt(0)
    private var observer: ViewTreeObserver? = null
    private val listener = ViewTreeObserver.OnGlobalLayoutListener { onLayout() }
    private val visibleFrame = Rect()
    private val params = view!!.layoutParams as FrameLayout.LayoutParams
    private val originalHeight = params.height
    private val threshold = (activity.resources.displayMetrics.density * 120f + .5f).toInt()
    private var lastHeight = 0
    private var lastWidth = 0
    private var keyboardVisible = false
    var resizeEnabled = true
    var onKeyboardHiddenChanged: ((Boolean) -> Unit)? = null

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_PAUSE -> pause()
            Lifecycle.Event.ON_RESUME -> resume()
            Lifecycle.Event.ON_DESTROY -> { view = null; content = null; observer = null }
            else -> Unit
        }
    }

    private fun pause() {
        observer?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(listener)
    }

    private fun resume() {
        if (observer?.isAlive != true) observer = view?.viewTreeObserver
        observer?.removeOnGlobalLayoutListener(listener)
        observer?.addOnGlobalLayoutListener(listener)
    }

    private fun onLayout() {
        val root = content
        val child = view
        if (root == null) {
            if (child != null && params.height != originalHeight) {
                params.height = originalHeight
                child.layoutParams = params
            }
            return
        }
        root.getWindowVisibleDisplayFrame(visibleFrame)
        val width = visibleFrame.width()
        val insets = child?.let(ViewCompat::getRootWindowInsets)
        // k8.r.c: on a tappable (3-button) nav bar the content height reaches
        // past the visible frame by the nav-bar inset; gesture nav adds none.
        val navBars = insets?.getInsets(WindowInsetsCompat.Type.navigationBars()) ?: Insets.NONE
        val tappable = insets?.isVisible(WindowInsetsCompat.Type.tappableElement()) == true
        val height = visibleFrame.bottom + if (tappable) navBars.bottom else 0
        if (height == lastHeight) return
        val nextHeight: Int
        if (kotlin.math.abs(lastHeight - height) <= threshold || kotlin.math.abs(lastWidth - width) >= threshold / 2) {
            nextHeight = if (params.height == originalHeight) originalHeight else height
        } else {
            val showing = lastHeight > height && (insets == null || insets.isVisible(WindowInsetsCompat.Type.ime()))
            if (showing != keyboardVisible && onKeyboardHiddenChanged != null) {
                onKeyboardHiddenChanged?.invoke(!showing)
                keyboardVisible = showing
            }
            nextHeight = if (resizeEnabled && showing) height else originalHeight
        }
        if (nextHeight != params.height) {
            params.height = nextHeight
            child?.requestLayout()
        }
        lastHeight = height
        lastWidth = width
    }
}
