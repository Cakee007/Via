package dev.ujhhgtg.via.engine.webview

import android.content.Context
import android.util.AttributeSet
import android.view.ActionMode
import android.view.MotionEvent
import android.webkit.WebView
import java.lang.ref.WeakReference

/** t4.b.l/onTouchEvent/onOverScrolled: page gestures start only after an X edge clamp. */
class GestureWebView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : WebView(context, attrs) {
    /** n4.a: [type] 0 adds a menu entry, 1 hides a platform entry with the same title. */
    data class ActionItem(val id: Int, val title: String, val type: Int = 0)

    var edgeGestureReady: Boolean = false
        private set
    /** t4.b.u / setActionItems */
    var actionItems: List<ActionItem> = emptyList()
    /** t4.b.v / setOnActionItemClickListener: receives the selected text. */
    var onActionItemClick: ((ActionItem, String) -> Unit)? = null
    private var actionMode: ActionMode? = null
    /** Sees every touch event before the view's OnTouchListener, which may consume it. */
    var touchObserver: ((MotionEvent) -> Unit)? = null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        touchObserver?.invoke(event)
        return super.dispatchTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (parent != null && event.action == MotionEvent.ACTION_DOWN) edgeGestureReady = url == null
        return super.onTouchEvent(event)
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        if (clampedX && parent != null) edgeGestureReady = true
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
    }

    override fun startActionMode(callback: ActionMode.Callback?): ActionMode? = decorate(super.startActionMode(callback))
    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? = decorate(super.startActionMode(callback, type))

    /** t4.b.p: hide matching platform items, then add ours at "Select all"'s position when "Copy" is offered. */
    private fun decorate(mode: ActionMode?): ActionMode? {
        actionMode = mode
        val menu = mode?.menu ?: return mode
        if (actionItems.isEmpty() || !settings.javaScriptEnabled) return mode
        var copyable = false
        var order = 0
        for (index in menu.size() - 1 downTo 0) {
            val item = menu.getItem(index)
            val title = item.title?.toString() ?: continue
            if (title.equals(context.getString(android.R.string.selectAll), true)) order = item.order
            else if (title.equals(context.getString(android.R.string.copy), true)) copyable = true
            actionItems.forEach { action ->
                if (action.type == 0 || !title.equals(action.title, true)) return@forEach
                if (action.type == 1) item.isVisible = false
                else if (action.type == 2) item.setOnMenuItemClickListener { deliver(action); true }
            }
        }
        if (copyable) actionItems.filter { it.type == 0 }.forEach { action ->
            menu.add(0, action.id, order, action.title).setOnMenuItemClickListener { deliver(action); true }
        }
        mode.invalidateContentRect()
        return mode
    }

    /** t4.b.i: read the selection, deliver it, and close the selection toolbar. */
    private fun deliver(action: ActionItem) {
        val listener = onActionItemClick ?: return
        evaluateJavascript(SELECTION_SCRIPT) { raw ->
            val text = if (raw != null && raw.length > 2 && raw[0] == '"')
                raw.substring(1, raw.length - 1).replace("\\\"", "\"").replace("\\\\", "\\") else ""
            listener(action, text)
            finishActionMode()
        }
        val pending = WeakReference(actionMode)
        postDelayed({ if (pending.get() != null) finishActionMode() }, 100L)
    }

    /** t4.b.n */
    fun finishActionMode() {
        actionMode?.finish()
        actionMode = null
    }

    private companion object {
        const val SELECTION_SCRIPT = "javascript:(function(){return window.getSelection?window.getSelection().toString():window.document.getSelection?window.document.getSelection().toString():window.document.selection?window.document.selection.createRange().text:\"\"})();"
    }
}
