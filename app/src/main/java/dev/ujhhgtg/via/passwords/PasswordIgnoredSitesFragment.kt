package dev.ujhhgtg.via.passwords

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.settings.SettingsListFragment
import dev.ujhhgtg.via.settings.SettingsRow
import dev.ujhhgtg.via.settings.SettingsRowsAdapter
import dev.ujhhgtg.via.settings.SettingsToolbar
import dev.ujhhgtg.via.ui.dialog.ViaDialog

/** va.f -> o8.g: independently navigable exclusions list and its full-height empty overlay. */
class PasswordIgnoredSitesFragment : SettingsListFragment() {
    private val operations = PasswordPageOperations(this)
    private val repository get() = operations.repository
    private lateinit var rows: SettingsRowsAdapter
    override fun configureToolbar(toolbar: SettingsToolbar) {
        toolbar.setTitle(R.string.sites_that_never_save_passwords)
        toolbar.addAction(R.drawable.plus, R.string.action_new) { editIgnored(null) }
    }
    override fun configureEmptyState(view: TextView) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_empty_state_size).toFloat())
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rows = SettingsRowsAdapter { editIgnored(it.title) }
        list.adapter = rows
        loadSites()
    }
    private fun loadSites() {
        operations.work({ repository.ignoredSites().sortedWith { a, b -> PasswordPickerFragment.compareDomains(a, b) } }) { sites ->
            rows.submit(sites.map { SettingsRow(it.hashCode(), it) })
            showEmptyState(sites.isEmpty())
        }
    }
    private fun editIgnored(previous: String?) {
        val dialog = ViaDialog(requireActivity()).title(if (previous == null) R.string.action_new else R.string.action_edit)
            .input(previous.orEmpty(), "www.example.com", 1).canceledOnTouchOutside(false)
            .positive(android.R.string.ok) { _, result ->
                val name = PasswordCsv.normalizeName(result.edit?.firstOrNull().orEmpty())
                if (name.isNotEmpty() && name != previous) operations.work({
                    if (previous != null) repository.setSavingAllowed(previous, true)
                    repository.setSavingAllowed(name, false)
                }) { loadSites() }
            }.negative(android.R.string.cancel)
        if (previous != null) dialog.neutral(R.string.action_delete) {
            operations.work({ repository.setSavingAllowed(previous, true) }) { loadSites() }
        }
        dialog.show()
    }
    override fun onDestroyView() { operations.destroyView(); super.onDestroyView() }
}
