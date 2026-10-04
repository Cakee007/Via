package dev.ujhhgtg.via.settings

import android.os.Bundle
import android.view.View
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.dialog.ViaDialog

/** hb.g4: six original SSL error bits and their help action. */
class SslWarningsSettingsFragment : SettingsListFragment() {
    private lateinit var preferences: BrowserPreferences
    private lateinit var rows: SettingsRowsAdapter
    private var flags = 0
    override fun configureToolbar(toolbar: SettingsToolbar) {
        toolbar.setTitle(R.string.title_ignore_ssl_warnings)
        toolbar.addAction(null, R.string.help) {
            ViaDialog(requireActivity()).title(R.string.title_ignore_ssl_warnings).message(R.string.message_ignore_ssl_warnings)
                .positive(android.R.string.ok).show()
        }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = BrowserPreferences(requireContext())
        flags = preferences.ignoredSslWarning
        rows = SettingsRowsAdapter { row ->
            flags = if (flags and row.id == row.id) flags and row.id.inv() else flags or row.id
            preferences.ignoredSslWarning = flags
            bindRows()
        }
        list.adapter = rows
        bindRows()
    }
    private fun bindRows() {
        val labels = intArrayOf(R.string.ssl_warning_certificate_untrusted, R.string.ssl_warning_certificate_date_invalid,
            R.string.ssl_warning_certificate_expired, R.string.ssl_warning_certificate_domain_mismatch,
            R.string.ssl_warning_certificate_not_yet_valid, R.string.ssl_warning_certificate_invalid)
        rows.submit(labels.mapIndexed { index, label ->
            val flag = 1 shl index
            SettingsChoiceRow(flag, getString(label), flags and flag != 0)
        })
    }
}
