package dev.ujhhgtg.via.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.webkit.URLUtil
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.browser.script.ScriptInstaller
import dev.ujhhgtg.via.browser.script.ScriptManager
import dev.ujhhgtg.via.browser.script.ScriptStore
import dev.ujhhgtg.via.browser.script.UserScript
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import java.io.InputStream
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking

/** sa.d1: imports, batch updates, detail navigation and separately clickable enable controls. */
class ScriptSettingsFragment : SettingsListFragment() {
    private lateinit var preferences: BrowserPreferences
    private lateinit var store: ScriptStore
    private lateinit var manager: ScriptManager
    private lateinit var installer: ScriptInstaller
    private lateinit var rows: ScriptSettingsAdapter
    private lateinit var updateAction: View
    private val worker = Executors.newSingleThreadExecutor()
    private var scripts = emptyList<UserScript>()
    private var updating = false
    private var scrollToInitial = true
    private val importFile = registerForActivityResult(object : ActivityResultContracts.OpenDocument() {
        override fun createIntent(context: Context, input: Array<String>): Intent = super.createIntent(context, input)
            .putExtra(DocumentsContract.EXTRA_INITIAL_URI, "content://com.android.externalstorage.documents/document/primary:Download".toUri())
    }) { uri ->
        if (uri != null) {
            toast(R.string.toast_parsing_script)
            val context = requireContext().applicationContext
            worker.execute {
                val source = runCatching { readImportedSource(context.contentResolver.openInputStream(uri)) }.getOrNull()
                val parsed = source?.let { UserScript.parse(it) }
                val existing = parsed?.let { store.findByScriptId(it.scriptId) }
                onUi { installer.confirm(parsed, existing, ::onInstalled) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = BrowserPreferences(requireContext())
        store = ScriptStore(requireContext())
        manager = ScriptManager(store)
    }

    override fun configureToolbar(toolbar: SettingsToolbar) {
        toolbar.setTitle(R.string.settings_script)
        toolbar.addAction(R.drawable.plus, R.string.add_script) { anchor ->
            ViaDialog(requireActivity()).items(arrayOf(getString(R.string.add_script), getString(R.string.import_script_from_url),
                getString(R.string.import_script_from_file)), onClick = { item ->
                when (item) {
                    0 -> openEditor()
                    1 -> importFromUrl()
                    2 -> try { importFile.launch(arrayOf("text/plain", "application/javascript", "text/javascript")) }
                        catch (_: ActivityNotFoundException) { toast(R.string.toast_operation_failed) }
                }
            }).showAnchored(anchor)
        }
        updateAction = toolbar.addAction(null, R.string.update) { updateScripts() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rows = ScriptSettingsAdapter(::click, { script, enabled ->
            store.setEnabled(script.id, enabled)
            scripts = scripts.map { if (it.id == script.id) it.copy(enabled = enabled) else it }
        }, ::longClick)
        list.adapter = rows
        installer = ScriptInstaller(this)
        parentFragmentManager.setFragmentResultListener(ScriptEditorFragment.RESULT, viewLifecycleOwner) { key, result ->
            parentFragmentManager.clearFragmentResult(key)
            refreshSavedScript(result.getInt("id"))
        }
        parentFragmentManager.setFragmentResultListener(ScriptDetailsFragment.RESULT, viewLifecycleOwner) { key, result ->
            parentFragmentManager.clearFragmentResult(key)
            refreshSavedScript(result.getInt("id"))
        }
        loadScripts()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && ::rows.isInitialized) loadScripts()
    }

    private fun click(row: SettingsRow) {
        when (row.id) {
            TOGGLE_ID -> {
                preferences.scriptsEnabled = !preferences.scriptsEnabled
                (requireActivity() as Shell).browserReloadPreferences()
                bindRows()
            }
            INTERVAL_ID -> chooseInterval()
            else -> (row as? ScriptListRow)?.script?.let { script ->
                if (script.content.isEmpty()) {
                    ViaDialog(requireActivity()).title(script.name).message(R.string.request_to_download_full_script)
                        .positive(android.R.string.ok) { _, _ -> script.downloadUrl?.let { importUrl(it, script.id) } }
                        .negative(android.R.string.cancel).show()
                } else openDetails(script.id)
            }
        }
    }

    private fun longClick(anchor: View, row: SettingsRow): Boolean {
        val script = (row as? ScriptListRow)?.script ?: return false
        val actions = buildList {
            add(ViaDialog.Item(1, getString(R.string.action_edit)))
            if (!script.downloadUrl.isNullOrEmpty()) add(ViaDialog.Item(2, getString(R.string.update)))
            if (!script.homepageUrl.isNullOrEmpty()) add(ViaDialog.Item(3, getString(R.string.home)))
            add(ViaDialog.Item(4, getString(R.string.action_copy)))
            add(ViaDialog.Item(5, getString(R.string.action_delete)))
        }
        ViaDialog(requireActivity()).items(actions, onClick = { position ->
            when (actions[position].id) {
                1 -> openDetails(script.id)
                2 -> script.downloadUrl?.let { importUrl(it, script.id) }
                3 -> script.homepageUrl?.let { (requireActivity() as Shell).openRecordFromRecords(it, false) }
                4 -> copyLink(script.downloadUrl)
                5 -> ViaDialog(requireActivity()).title(R.string.action_delete).message(getString(R.string.delete_item_message, script.name))
                    .positive(android.R.string.ok) { _, _ ->
                        scripts = scripts.filterNot { it.id == script.id }; bindRows()
                        worker.execute { manager.remove(script.id); manager.cleanupResources() }
                    }.negative(android.R.string.cancel).show()
            }
        }).showAnchored(anchor)
        return true
    }

    private fun loadScripts() {
        worker.execute {
            val loaded = runCatching { store.list() }.getOrDefault(emptyList())
            onUi {
                scripts = loaded; bindRows()
                if (scrollToInitial) {
                    val id = arguments?.getInt("id", 0) ?: 0
                    val position = scripts.indexOfFirst { it.id == id }
                    if (position >= 0) list.scrollToPosition(position + if (scripts.any { it.downloadUrl != null }) 3 else 2)
                    scrollToInitial = false
                }
            }
        }
    }

    private fun bindRows() {
        if (!::rows.isInitialized) return
        val downloadable = scripts.any { it.downloadUrl != null }
        showEmptyState(scripts.isEmpty())
        if (scripts.isEmpty()) { rows.submit(emptyList()); return }
        updateAction.visibility = if (downloadable) View.VISIBLE else View.GONE
        rows.submit(buildList {
            add(SettingsToggleRow(TOGGLE_ID, getString(R.string.enable_scripts), checked = preferences.scriptsEnabled))
            if (downloadable) add(SettingsRow(INTERVAL_ID, getString(R.string.update_interval), intervalLabels()[intervalIndex()]))
            add(SettingsHeadingRow(getString(R.string.settings_script)))
            scripts.forEach { add(ScriptListRow(it, preferences.scriptsEnabled)) }
        })
    }

    private fun refreshSavedScript(id: Int) {
        loadScripts()
        worker.execute { runBlocking { store.find(id)?.let { manager.ensureDependencies(it) } } }
    }

    /** sa.d1.o4: batch refresh includes enabled scripts with a network download URL. */
    private fun updateScripts() {
        if (updating) return
        val candidates = scripts.filter { it.enabled && it.downloadUrl?.let(URLUtil::isNetworkUrl) == true }
        if (candidates.isEmpty()) return
        val dialog = ViaDialog(requireActivity()).title(R.string.update)
        if (candidates.size == 1) dialog.message(getString(R.string.request_item_update, candidates[0].name))
        else dialog.multipleChoice(candidates.map { it.name }.toTypedArray(), IntArray(candidates.size) { it })
        dialog.positive(android.R.string.ok) { _, result ->
            val selected = if (candidates.size == 1) candidates else (result.selected ?: intArrayOf()).toList().mapNotNull(candidates::getOrNull)
            if (selected.isNotEmpty()) {
                updating = true; toast(R.string.update_pending)
                worker.execute {
                    selected.forEach { script ->
                        val updated = runBlocking { runCatching { manager.updateFromNetwork(script.id) }.getOrNull() }
                        if (updated != null) onUi {
                            scripts = scripts.map { if (it.id == updated.id) updated else it }; bindRows()
                        }
                    }
                    manager.cleanupResources()
                    onUi { updating = false; toast(R.string.update_scripts_completed) }
                }
            }
        }.negative(android.R.string.cancel).show()
    }

    private fun importFromUrl() {
        ViaDialog(requireActivity()).title(R.string.import_script_from_url).input("", getString(R.string.info_url), 1)
            .positive(android.R.string.ok) { _, result ->
                val url = result.edit?.firstOrNull()?.trim().orEmpty()
                if (URLUtil.isNetworkUrl(url)) importUrl(url) else toast(R.string.url_is_invalid)
            }.show()
    }

    private fun importUrl(url: String, id: Int = 0) = installer.fromSettingsUrl(url, id, ::onInstalled)

    /** sa.d1.H3/l4: keep edited rows in place and scroll only a newly installed row to the top. */
    private fun onInstalled(saved: UserScript) {
        val exists = scripts.any { it.id == saved.id }
        scripts = if (exists) scripts.map { if (it.id == saved.id) saved else it } else listOf(saved) + scripts
        bindRows()
        if (!exists) list.scrollToPosition(0)
    }

    private fun intervalLabels() = arrayOf(R.string.never, R.string.every_day, R.string.every_3_days, R.string.every_week, R.string.every_15_days).map(::getString).toTypedArray()
    private fun intervalIndex() = INTERVAL_DAYS.indexOfLast { preferences.scriptUpdateInterval >= it * DAY }.coerceAtLeast(0)
    private fun chooseInterval() {
        ViaDialog(requireActivity()).title(R.string.update_interval).singleChoice(intervalLabels(), intervalIndex()) {
            preferences.scriptUpdateInterval = INTERVAL_DAYS[it] * DAY; bindRows()
        }.show()
    }
    private fun openEditor() {
        parentFragmentManager.setFragmentResultListener(ScriptEditorFragment.RESULT, viewLifecycleOwner) { key, result ->
            parentFragmentManager.clearFragmentResult(key)
            refreshSavedScript(result.getInt("id"))
        }
        (requireActivity() as Shell).navigate(ScriptEditorFragment.newInstance(-1))
    }
    private fun openDetails(id: Int, dependencies: Boolean = false) =
        (requireActivity() as Shell).navigate(ScriptDetailsFragment.newInstance(id, dependencies))
    private fun copyLink(url: String?) {
        if (url.isNullOrEmpty()) return
        (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("", url))
        toast(R.string.toast_copy_url_successful)
    }
    private fun toast(resource: Int) = ViaToast.show(requireContext(), resource)
    private fun onUi(action: () -> Unit) { activity?.runOnUiThread { if (view != null) action() } }
    override fun onDestroy() { worker.execute { store.close() }; worker.shutdown(); super.onDestroy() }

    companion object {
        private const val TOGGLE_ID = -100
        private const val INTERVAL_ID = -101
        private const val DAY = 86_400_000L
        private val INTERVAL_DAYS = longArrayOf(0, 1, 3, 7, 15)

        /** sa.d1.x3, cross-checked in smali: preserve all lines, abandon after 32 without a marker. */
        internal fun readImportedSource(input: InputStream?): String? {
            if (input == null) return null
            var found = false
            val text = StringBuilder()
            input.bufferedReader().use { reader ->
                var count = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    count++
                    if (!found && line.startsWith("// ==UserScript==")) found = true
                    if (!found && count > 32) break
                    text.append(line).append('\n')
                }
            }
            return if (found) text.toString() else ""
        }
    }
}
