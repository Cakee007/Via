package dev.ujhhgtg.via.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.engine.DisabledReason
import dev.ujhhgtg.via.engine.Engines
import dev.ujhhgtg.via.engine.ExtensionInfo
import dev.ujhhgtg.via.engine.ExtensionInstallError
import dev.ujhhgtg.via.engine.ExtensionManager
import dev.ujhhgtg.via.extensions.ExtensionFiles
import dev.ujhhgtg.via.extensions.ExtensionHost
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import kotlinx.coroutines.launch

/** The Extensions page, laid out like the Scripts page: master switch, update interval, one row per extension. */
class ExtensionSettingsFragment : SettingsListFragment() {
    private lateinit var preferences: BrowserPreferences
    private lateinit var manager: ExtensionManager
    private lateinit var rows: ExtensionRowsAdapter
    private lateinit var updateAction: View
    private var extensions = emptyList<ExtensionInfo>()
    private var updating = false
    private val listener: () -> Unit = { load() }
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val context = requireContext().applicationContext
        lifecycleScope.launch {
            try {
                val installed = ExtensionHost.installFile(context, manager, uri)
                toast(getString(R.string.extension_installed, installed.name))
            } catch (error: ExtensionInstallError) {
                if (error.kind != ExtensionInstallError.Kind.CANCELED) failed(error)
            } catch (error: Throwable) {
                failed(ExtensionInstallError(ExtensionInstallError.Kind.CORRUPT, null, error))
            }
            load()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = BrowserPreferences(requireContext())
        manager = requireNotNull(Engines.backend.extensions)
    }

    override fun configureToolbar(toolbar: SettingsToolbar) {
        toolbar.setTitle(R.string.settings_extensions)
        toolbar.addAction(R.drawable.plus, R.string.find_extensions) { anchor ->
            ViaDialog(requireActivity()).items(arrayOf(getString(R.string.find_extensions), getString(R.string.install_extension_from_file)),
                onClick = { item ->
                    when (item) {
                        0 -> (requireActivity() as Shell).openRecordFromRecords(AMO, 1)
                        1 -> try { importFile.launch(arrayOf(ExtensionFiles.MIME, "application/zip", "application/octet-stream")) }
                            catch (_: ActivityNotFoundException) { toast(R.string.toast_operation_failed) }
                    }
                }).showAnchored(anchor)
        }
        updateAction = toolbar.addAction(null, R.string.update) { updateExtensions() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rows = ExtensionRowsAdapter(::click, ::toggle, ::longClick)
        list.adapter = rows
        manager.addListener(listener)
        load()
    }

    override fun onDestroyView() {
        manager.removeListener(listener)
        super.onDestroyView()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && ::rows.isInitialized) load()
    }

    private fun load() {
        viewLifecycleOwnerLiveData.value ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            extensions = runCatching { manager.list() }.getOrDefault(emptyList())
            bindRows()
        }
    }

    private fun bindRows() {
        if (!::rows.isInitialized || view == null) return
        updateAction.visibility = if (extensions.isEmpty()) View.GONE else View.VISIBLE
        // The settings rows stay with no extensions installed: unsigned installs must be allowed first.
        rows.submit(buildList {
            add(SettingsToggleRow(TOGGLE_ID, getString(R.string.enable_extensions), checked = preferences.extensionsEnabled))
            add(SettingsRow(INTERVAL_ID, getString(R.string.update_interval), intervalLabels()[intervalIndex()]))
            add(SettingsToggleRow(UNSIGNED_ID, getString(R.string.allow_unsigned_extensions),
                getString(R.string.allow_unsigned_extensions_description), preferences.allowUnsignedExtensions))
            if (extensions.isEmpty()) return@buildList
            add(SettingsHeadingRow(getString(R.string.settings_extensions)))
            extensions.forEachIndexed { index, extension ->
                add(ExtensionListRow(FIRST_EXTENSION + index, extension, summary(extension), preferences.extensionsEnabled))
            }
        })
    }

    private fun summary(extension: ExtensionInfo): String = when (extension.disabledReason) {
        DisabledReason.UNSIGNED -> getString(R.string.extension_disabled_unsigned)
        DisabledReason.BLOCKLISTED -> getString(R.string.extension_disabled_blocklisted)
        DisabledReason.INCOMPATIBLE -> getString(R.string.extension_disabled_incompatible)
        null -> extension.version
    }

    private fun click(row: SettingsRow) {
        when (row.id) {
            TOGGLE_ID -> {
                preferences.extensionsEnabled = !preferences.extensionsEnabled
                manager.configure(preferences.extensionsEnabled, preferences.allowUnsignedExtensions)
                bindRows()
            }
            INTERVAL_ID -> chooseInterval()
            UNSIGNED_ID -> {
                preferences.allowUnsignedExtensions = !preferences.allowUnsignedExtensions
                manager.configure(preferences.extensionsEnabled, preferences.allowUnsignedExtensions)
                bindRows()
            }
            else -> (row as? ExtensionListRow)?.let { openDetails(it.extension.id) }
        }
    }

    private fun toggle(extension: ExtensionInfo, enabled: Boolean) {
        lifecycleScope.launch {
            runCatching { manager.setEnabled(extension.id, enabled) }
            load()
        }
    }

    private fun longClick(anchor: View, row: SettingsRow): Boolean {
        val extension = (row as? ExtensionListRow)?.extension ?: return false
        val actions = buildList {
            add(ViaDialog.Item(1, getString(R.string.action_edit)))
            add(ViaDialog.Item(2, getString(R.string.update)))
            if (extension.optionsUrl != null) add(ViaDialog.Item(3, getString(R.string.extension_options)))
            if (extension.homepageUrl != null) add(ViaDialog.Item(4, getString(R.string.home)))
            if (extension.amoUrl != null) add(ViaDialog.Item(5, getString(R.string.action_copy)))
            add(ViaDialog.Item(6, getString(R.string.uninstall)))
        }
        ViaDialog(requireActivity()).items(actions, onClick = { position ->
            when (actions[position].id) {
                1 -> openDetails(extension.id)
                2 -> update(listOf(extension))
                3 -> manager.openOptions(extension.id)
                4 -> extension.homepageUrl?.let { (requireActivity() as Shell).openRecordFromRecords(it, 1) }
                5 -> copyLink(extension.amoUrl)
                6 -> confirmUninstall(this, manager, extension) { load() }
            }
        }).showAnchored(anchor)
        return true
    }

    private fun updateExtensions() {
        if (updating) return
        val candidates = extensions.filter { it.userEnabled }
        if (candidates.isEmpty()) return
        val dialog = ViaDialog(requireActivity()).title(R.string.update)
        if (candidates.size == 1) dialog.message(getString(R.string.request_item_update, candidates[0].name))
        else dialog.multipleChoice(candidates.map { it.name }.toTypedArray(), IntArray(candidates.size) { it })
        dialog.positive(android.R.string.ok) { _, result ->
            val selected = if (candidates.size == 1) candidates else (result.selected ?: intArrayOf()).toList().mapNotNull(candidates::getOrNull)
            update(selected)
        }.negative(android.R.string.cancel).show()
    }

    private fun update(selected: List<ExtensionInfo>) {
        if (selected.isEmpty() || updating) return
        updating = true
        toast(R.string.update_pending)
        lifecycleScope.launch {
            var updated = 0
            selected.forEach { extension ->
                try { if (manager.update(extension.id) != null) updated++ }
                catch (error: ExtensionInstallError) { if (error.kind != ExtensionInstallError.Kind.CANCELED) failed(error) }
            }
            updating = false
            if (updated == 0 && selected.size == 1) toast(getString(R.string.extension_up_to_date, selected[0].name))
            else toast(R.string.update_extensions_completed)
            load()
        }
    }

    private fun intervalLabels() = arrayOf(R.string.never, R.string.every_day, R.string.every_3_days, R.string.every_week, R.string.every_15_days).map(::getString).toTypedArray()
    private fun intervalIndex() = INTERVAL_DAYS.indexOfLast { preferences.extensionUpdateInterval >= it * DAY }.coerceAtLeast(0)
    private fun chooseInterval() {
        ViaDialog(requireActivity()).title(R.string.update_interval).singleChoice(intervalLabels(), intervalIndex()) {
            preferences.extensionUpdateInterval = INTERVAL_DAYS[it] * DAY; bindRows()
        }.show()
    }

    private fun openDetails(id: String) = (requireActivity() as Shell).navigate(ExtensionDetailsFragment.newInstance(id))
    private fun copyLink(url: String?) {
        if (url.isNullOrEmpty()) return
        (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("", url))
        toast(R.string.toast_copy_url_successful)
    }
    private fun failed(error: ExtensionInstallError) {
        if (!isAdded) return
        ViaDialog(requireActivity()).title(R.string.extension_install_failed)
            .message(ExtensionHost.errorMessage(requireContext(), error)).positive(android.R.string.ok).show()
    }
    private fun toast(resource: Int) = ViaToast.show(requireContext(), resource)
    private fun toast(text: String) = ViaToast.makeText(requireContext(), text, ViaToast.LENGTH_SHORT).show()

    companion object {
        private const val TOGGLE_ID = -100
        private const val INTERVAL_ID = -101
        private const val UNSIGNED_ID = -102
        private const val FIRST_EXTENSION = 1
        private const val DAY = 86_400_000L
        private val INTERVAL_DAYS = longArrayOf(0, 1, 3, 7, 15)
        private const val AMO = "https://addons.mozilla.org/android/"

        internal fun confirmUninstall(fragment: androidx.fragment.app.Fragment, manager: ExtensionManager, extension: ExtensionInfo, done: () -> Unit) {
            ViaDialog(fragment.requireActivity()).title(R.string.uninstall)
                .message(fragment.getString(R.string.uninstall_item_message, extension.name))
                .positive(android.R.string.ok) { _, _ ->
                    fragment.lifecycleScope.launch { runCatching { manager.uninstall(extension.id) }; done() }
                }.negative(android.R.string.cancel).show()
        }
    }
}

internal class ExtensionListRow(id: Int, val extension: ExtensionInfo, summary: String, val extensionsEnabled: Boolean) :
    SettingsRow(id, extension.name, summary) {
    override fun equals(other: Any?) = super.equals(other) && other is ExtensionListRow &&
        extension == other.extension && extensionsEnabled == other.extensionsEnabled
    override fun hashCode() = 31 * extension.hashCode() + extensionsEnabled.hashCode()
}

/** The Scripts page's rows, with extension rows drawn by the same independently checkable view. */
internal class ExtensionRowsAdapter(
    private val click: (SettingsRow) -> Unit,
    private val toggle: (ExtensionInfo, Boolean) -> Unit,
    private val longClick: (View, SettingsRow) -> Boolean,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val common = SettingsRowsAdapter(click).apply { onLongClick = longClick }
    init {
        common.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = notifyDataSetChanged()
            override fun onItemRangeChanged(start: Int, count: Int, payload: Any?) = notifyItemRangeChanged(start, count, payload)
            override fun onItemRangeInserted(start: Int, count: Int) = notifyItemRangeInserted(start, count)
            override fun onItemRangeRemoved(start: Int, count: Int) = notifyItemRangeRemoved(start, count)
            override fun onItemRangeMoved(from: Int, to: Int, count: Int) = notifyItemMoved(from, to)
        })
    }
    override fun getItemCount() = common.itemCount
    override fun getItemViewType(position: Int) = if (common.rowAt(position) is ExtensionListRow) 100 else common.getItemViewType(position)
    override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder {
        if (type != 100) return common.onCreateViewHolder(parent, type)
        val view = ScriptListView(parent.context).apply { layoutParams = RecyclerView.LayoutParams(-1, -2) }
        return object : RecyclerView.ViewHolder(view) {}
    }
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = common.rowAt(position) as? ExtensionListRow ?: return common.onBindViewHolder(holder, position)
        val extension = row.extension
        (holder.itemView as ScriptListView).bind(extension.name, row.summary, extension.userEnabled, row.extensionsEnabled,
            row.extensionsEnabled && extension.disabledReason == null, { click(row) },
            { longClick(holder.itemView, row) }, { toggle(extension, it) })
    }
    fun submit(rows: List<SettingsRow>) = common.submit(rows)
}
