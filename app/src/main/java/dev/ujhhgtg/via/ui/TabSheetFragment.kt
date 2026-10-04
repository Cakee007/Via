package dev.ujhhgtg.via.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.browser.BrowserTab
import dev.ujhhgtg.via.browser.TabController
import dev.ujhhgtg.via.browser.UrlResolver
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.SettingsRecyclerView
import dev.ujhhgtg.via.skins.SkinResources
import dev.ujhhgtg.via.skins.setSkinImageResource
import dev.ujhhgtg.via.ui.dialog.ViaDialog

/**
 * f8.l0: the vertical tab sheet opened by the toolbar tab-counter control
 * (c8.s6.fa(23) -> nb()).  Slides out of the browser root's popup container
 * (mark.via:id/bg) above the content and below the toolbars, anchored to the
 * toolbar edge, with a RecyclerView of tab rows (f8.y binding mark.via.common
 * .widget.b0 cells), drag-to-reorder via the original's bundled ItemTouchHelper
 * flags a(3, 48) (UP|DOWN drag, START|END swipe-to-close), and a new-tab button.
 */
class TabSheetFragment : BrowserOverlayFragment() {

    /** f8.l0.b, implemented by c8.s6$l. */
    interface Callback {
        fun onRowSelected(position: Int) // i
        fun onRowDismiss(position: Int) // e (close button and swipe)
        fun onNewTabClicked() // d
        fun onNewTabLongClicked() // f
        fun onRowDuplicate(position: Int) // a
        fun onRowMenuAction(position: Int, action: Int) // b
        fun onRowMove(from: Int, to: Int): Boolean // h
    }

    var callback: Callback? = null
    private var maxHeight = 500
    private val rows = mutableListOf<BrowserTab>()
    private var selected = -1
    private var dragging = false
    private var heightAnimator: ValueAnimator? = null
    private lateinit var listView: RecyclerView
    private val adapter = RowAdapter()
    private val itemTouchHelper: ItemTouchHelper
    private var rowRipple: Drawable? = null
    private var fallbackFavicon: Drawable? = null
    private lateinit var preferences: BrowserPreferences
    private var registered = false

    /** The r4.f model flow: the sheet follows live tab changes (c8.s6$l.g/c). */
    private val modelListener = object : TabController.Listener {
        override fun onTabInserted(index: Int, tab: BrowserTab) {
            this@TabSheetFragment.onTabInserted(index, tab)
        }
        override fun onTabRemoved(index: Int) = this@TabSheetFragment.onTabRemoved(index)
        override fun onTabSelected(from: Int, to: Int) = this@TabSheetFragment.onTabSelected(from, to)
        override fun onTabChanged(index: Int) = this@TabSheetFragment.onTabChanged(index)
        override fun onTabsMoved(from: Int, to: Int, selected: Int) =
            this@TabSheetFragment.onTabsMoved(from, to, selected)
    }

    init {
        // f8/l0$a extends the original's androidx.recyclerview.widget.j.i with
        // (dragDirs = 3 = UP|DOWN, swipeDirs = 48 = START|END).
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.START or ItemTouchHelper.END,
        ) {
            override fun isLongPressDragEnabled(): Boolean = false
            override fun onMove(
                recyclerView: RecyclerView,
                source: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val from = source.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                return !(from < 0 || to < 0) && this@TabSheetFragment.callback?.onRowMove(from, to) == true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position >= 0) this@TabSheetFragment.callback?.onRowDismiss(position)
            }
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null && callback != null) {
                    viewHolder.itemView.setBackgroundColor(dragRowColor(viewHolder.itemView.context))
                } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                    dragging = false
                }
            }
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                if (callback != null) viewHolder.itemView.background = rowRipple(viewHolder.itemView.context)
            }
        }
        itemTouchHelper = ItemTouchHelper(callback)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        arguments?.let { maxHeight = it.getInt("max_height", maxHeight) }
        preferences = BrowserPreferences(context)
        // f8.l0.p3 / c8.s6.G9: register with the tab model on creation.
        val controller = (parentFragment as? dev.ujhhgtg.via.BrowserFragment)?.tabController
        if (controller != null) {
            controller.addListener(modelListener)
            registered = true
        }
    }

    override fun paneTitle(): CharSequence = getString(R.string.tabs)

    override fun createContent(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        // f8.l0.V2: RecyclerView dn with dynamic height + new-tab ImageView ab,
        // inside a vertically padded LinearLayout.
        listView = RecyclerView(context)
        listView.id = R.id.tab_sheet_list
        listView.layoutParams = LinearLayout.LayoutParams(-1, listHeight())
        val manager = LinearLayoutManager(context)
        // f8.l0.V2 calls LinearLayoutManager.E2(bottom) — E2 is setStackFromEnd
        // (the supportPredictiveItemAnimations check compares mLastStackFromEnd
        // with the field it writes), so the list anchors at the toolbar edge.
        manager.stackFromEnd = gravity and Gravity.BOTTOM == Gravity.BOTTOM
        listView.layoutManager = manager
        // z8.r3.d
        listView.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        listView.edgeEffectFactory = SettingsRecyclerView.StretchEdgeEffectFactory()
        listView.setHasFixedSize(true)
        // f8.l0.V2 configures the bundled DefaultItemAnimator with
        // change/add/remove durations of 100 ms (move stays at 250 ms).
        listView.itemAnimator = DefaultItemAnimator().apply {
            changeDuration = 100
            addDuration = 100
            removeDuration = 100
        }
        itemTouchHelper.attachToRecyclerView(listView)
        listView.adapter = adapter

        val newTab = ImageView(context)
        newTab.id = R.id.tab_sheet_new_tab
        val height = resources.getDimensionPixelSize(R.dimen.tab_new_tab_height)
        newTab.layoutParams = LinearLayout.LayoutParams(-1, height)
        val padding = resources.getDimensionPixelSize(R.dimen.menu_control_padding)
        newTab.setPaddingRelative(padding, padding, padding, padding)
        newTab.background = rowRipple(context)
        newTab.contentDescription = getString(R.string.action_new_tab)
        newTab.isFocusable = true
        newTab.setImageDrawable(tinted(context, R.drawable.plus, R.drawable.plus.toString()))
        newTab.setColorFilter(subtleColor(context))
        newTab.setOnClickListener { callback?.onNewTabClicked() }
        newTab.setOnLongClickListener { callback?.onNewTabLongClicked(); true }

        val content = LinearLayout(context)
        content.orientation = LinearLayout.VERTICAL
        val vertical = resources.getDimensionPixelSize(R.dimen.tab_sheet_padding)
        content.setPaddingRelative(0, vertical, 0, vertical)
        content.addView(listView)
        content.addView(newTab)
        return content
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.adapter = adapter
        // f8.l0.V1: reveal the selected row and give it accessibility focus.
        if (selected in rows.indices) {
            listView.scrollToPosition(selected)
            focusRow(selected)
        }
    }

    override fun focusContent() {
        // f8.l0.W2: focus the selected row when it exists, the pane otherwise.
        if (!::listView.isInitialized || selected < 0) super.focusContent() else focusRow(selected)
    }

    override fun onDestroy() {
        // f8.l0.D1: unregister from the model and drop the callback.
        if (registered) {
            (parentFragment as? dev.ujhhgtg.via.BrowserFragment)?.tabController?.removeListener(modelListener)
            registered = false
        }
        callback = null
        super.onDestroy()
    }

    /** f8.l0.q3, fed by c8.s6.G9 right after the fragment is created. */
    fun submitTabs(tabs: List<BrowserTab>, selectedPosition: Int) {
        rows.clear()
        rows.addAll(tabs)
        selected = if (selectedPosition in tabs.indices) selectedPosition else -1
        if (view != null) {
            adapter.notifyDataSetChanged()
            if (selected >= 0) listView.scrollToPosition(selected)
        }
    }

    /** f8.l0.l3: list height is bounded by the sheet's max height budget. */
    private fun listHeight(): Int {
        val row = resources.getDimensionPixelSize(R.dimen.tab_row_height)
        val reserved = resources.getDimensionPixelSize(R.dimen.tab_sheet_max_reserve)
        val padding = resources.getDimensionPixelSize(R.dimen.tab_sheet_padding)
        return minOf(maxOf(0, maxHeight - reserved - padding * 2), row * rows.size)
    }

    /** f8.l0.m3/n3: animate the list height (350 ms) as rows are added/removed. */
    private fun animateListHeight() {
        if (!isAdded) return
        val params = listView.layoutParams
        val target = listHeight()
        val current = params.height
        heightAnimator?.cancel()
        if (current == target) return
        val animator = ValueAnimator.ofInt(current, current, target)
        heightAnimator = animator
        animator.duration = 350
        animator.interpolator = android.view.animation.PathInterpolator(.2f, .2f, .8f, .8f)
        animator.addUpdateListener {
            params.height = it.animatedValue as Int
            listView.layoutParams = params
        }
        animator.start()
    }

    /** g6.y.M: focus the row at the position (accessibility pane support). */
    private fun focusRow(position: Int) {
        listView.post {
            if (!isAdded || !listView.isAttachedToWindow) return@post
            val holder = listView.findViewHolderForAdapterPosition(position)
            val row = holder?.itemView
            if (row != null) {
                row.requestFocus()
                row.performAccessibilityAction(64, null)
            }
        }
    }

    /** TabController.Listener flow, the r4.f model events shared with the strip. */
    fun onTabInserted(index: Int, tab: BrowserTab) {
        if (index < 0 || index > rows.size) return
        rows.add(index, tab)
        adapter.notifyItemInserted(index)
        animateListHeight()
    }

    fun onTabRemoved(index: Int) {
        if (index !in rows.indices) return
        rows.removeAt(index)
        adapter.notifyItemRemoved(index)
        animateListHeight()
    }

    fun onTabSelected(from: Int, to: Int) {
        selected = to
        if (from >= 0 && from < rows.size) adapter.notifyItemChanged(from)
        if (to in rows.indices && to != from) adapter.notifyItemChanged(to)
    }

    fun onTabChanged(index: Int) {
        if (index in rows.indices) adapter.notifyItemChanged(index)
    }

    fun onTabsMoved(from: Int, to: Int, selected: Int) {
        if (from !in rows.indices || to !in rows.indices) return
        this.selected = selected
        rows.add(to, rows.removeAt(from))
        adapter.notifyItemMoved(from, to)
    }


    private inner class RowAdapter : RecyclerView.Adapter<RowHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
            val row = TabSheetRowView(parent.context)
            // f8.y.d: every row is MATCH_PARENT x 48dp (dimen ba).
            row.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.tab_row_height),
            )
            return RowHolder(row)
        }
        override fun getItemCount(): Int = rows.size
        override fun onBindViewHolder(holder: RowHolder, position: Int) = holder.bind(rows[position], position)
    }

    private inner class RowHolder(val row: TabSheetRowView) : RecyclerView.ViewHolder(row) {
        fun bind(tab: BrowserTab, position: Int) {
            val context = row.context
            val selectedTab = position == selected
            // f8.y.b: title fallbacks (title -> url -> Untitled), bold + accent
            // for the active tab.
            var display = displayTitle(tab.title, tab.url)
            if (display.isEmpty()) display = tab.url
            if (display.isEmpty()) display = getString(R.string.untitled)
            row.title.text = display
            row.title.setTypeface(preferences.selectedTypeface(), if (selectedTab) Typeface.BOLD else Typeface.NORMAL)
            row.title.setTextColor(if (selectedTab) accentColor(context) else primaryTextColor(context))
            // f8.y/r4.d.u: current WebView favicon, then the disk-backed x0 cache.
            val bitmap = tab.favicon
                ?: dev.ujhhgtg.via.home.HomeIcons.load(context, tab.url)
            if (bitmap != null) {
                row.icon.setImageBitmap(bitmap)
            } else {
                row.icon.setImageDrawable(fallbackFavicon(context))
            }
            row.close.contentDescription = getString(R.string.close_tab_description, display)
            row.close.setOnClickListener {
                val position1 = bindingAdapterPosition
                if (position1 >= 0) callback?.onRowDismiss(position1)
            }
            row.setOnClickListener {
                val position1 = bindingAdapterPosition
                if (position1 >= 0) callback?.onRowSelected(position1)
            }
            row.setOnLongClickListener {
                val position1 = bindingAdapterPosition
                if (position1 < 0 || dragging) return@setOnLongClickListener true
                showRowMenu(row, rows.getOrNull(position1) ?: return@setOnLongClickListener true, position1)
                true
            }
            @SuppressLint("ClickableViewAccessibility")
            row.icon.setOnTouchListener { _, event ->
                // f8.y.l / f8.l0.Z2: drag starts from the favicon when two or
                // more tabs are open.
                if (event.actionMasked == MotionEvent.ACTION_DOWN && rows.size >= 2) {
                    dragging = true
                    itemTouchHelper.startDrag(this)
                }
                false
            }
        }
    }

    /** f8.l0.r3: the anchored row menu (w5.k.l items 4/1/3/2/6/5). */
    private fun showRowMenu(view: View, tab: BrowserTab, position: Int) {
        val count = rows.size
        val url = tab.url
        val items = mutableListOf<Pair<Int, String>>()
        if (count > 1) {
            items += 4 to getString(R.string.action_close_all_tabs)
            items += 1 to getString(R.string.action_close_other_tabs)
        }
        if (position != 0 && position != count - 1) {
            items += 3 to getString(R.string.action_close_tabs_to_the_top)
            items += 2 to getString(R.string.action_close_tabs_to_the_bottom)
        }
        val network = url.startsWith("http://") || url.startsWith("https://")
        if (network) items += 6 to getString(R.string.duplicate_tab)
        if (!url.startsWith("file://")) items += 5 to getString(R.string.action_copy)
        if (items.isEmpty()) return
        ViaDialog(requireActivity()).items(items.map { it.second }.toTypedArray(), onClick = { which ->
            when (items[which].first) {
                1, 2, 3, 4 -> callback?.onRowMenuAction(position, items[which].first)
                5 -> {
                    ViaToast.makeText(requireContext(), copyToClipboard(url), ViaToast.LENGTH_SHORT).show()
                }
                6 -> callback?.onRowDuplicate(position)
            }
        }).showAnchored(view)
    }

    private fun copyToClipboard(value: String): String {
        val label = getString(R.string.hint_url)
        val manager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        manager.setPrimaryClip(android.content.ClipData.newPlainText(label, value))
        return getString(R.string.toast_copy_url_successful)
    }

    /** z8.b0.G: the original normalizes the platform error-page title to the host. */
    private fun displayTitle(title: String, url: String): String {
        if ((title.length == 6 && title == "网页无法打开") || (title.length == 21 && title == "Webpage not available")) {
            return UrlResolver.host(url).orEmpty()
        }
        return title
    }

    private fun rowRipple(context: Context): Drawable = rowRipple ?: ContextCompat.getDrawable(context, R.drawable.flat_ripple)!!.also { rowRipple = it }
    private fun fallbackFavicon(context: Context): Drawable {
        val cached = fallbackFavicon
        if (cached != null) return cached
        val drawable = SkinResources.drawable(context, R.drawable.globe)!!.mutate()
        drawable.setColorFilter(subtleColor(context), android.graphics.PorterDuff.Mode.SRC_IN)
        val size = resources.getDimensionPixelSize(R.dimen.favicon_size)
        drawable.setBounds(0, 0, size, size)
        fallbackFavicon = drawable
        return drawable
    }

    private fun subtleColor(context: Context): Int = context.obtainStyledAttributes(intArrayOf(R.attr.viaSubtleColor)).let {
        try { it.getColor(0, 0) } finally { it.recycle() }
    }
    private fun accentColor(context: Context): Int = context.obtainStyledAttributes(intArrayOf(R.attr.viaAccentColor)).let {
        try { it.getColor(0, 0) } finally { it.recycle() }
    }
    private fun primaryTextColor(context: Context): Int = context.obtainStyledAttributes(intArrayOf(R.attr.viaPrimaryTextColor)).let {
        try { it.getColor(0, 0) } finally { it.recycle() }
    }
    private fun dragRowColor(context: Context): Int = context.getColor(R.color.drag_row_background)

    private fun tinted(context: Context, drawableRes: Int, name: String): Drawable {
        val drawable = SkinResources.drawable(context, drawableRes)!!.mutate()
        drawable.setColorFilter(subtleColor(context), android.graphics.PorterDuff.Mode.SRC_IN)
        return drawable
    }

    /**
     * mark.via.common.widget.b0: a 48dp row of favicon slot (13dp inset),
     * single-line title with a 24dp horizontal fading edge, and a 48dp close
     * button (14dp inset).
     */
    private class TabSheetRowView(context: Context) : LinearLayout(context) {
        val icon = ImageView(context)
        val title = TextView(context)
        val close = ImageView(context)

        init {
            orientation = HORIZONTAL
            icon.layoutParams = LayoutParams(dp(48f), -1)
            icon.setPaddingRelative(dp(13f), 0, dp(13f), 0)
            icon.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            title.layoutParams = LayoutParams(0, -1, 1f)
            title.isSingleLine = true
            title.gravity = Gravity.CENTER_VERTICAL
            title.setFadingEdgeLength(dp(24f))
            title.isHorizontalFadingEdgeEnabled = true
            title.textDirection = TEXT_DIRECTION_LOCALE
            // x8.g.m(textView, x8.h.r(context)): dimen a_ = 14sp, resolved in px.
            title.setTextUnitSize(14f)
            close.layoutParams = LayoutParams(dp(48f), -1)
            close.setPaddingRelative(dp(14f), 0, dp(14f), 0)
            close.scaleType = ImageView.ScaleType.CENTER_INSIDE
            close.setSkinImageResource(R.drawable.close)
            close.setColorFilter((context.obtainStyledAttributes(intArrayOf(R.attr.viaSubtleColor)).let {
                try { it.getColor(0, 0) } finally { it.recycle() }
            }))
            // b0's close view carries the o rounded pressed background.
            close.background = ContextCompat.getDrawable(context, R.drawable.rounded_rect_ripple)
            addView(icon)
            addView(title)
            addView(close)
        }

        private fun dp(value: Float): Int = (value * resources.displayMetrics.density + .5f).toInt()
        private fun TextView.setTextUnitSize(sp: Float) {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp)
        }
    }

    companion object {
        fun newInstance(gravity: Int, width: Int, maxHeight: Int): TabSheetFragment = TabSheetFragment().apply {
            arguments = Bundle().apply {
                putInt("gravity", gravity)
                putInt("width", width)
                putInt("max_height", maxHeight)
            }
        }
    }
}
