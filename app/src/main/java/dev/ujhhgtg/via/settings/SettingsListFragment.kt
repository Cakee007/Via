package dev.ujhhgtg.via.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import dev.ujhhgtg.via.common.WindowInsetsHelper

/** o8.g: original u0 RecyclerView body and scrollable inset placement. */
abstract class SettingsListFragment : SettingsPageFragment() {
    protected lateinit var list: SettingsRecyclerView
        private set
    private lateinit var emptyState: android.widget.TextView
    override fun createContent(inflater: LayoutInflater, container: ViewGroup?): View {
        list = SettingsRecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(context)
            itemAnimator = DefaultItemAnimator()
            clipToPadding = false
        }
        emptyState = android.widget.TextView(requireContext()).apply {
            text = "¯\\_(ツ)_/¯"
            contentDescription = getString(dev.ujhhgtg.via.R.string.empty_hint)
            gravity = android.view.Gravity.CENTER
            textSize = 22f
            setTextColor(settingsColor(context, dev.ujhhgtg.via.R.attr.viaSecondaryTextColor, android.graphics.Color.GRAY))
            val padding = (20 * resources.displayMetrics.density + .5f).toInt()
            setPadding(0, padding, 0, padding)
            visibility = View.GONE
            configureEmptyState(this)
        }
        return FrameLayout(requireContext()).apply {
            addView(list, FrameLayout.LayoutParams(-1, -1))
            addView(emptyState, FrameLayout.LayoutParams(-1, -1))
        }
    }
    /** o8.g.c3 / z8.r3.b: full-height empty overlay above the RecyclerView. */
    protected fun showEmptyState(show: Boolean) { emptyState.visibility = if (show) View.VISIBLE else View.GONE }
    protected open fun configureEmptyState(view: android.widget.TextView) = Unit
    override fun applyInsets(body: View, toolbar: SettingsToolbar) =
        WindowInsetsHelper.apply(body, toolbar, list)
}
