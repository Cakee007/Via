package dev.ujhhgtg.via.settings

import android.os.Bundle
import android.view.View
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.ui.behavior.BrowserActions

/** hb.l6, disposable action selector used by hb.u4's five controls and two swipes. */
class GestureActionFragment : SettingsListFragment() {
    override fun configureToolbar(toolbar: SettingsToolbar) = toolbar.setTitle(R.string.select_action)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val selected = arguments?.getInt("id") ?: 0
        val rows = SettingsRowsAdapter { row ->
            parentFragmentManager.setFragmentResult("hb.l6", Bundle().apply { putInt("id", row.id) })
            onToolbarBack()
        }
        val values = buildList {
            fun action(id: Int) { add(SettingsChoiceRow(id, BrowserActions.entries.getValue(id).title(requireContext()), id == selected)) }
            action(0)
            add(SettingsHeadingRow(getString(R.string.tab_actions)))
            intArrayOf(5, 10, 11, 12, 13, 9, 16, 27).forEach(::action)
            add(SettingsHeadingRow(getString(R.string.feature_actions)))
            intArrayOf(7, 30, 8, 28, 26, 14, 15, 24, 25).forEach(::action)
            add(SettingsHeadingRow(getString(R.string.address_bar_actions)))
            action(4)
            add(SettingsHeadingRow(getString(R.string.webpage_actions)))
            intArrayOf(19, 1, 20, 6, 18, 17, 2, 3).forEach(::action)
        }
        list.adapter = rows; rows.submit(values)
        list.scrollToPosition(values.indexOfFirst { it is SettingsChoiceRow && it.id == selected }.coerceAtLeast(0))
    }
    companion object { fun newInstance(id: Int) = GestureActionFragment().apply { arguments = Bundle().apply { putInt("id", id) } } }
}
