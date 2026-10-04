package dev.ujhhgtg.via.sync

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.SettingsRow
import dev.ujhhgtg.via.settings.SettingsRowsAdapter
import dev.ujhhgtg.via.settings.controlRipple
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.settings.settingsRipple
import dev.ujhhgtg.via.skins.setSkinImageResource
import dev.ujhhgtg.via.ui.dp

internal class SyncAccountRow(title: String, summary: String, val configured: Boolean) : SettingsRow(2, title, summary) {
    override fun equals(other: Any?) = super.equals(other) && other is SyncAccountRow && configured == other.configured
    override fun hashCode() = 31 * super.hashCode() + configured.hashCode()
}

/** nb.n uses a6.c only for the account row; the remaining rows are a6.s/p/m. */
internal class SyncSettingsAdapter(click: (SettingsRow) -> Unit, private val sync: () -> Unit,
    private val operations: () -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val common = SettingsRowsAdapter(click)
    private val click = click
    init {
        common.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = notifyDataSetChanged()
            override fun onItemRangeChanged(start: Int, count: Int, payload: Any?) = notifyItemRangeChanged(start, count, payload)
            override fun onItemRangeInserted(start: Int, count: Int) = notifyItemRangeInserted(start, count)
            override fun onItemRangeRemoved(start: Int, count: Int) = notifyItemRangeRemoved(start, count)
            override fun onItemRangeMoved(from: Int, to: Int, count: Int) = notifyItemMoved(from, to)
        })
    }
    override fun getItemCount() = common.itemCount
    override fun getItemViewType(position: Int) = if (common.rowAt(position) is SyncAccountRow) 100 else common.getItemViewType(position)
    override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder = if (type == 100)
        object : RecyclerView.ViewHolder(SyncAccountView(parent.context).apply { layoutParams = RecyclerView.LayoutParams(-1, -2) }) {}
        else common.onCreateViewHolder(parent, type)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = common.rowAt(position) ?: return
        if (row is SyncAccountRow) (holder.itemView as SyncAccountView).bind(row, { click(row) }, sync, operations)
        else common.onBindViewHolder(holder, position)
    }
    fun submit(rows: List<SettingsRow>) = common.submit(rows)
}

/** a6.b: independently clickable 48dp reload control aligned with the two text lines. */
private class SyncAccountView(context: Context) : RelativeLayout(context) {
    private val title = label(R.dimen.settings_row_title_size, R.attr.viaPrimaryTextColor).apply { id = generateViewId(); setSingleLine() }
    private val summary = label(R.dimen.settings_row_summary_size, R.attr.viaSecondaryTextColor).apply { id = generateViewId(); maxLines = 3 }
    private val sync = ImageView(context).apply {
        id = generateViewId(); setSkinImageResource(R.drawable.reload, "ic_reload")
        setColorFilter(settingsColor(context, R.attr.viaAccentColor, 0xff6f8de1.toInt()))
        setPadding(context.dp(8f), context.dp(8f), context.dp(8f), context.dp(8f))
        maxHeight = context.dp(48f); background = controlRipple(context)
        contentDescription = context.getString(R.string.sync)
    }
    init {
        val horizontal = context.dp(16f)
        setPaddingRelative(horizontal, context.dp(20f), horizontal / 5, context.dp(20f))
        background = settingsRipple(context)
        addView(title, LayoutParams(-1, -2).apply { addRule(ALIGN_PARENT_START); addRule(START_OF, sync.id) })
        addView(summary, LayoutParams(-1, -2).apply { addRule(ALIGN_PARENT_START); addRule(START_OF, sync.id); addRule(BELOW, title.id) })
        addView(sync, LayoutParams(context.dp(48f), -2).apply {
            addRule(ALIGN_PARENT_END); addRule(ALIGN_TOP, title.id); addRule(ALIGN_BOTTOM, summary.id); marginStart = context.dp(12f)
        })
    }
    private fun label(size: Int, color: Int) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(size).toFloat())
        setTextColor(settingsColor(context, color, 0xff000000.toInt()))
        typeface = BrowserPreferences(context).selectedTypeface()
        ellipsize = TextUtils.TruncateAt.END; textDirection = View.TEXT_DIRECTION_LOCALE
    }
    fun bind(row: SyncAccountRow, configure: () -> Unit, synchronize: () -> Unit, operations: () -> Unit) {
        title.text = row.title; summary.text = row.summary
        sync.isEnabled = row.configured; sync.alpha = if (row.configured) 1f else .5f
        setOnClickListener { configure() }; setOnLongClickListener { false }
        sync.setOnClickListener { synchronize() }
        sync.setOnLongClickListener { operations(); true }
    }
}
