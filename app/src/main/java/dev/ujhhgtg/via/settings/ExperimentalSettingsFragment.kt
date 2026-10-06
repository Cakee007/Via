package dev.ujhhgtg.via.settings

import android.os.Bundle
import android.view.View
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.ViaToast

/** hb.y1: skins and the platform-gated experimental controls. */
class ExperimentalSettingsFragment : SettingsListFragment() {
    private lateinit var preferences: BrowserPreferences
    private lateinit var rows: SettingsRowsAdapter

    override fun configureToolbar(toolbar: SettingsToolbar) = toolbar.setTitle(R.string.experimental)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = BrowserPreferences(requireContext())
        rows = SettingsRowsAdapter { row ->
            when (row.id) {
                0 -> (requireActivity() as dev.ujhhgtg.via.Shell).navigate(SkinSettingsFragment())
                1 -> preferences.blurEffect = !preferences.blurEffect
                2 -> {
                    preferences.showSettingsBackground = !preferences.showSettingsBackground
                    ViaToast.makeText(requireContext(), R.string.show_background_in_settings_toast, ViaToast.LENGTH_SHORT).show()
                }
            }
            bindRows()
        }
        list.itemAnimator = null
        list.adapter = rows
        bindRows()
    }
    private fun bindRows() = rows.submit(buildList {
        add(SettingsRow(0, getString(R.string.skins)))
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            add(SettingsToggleRow(1, getString(R.string.blur_effect), getString(R.string.blur_effect_description), preferences.blurEffect))
        }
        add(SettingsToggleRow(2, getString(R.string.show_background_in_settings), checked = preferences.showSettingsBackground))
    })
}
