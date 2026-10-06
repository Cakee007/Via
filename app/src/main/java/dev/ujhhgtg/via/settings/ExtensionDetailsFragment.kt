package dev.ujhhgtg.via.settings

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.engine.Engines
import dev.ujhhgtg.via.engine.ExtensionInfo
import dev.ujhhgtg.via.engine.ExtensionInstallError
import dev.ujhhgtg.via.engine.ExtensionManager
import dev.ujhhgtg.via.extensions.ExtensionHost
import dev.ujhhgtg.via.extensions.ExtensionPermissions
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import kotlinx.coroutines.launch

/** One extension, laid out like a script's details: metadata, permissions, optional grants and options. */
class ExtensionDetailsFragment : SettingsListFragment() {
    private lateinit var manager: ExtensionManager
    private lateinit var rows: SettingsRowsAdapter
    private var extension: ExtensionInfo? = null
    private enum class Kind { PERMISSION, ORIGIN, DATA }
    /** Optional permission, origin or data-collection permission by row id. */
    private val optional = HashMap<Int, Pair<String, Kind>>()
    private val listener: () -> Unit = { load() }
    private var closing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = requireNotNull(Engines.backend.extensions)
    }

    override fun configureToolbar(toolbar: SettingsToolbar) {
        toolbar.setTitle(R.string.settings_extensions)
        toolbar.addAction(null, R.string.update) { update() }
        toolbar.addAction(null, R.string.uninstall) {
            // The change listener reloads, finds the extension gone and closes the page.
            extension?.let { ExtensionSettingsFragment.confirmUninstall(this, manager, it) { load() } }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rows = SettingsRowsAdapter(::click)
        list.adapter = rows
        manager.addListener(listener)
        load()
    }

    override fun onDestroyView() {
        manager.removeListener(listener)
        super.onDestroyView()
    }

    private fun load() {
        viewLifecycleOwnerLiveData.value ?: return
        val id = arguments?.getString("id") ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            extension = runCatching { manager.list() }.getOrDefault(emptyList()).firstOrNull { it.id == id }
            if (extension != null) bindRows()
            else if (!closing && isAdded) { closing = true; parentFragmentManager.popBackStack() }
        }
    }

    private fun bindRows() {
        val extension = extension ?: return
        toolbar.setTitle(extension.name)
        optional.clear()
        var key = 100
        rows.submit(buildList {
            add(SettingsRow(INFO, extension.name, listOfNotNull(extension.version.takeIf(String::isNotEmpty),
                extension.creator).joinToString(" · ").ifEmpty { null }))
            extension.description?.let { add(SettingsTextRow(DESCRIPTION, it)) }
            if (extension.optionsUrl != null) add(SettingsRow(OPTIONS, getString(R.string.extension_options)))
            extension.homepageUrl?.let { add(SettingsRow(HOMEPAGE, getString(R.string.info_homepage_url), it)) }
            if (extension.amoUrl != null) add(SettingsRow(AMO, getString(R.string.extension_amo_page)))
            val required = ExtensionPermissions.lines(requireContext(), extension.permissions, extension.origins)
            if (required.isNotEmpty()) {
                add(SettingsHeadingRow(getString(R.string.extension_permissions)))
                required.forEach { add(SettingsRow(++key, it)) }
            }
            val choices = extension.optionalPermissions.mapNotNull { name ->
                ExtensionPermissions.lines(requireContext(), listOf(name), emptyList()).firstOrNull()?.let { Triple(name, Kind.PERMISSION, it) }
            } + extension.optionalOrigins.map { origin ->
                Triple(origin, Kind.ORIGIN, ExtensionPermissions.lines(requireContext(), emptyList(), listOf(origin)).first())
            } + extension.optionalDataCollection.mapNotNull { name ->
                ExtensionPermissions.dataLines(requireContext(), listOf(name)).firstOrNull()?.let { Triple(name, Kind.DATA, it) }
            }
            if (choices.isNotEmpty()) {
                add(SettingsHeadingRow(getString(R.string.extension_optional_permissions)))
                choices.forEach { (name, kind, label) ->
                    val granted = name in when (kind) {
                        Kind.PERMISSION -> extension.grantedOptionalPermissions
                        Kind.ORIGIN -> extension.grantedOptionalOrigins
                        Kind.DATA -> extension.grantedOptionalDataCollection
                    }
                    optional[++key] = name to kind
                    add(SettingsToggleRow(key, label, checked = granted))
                }
            }
        })
    }

    private fun click(row: SettingsRow) {
        val extension = extension ?: return
        when (row.id) {
            OPTIONS -> manager.openOptions(extension.id)
            HOMEPAGE -> extension.homepageUrl?.let { (requireActivity() as Shell).openRecordFromRecords(it, 1) }
            AMO -> extension.amoUrl?.let { (requireActivity() as Shell).openRecordFromRecords(it, 1) }
            else -> optional[row.id]?.let { (name, kind) ->
                val granted = (row as? SettingsToggleRow)?.checked != true
                lifecycleScope.launch {
                    runCatching {
                        manager.setOptionalPermission(extension.id, name.takeIf { kind == Kind.PERMISSION },
                            name.takeIf { kind == Kind.ORIGIN }, granted, name.takeIf { kind == Kind.DATA })
                    }
                    load()
                }
            }
        }
    }

    private fun update() {
        val extension = extension ?: return
        ViaToast.show(requireContext(), R.string.update_pending)
        lifecycleScope.launch {
            try {
                val updated = manager.update(extension.id)
                ViaToast.makeText(requireContext(), if (updated == null) getString(R.string.extension_up_to_date, extension.name)
                    else getString(R.string.update_extensions_completed), ViaToast.LENGTH_SHORT).show()
            } catch (error: ExtensionInstallError) {
                if (error.kind != ExtensionInstallError.Kind.CANCELED && isAdded) ViaDialog(requireActivity())
                    .title(R.string.extension_install_failed).message(ExtensionHost.errorMessage(requireContext(), error))
                    .positive(android.R.string.ok).show()
            }
            load()
        }
    }

    companion object {
        private const val INFO = 1
        private const val DESCRIPTION = 2
        private const val OPTIONS = 3
        private const val HOMEPAGE = 4
        private const val AMO = 5

        fun newInstance(id: String) = ExtensionDetailsFragment().apply { arguments = Bundle().apply { putString("id", id) } }
    }
}
