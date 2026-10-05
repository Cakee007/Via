package dev.ujhhgtg.via.settings

import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.skins.SkinResources
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import dev.ujhhgtg.via.ui.dp
import java.util.Locale

/** jb.k5: global percentage preview, force zoom and complete per-site exception management. */
class WebTextSizeSettingsFragment : SettingsListFragment() {
    private lateinit var repository: TextZoomRepository
    private lateinit var rows: TextZoomRowsAdapter
    private lateinit var globalSample: TextSizeSampleRow

    override fun configureToolbar(toolbar: SettingsToolbar) = toolbar.setTitle(R.string.size)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = TextZoomRepository(requireContext())
        globalSample = TextSizeSampleRow(1, getString(R.string.size), "%d%%", TextZoomDialogs.sample(requireContext()),
            repository.global, 50, 200, 5, 100)
        rows = TextZoomRowsAdapter({ row ->
            when (row) {
                is AddExceptionRow -> addException()
                is ExceptionRow -> TextZoomDialogs.editException(requireActivity(), repository, row.domain, row.percent, true, ::refreshExceptions)
                is SettingsToggleRow -> { repository.forceZoom = !row.checked; bindRows() }
            }
        }, { repository.global = it })
        list.adapter = rows
        bindRows()
    }

    private fun bindRows() {
        val exceptions = repository.exceptions()
        rows.submit(buildList {
            // jb.k5.b leaves ib.u unchanged when exception/toggle rows change.
            add(globalSample)
            add(SettingsToggleRow(3, getString(R.string.force_enable_zoom), checked = repository.forceZoom))
            if (!dev.ujhhgtg.via.engine.Engines.backend.capabilities.perPageTextZoom) return@buildList
            add(AddExceptionRow(getString(R.string.add_site_exception)))
            if (exceptions.isNotEmpty()) {
                add(SettingsHeadingRow(getString(R.string.exception)))
                exceptions.keys.sortedWith(TextZoomRepository.domainOrder).forEach { domain ->
                    add(ExceptionRow(domain, exceptions.getValue(domain)))
                }
            }
        })
    }

    private fun refreshExceptions() {
        // jb.k5.q3 replaces the model; its DiffUtil preserves the visible ib.u control state.
        globalSample = TextSizeSampleRow(1, getString(R.string.size), "%d%%", TextZoomDialogs.sample(requireContext()),
            repository.global, 50, 200, 5, 100)
        bindRows()
    }

    private fun addException() {
        ViaDialog(requireActivity()).title(R.string.add_site_exception).message(R.string.text_size_site_exception_add)
            .input("", "www.example.com", 1)
            .positive(android.R.string.ok) { _, result ->
                val value = result.edit?.firstOrNull().orEmpty()
                if (value.isNotEmpty()) TextZoomDialogs.editException(requireActivity(), repository,
                    repository.encodeExceptionInput(value), repository.global, false, ::refreshExceptions)
            }.negative(android.R.string.cancel).show()
    }

    private class AddExceptionRow(title: String) : SettingsRow(2, title)
    private class ExceptionRow(val domain: String, val percent: Int) :
        SettingsRow(domain.hashCode(), domain, String.format(Locale.ROOT, "%d%%", percent))

    /** a6.f is the only specialized row; all other jb.k5 rows use the common original renderers. */
    private class TextZoomRowsAdapter(private val click: (SettingsRow) -> Unit, changed: (Int) -> Unit) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val common = SettingsRowsAdapter(click).apply { onTextSizeChanged = changed }
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
        override fun getItemViewType(position: Int) = if (common.rowAt(position) is AddExceptionRow) 100 else common.getItemViewType(position)
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder {
            if (type != 100) return common.onCreateViewHolder(parent, type)
            val view = TextView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(-1, -2)
                setPadding(context.dp(16f), context.dp(20f), context.dp(16f), context.dp(20f))
                setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, Color.BLACK))
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
            return object : RecyclerView.ViewHolder(view) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val row = common.rowAt(position) ?: return
            if (row is AddExceptionRow) (holder.itemView as TextView).apply { text = row.title; setOnClickListener { click(row) } }
            else common.onBindViewHolder(holder, position)
        }
        fun submit(rows: List<SettingsRow>) = common.submit(rows)
    }
}
