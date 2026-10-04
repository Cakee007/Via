package dev.ujhhgtg.via.browser

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

/** ua.i: selected script actions and the script's registered GM commands. */
class PageScriptActionsFragment : Fragment() {
    private val owner get() = requireParentFragment() as PageScriptsDialogFragment
    private lateinit var title: TextView
    private val rows = ActionsAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        val backId = View.generateViewId()
        title = pageScriptsTitle(context).apply {
            id = View.generateViewId()
            setPaddingRelative(context.pageScriptsUnits(6), context.pageScriptsUnits(16), context.pageScriptsUnits(16), context.pageScriptsUnits(16))
        }
        val back = ImageView(context).apply {
            id = backId; setSkinImageResource(R.drawable.favorite_back)
            setColorFilter(settingsColor(context, R.attr.viaSubtleColor, 0)); contentDescription = getString(R.string.navigation_up)
            setBackgroundResource(R.drawable.rounded_rect_ripple)
            val padding = context.pageScriptsUnits(13); setPaddingRelative(padding, padding, padding, padding)
            setOnClickListener { owner.showPage(0) }
        }
        val list = SettingsRecyclerView(context).apply { layoutManager = PageScriptsLayoutManager(context, 4); adapter = rows }
        return RelativeLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            addView(title, RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.END_OF, backId) })
            addView(list, RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.BELOW, title.id) })
            addView(back, RelativeLayout.LayoutParams(context.dp(48f), -2).apply { addRule(RelativeLayout.ALIGN_TOP, title.id); addRule(RelativeLayout.ALIGN_BOTTOM, title.id) })
        }
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        owner.model.selected.observe(viewLifecycleOwner) { selected ->
            // ua.i.X2 overwrites Y2's initial “Scripts” title with the selected name.
            title.text = selected.script.name
            bindActions()
        }
        owner.model.menus.observe(viewLifecycleOwner) { bindActions() }
        owner.model.enabledChange.observe(viewLifecycleOwner) { enabled ->
            if (rows.values.isNotEmpty()) {
                rows.values[0] = Action(2, getString(if (enabled) R.string.enabled else R.string.disabled))
                rows.notifyItemChanged(0)
            }
        }
    }
    override fun onResume() { super.onResume(); view?.requestLayout() }

    /** ua.i.a3 is called for a selection or new command map, not for an enable-only change. */
    private fun bindActions() {
        val enabled = owner.model.selected.value?.script?.enabled == true
        val source = owner.model.url.orEmpty()
        val host = if (enabled && pageScriptsNetworkUrl(source)) AddressTitleFormatter.domain(source) else ""
        rows.values = buildList {
            add(Action(2, getString(if (enabled) R.string.enabled else R.string.disabled)))
            add(Action(1, getString(R.string.action_edit)))
            if (enabled && host.length > 3) add(Action(4, getString(R.string.exclude_domain, host)))
            add(Action(3, getString(R.string.action_view_in_settings)))
            owner.model.selectedCommands().forEach { add(Action(-1, it)) }
        }.toMutableList()
        rows.notifyDataSetChanged()
    }

    private inner class ActionsAdapter : RecyclerView.Adapter<ActionHolder>() {
        var values = mutableListOf<Action>()
        override fun getItemCount() = values.size
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): ActionHolder = ActionHolder(TextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(-1, -2)
            val padding = context.dp(16f); setPaddingRelative(padding, padding, padding, padding)
            setBackgroundResource(R.drawable.flat_ripple)
            setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, 0)); gravity = Gravity.CENTER_VERTICAL
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
            pageScriptsFadingText()
        })
        override fun onBindViewHolder(holder: ActionHolder, position: Int) {
            val action = values[position]
            holder.text.text = action.label
            holder.text.setPageScriptsRowClick { if (holder.bindingAdapterPosition != RecyclerView.NO_POSITION) owner.perform(action.id, action.label) }
        }
    }
    private data class Action(val id: Int, val label: String)
    private class ActionHolder(val text: TextView) : RecyclerView.ViewHolder(text)
}
