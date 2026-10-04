package dev.ujhhgtg.via.passwords

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.skins.SkinResources
import dev.ujhhgtg.via.skins.setSkinImageResource
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.search.TextChanges
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.ui.dialog.ViaDialogFragment
import dev.ujhhgtg.via.ui.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.debounce

/** va.c0 / layout j / xa.b: searchable password metadata, grouped by domain. */
class PasswordPickerFragment : ViaDialogFragment() {
    private lateinit var list: RecyclerView
    private lateinit var title: TextView
    private lateinit var search: EditText
    private lateinit var button: ImageView
    private lateinit var empty: TextView
    private var initialScroll = false
    private var rows: List<Any> = emptyList()
    private val adapter = PasswordRows()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.password_picker_dialog, container, false)

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    override fun onViewCreated(view: View, state: Bundle?) {
        title = view.findViewById<TextView>(R.id.password_picker_title).apply {
            setText(R.string.passwords)
            typeface = Typeface.create(BrowserPreferences(context).selectedTypeface(), Typeface.BOLD)
        }
        search = view.findViewById(R.id.password_picker_search)
        search.typeface = Typeface.create(BrowserPreferences(requireContext()).selectedTypeface(), Typeface.BOLD)
        button = view.findViewById(R.id.password_picker_search_button)
        button.setSkinImageResource(R.drawable.search)
        button.setOnClickListener { setSearching(search.visibility != View.VISIBLE) }
        empty = view.findViewById<TextView>(R.id.password_picker_empty).apply {
            text = "¯\\_(ツ)_/¯"; contentDescription = getString(R.string.empty_hint)
            typeface = BrowserPreferences(context).selectedTypeface()
        }
        list = view.findViewById<RecyclerView>(android.R.id.list).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@PasswordPickerFragment.adapter
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isVerticalScrollBarEnabled = false
        }
        viewLifecycleOwner.lifecycleScope.launch {
            TextChanges(search).drop(1).debounce(100).collect { load(it) }
        }
        load("")
    }

    override fun onStart() {
        super.onStart()
        val metrics = resources.displayMetrics
        val shorter = minOf(metrics.widthPixels, metrics.heightPixels)
        dialog?.window?.setLayout(minOf(shorter * 4 / 5, requireContext().dp(450f)), minOf(shorter * 4 / 5, requireContext().dp(600f)))
    }

    private fun load(query: String) {
        val repository = PasswordRepository.get(requireContext())
        repository.async({ repository.list(query).sortedWith { a, b -> compareDomains(a.name, b.name) } }) { result ->
            if (view == null) return@async
            val records = result.getOrDefault(emptyList())
            rows = buildList {
                var name: String? = null
                records.forEach { record ->
                    if (name != record.name) { name = record.name; add(record.name) }
                    add(record)
                }
            }
            adapter.notifyDataSetChanged()
            empty.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
            list.visibility = if (records.isEmpty()) View.GONE else View.VISIBLE
            if (!initialScroll && records.isNotEmpty()) {
                val domain = PasswordCsv.host(arguments?.getString("url").orEmpty())
                var index = rows.indexOf(domain)
                if (index < 0) {
                    val labels = domain.split('.')
                    val base = if (labels.size < 3) domain else labels.takeLast(if (labels.last() in listOf("com", "net", "org", "gov", "co", "edu")) 2 else 3).joinToString(".")
                    if (base != domain) index = rows.indexOfFirst { it is String && it.contains(base) }
                }
                if (index >= 0) list.scrollToPosition(index)
            }
            initialScroll = true
        }
    }

    private fun setSearching(enabled: Boolean) {
        val input = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (enabled) {
            title.animate().alpha(0f).setDuration(100).withEndAction { title.visibility = View.INVISIBLE }.start()
            search.alpha = 0f; search.visibility = View.VISIBLE
            search.animate().alpha(1f).setDuration(100).start()
            button.setSkinImageResource(R.drawable.close); button.setContentDescription(getString(android.R.string.cancel))
            search.requestFocus(); input.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
        } else {
            search.setText(""); input.hideSoftInputFromWindow(search.windowToken, 0)
            title.visibility = View.VISIBLE; title.animate().alpha(1f).setDuration(100).start()
            search.animate().alpha(0f).setDuration(100).withEndAction { search.visibility = View.GONE }.start()
            button.setSkinImageResource(R.drawable.search); button.contentDescription = getString(R.string.search)
        }
    }


    private inner class PasswordRows : RecyclerView.Adapter<Row>() {
        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = if (rows[position] is String) 0 else 1
        override fun onCreateViewHolder(parent: ViewGroup, kind: Int): Row = Row(TextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(-1, -2)
            textDirection = View.TEXT_DIRECTION_LOCALE
            typeface = Typeface.create(BrowserPreferences(context).selectedTypeface(), if (kind == 0) Typeface.BOLD else Typeface.NORMAL)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(if (kind == 0) R.dimen.settings_row_summary_size else R.dimen.settings_row_title_size).toFloat())
            setTextColor(settingsColor(context, if (kind == 0) R.attr.viaAccentColor else R.attr.viaPrimaryTextColor, 0xff000000.toInt()))
            setPadding(context.dp(16f), context.dp(12f), context.dp(16f), context.dp(if (kind == 0) 4f else 12f))
            if (kind == 1) {
                gravity = Gravity.CENTER_VERTICAL; setSingleLine(); ellipsize = null
                isHorizontalFadingEdgeEnabled = true; setFadingEdgeLength(context.dp(24f))
                compoundDrawablePadding = context.dp(16f)
                background = ContextCompat.getDrawable(context, R.drawable.flat_ripple)
            }
        })
        override fun onBindViewHolder(holder: Row, position: Int) {
            val item = rows[position]
            if (item is String) holder.text.text = item
            else if (item is PasswordRecord) {
                holder.text.text = item.username
                val context = holder.text.context
                // z8.y0.d: external favicons/<MD5(host)> with the source account fallback.
                val iconFile = dev.ujhhgtg.via.home.HomeIcons.file(context, item.name)
                val icon = iconFile?.let { Drawable.createFromPath(it.path) } ?: SkinResources.drawable(context, R.drawable.globe)?.mutate()?.apply {
                    setTint(settingsColor(context, R.attr.viaSubtleColor, 0xff000000.toInt()))
                }
                icon?.setBounds(0, 0, context.dp(20f), context.dp(20f))
                holder.text.setCompoundDrawablesRelative(icon, null, null, null)
                holder.text.setOnClickListener {
                    parentFragmentManager.setFragmentResult(arguments?.getString("result") ?: RESULT, Bundle().apply { putString("id", item.id) })
                    dismiss()
                }
            }
        }
    }
    private class Row(val text: TextView) : RecyclerView.ViewHolder(text)

    companion object {
        const val RESULT = "password_picker_result"
        fun newInstance(url: String, result: String = RESULT) = PasswordPickerFragment().apply {
            arguments = Bundle().apply { putString("url", url); putString("result", result) }
        }

        /** k8.p compares domain labels from the suffix, preserving source case order. */
        internal fun compareDomains(first: String, second: String): Int {
            var length = first.length; var otherLength = second.length
            var firstStart = length; var otherStart = otherLength
            while (length > 0 && otherLength > 0) {
                val dot = first.lastIndexOf('.', length); val start = dot + 1
                val otherDot = second.lastIndexOf('.', otherLength); val startOther = otherDot + 1
                val count = length - start
                for (index in 0 until minOf(count, otherLength - startOther)) {
                    val difference = first[start + index] - second[startOther + index]
                    if (difference != 0) return difference
                }
                val difference = count - otherLength + startOther
                if (difference != 0) return difference
                length = dot - 1; otherLength = otherDot - 1
                firstStart = start; otherStart = startOther
            }
            return firstStart - otherStart
        }
    }
}
