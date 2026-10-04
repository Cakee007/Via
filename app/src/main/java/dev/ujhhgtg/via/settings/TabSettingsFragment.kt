package dev.ujhhgtg.via.settings

import android.os.Bundle
import android.view.View
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog

/**
 * hb.e7: the Tab settings page opened by the closed-tabs page's More action.
 * Two rows: the startup-restore choice (array g via hb.e7.i3) and the
 * undo-close toast toggle (hb.e7.h3), writing w9.l.y0/w9.a.v().L equivalents.
 */
class TabSettingsFragment : SettingsListFragment() {
    private lateinit var preferences: BrowserPreferences
    private lateinit var rows: SettingsRowsAdapter

    override fun configureToolbar(toolbar: SettingsToolbar) = toolbar.setTitle(R.string.tab_settings)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = BrowserPreferences(requireContext())
        rows = SettingsRowsAdapter { row ->
            when (row.id) {
                1 -> chooseRestore()
                2 -> { preferences.showUndoCloseTab = !preferences.showUndoCloseTab; bindRows() }
            }
        }
        list.itemAnimator = null
        list.adapter = rows
        bindRows()
    }

    private fun choiceTitle(): Int = intArrayOf(R.string.disable_restore, R.string.always_restore, R.string.ask_first)[preferences.restoreClosedTabs.coerceIn(0, 2)]

    private fun bindRows() = rows.submit(listOf(
        SettingsRow(1, getString(R.string.restore_tabs), getString(choiceTitle())),
        SettingsToggleRow(2, getString(R.string.show_toast_to_undo_closing_tab), getString(R.string.show_toast_to_undo_closing_tab_description), preferences.showUndoCloseTab),
    ))

    private fun chooseRestore() {
        ViaDialog(requireActivity()).title(R.string.restore_tabs).singleChoice(
            arrayOf(getString(R.string.disable_restore), getString(R.string.always_restore), getString(R.string.ask_first)), preferences.restoreClosedTabs
        ) { which ->
            preferences.restoreClosedTabs = which
            if (which != 0) ViaToast.makeText(requireContext(), getString(R.string.restore_tabs_hint_settings), ViaToast.LENGTH_SHORT).show()
            bindRows()
        }.show()
    }
}
