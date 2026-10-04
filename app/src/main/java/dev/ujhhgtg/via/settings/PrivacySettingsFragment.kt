package dev.ujhhgtg.via.settings

import android.os.Bundle
import android.view.View
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.common.GeneratedDocumentState

/** hb.y4: the three privacy controls actually included by V1. */
class PrivacySettingsFragment : SettingsListFragment() {
    private lateinit var preferences: BrowserPreferences
    private lateinit var rows: SettingsRowsAdapter

    override fun configureToolbar(toolbar: SettingsToolbar) = toolbar.setTitle(R.string.settings_privacy)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = BrowserPreferences(requireContext())
        GeneratedDocumentState.initialize(preferences)
        rows = SettingsRowsAdapter { row ->
            val enabled = !(row as SettingsToggleRow).checked
            when (row.id) {
                1 -> preferences.doNotTrack = enabled
                6 -> preferences.disableWebRtc = enabled
                4 -> preferences.doNotSellOrShare = enabled
            }
            // hb.y4.i3: WebRTC is checked on the next page start; header
            // switches additionally request e8.i.L to rebind resident WebViews.
            if (row.id == 1 || row.id == 4) GeneratedDocumentState.mark(GeneratedDocumentState.PAGE_SETTINGS)
            bindRows()
        }
        list.itemAnimator = null
        list.adapter = rows
        bindRows()
    }

    private fun bindRows() = rows.submit(listOf(
        SettingsToggleRow(1, getString(R.string.do_not_track), checked = preferences.doNotTrack),
        SettingsToggleRow(6, getString(R.string.disable_webrtc), checked = preferences.disableWebRtc),
        SettingsToggleRow(4, getString(R.string.do_not_sell_or_share), getString(R.string.do_not_sell_or_share_description), preferences.doNotSellOrShare),
    ))
}
