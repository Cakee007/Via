package dev.ujhhgtg.via.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.util.TypedValue
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat.replaceAccessibilityAction
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.browser.BrowserTab
import dev.ujhhgtg.via.skins.SkinResources
import dev.ujhhgtg.via.skins.setSkinImageResource
import kotlin.math.max
import kotlin.math.min

/**
 * The original mark.via.common.widget.t tab strip, translated without changing
 * its interaction model: a horizontal RecyclerView of favicon/title/close tab
 * cells, a weighted new-tab cell, favicon-only drag reordering, and the same active
 * versus inactive alpha treatment.  This is deliberately a view component;
 * tab navigation remains owned by BrowserFragment/TabController.
 */
class ViaTabBar(context: Context) : LinearLayout(context) {
    interface Callbacks {
        fun onTabSelected(index: Int)
        fun onTabClosed(index: Int)
        fun onNewTab(view: View)
        fun onTabsMoved(from: Int, to: Int): Boolean = false
        fun onTabLongPress(view: View, index: Int): Boolean = false
    }

    private val tabsView = RecyclerView(context)
    private val newTab = ImageView(context)
    private val adapter = TabAdapter()
    private var itemTouchHelper: ItemTouchHelper
    private var items: List<BrowserTab> = emptyList()
    private var selectedId: Long = Long.MIN_VALUE
    private var callbacks: Callbacks? = null
    private var tabWidth = 0
    private var tint = Color.BLACK
    private var labelColor = resolveColor(context, R.attr.viaPrimaryTextColor, Color.BLACK)
    private var inactiveAlpha = .45f
    private var draggingIcon = false
    private var lastScrollPosition = -1
    private val itemTouch = object : ItemTouchHelper.Callback() {
        override fun getMovementFlags(recyclerView: RecyclerView, holder: RecyclerView.ViewHolder): Int =
            makeMovementFlags(ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0)
        override fun onMove(recyclerView: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val from = source.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from < 0 || to < 0 || from == to) return false
            // t's callback delegates to r4.f.G; that event performs the single adapter move.
            return callbacks?.onTabsMoved(from, to) == true
        }
        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        // common/widget/t$b.r() disables RecyclerView's blanket long-press
        // drag recognizer. Dragging starts only from the favicon touch target;
        // the title/empty part must remain available for one context-menu
        // long-press (and therefore one platform haptic feedback).
        override fun isLongPressDragEnabled() = false
        override fun isItemViewSwipeEnabled() = false
        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                viewHolder.itemView.setBackgroundColor(0x30808080)
            } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) draggingIcon = false
        }
        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.background = ContextCompat.getDrawable(context, R.drawable.flat_ripple)
        }

    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setWillNotDraw(false)
        tabsView.apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            itemAnimator = DefaultItemAnimator().apply { supportsChangeAnimations = false }
            overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
            isHorizontalScrollBarEnabled = false
            adapter = this@ViaTabBar.adapter
            addItemDecoration(DividerItemDecoration(context, RecyclerView.HORIZONTAL).apply {
                setDrawable(GradientDrawable().apply {
                    setColor(0x30808080)
                    val size = dp(1f)
                    setSize(size, size)
                })
            })
        }
        // The source gives the tab list N-1 weight units and the new-tab
        // button one unit. This keeps the tab width stable as the viewport
        // changes instead of reserving an arbitrary fixed button width.
        addView(tabsView, LayoutParams(0, -1, 1f))
        newTab.apply {
            setSkinImageResource(R.drawable.plus)
            // t$b: the strip controls carry the accent tint (s6 passes the
            // accent as the first y() argument) and the rounded ripple.
            setColorFilter(tint)
            val padding = (dp(36f) - dp(22f)) / 2
            setPadding(0, padding, 0, padding)
            // The new-tab control uses the o rounded pressed selector; the
            // tab cells use the square-corner p selector.
            background = ContextCompat.getDrawable(context, R.drawable.rounded_rect_ripple)
            contentDescription = context.getString(R.string.action_new_tab)
            isFocusable = true
            setSourceClickListener { callbacks?.onNewTab(this) }
        }
        addView(newTab, LayoutParams(0, -1, 1f))
        itemTouchHelper = ItemTouchHelper(itemTouch)
        itemTouchHelper.attachToRecyclerView(tabsView)
    }

    fun setCallbacks(value: Callbacks?) { callbacks = value }

    /** ua.O -> r4.f.f -> t.c: a single row rebinds from the live tab. */
    fun onTabChanged(index: Int) {
        if (index in items.indices) adapter.notifyItemChanged(index)
    }

    /** r4.f.Z/H: mutate the local snapshot before issuing the original single-row notification. */
    fun onTabInserted(index: Int, tab: BrowserTab) {
        if (index !in 0..items.size) return
        items = items.toMutableList().apply { add(index, tab) }
        adapter.notifyItemInserted(index)
    }

    fun onTabRemoved(index: Int) {
        if (index !in items.indices) return
        items = items.toMutableList().apply { removeAt(index) }
        adapter.notifyItemRemoved(index)
    }

    fun onTabsMoved(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices) return
        items = items.toMutableList().also { list -> list.add(to, list.removeAt(from)) }
        adapter.notifyItemMoved(from, to)
    }

    fun submitTabs(value: List<BrowserTab>, selected: Long?) {
        val old = items
        val next = value.toList()
        val oldSelected = selectedId
        selectedId = selected ?: Long.MIN_VALUE
        items = next
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = next.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) = old[oldItemPosition].id == next[newItemPosition].id
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val a = old[oldItemPosition]; val b = next[newItemPosition]
                return a.id == b.id && a.title == b.title && a.url == b.url && a.favicon === b.favicon &&
                    (a.id == oldSelected) == (b.id == selectedId)
            }
        })
        diff.dispatchUpdatesTo(adapter)
    }

    fun setColors(color: Int, inactive: Float = .45f) {
        inactiveAlpha = inactive
        setContentColors(color, labelColor)
    }

    /** mark.via.common.widget.t.y: icon and text colors have separate roles. */
    fun setContentColors(iconColor: Int, textColor: Int) {
        if (tint == iconColor && labelColor == textColor) return
        tint = iconColor
        labelColor = textColor
        newTab.setColorFilter(iconColor)
        adapter.notifyItemRangeChanged(0, items.size, Unit)
    }

    fun scrollToSelected() = scrollToSelected(force = false)

    private fun scrollToSelected(force: Boolean) {
        val index = items.indexOfFirst { it.id == selectedId }
        if (index >= 0 && (force || index != lastScrollPosition)) {
            lastScrollPosition = index
            // t.x / RecyclerView.p1 is scrollToPosition, not smoothScrollToPosition.
            tabsView.scrollToPosition(index)
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        recalculateWidth()
    }

    /** t.setParentWidth runs on size changes; content/selection changes never resize every cell. */
    private fun recalculateWidth() {
        if (width <= 0) return
        val widthDp = (width / resources.displayMetrics.density + .5f).toInt()
        val unit = if (widthDp < 420) widthDp / 7 else if (widthDp > 900) 72 else 60
        if (unit <= 0) return
        val count = widthDp / unit
        weightSum = count.toFloat()
        (tabsView.layoutParams as LayoutParams).apply {
            weight = (count - 1).toFloat()
            tabsView.layoutParams = this
        }
        tabsView.requestLayout()
        val item = (width / count * (count - 1) - dp(1f) * 2) / 2
        val next = min(dp(180f), max(dp(100f), item))
        post { requestLayout() }
        if (next != tabWidth) {
            tabWidth = next
            adapter.notifyItemRangeChanged(0, items.size, Unit)
            scrollToSelected(force = true)
        }
    }

    private inner class TabAdapter : RecyclerView.Adapter<TabHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabHolder = TabHolder(TabCell(parent.context))
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: TabHolder, position: Int) {
            holder.bind(items[position], position, position == items.indexOfFirst { it.id == selectedId })
        }
        override fun onBindViewHolder(holder: TabHolder, position: Int, payloads: MutableList<Any>) {
            if (payloads.isEmpty()) onBindViewHolder(holder, position) else holder.updateColorsAndSize()
        }
    }

    private inner class TabHolder(private val cell: TabCell) : RecyclerView.ViewHolder(cell) {
        fun bind(tab: BrowserTab, position: Int, selected: Boolean) {
            cell.bind(tab, selected, position + 1, items.size, tint, labelColor, inactiveAlpha)
            updateSize()
            cell.setSourceClickListener { bindingAdapterPosition.takeIf { it >= 0 }?.let { callbacks?.onTabSelected(it) } }
            cell.setOnLongClickListener {
                if (draggingIcon) true
                else bindingAdapterPosition.takeIf { it >= 0 }?.let { callbacks?.onTabLongPress(cell, it) } == true
            }
            cell.close.setSourceClickListener { bindingAdapterPosition.takeIf { it >= 0 }?.let { callbacks?.onTabClosed(it) } }
            // t$c$x: exposed "move earlier/later" accessibility actions
            // (the standard scroll ids carry the custom labels).
            androidx.core.view.ViewCompat.replaceAccessibilityAction(
                cell, AccessibilityActionCompat.ACTION_SCROLL_UP, context.getString(R.string.move_tab_previous)
            ) { _, _ -> moveBy(bindingAdapterPosition, -1) }
            androidx.core.view.ViewCompat.replaceAccessibilityAction(
                cell, AccessibilityActionCompat.ACTION_SCROLL_DOWN, context.getString(R.string.move_tab_next)
            ) { _, _ -> moveBy(bindingAdapterPosition, 1) }
            @SuppressLint("ClickableViewAccessibility")
            cell.icon.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN && items.size >= 2) {
                    draggingIcon = true
                    itemTouchHelper.startDrag(this)
                } else if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) draggingIcon = false
                false
            }
        }

        fun updateColorsAndSize() {
            cell.setColors(tint, labelColor)
            updateSize()
        }

        private fun updateSize() {
            val width = tabWidth
            val parameters = cell.layoutParams
            if (parameters == null) cell.layoutParams = RecyclerView.LayoutParams(width, -1)
            else if (parameters.width != width) {
                parameters.width = width
                parameters.height = -1
                cell.layoutParams = parameters
            }
        }

        private fun moveBy(position: Int, delta: Int): Boolean {
            val target = position + delta
            if (position < 0 || target !in items.indices) return false
            return callbacks?.onTabsMoved(position, target) == true
        }
    }

    private class TabCell(context: Context) : LinearLayout(context) {
        val icon = ImageView(context)
        val title = TextView(context)
        val close = ImageView(context)
        private var fallback: Drawable? = null

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.flat_ripple)
            // b0's icon slot is 48dp wide with 13dp horizontal inset.  The
            // inset keeps the favicon at the same visual size as the source
            // row while leaving the title immediately beside the slot.
            icon.layoutParams = LayoutParams(dp(context, 48f), -1)
            icon.setPadding(dp(context, 13f), 0, dp(context, 13f), 0)
            icon.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            title.layoutParams = LayoutParams(0, -1, 1f)
            title.setSingleLine(); title.ellipsize = TextUtils.TruncateAt.END
            title.gravity = Gravity.CENTER_VERTICAL
            // The original b0 TextView has no horizontal padding; adding it
            // makes the gap after the favicon visibly too large.
            title.setPadding(0, 0, 0, 0)
            // x8.g.m(textView, x8.h.r(context)): dimen a_ = 14sp.
            title.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
            close.layoutParams = LayoutParams(dp(context, 48f), -1)
            // b0.d keeps ImageView's FIT_CENTER default, including scaling the
            // packaged bitmap up to the 48dp slot minus its truncated 14dp insets.
            close.setSkinImageResource(R.drawable.close)
            close.contentDescription = context.getString(R.string.operation_closetab)
            val closePadding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14f, resources.displayMetrics).toInt()
            close.setPadding(closePadding, 0, closePadding, 0)
            close.background = ContextCompat.getDrawable(context, R.drawable.rounded_rect_ripple)
            addView(icon); addView(title); addView(close)
        }

        fun bind(tab: BrowserTab, selected: Boolean, position: Int, total: Int, iconColor: Int, textColor: Int, inactiveAlpha: Float) {
            title.text = tab.title.ifBlank { tab.url }
            title.typeface = dev.ujhhgtg.via.data.BrowserPreferences(context).selectedTypeface()
            setColors(iconColor, textColor)
            val alpha = if (selected) 1f else inactiveAlpha
            title.alpha = alpha; icon.alpha = alpha; close.alpha = alpha
            // t$c$l: the fallback globe is accent-tinted at the 22dp icon size.
            // f8.y/m.a: the row reads the disk-backed cache, so favicons from
            // previous visits show before any icon event arrives.
            tab.favicon?.let(icon::setImageBitmap)
                ?: dev.ujhhgtg.via.home.HomeIcons.load(context, tab.url)?.let(icon::setImageBitmap)
                ?: icon.setImageDrawable(fallbackIcon(iconColor))
            contentDescription = context.getString(R.string.tab_position_description, position, total)
        }

        fun setColors(iconColor: Int, textColor: Int) {
            close.setColorFilter(iconColor)
            title.setTextColor(textColor)
            fallback?.setColorFilter(iconColor, android.graphics.PorterDuff.Mode.SRC_IN)
        }

        private fun fallbackIcon(iconColor: Int): Drawable {
            fallback?.let { return it }
            val drawable = SkinResources.drawable(context, R.drawable.globe)!!.mutate()
            drawable.setColorFilter(iconColor, android.graphics.PorterDuff.Mode.SRC_IN)
            fallback = drawable
            return drawable
        }
    }


    /** y5.a/v5.c rebinds click handlers without replacing the row's ripple drawable. */
    private fun View.setSourceClickListener(action: (View) -> Unit) {
        var previous: Long? = null
        setOnClickListener { clicked ->
            val now = SystemClock.elapsedRealtime()
            val last = previous
            if (last == null || kotlin.math.abs(now - last) > 300L) {
                previous = now
                action(clicked)
            }
        }
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density + .5f).toInt()
    companion object {
        private fun resolveColor(context: Context, attribute: Int, fallback: Int): Int = context.obtainStyledAttributes(intArrayOf(attribute)).let { attrs ->
            try { attrs.getColor(0, fallback) } finally { attrs.recycle() }
        }
        private fun dp(context: Context, value: Float): Int = (value * context.resources.displayMetrics.density + .5f).toInt()
    }
}
