package dev.ujhhgtg.via.sites

import android.os.Bundle
import android.view.View
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.SettingsData
import dev.ujhhgtg.via.data.SettingsDataRepository
import dev.ujhhgtg.via.settings.SettingsHeadingRow
import dev.ujhhgtg.via.settings.SettingsListFragment
import dev.ujhhgtg.via.settings.SettingsRow
import dev.ujhhgtg.via.settings.SettingsRowsAdapter
import dev.ujhhgtg.via.settings.SettingsToolbar

/** jb.y4.h3/m3: every policy is a summary/navigation row, followed by its own settings page. */
class GlobalSiteSettingsFragment : SettingsListFragment() {
    private lateinit var preferences: BrowserPreferences
    private lateinit var rows: SettingsRowsAdapter

    override fun configureToolbar(toolbar: SettingsToolbar) = toolbar.setTitle(R.string.site_conf)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = BrowserPreferences(requireContext())
        rows = SettingsRowsAdapter(::click)
        list.adapter = rows
        bindRows()
    }

    private fun bindRows() {
        val flags = preferences.webFlags
        fun allowed(value: Boolean) = getString(if (value) R.string.allowed else R.string.blocked)
        fun on(value: Boolean) = getString(if (value) R.string.on else R.string.off)
        fun permission(value: Boolean) = getString(if (value) R.string.ask_first else R.string.blocked)
        fun mode(block: Int, ask: Int) = when { flags and ask != 0 -> getString(R.string.ask_first); flags and block != 0 -> getString(R.string.blocked); else -> getString(R.string.allowed) }
        rows.submit(listOf(
            SettingsRow(1, getString(R.string.all_sites)),
            SettingsHeadingRow(getString(R.string.category_content)),
            SettingsRow(14, getString(R.string.size), "${preferences.textSize}%"),
            SettingsRow(3, getString(R.string.agent), agentLabel()),
            SettingsRow(17, getString(R.string.action_pcview), on(flags and 2048 != 0)),
            SettingsRow(2, getString(R.string.images), if (flags and 32 != 0) getString(R.string.images_allowed_via_wifi_description_short) else allowed(flags and 16 != 0)),
            SettingsRow(8, getString(R.string.javascript), allowed(flags and 8 != 0)),
            SettingsRow(9, getString(R.string.cookies), if (flags and 8192 != 0 && flags and 16384 == 0) getString(R.string.cookies_allowed_except_third_party_description_short) else allowed(flags and 8192 != 0)),
            SettingsRow(10, getString(R.string.popups), getString(if (flags and 32768 != 0) R.string.ask_first else R.string.allowed)),
            SettingsHeadingRow(getString(R.string.category_basics)),
            SettingsRow(12, getString(R.string.block_ads), on(flags and 1 != 0)),
            SettingsHeadingRow(getString(R.string.category_permissions)),
            SettingsRow(4, getString(R.string.clipboard), mode(131072, 262144)),
            SettingsRow(11, getString(R.string.open_app), mode(33554432, 67108864)),
            SettingsRow(5, getString(R.string.location1), permission(flags and 2 != 0)),
            SettingsRow(6, getString(R.string.camera), permission(flags and 8388608 != 0)),
            SettingsRow(7, getString(R.string.microphone), permission(flags and 16777216 != 0)),
            SettingsRow(13, getString(R.string.page_redirection), if (flags and 134217728 == 0) allowed(true) else getString(R.string.page_redirection_ask_first_description_short)),
            SettingsRow(15, getString(R.string.vibrate), allowed(flags and 1073741824 != 0)),
            SettingsHeadingRow(getString(R.string.settings_advanced)),
            SettingsRow(16, getString(R.string.quick_back), on(flags and 512 != 0)),
        ))
    }

    private fun click(row: SettingsRow) {
        val host = requireActivity() as Shell
        when (row.id) {
            1 -> host.navigate(SiteSettingsFragment.newInstance())
            3 -> host.navigate(SiteUserAgentSettingsFragment())
            14 -> host.navigate(dev.ujhhgtg.via.settings.WebTextSizeSettingsFragment())
            else -> host.navigate(SitePolicySettingsFragment.create(row.id))
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && ::rows.isInitialized) bindRows()
    }

    private fun agentLabel(): String? {
        val choice = preferences.userAgentChoice
        val builtins = mapOf(0 to R.string.default_set, -1 to R.string.agent_android_phone,
            -2 to R.string.agent_android_tablet, -3 to R.string.agent_windows_chrome,
            -4 to R.string.agent_windows_ie11, -5 to R.string.agent_osx,
            -6 to R.string.agent_iphone, -7 to R.string.agent_ipad, -8 to R.string.agent_symbian)
        builtins[choice]?.let { return getString(it) }
        return BrowserDatabase(requireContext()).use { database ->
            SettingsDataRepository(database).list(SettingsData.USER_AGENT).firstOrNull { it.id == choice }?.title
        }
    }
}
