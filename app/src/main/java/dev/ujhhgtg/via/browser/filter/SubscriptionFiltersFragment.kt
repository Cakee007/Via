package dev.ujhhgtg.via.browser.filter

import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.SettingsListFragment
import dev.ujhhgtg.via.settings.SettingsToolbar
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.ui.dp
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import java.io.File
import java.util.concurrent.Executors

/** z7.v0 and b6.f.h: nonempty file lines with a separate full-line preview. */
class SubscriptionFiltersFragment : SettingsListFragment() {
    private val worker = Executors.newSingleThreadExecutor()
    private var lines = emptyList<String>()

    override fun configureToolbar(toolbar: SettingsToolbar) {
        toolbar.setTitle(arguments?.getString("title") ?: getString(R.string.action_preview))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = lines.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = object : RecyclerView.ViewHolder(
                TextView(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(-1, -2)
                    setPadding(context.dp(16f), context.dp(16f), context.dp(16f), context.dp(16f))
                    setBackgroundResource(R.drawable.flat_ripple)
                    setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, 0xff000000.toInt()))
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
                    if (Build.VERSION.SDK_INT >= 35) isLocalePreferredLineHeightForMinimumUsed = false
                    gravity = Gravity.CENTER_VERTICAL
                    ellipsize = null
                    setSingleLine(true)
                    setFadingEdgeLength(context.dp(24f))
                    isHorizontalFadingEdgeEnabled = true
                    textDirection = View.TEXT_DIRECTION_LOCALE
                    typeface = BrowserPreferences(context).selectedTypeface()
                }
            ) {}
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                (holder.itemView as TextView).text = lines[position]
                holder.itemView.setOnClickListener {
                    ViaDialog(requireActivity()).title(R.string.action_preview).message(lines[holder.bindingAdapterPosition])
                        .positive(android.R.string.ok).show()
                }
            }
        }
        list.adapter = adapter
        val host = requireActivity()
        val path = arguments?.getString("path")
        worker.execute {
            val content = path?.let { runCatching { File(it).readLines().filter(String::isNotEmpty) }.getOrDefault(emptyList()) }.orEmpty()
            host.runOnUiThread {
                if (this.view == null) return@runOnUiThread
                lines = content
                adapter.notifyDataSetChanged()
                showEmptyState(lines.isEmpty())
            }
        }
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    companion object {
        fun create(path: String?, title: String?) = SubscriptionFiltersFragment().apply {
            arguments = Bundle().apply {
                putString("path", path)
                if (!title.isNullOrEmpty()) putString("title", title)
            }
        }
    }
}
