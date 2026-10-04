package dev.ujhhgtg.via.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible

/** c8.f8 canvas plus c8.s6.Ka/ub/fb/U8 chrome placement and viewport policy. */
class BrowserLayout(context: Context) : FrameLayout(context) {
    private val canvas = RelativeLayout(context)
    val content = FrameLayout(context).apply { id = generateViewId() }
    val top = LinearLayout(context).apply { id = generateViewId(); orientation = LinearLayout.VERTICAL }
    val bottom = LinearLayout(context).apply { id = generateViewId(); orientation = LinearLayout.VERTICAL }
    val chromeOverlay = FrameLayout(context).apply { id = dev.ujhhgtg.via.R.id.browser_overlay_container }
    internal val progress = BrowserProgressBar(context)

    private var chromeConfigured = false
    private var addressBar: View? = null
    private var navigation: View? = null
    private var tabs: View? = null
    private var customBar: View? = null
    private var embedAddress: (View?) -> Unit = {}
    private var geometry = BrowserLayoutPolicy.Geometry(0, false, dp(48f), dp(48f))
    private val easing = PathInterpolator(.2f, .2f, .8f, .8f)
    private var keyboardVisible = false
    private var bottomBeforeKeyboard = false
    private var onVisibilityChanged: (Boolean) -> Unit = {}
    private val bottomCompanions = mutableListOf<View>()

    var toolbarsShown: Boolean = true
        private set
    var usesOverlayViewport: Boolean = false
        private set
    val mode: Int get() = geometry.mode
    val topHeight: Int get() = geometry.topHeight
    val bottomHeight: Int get() = geometry.bottomHeight
    val tabBarEnabled: Boolean get() = geometry.tabBar

    init {
        canvas.addView(content, bounded(-1))
        canvas.addView(progress, RelativeLayout.LayoutParams(-1, progress.stripHeight).apply { addRule(RelativeLayout.BELOW, top.id); alignWithParent = true })
        canvas.addView(chromeOverlay, bounded(-1))
        canvas.addView(top, RelativeLayout.LayoutParams(-1, topHeight).apply { addRule(RelativeLayout.ALIGN_PARENT_TOP) })
        canvas.addView(bottom, RelativeLayout.LayoutParams(-1, bottomHeight).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM) })
        addView(canvas, LayoutParams(-1, -1))
        val colors = context.obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground))
        val background = try { colors.getColor(0, Color.WHITE) } finally { colors.recycle() }
        setChromeColor(background)
    }

    /** embedAddress mirrors g0.f/k: combined modes place address inside the navigation row. */
    fun setChrome(addressBar: View, navigation: View, tabBar: View? = null, customToolbar: View? = null, embedAddress: (View?) -> Unit = {}) {
        this.addressBar = addressBar; this.navigation = navigation; this.tabs = tabBar; this.customBar = customToolbar
        this.embedAddress = embedAddress
        chromeConfigured = false
    }

    fun configure(
        storedMode: Int,
        tabBarEnabled: Boolean,
        landscape: Boolean,
        preferTop: Boolean = false,
        customTab: Boolean = false,
        appFullscreen: Boolean = false,
        fullscreenPreference: Boolean = false,
        hideMode: Int = 0,
    ): BrowserLayoutPolicy.Geometry {
        val desired = BrowserLayoutPolicy.resolve(storedMode, tabBarEnabled, landscape, preferTop, customTab, dp(48f), dp(48f), dp(36f))
        val overlay = BrowserLayoutPolicy.overlayViewport(customTab, appFullscreen, fullscreenPreference, hideMode)
        val changed = desired != geometry || !chromeConfigured
        geometry = desired
        if (changed) {
            top.animate().cancel(); bottom.animate().cancel(); content.animate().cancel(); progress.animate().cancel()
            embedAddress(null)
            listOfNotNull(addressBar, navigation, tabs, customBar).forEach(::detach)
            top.removeAllViews(); bottom.removeAllViews()
            if (desired.mode == 1 || desired.mode == 2) addressBar?.let { embedAddress(it) }
            fun place(container: LinearLayout, view: View?, height: Int) {
                view?.let { detach(it); container.addView(it, LinearLayout.LayoutParams(-1, height)) }
            }
            when (desired.mode) {
                0 -> { place(top, addressBar, dp(48f)); place(bottom, navigation, dp(48f)) }
                1 -> { place(top, navigation, dp(48f)); if (desired.tabBar) place(top, tabs, dp(36f)) }
                2 -> { if (desired.tabBar) place(bottom, tabs, dp(36f)); place(bottom, navigation, dp(48f)) }
                3 -> { place(bottom, addressBar, dp(48f)); place(bottom, navigation, dp(48f)) }
                4 -> place(top, customBar, dp(48f))
            }
            top.layoutParams = top.layoutParams.apply { height = desired.topHeight }
            bottom.layoutParams = bottom.layoutParams.apply { height = desired.bottomHeight }
            chromeConfigured = true
        }
        usesOverlayViewport = overlay
        if (!overlay) toolbarsShown = true
        content.layoutParams = if (overlay) RelativeLayout.LayoutParams(-1, -1) else bounded(-1)
        chromeOverlay.layoutParams = bounded(-1)
        content.translationY = if (overlay && toolbarsShown) topHeight.toFloat() else 0f
        top.translationY = if (toolbarsShown) 0f else -topHeight.toFloat()
        bottom.translationY = if (toolbarsShown) 0f else bottomHeight.toFloat()
        top.visibility = if (toolbarsShown) VISIBLE else GONE
        bottom.visibility = if (toolbarsShown && !keyboardVisible) VISIBLE else GONE
        progress.translationY = 0f
        progress.layoutParams = RelativeLayout.LayoutParams(-1, progress.stripHeight).apply {
            alignWithParent = true
            if (top.isNotEmpty()) addRule(RelativeLayout.BELOW, top.id) else addRule(RelativeLayout.ABOVE, bottom.id)
        }
        requestApplyInsets()
        return desired
    }

    fun setChromeColor(color: Int) { setChromeColors(color, color) }
    fun setChromeColors(topColor: Int, bottomColor: Int) {
        top.setBackgroundColor(topColor)
        bottom.setBackgroundColor(bottomColor)
    }

    /**
     * c8.s6.b9/Cb: the engine quick-switch strip rides directly above the
     * content in the canvas (inserted at content+1, above the bottom toolbar),
     * and the content gains a matching bottom inset so the page is not
     * overlaid. It slides with the toolbar as a bottom companion.
     */
    fun attachSearchStrip(strip: View): Boolean {
        if (strip.parent === canvas) return false
        detachSearchStrip()
        (strip.parent as? ViewGroup)?.removeView(strip)
        canvas.addView(strip, canvas.indexOfChild(content) + 1, RelativeLayout.LayoutParams(-1, dp(40f)).apply {
            addRule(RelativeLayout.ABOVE, bottom.id)
            alignWithParent = true
        })
        content.setPadding(0, 0, 0, dp(40f))
        strip.tag = SEARCH_STRIP_TAG
        addBottomCompanion(strip)
        return true
    }

    fun detachSearchStrip() {
        (0 until canvas.childCount).map(canvas::getChildAt).filter { it.tag == SEARCH_STRIP_TAG }.forEach { strip ->
            removeBottomCompanion(strip)
            canvas.removeView(strip)
        }
        content.setPadding(0, 0, 0, 0)
    }
    fun setToolbarVisibilityListener(listener: (Boolean) -> Unit) { onVisibilityChanged = listener }
    fun addBottomCompanion(view: View) { if (view !in bottomCompanions) bottomCompanions += view }
    fun removeBottomCompanion(view: View) { bottomCompanions -= view }

    /** ub's fixed viewport expands beneath the chrome; hiding never resizes the WebView. */
    fun hideToolbars(animated: Boolean = true) {
        if (!toolbarsShown || !usesOverlayViewport || keyboardVisible) return
        toolbarsShown = false
        move(content, 0f, animated)
        if (topHeight > 0) move(top, -topHeight.toFloat(), animated) { top.visibility = GONE }
        if (bottomHeight > 0) move(bottom, bottomHeight.toFloat(), animated) { bottom.visibility =
            GONE
        }
        val progressOffset = if (top.isNotEmpty()) -topHeight.toFloat() else bottomHeight.toFloat()
        move(progress, progressOffset, animated) { progress.translationY = 0f }
        bottomCompanions.filter { it.isVisible }.forEach { companion -> move(companion, bottomHeight.toFloat(), animated) { companion.translationY = 0f } }
        chromeOverlayOffset()?.let { offset ->
            move(chromeOverlay, offset, animated) { chromeOverlay.translationY = 0f }
        }
        onVisibilityChanged(false)
    }

    fun showToolbars(animated: Boolean = true) {
        if (toolbarsShown || !usesOverlayViewport || keyboardVisible) return
        toolbarsShown = true
        if (topHeight > 0) { top.visibility = VISIBLE; move(top, 0f, animated); move(content, topHeight.toFloat(), animated) }
        if (bottomHeight > 0) { bottom.visibility = VISIBLE; move(bottom, 0f, animated) }
        progress.translationY = if (top.isNotEmpty()) -topHeight.toFloat() else bottomHeight.toFloat()
        move(progress, 0f, animated)
        bottomCompanions.filter { it.isVisible }.forEach { companion -> companion.translationY = bottomHeight.toFloat(); move(companion, 0f, animated) }
        chromeOverlayOffset()?.let { offset ->
            chromeOverlay.translationY = offset
            move(chromeOverlay, 0f, animated)
        }
        onVisibilityChanged(true)
    }

    /** s6.ha reads the attached pane's LayoutParams, including after AdMarker moves it. */
    private fun chromeOverlayOffset(): Float? {
        if (chromeOverlay.childCount != 1) return null
        val root = chromeOverlay.getChildAt(0) as? ViewGroup ?: return null
        // BrowserOverlayFragment contains the stationary scrim followed by the pane.
        val params = root.getChildAt(1)?.layoutParams as? LayoutParams ?: return null
        return if (params.gravity and Gravity.BOTTOM == Gravity.BOTTOM) bottomHeight.toFloat() else -topHeight.toFloat()
    }

    /** c8.s6.a: IME hides the bottom container and freezes toolbar reveal/hide decisions. */
    fun setKeyboardVisible(visible: Boolean) {
        if (visible == keyboardVisible) return
        keyboardVisible = visible
        if (visible) { bottomBeforeKeyboard = bottom.isVisible; if (bottomBeforeKeyboard) bottom.visibility =
            GONE
        }
        else if (bottomBeforeKeyboard) bottom.visibility = VISIBLE
    }

    private fun move(view: View, target: Float, animated: Boolean, ended: (() -> Unit)? = null) {
        if (!animated) { view.translationY = target; ended?.invoke(); return }
        view.animate().translationY(target).setDuration(180).setInterpolator(easing).withEndAction { ended?.invoke() }.start()
    }
    private fun bounded(height: Int) = RelativeLayout.LayoutParams(-1, height).apply { addRule(RelativeLayout.BELOW, top.id); addRule(RelativeLayout.ABOVE, bottom.id); alignWithParent = true }
    private fun detach(view: View) { (view.parent as? ViewGroup)?.removeView(view) }
    private fun dp(value: Float) = (value * resources.displayMetrics.density + .5f).toInt()

    companion object {
        const val SEARCH_STRIP_TAG = "search_strip"
    }
}
