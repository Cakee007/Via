package dev.ujhhgtg.via.sites

import android.graphics.Color
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.SettingsRow
import dev.ujhhgtg.via.settings.SettingsRowsAdapter
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.settings.settingsRipple
import dev.ujhhgtg.via.skins.SkinResources
import dev.ujhhgtg.via.ui.dp

internal class AddSiteExceptionRow(title: String) : SettingsRow(2, title)
internal class SavedSiteRow(val domain: String) : SettingsRow(domain.hashCode(), domain)

/** a6.f (add exception) and b6.c.h/b6.f.h (all sites); ordinary cells retain a6.s/p/m. */
internal class SiteSettingsRowsAdapter(private val click: (SettingsRow) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val common = SettingsRowsAdapter(click)
    var onLongClick: ((View, SettingsRow) -> Boolean)? = null
        set(value) { field = value; common.onLongClick = value }
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
    override fun getItemViewType(position: Int) = when (common.rowAt(position)) {
        is AddSiteExceptionRow -> 100
        is SavedSiteRow -> 101
        else -> common.getItemViewType(position)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType < 100) return common.onCreateViewHolder(parent, viewType)
        return object : RecyclerView.ViewHolder(TextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(-1, -2)
            val vertical = if (viewType == 100) 20f else 16f
            setPadding(context.dp(16f), context.dp(vertical), context.dp(16f), context.dp(vertical))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
            setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, Color.BLACK))
            typeface = BrowserPreferences(context).selectedTypeface()
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END; textDirection = View.TEXT_DIRECTION_LOCALE
            background = settingsRipple(context)
            if (viewType == 100) {
                compoundDrawablePadding = context.dp(18f)
                val icon = SkinResources.drawable(context, R.drawable.plus)?.mutate()?.apply {
                    setTint(currentTextColor); setBounds(0, 0, context.dp(18f), context.dp(18f))
                }
                setCompoundDrawablesRelative(icon, null, null, null)
            }
        }) {}
    }
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = common.rowAt(position) ?: return
        if (getItemViewType(position) < 100) common.onBindViewHolder(holder, position)
        else (holder.itemView as TextView).apply {
            text = row.title
            setOnClickListener { click(row) }
            setOnLongClickListener { onLongClick?.invoke(this, row) ?: false }
        }
    }
    fun submit(rows: List<SettingsRow>) = common.submit(rows)
}
