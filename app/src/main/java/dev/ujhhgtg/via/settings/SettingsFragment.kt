package dev.ujhhgtg.via.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.data.BrowserPreferences

/** Direct Kotlin translation of hb.o6, the original settings root. */
class SettingsFragment : SettingsListFragment() {
    private lateinit var preferences: BrowserPreferences
    private lateinit var rows: SettingsRowsAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = BrowserPreferences(requireContext())
        rows = SettingsRowsAdapter(::openRow)
        list.adapter = rows
        rows.submit(items())
    }

    override fun configureToolbar(toolbar: SettingsToolbar) = toolbar.setTitle(R.string.settings)

    private fun items() = buildList {
        add(SettingsRow(1, getString(R.string.settings_general)))
        add(SettingsRow(8, getString(R.string.settings_skin)))
        add(SettingsRow(2, getString(R.string.settings_privacy)))
        add(SettingsRow(3, getString(R.string.settings_advanced)))
        add(SettingsRow(9, getString(R.string.settings_script)))
        if (dev.ujhhgtg.via.engine.Engines.backend.extensions != null) add(SettingsRow(13, getString(R.string.settings_extensions)))
        add(SettingsRow(5, getString(R.string.settings_about)))
    }

    private fun openRow(row: SettingsRow) {
        val shell = requireActivity() as Shell
        when (row.id) {
            1 -> shell.navigate(GeneralSettingsFragment())
            2 -> shell.navigate(PrivacySettingsFragment())
            3 -> shell.navigate(AdvancedSettingsFragment())
            5 -> shell.navigate(AboutSettingsFragment())
            8 -> shell.openPage("homepage_customization")
            9 -> shell.navigate(ScriptSettingsFragment())
            13 -> shell.navigate(ExtensionSettingsFragment())
        }
    }

    companion object {
        fun newInstance(action: String? = null): Fragment =
            when (action) {
                null, "settings" -> SettingsFragment()
                "settings_general" -> GeneralSettingsFragment()
                "settings_privacy" -> PrivacySettingsFragment()
                "settings_advanced" -> AdvancedSettingsFragment()
                "toolbars_settings" -> ToolbarSettingsFragment()
                "search_toolbar", "search_toolbar_settings" -> SearchToolbarSettingsFragment()
                "action_night" -> NightModeSettingsFragment()
                "night_filter_for_web_contents" -> NightModeSettingsFragment()
                "agent" -> UserAgentSettingsFragment()
                "reader_mode" -> ReaderSettingsFragment()
                "text_size", "custom_reader_css", "theme_color" -> ReaderSettingsFragment()
                "textsize" -> WebTextSizeSettingsFragment()
                "block_ads" -> BlockAdsSettingsFragment()
                "title_ignore_ssl_warnings" -> SslWarningsSettingsFragment()
                "filter_subscriptions" -> FilterSubscriptionsFragment()
                "settings_operation" -> GesturesSettingsFragment()
                "customize_menu" -> MenuCustomizationFragment()
                "customize_context_menu" -> ContextMenuSettingsFragment()
                "search_settings" -> SearchSettingsFragment()
                "font" -> FontSettingsFragment()
                "settings_script" -> ScriptSettingsFragment()
                "update_interval" -> ScriptSettingsFragment()
                "settings_extensions" -> ExtensionSettingsFragment()
                "settings_about" -> AboutSettingsFragment()
                // Unrouted deep-link actions fall back to the settings root; every known page has its own fragment.
                else -> SettingsFragment()
            }
    }
}
