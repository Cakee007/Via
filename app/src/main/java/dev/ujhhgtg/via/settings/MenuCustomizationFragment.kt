package dev.ujhhgtg.via.settings

import android.os.Bundle
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.OriginalMenu
import dev.ujhhgtg.via.ui.behavior.BehaviorPreferences
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import dev.ujhhgtg.via.ui.dp

/** i8.s: two ordered groups, five columns, tap/drag transfer and mandatory Settings action. */
class MenuCustomizationFragment : SettingsListFragment() {
    private lateinit var preferences: BrowserPreferences
    private lateinit var behavior: BehaviorPreferences
    private lateinit var menuAdapter: MenuGridAdapter
    private val displayed = mutableListOf<Int>()
    private val hidden = mutableListOf<Int>()
    private var dragging = false

    override fun configureToolbar(toolbar: SettingsToolbar) {
        toolbar.setTitle(R.string.customize_menu)
        toolbar.addAction(null, R.string.action_reset) { resetMenus() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = BrowserPreferences(requireContext())
        behavior = BehaviorPreferences(preferences)
        menuAdapter = MenuGridAdapter(requireContext(), ::toggle)
        list.layoutManager = GridLayoutManager(requireContext(), 5).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) = if (menuAdapter.isHeading(position)) 5 else 1
            }
        }
        list.adapter = menuAdapter
        drag.attachToRecyclerView(list)
        bindRows()
    }

    private fun bindRows() {
        displayed.clear()
        hidden.clear()
        // i8.s.u3 consumes the known IDs in displayed order first, then hidden
        // order, and finally appends any remaining actions by their numeric ID.
        val available = OriginalMenu.entries.keys.toMutableSet()
        val shown = preferences.displayedMenus?.split(',')?.mapNotNull(String::toIntOrNull) ?: OriginalMenu.defaults
        for (id in shown) if (available.remove(id)) displayed.add(id)
        for (id in preferences.hiddenMenus.orEmpty().split(',').mapNotNull(String::toIntOrNull)) {
            if (available.remove(id)) hidden.add(id)
        }
        hidden.addAll(available.sorted())
        // i8.s.n3 keeps Settings visible even if an imported ordering hides it.
        if (hidden.remove(10) || 10 !in displayed) displayed.add(10)
        menuAdapter.submit(displayed, hidden)
    }

    private fun positionOf(id: Int): Int {
        val shown = displayed.indexOf(id)
        if (shown >= 0) return shown + 1
        val concealed = hidden.indexOf(id)
        return if (concealed >= 0) displayed.size + 2 + concealed else -1
    }

    /** i8.s.e3/y3: taps animate a move, rather than replacing the complete adapter. */
    private fun toggle(position: Int) {
        val row = menuAdapter.itemAt(position) ?: return
        if (dragging || list.itemAnimator?.isRunning == true || row.heading || row.id == 10) return
        if (displayed.remove(row.id)) hidden.add(0, row.id)
        else if (hidden.remove(row.id)) displayed.add(row.id)
        else return
        menuAdapter.submit(displayed, hidden, position to positionOf(row.id))
    }

    /** i8.s.v3, including the separator target and its index after source removal. */
    private fun move(source: Int, target: Int): Boolean {
        val row = menuAdapter.itemAt(source) ?: return false
        if (source == target || row.heading || target <= 0 || target >= menuAdapter.itemCount) return false
        if (row.id == 10 && target >= displayed.size + 1) return false
        val fromDisplayed = displayed.remove(row.id)
        if (!fromDisplayed && !hidden.remove(row.id)) return false
        val separator = displayed.size + 1
        when {
            target == separator -> if (fromDisplayed) displayed.add(row.id) else hidden.add(0, row.id)
            target < separator -> displayed.add((target - 1).coerceIn(0, displayed.size), row.id)
            target > separator -> hidden.add((target - separator - 1).coerceIn(0, hidden.size), row.id)
            else -> displayed.add(row.id)
        }
        menuAdapter.submit(displayed, hidden, source to positionOf(row.id))
        return true
    }

    private val drag = ItemTouchHelper(object : ItemTouchHelper.Callback() {
        override fun getMovementFlags(recyclerView: RecyclerView, holder: RecyclerView.ViewHolder): Int =
            if (menuAdapter.itemAt(holder.bindingAdapterPosition)?.heading == false)
                makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0)
            else makeMovementFlags(0, 0)
        override fun onMove(recyclerView: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean =
            move(source.bindingAdapterPosition, target.bindingAdapterPosition)
        override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) = Unit
        override fun onSelectedChanged(holder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(holder, actionState)
            // The source leaves drag elevation/drawing to ItemTouchHelper; it never fades the cell.
            dragging = actionState != ItemTouchHelper.ACTION_STATE_IDLE
        }
    })

    private fun resetMenus() = ViaDialog(requireActivity()).title(R.string.reset_to_default_settings).message(R.string.dialog_sure)
        .positive(R.string.action_reset) { _, _ -> behavior.resetMenus(); bindRows() }.negative(android.R.string.cancel).show()

    /** i8.s.M1/G1/x3: write the two original preference keys when pausing or hiding. */
    private fun saveMenus() { if (::behavior.isInitialized) behavior.reorderMenus(displayed, hidden) }
    override fun onPause() { saveMenus(); super.onPause() }
    override fun onHiddenChanged(hidden: Boolean) { super.onHiddenChanged(hidden); if (hidden) saveMenus() }
}

/** i8.o/y: compound-drawable text cells and centered, non-bold section instructions. */
private class MenuGridAdapter(
    private val context: android.content.Context,
    private val onClick: (Int) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    data class Row(val heading: Boolean, val title: String, val id: Int = -1)
    private var rows = emptyList<Row>()
    fun isHeading(position: Int) = rows.getOrNull(position)?.heading == true
    fun itemAt(position: Int) = rows.getOrNull(position)
    fun submit(displayed: List<Int>, hidden: List<Int>, moved: Pair<Int, Int>? = null) {
        rows = buildList {
            add(Row(true, context.getString(R.string.hold_and_drag_to_rearrange_items)))
            displayed.forEach { id -> OriginalMenu.entries[id]?.let { add(Row(false, it.title(context), id)) } }
            add(Row(true, context.getString(R.string.hold_and_drag_or_tap_to_add_item)))
            hidden.forEach { id -> OriginalMenu.entries[id]?.let { add(Row(false, it.title(context), id)) } }
        }
        if (moved == null) notifyDataSetChanged() else notifyItemMoved(moved.first, moved.second)
    }
    override fun getItemCount() = rows.size
    override fun getItemViewType(position: Int) = if (rows[position].heading) 0 else 1
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = (if (viewType == 0) TextView(parent.context) else TextView(parent.context, null, 0)).apply {
            typeface = BrowserPreferences(context).selectedTypeface()
            gravity = Gravity.CENTER
            layoutParams = RecyclerView.LayoutParams(-1, -2)
            if (viewType == 0) {
                setPaddingRelative(context.dp(16f), 0, context.dp(16f), 0)
                (layoutParams as RecyclerView.LayoutParams).apply {
                    // h6.a.B/t use TypedValue.applyDimension and truncate.
                    val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics).toInt()
                    topMargin = margin
                    bottomMargin = margin
                }
                setTextColor(settingsColor(context, R.attr.viaSecondaryTextColor, 0xff444444.toInt()))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
            } else {
                background = controlRipple(context)
                setPaddingRelative(context.dp(5f), context.dp(15f), context.dp(5f), context.dp(15f))
                compoundDrawablePadding = context.dp(2f)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.menu_text_size).toFloat())
                setLines(2)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }
        }
        return object : RecyclerView.ViewHolder(view) {}

    }
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = rows[position]
        val view = holder.itemView as TextView
        view.text = row.title
        if (row.heading) return
        // y5.a installs v5.c on binding, with its 300ms per-view debounce.
        var previousClick: Long? = null
        view.setOnClickListener {
            val now = SystemClock.elapsedRealtime()
            val previous = previousClick
            if (previous == null || kotlin.math.abs(now - previous) > 300) {
                previousClick = now
                val position = holder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onClick(position)
            }
        }
        val entry = OriginalMenu.entries[row.id] ?: return
        val icon = entry.drawable(context)?.mutate()?.apply {
            val size = context.resources.getDimensionPixelSize(R.dimen.menu_icon_size)
            setBounds(0, 0, size, size)
            setTint(settingsColor(context, R.attr.viaSubtleColor, 0xff444444.toInt()))
        }
        view.setCompoundDrawables(null, icon, null, null)
    }
}

/** i8.w source context-menu visibility page. */
class ContextMenuSettingsFragment : SettingsListFragment() {
    private lateinit var behavior: BehaviorPreferences
    private lateinit var rows: SettingsRowsAdapter
    private val entries = listOf(
        0 to R.string.action_open_in_background, 1 to R.string.action_open_in_new,
        2 to R.string.action_view_image, 6 to R.string.action_save_img, 38 to R.string.action_download_image,
        33 to R.string.share_image, 7 to R.string.search_by_image, 8 to R.string.action_pic_mode,
        22 to R.string.action_page_info, 23 to R.string.action_mark, 29 to R.string.action_copy_link_text,
        34 to R.string.scan_qr_code, 3 to R.string.action_copy, 31 to R.string.action_share,
    )
    override fun configureToolbar(toolbar: SettingsToolbar) {
        toolbar.setTitle(R.string.customize_context_menu)
        toolbar.addAction(null, R.string.action_reset) { resetContextMenus() }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        behavior = BehaviorPreferences(BrowserPreferences(requireContext()))
        rows = SettingsRowsAdapter { row -> behavior.setContextMenuVisible(row.id, !behavior.contextMenuVisible(row.id)); bindRows() }
        list.itemAnimator = null; list.adapter = rows; bindRows()
    }
    private fun bindRows() = rows.submit(entries.map { (id, label) -> SettingsToggleRow(id, getString(label), checked = behavior.contextMenuVisible(id)) })
    private fun resetContextMenus() = ViaDialog(requireActivity()).title(R.string.reset_to_default_settings).message(R.string.dialog_sure)
        .positive(R.string.action_reset) { _, _ -> behavior.resetContextMenus(); bindRows() }.negative(android.R.string.cancel).show()
}
