package dev.ujhhgtg.via.settings

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.TouchDelegate
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.skins.SkinResources
import dev.ujhhgtg.via.browser.script.UserScript
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.dp

internal class ScriptListRow(val script: UserScript, val scriptsEnabled: Boolean) :
    SettingsRow(script.id, script.name, script.version) {
    override fun equals(other: Any?) = super.equals(other) && other is ScriptListRow &&
        script == other.script && scriptsEnabled == other.scriptsEnabled
    override fun hashCode() = 31 * script.hashCode() + scriptsEnabled.hashCode()
}
internal class ScriptAddRow(id: Int, title: String) : SettingsRow(id, title)
internal class ScriptResourceRow(id: Int, title: String, val available: Boolean) : SettingsRow(id, title) {
    override fun equals(other: Any?) = super.equals(other) && other is ScriptResourceRow && available == other.available
    override fun hashCode() = 31 * super.hashCode() + available.hashCode()
}

/** sa.h1 and ta.u's a6.f rows; ordinary rows retain the common a6 renderers. */
internal class ScriptSettingsAdapter(
    private val click: (SettingsRow) -> Unit,
    private val toggle: (UserScript, Boolean) -> Unit = { _, _ -> },
    longClick: ((View, SettingsRow) -> Boolean)? = null,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val common = SettingsRowsAdapter(click).apply { onLongClick = longClick }
    private val longClick = longClick
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
        is ScriptListRow -> 100
        is ScriptAddRow -> 101
        else -> common.getItemViewType(position)
    }
    override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder {
        val context = parent.context
        val view = when (type) {
            100 -> ScriptListView(context)
            101 -> TextView(context).apply {
                setPadding(context.dp(16f), context.dp(20f), context.dp(16f), context.dp(20f))
                setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, 0xff000000.toInt()))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
                typeface = BrowserPreferences(context).selectedTypeface()
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END; textDirection = View.TEXT_DIRECTION_LOCALE
                compoundDrawablePadding = context.dp(18f)
                val icon = SkinResources.drawable(context, R.drawable.plus)?.mutate()?.apply {
                    setTint(currentTextColor); setBounds(0, 0, context.dp(18f), context.dp(18f))
                }
                setCompoundDrawablesRelative(icon, null, null, null)
                background = settingsRipple(context)
            }
            else -> return common.onCreateViewHolder(parent, type)
        }
        view.layoutParams = RecyclerView.LayoutParams(-1, -2)
        return object : RecyclerView.ViewHolder(view) {}
    }
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = common.rowAt(position) ?: return
        when (row) {
            is ScriptListRow -> (holder.itemView as ScriptListView).bind(row, { click(row) },
                { longClick?.invoke(holder.itemView, row) ?: false }, { toggle(row.script, it) })
            is ScriptAddRow -> (holder.itemView as TextView).apply { text = row.title; setOnClickListener { click(row) } }
            else -> {
                common.onBindViewHolder(holder, position)
                // ta.b dims an unavailable resource, but its row remains clickable to download it.
                holder.itemView.alpha = if (row is ScriptResourceRow && !row.available) .5f else 1f
            }
        }
    }
    fun submit(rows: List<SettingsRow>) = common.submit(rows)
}

/** sa.h1 uses an independently clickable checkbox; tapping the text opens ta.u. */
private class ScriptListView(context: Context) : RelativeLayout(context) {
    private val name = label(R.dimen.settings_row_title_size, R.attr.viaPrimaryTextColor).apply {
        id = generateViewId(); setSingleLine()
    }
    private val version = label(R.dimen.settings_row_summary_size, R.attr.viaSecondaryTextColor).apply { maxLines = 3 }
    private val enabled = CheckBox(context).apply {
        id = generateViewId(); isClickable = true; isFocusable = true
        context.obtainStyledAttributes(intArrayOf(R.attr.viaChoiceIndicator)).let {
            try { buttonDrawable = it.getDrawable(0) } finally { it.recycle() }
        }
    }
    init {
        setPadding(context.dp(16f), context.dp(20f), context.dp(16f), context.dp(20f))
        background = settingsRipple(context)
        addView(name, LayoutParams(-1, -2).apply { addRule(ALIGN_PARENT_START); addRule(START_OF, enabled.id) })
        addView(version, LayoutParams(-1, -2).apply { addRule(ALIGN_PARENT_START); addRule(START_OF, enabled.id); addRule(BELOW, name.id) })
        addView(enabled, LayoutParams(-2, context.dp(20f)).apply {
            addRule(ALIGN_PARENT_END); addRule(CENTER_VERTICAL); marginStart = context.dp(12f)
        })
        enabled.post {
            val rect = Rect(); enabled.getHitRect(rect); rect.inset(-context.dp(24f), -context.dp(24f))
            touchDelegate = TouchDelegate(rect, enabled)
        }
    }
    private fun label(size: Int, color: Int) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(size).toFloat())
        setTextColor(settingsColor(context, color, 0xff000000.toInt()))
        typeface = BrowserPreferences(context).selectedTypeface()
        textDirection = View.TEXT_DIRECTION_LOCALE; ellipsize = TextUtils.TruncateAt.END
    }
    fun bind(row: ScriptListRow, click: () -> Unit, longClick: () -> Boolean, toggle: (Boolean) -> Unit) {
        name.text = row.script.name; version.text = row.script.version
        version.visibility = if (row.script.version.isNullOrEmpty()) GONE else VISIBLE
        enabled.setOnCheckedChangeListener(null); enabled.isChecked = row.script.enabled
        enabled.setOnCheckedChangeListener { _, value -> toggle(value) }
        isEnabled = row.scriptsEnabled
        val opacity = if (row.scriptsEnabled && row.script.content.isNotEmpty()) 1f else .5f
        name.alpha = opacity; version.alpha = opacity; enabled.alpha = opacity
        setOnClickListener { click() }; setOnLongClickListener { longClick() }
    }
}
