package dev.ujhhgtg.via.settings

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import dev.ujhhgtg.via.common.launchIo
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.skins.SkinEntry
import dev.ujhhgtg.via.skins.SkinRepository
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import kotlin.system.exitProcess

/** lb.k: ZIP skin imports, selection, deletion and the original restart prompt. */
class SkinSettingsFragment : SettingsListFragment() {
    private lateinit var preferences: BrowserPreferences
    private lateinit var repository: SkinRepository
    private lateinit var rows: SettingsRowsAdapter
    private val skins = mutableListOf<SkinEntry>()
    private var selected = ""
    private val importer = registerForActivityResult(object : ActivityResultContracts.OpenDocument() {
        override fun createIntent(context: Context, input: Array<String>): Intent =
            super.createIntent(context, input).putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                "content://com.android.externalstorage.documents/document/primary:Download".toUri())
    }) { uri -> uri?.let(::importSkin) }

    override fun configureToolbar(toolbar: SettingsToolbar) {
        toolbar.setTitle(R.string.skins)
        toolbar.addAction(R.drawable.plus, R.string.load_skin) {
            try {
                importer.launch(arrayOf("application/zip"))
            } catch (error: ActivityNotFoundException) {
                android.util.Log.w("ViaSkins", "Unable to open skin picker", error)
                ViaToast.makeText(requireContext(), R.string.toast_operation_failed, ViaToast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = BrowserPreferences(requireContext())
        repository = SkinRepository(requireContext())
        selected = preferences.skin
        rows = SettingsRowsAdapter { row ->
            skins.firstOrNull { it.hashCode() == row.id }?.let { skin ->
                if (skin.name != selected) { select(skin.name); bindRows() }
            }
        }.apply {
            onLongClick = { _, row ->
                val skin = skins.firstOrNull { it.hashCode() == row.id }
                if (skin == null || skin.name.isEmpty()) false
                else { confirmDelete(skin); true }
            }
        }
        list.adapter = rows
        skins.clear()
        skins += SkinEntry("", getString(R.string.default_set))
        skins += repository.list()
        bindRows()
    }

    private fun bindRows() = rows.submit(skins.map { SettingsChoiceRow(it.hashCode(), it.title, it.name == selected) })

    private fun select(name: String) {
        selected = name
        preferences.skin = name
        ViaDialog(requireActivity()).title(R.string.skin_changed).message(R.string.restart_to_take_effect)
            .positive(R.string.crash_restart) { _, _ ->
                // lb.k.j3 / z8.f.k start the launcher task before terminating this process.
                val context = requireContext()
                context.startActivity(Intent.makeRestartActivityTask(ComponentName(context, Shell::class.java)).setPackage(context.packageName))
                exitProcess(0)
            }.negative(android.R.string.cancel).show()
    }

    private fun confirmDelete(skin: SkinEntry) {
        ViaDialog(requireActivity()).title(R.string.action_delete)
            .message(getString(R.string.delete_item_message, skin.title))
            .positive(android.R.string.ok) { _, _ ->
                if (repository.delete(skin.name)) {
                    skins.remove(skin)
                    if (selected == skin.name) select("")
                    bindRows()
                }
            }.negative(android.R.string.cancel).show()
    }

    private fun importSkin(uri: Uri) {
        // lb.k.l3 refreshes an existing row in place; only a new skin is inserted at index 1.
        viewLifecycleOwner.launchIo({ ImportResult(repository.importSkin(uri)) }, { result ->
                val skin = result.skin
                if (skin == null) ViaToast.makeText(requireContext(), R.string.load_skin_failed, ViaToast.LENGTH_LONG).show()
                else {
                    val index = skins.indexOfFirst { it.name == skin.name }
                    if (index > 0) rows.notifyItemChanged(index)
                    else { skins.add(1, skin); bindRows() }
                }
        }) { android.util.Log.w("ViaSkins", "Unable to import skin", it) }
    }

    private data class ImportResult(val skin: SkinEntry?)
}
