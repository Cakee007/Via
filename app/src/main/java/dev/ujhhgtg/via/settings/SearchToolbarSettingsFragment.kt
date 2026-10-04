package dev.ujhhgtg.via.settings

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.search.SearchProvider
import dev.ujhhgtg.via.search.SearchProviders
import dev.ujhhgtg.via.ui.SwipeBackLayout
import dev.ujhhgtg.via.ui.dp

internal class SearchToolbarRow(id: Int, title: String, summary: String?, checked: Boolean, disabled: Boolean) :
    SettingsToggleRow(id, title, summary, checked, disabled)

/** ib.r/t: engine label, optional default description, drag grip and trailing checkbox. */
internal class SearchToolbarRowView(context: Context) : RelativeLayout(context) {
    var onDrag: (() -> Unit)? = null
    private val title = TextView(context).apply {
        id = generateViewId(); maxLines = 1; setLines(1); ellipsize = TextUtils.TruncateAt.END
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
        setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, 0xff000000.toInt()))
        textDirection = TEXT_DIRECTION_LOCALE; typeface = BrowserPreferences(context).selectedTypeface()
    }
    private val summary = TextView(context).apply {
        maxLines = 3; ellipsize = TextUtils.TruncateAt.END; textDirection = TEXT_DIRECTION_LOCALE
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_summary_size).toFloat())
        setTextColor(settingsColor(context, R.attr.viaSecondaryTextColor, 0xff444444.toInt()))
        typeface = BrowserPreferences(context).selectedTypeface()
    }
    private val checkbox = CheckBox(context).apply {
        id = generateViewId(); isClickable = false; isFocusable = false
        context.obtainStyledAttributes(intArrayOf(R.attr.viaChoiceIndicator)).let { try { buttonDrawable = it.getDrawable(0) } finally { it.recycle() } }
    }
    private val grip = ImageView(context).apply {
        id = generateViewId(); minimumWidth = context.dp(48f)
        setImageResource(R.drawable.drag_handle)
        setColorFilter(settingsColor(context, R.attr.viaSubtleColor, 0xff000000.toInt()))
        setPaddingRelative(context.dp(13f), 0, context.dp(13f), 0)
        setOnTouchListener { _, event -> if (event.action == MotionEvent.ACTION_DOWN && alpha == 1f) onDrag?.invoke(); true }
    }
    init {
        setPadding(context.dp(16f), context.dp(20f), context.dp(16f), context.dp(20f)); background = settingsRipple(context)
        addView(title, LayoutParams(-1, -2).apply { addRule(ALIGN_PARENT_START); addRule(START_OF, grip.id) })
        addView(summary, LayoutParams(-1, -2).apply { addRule(ALIGN_PARENT_START); addRule(START_OF, grip.id); addRule(BELOW, title.id) })
        addView(grip, LayoutParams(-2, -2).apply { addRule(START_OF, checkbox.id); addRule(CENTER_VERTICAL); marginEnd = context.dp(16f) })
        addView(checkbox, LayoutParams(-2, context.dp(20f)).apply { addRule(ALIGN_PARENT_END); addRule(CENTER_VERTICAL) })
    }
    fun bind(item: SearchToolbarRow) {
        title.text = item.title; summary.text = item.summary; summary.visibility = if (item.summary.isNullOrEmpty()) GONE else VISIBLE
        checkbox.isChecked = item.checked
        listOf(title, summary, checkbox, grip).forEach { it.alpha = if (item.disabled) .5f else 1f }
        isEnabled = !item.disabled
    }
}

/** hb.i6: search toolbar enable flag, mandatory default engine and persisted drag order. */
class SearchToolbarSettingsFragment : SettingsListFragment() {
    private lateinit var preferences: BrowserPreferences
    private lateinit var rows: SettingsRowsAdapter
    private val engines = mutableListOf<SearchProvider>()
    private lateinit var rangeSelection: SettingsRangeSelection
    private val excluded = mutableSetOf<Int>()
    private val enabled get() = preferences.appFlags and 32768 == 0

    override fun configureToolbar(toolbar: SettingsToolbar) = toolbar.setTitle(R.string.search_toolbar)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = BrowserPreferences(requireContext())
        excluded += preferences.searchToolBarDisabled.orEmpty().split(',').mapNotNull(String::toIntOrNull)
        val order = preferences.searchToolBarOrder.split(',').mapNotNull(String::toIntOrNull)
        BrowserDatabase(requireContext()).use { db ->
            engines += SearchProviders(requireContext(), preferences, db).list().sortedBy { order.indexOf(it.id).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
        }
        rows = SettingsRowsAdapter { row ->
            if (row is SearchToolbarRow) {
                if (row.id != preferences.searchMode) { setChecked(row.id, !row.checked); persistSelection(); bindRows() }
            } else if (row is SettingsToggleRow) { preferences.appFlags = preferences.appFlags xor 32768; bindRows() }
        }
        val drag = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            private var changed = false
            override fun isLongPressDragEnabled() = false
            override fun onMove(recyclerView: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = source.bindingAdapterPosition; val to = target.bindingAdapterPosition
                if (from < 2 || to < 2 || from == to) return false
                engines.add(to - 2, engines.removeAt(from - 2)); rows.move(from, to); changed = true
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) viewHolder?.itemView?.setBackgroundColor(0x30808080)
                if (actionState == ItemTouchHelper.ACTION_STATE_IDLE && changed) {
                    changed = false; preferences.searchToolBarOrder = engines.joinToString(",") { it.id.toString() }
                }
            }
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder); viewHolder.itemView.background = settingsRipple(requireContext())
            }
        })
        rows.onStartDrag = { drag.startDrag(it) }
        rangeSelection = SettingsRangeSelection(requireContext(),
            selectable = { it >= 2 && enabled },
            checked = { (rows.rowAt(it) as? SearchToolbarRow)?.checked == true },
            update = { position, checked ->
                (rows.rowAt(position) as? SearchToolbarRow)?.let { row ->
                    if (row.id != preferences.searchMode && row.checked != checked) { setChecked(row.id, checked); bindRows() }
                }
            }, changed = { active -> (this.view as? SwipeBackLayout)?.setGestureEnabled(!active); if (!active) persistSelection() })
        rows.onLongClick = { _, row ->
            if (row is SearchToolbarRow) rangeSelection.start(engines.indexOfFirst { it.id == row.id } + 2) else false
        }
        list.adapter = rows; drag.attachToRecyclerView(list); list.addOnItemTouchListener(rangeSelection)
        bindRows()
    }
    private fun setChecked(id: Int, checked: Boolean) { if (checked) excluded.remove(id) else excluded.add(id) }
    private fun persistSelection() { preferences.searchToolBarDisabled = engines.filter { it.id != preferences.searchMode && it.id in excluded }.joinToString(",") { it.id.toString() } }
    private fun bindRows() = rows.submit(buildList {
        add(SettingsToggleRow(1, getString(R.string.enable_search_toobar), checked = enabled))
        add(SettingsHeadingRow(getString(R.string.search_engines)))
        engines.forEach { add(SearchToolbarRow(it.id, it.name, if (it.id == preferences.searchMode) getString(R.string.default_set) else null,
            it.id == preferences.searchMode || it.id !in excluded, !enabled)) }
    })
    override fun onDestroyView() { rangeSelection.finish(); super.onDestroyView() }
}
