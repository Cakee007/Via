package dev.ujhhgtg.via.settings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.behavior.BehaviorPreferences
import dev.ujhhgtg.via.ui.dialog.ViaDialog

/** hb.l7: toolbar mode/fullscreen/tab bar/address field/color settings. */
class ToolbarSettingsFragment : SettingsListFragment() {
    private lateinit var preferences: BrowserPreferences
    private lateinit var behavior: BehaviorPreferences
    private lateinit var rows: SettingsRowsAdapter
    private lateinit var previewAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>
    override fun configureToolbar(toolbar: SettingsToolbar) = toolbar.setTitle(R.string.toolbars_settings)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = BrowserPreferences(requireContext()); behavior = BehaviorPreferences(preferences)
        rows = SettingsRowsAdapter(::click)
        previewAdapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = 1
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = object : RecyclerView.ViewHolder(ToolbarPreviewView(parent.context)) {}
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                (holder.itemView as ToolbarPreviewView).bind(behavior.toolbarMode, behavior.tabBarEnabled, preferences.urlBoxMode)
            }
        }
        list.adapter = ConcatAdapter(previewAdapter, rows)
        bindRows()
    }
    private fun bindRows() {
        val modes = intArrayOf(R.string.ui_traditional, R.string.ui_simple_top, R.string.ui_simple_bottom, R.string.two_row_toolbar)
        val fullscreen = intArrayOf(R.string.fullscreen_0, R.string.fullscreen_1, R.string.fullscreen_2)
        val url = intArrayOf(R.string.hint_title, R.string.hint_url, R.string.url_box_domain)
        rows.submit(listOf(
            SettingsRow(1, getString(R.string.ui_mode), getString(modes[preferences.appUi.takeIf { it in 0..3 } ?: 0])),
            SettingsRow(2, getString(R.string.fullscreen), getString(fullscreen[preferences.fullScreenMode.coerceIn(0, 2)])),
            SettingsToggleRow(3, getString(R.string.enable_tab_bar), checked = behavior.tabBarEnabled && behavior.toolbarMode in 1..2),
            SettingsRow(4, getString(R.string.url_box), getString(url[preferences.urlBoxMode.coerceIn(0, 2)])),
            SettingsToggleRow(5, getString(R.string.color_mode), getString(R.string.color_mode_hint), behavior.colorToolbars),
        ))
    }
    private fun click(row: SettingsRow) {
        when (row.id) {
            1 -> chooseMode()
            2 -> chooseFullscreen()
            3 -> {
                // hb.l7.m3 checks the stored tab-bar flag, not the row's
                // mode-filtered check state, before deciding whether to offer a mode.
                val enable = !behavior.tabBarEnabled
                if (enable && behavior.toolbarMode !in 1..2) {
                    ViaDialog(requireActivity()).title(R.string.ui_mode).message(R.string.toast_enable_tab_bar)
                        .singleChoice(arrayOf(getString(R.string.ui_simple_top), getString(R.string.ui_simple_bottom)), -1) { behavior.toolbarMode = it + 1; behavior.tabBarEnabled = true; bindRows(); previewAdapter.notifyItemChanged(0, Unit) }.show()
                } else { behavior.tabBarEnabled = enable; bindRows(); previewAdapter.notifyItemChanged(0, Unit) }
            }
            4 -> ViaDialog(requireActivity()).title(R.string.url_box).singleChoice(
                arrayOf(getString(R.string.hint_title), getString(R.string.hint_url), getString(R.string.url_box_domain)), preferences.urlBoxMode.coerceIn(0, 2)
            ) { preferences.urlBoxMode = it; bindRows(); previewAdapter.notifyItemChanged(0, Unit) }.show()
            5 -> { behavior.colorToolbars = !(row as SettingsToggleRow).checked; bindRows() }
        }
    }
    /** hb.l7.n3: the three illustrated choices, selected by tint and bold label. */
    private fun chooseFullscreen() {
        val dialog = ViaDialog(requireActivity()).title(R.string.fullscreen)
        dialog.customView(ToolbarFullscreenChoices(requireContext(), preferences.fullScreenMode) { selected ->
            preferences.fullScreenMode = selected
            bindRows()
            dialog.dismiss()
        }).show()
    }

    private fun chooseMode() {
        ViaDialog(requireActivity()).title(R.string.ui_mode).singleChoice(
            arrayOf(getString(R.string.ui_traditional), getString(R.string.ui_simple_top), getString(R.string.ui_simple_bottom), getString(R.string.two_row_toolbar)), preferences.appUi.takeIf { it in 0..3 } ?: 0
        ) { mode ->
            behavior.toolbarMode = mode
            bindRows(); previewAdapter.notifyItemChanged(0, Unit)
        }.show()
    }

}
