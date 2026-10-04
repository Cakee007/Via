package dev.ujhhgtg.via.sync

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.SettingsPageFragment
import dev.ujhhgtg.via.settings.SettingsToolbar

/** Shell-hosted account/WebDAV page, retaining the account and sync controller handlers. */
class SyncSettingsFragment : SettingsPageFragment() {
    private var page: SyncSettingsPage? = null

    override fun configureToolbar(toolbar: SettingsToolbar) {
        toolbar.setTitle(R.string.sync)
        val preferences = BrowserPreferences(requireContext())
        toolbar.addAction(null, modeLabel(preferences)) { clicked ->
            preferences.syncingChoice = if (preferences.syncingChoice == 0) 1 else 0
            (clicked as? TextView)?.setText(modeLabel(preferences))
            page?.render()
        }
    }

    override fun createContent(inflater: LayoutInflater, container: ViewGroup?): View =
        SyncSettingsPage(requireActivity(),
            openWebDav = { saved -> WebDavConfigurationDialogFragment.newInstance(saved).show(childFragmentManager, "WebDavConfiguration") },
            openCloudLogin = { CloudLoginDialogFragment().show(childFragmentManager, "CloudLogin") },
        ).also { page = it }.view

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childFragmentManager.setFragmentResultListener(WebDavConfigurationDialogFragment.RESULT, viewLifecycleOwner) { _, result ->
            page?.updateWebDav(result)
        }
        childFragmentManager.setFragmentResultListener(CloudLoginDialogFragment.RESULT, viewLifecycleOwner) { _, result ->
            page?.login(result.getString("username").orEmpty(), result.getString("password").orEmpty())
        }
    }

    override fun onDestroyView() {
        page?.close()
        page = null
        super.onDestroyView()
    }

    private fun modeLabel(preferences: BrowserPreferences): Int =
        if (preferences.syncingChoice == 0) R.string.sync_webdav else R.string.settings_cloud
}
