package dev.ujhhgtg.via.passwords

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.settings.SettingsListFragment
import dev.ujhhgtg.via.settings.SettingsRow
import dev.ujhhgtg.via.settings.SettingsRowsAdapter
import dev.ujhhgtg.via.settings.SettingsToggleRow
import dev.ujhhgtg.via.settings.SettingsToolbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** va.c1 -> o8.g: the password settings home owns import/export, not its child pages. */
class PasswordManagerFragment : SettingsListFragment() {
    private val operations = PasswordPageOperations(this)
    private val repository get() = operations.repository
    private lateinit var rows: SettingsRowsAdapter

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        operations.restoreState(state)
        operations.onDocumentResult = ::onDocumentResult
    }
    override fun configureToolbar(toolbar: SettingsToolbar) = toolbar.setTitle(R.string.password_manager)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rows = SettingsRowsAdapter { row ->
            when (row.id) {
                1 -> { repository.offerToSave = !repository.offerToSave; bindRows() }
                2 -> (requireActivity() as Shell).navigate(PasswordListFragment())
                5 -> (requireActivity() as Shell).navigate(PasswordIgnoredSitesFragment())
                3 -> operations.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("text/*").addCategory(Intent.CATEGORY_OPENABLE), IMPORT)
                4 -> exportPasswords()
            }
        }
        list.adapter = rows
        bindRows()
    }
    private fun bindRows() = rows.submit(listOf(
        SettingsToggleRow(1, getString(R.string.offer_to_save_passwords), checked = repository.offerToSave),
        SettingsRow(2, getString(R.string.passwords)),
        SettingsRow(5, getString(R.string.sites_that_never_save_passwords)),
        SettingsRow(3, getString(R.string.import_passwords), getString(R.string.import_passwords_description)),
        SettingsRow(4, getString(R.string.export_passwords), getString(R.string.export_passwords_description)),
    ))
    private fun exportPasswords() {
        operations.work({ repository.list().isEmpty() }) { empty ->
            if (empty) operations.toast(getString(R.string.no_passwords))
            else operations.authentication.authenticate(getString(R.string.export_passwords), getString(R.string.unlock_device_to_export_passwords)) {
                val date = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
                operations.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/csv").addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_TITLE, "${getString(R.string.app_name)}_${getString(R.string.passwords)}_$date.csv"), EXPORT)
            }
        }
    }
    private fun onDocumentResult(request: Int, result: Int, data: Intent?) {
        val uri = data?.data?.takeIf { result == Activity.RESULT_OK } ?: return
        when (request) {
            // va.c1.k3/g3: an unrecognized header yields -1; nothing imported shows no message.
            IMPORT -> operations.work({
                runCatching { requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use(repository::importCsv) ?: -1 }
                    .getOrElse { if (it is IllegalArgumentException) -1 else throw it }
            }) { count ->
                if (count > 0) operations.toast(getString(R.string.passwords_import, count))
                else if (count < 0) operations.toast(getString(R.string.failed_to_import_passwords))
            }
            EXPORT -> operations.work({ requireContext().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use(repository::exportCsv) ?: error("Cannot write CSV") }) {
                operations.toast(getString(R.string.export_successfully))
            }
        }
    }
    override fun onSaveInstanceState(out: Bundle) { operations.saveState(out); super.onSaveInstanceState(out) }
    override fun onDestroyView() { operations.destroyView(); super.onDestroyView() }
    companion object { private const val IMPORT = 6210; private const val EXPORT = 6211 }
}
