package dev.ujhhgtg.via.browser

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.settings.SettingsRecyclerView
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.skins.setSkinImageResource
import dev.ujhhgtg.via.ui.dp

/** ua.t: matching script metadata with independently clickable enable checkboxes. */
class PageScriptsListFragment : Fragment() {
    private val owner get() = requireParentFragment() as PageScriptsDialogFragment
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private val rows = MatchAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        val buttonId = View.generateViewId()
        val title = pageScriptsTitle(context).apply {
            id = View.generateViewId()
            val padding = context.pageScriptsUnits(16); setPaddingRelative(padding, padding, padding, padding)
            val domain = AddressTitleFormatter.domain(owner.model.url)
            text = if (domain.isEmpty()) getString(R.string.settings_script) else getString(R.string.title_scripts_for_site, domain)
        }
        val add = ImageView(context).apply {
            id = buttonId; setSkinImageResource(R.drawable.plus)
            setColorFilter(settingsColor(context, R.attr.viaSubtleColor, 0))
            contentDescription = getString(R.string.add_script); setBackgroundResource(R.drawable.rounded_rect_ripple)
            val padding = context.pageScriptsUnits(13); setPaddingRelative(padding, padding, padding, padding)
            setOnClickListener { owner.perform(0) }
        }
        list = SettingsRecyclerView(context).apply { layoutManager = PageScriptsLayoutManager(context, 5); adapter = rows }
        empty = TextView(context).apply {
            text = "¯\\_(ツ)_/¯"; contentDescription = getString(R.string.empty_hint)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_empty_state_size).toFloat())
            setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, 0)); gravity = Gravity.CENTER; visibility = View.GONE
        }
        return RelativeLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            addView(title, RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.START_OF, buttonId) })
            addView(add, RelativeLayout.LayoutParams(context.pageScriptsUnits(48), -2).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END); addRule(RelativeLayout.ALIGN_TOP, title.id); addRule(RelativeLayout.ALIGN_BOTTOM, title.id)
            })
            addView(list, RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.BELOW, title.id) })
            addView(empty, RelativeLayout.LayoutParams(-1, context.pageScriptsUnits(100)).apply { addRule(RelativeLayout.BELOW, title.id) })
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        owner.model.scripts.observe(viewLifecycleOwner) {
            rows.values = it; rows.notifyDataSetChanged()
            empty.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
            list.visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
        }
        owner.model.enabledChange.observe(viewLifecycleOwner) { enabled ->
            val id = owner.model.selected.value?.script?.id
            val index = rows.values.indexOfFirst { entry -> entry.script.id == id }
            if (index >= 0) {
                // ua.t.c3 also updates freshly loaded metadata after recreation.
                rows.values[index].script = rows.values[index].script.copy(enabled = enabled)
                rows.notifyItemChanged(index)
            }
        }
    }
    override fun onResume() { super.onResume(); view?.requestLayout() }

    private inner class MatchAdapter : RecyclerView.Adapter<MatchHolder>() {
        var values = emptyList<PageScriptEntry>()
        override fun getItemCount() = values.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = MatchHolder(ScriptRow(parent.context))
        override fun onBindViewHolder(holder: MatchHolder, position: Int) {
            val entry = values[position]
            holder.row.title.text = entry.script.name
            holder.row.setPageScriptsRowClick { if (holder.bindingAdapterPosition != RecyclerView.NO_POSITION) owner.select(entry) }
            holder.row.enabled.setOnCheckedChangeListener(null)
            holder.row.enabled.isChecked = entry.script.enabled
            holder.row.enabled.setOnCheckedChangeListener { _, enabled -> owner.model.setEnabled(entry, enabled) }
        }
    }
    private class MatchHolder(val row: ScriptRow) : RecyclerView.ViewHolder(row)

    /** a6.o followed by ua.t$a.q: subtitle GONE, all padding 16dp, 24dp checkbox hit expansion. */
    private class ScriptRow(context: Context) : RelativeLayout(context) {
        val title = TextView(context).apply {
            id = View.generateViewId()
            setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, 0))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
            setLines(1); maxLines = 1; pageScriptsFadingText()
        }
        private val subtitle = TextView(context).apply { id = View.generateViewId(); visibility = View.GONE }
        val enabled = CheckBox(context).apply {
            id = View.generateViewId(); isClickable = true; isFocusable = true
            context.obtainStyledAttributes(intArrayOf(R.attr.viaChoiceIndicator)).let { try { buttonDrawable = it.getDrawable(0) } finally { it.recycle() } }
        }
        init {
            layoutParams = RecyclerView.LayoutParams(-1, -2)
            val padding = context.pageScriptsUnits(16); setPaddingRelative(padding, padding, padding, padding)
            setBackgroundResource(R.drawable.flat_ripple)
            addView(title, LayoutParams(-1, -2).apply { addRule(ALIGN_PARENT_START); addRule(START_OF, enabled.id) })
            addView(subtitle, LayoutParams(-1, -2).apply { addRule(ALIGN_PARENT_START); addRule(START_OF, enabled.id); addRule(BELOW, title.id) })
            addView(enabled, LayoutParams(-2, context.dp(20f)).apply { addRule(ALIGN_PARENT_END); addRule(CENTER_VERTICAL); marginStart = context.dp(12f) })
            post {
                val bounds = Rect(); enabled.getHitRect(bounds); val extra = context.dp(24f); bounds.inset(-extra, -extra)
                touchDelegate = TouchDelegate(bounds, enabled)
            }
        }
    }
}
