package dev.ujhhgtg.via.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Html
import android.text.format.DateUtils
import android.view.View
import android.webkit.URLUtil
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.browser.script.ScriptResources
import dev.ujhhgtg.via.browser.script.ScriptStore
import dev.ujhhgtg.via.browser.script.UserScript
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/** ta.u / ta.b0: editable user overrides alongside the script's immutable metadata rules. */
class ScriptDetailsFragment : SettingsListFragment() {
    private lateinit var store: ScriptStore
    private lateinit var resourcesCache: ScriptResources
    private lateinit var rows: ScriptSettingsAdapter
    private val worker = Executors.newSingleThreadExecutor()
    private var script: UserScript? = null
    private var overrides: ScriptOverrides? = null
    private val patterns = mutableMapOf<Int, ScriptPattern>()
    private val resourceUrls = mutableMapOf<Int, String>()
    private var scrolled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ScriptStore(requireContext()); resourcesCache = ScriptResources(requireContext())
    }
    override fun configureToolbar(toolbar: SettingsToolbar) { toolbar.setTitle(R.string.action_edit) }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rows = ScriptSettingsAdapter(::click)
        list.adapter = rows
        loadScript()
    }
    private fun loadScript() {
        val id = arguments?.getInt("id", -1) ?: -1
        val identity = arguments?.getString("script_id")
        worker.execute {
            val loaded = if (identity != null) store.findByScriptId(identity) else store.find(id)
            onUi {
                script = loaded
                overrides = loaded?.let(::ScriptOverrides)
                bindRows()
            }
        }
    }
    private fun bindRows() {
        val script = script ?: return
        val state = overrides ?: return
        patterns.clear(); resourceUrls.clear()
        var key = 100
        var missingPosition = -1
        val items = buildList {
            add(SettingsRow(INFO, script.name, script.version))
            add(SettingsRow(RUN_AT, getString(R.string.run_at), runAt(state.runAt).ifEmpty { defaultRunAt(script) }))
            add(SettingsHeadingRow(getString(R.string.matches)))
            state.rows(true).forEach { pattern -> patterns[++key] = pattern; add(SettingsRow(key, pattern.text)) }
            add(ScriptAddRow(ADD_MATCH, getString(R.string.add_match)))
            add(SettingsHeadingRow(getString(R.string.excludes)))
            state.rows(false).forEach { pattern -> patterns[++key] = pattern; add(SettingsRow(key, pattern.text)) }
            add(ScriptAddRow(ADD_EXCLUDE, getString(R.string.add_exclude)))
            fun dependencies(title: Int, urls: List<String>) {
                if (urls.isEmpty()) return
                add(SettingsHeadingRow(getString(title)))
                urls.sorted().forEach { url ->
                    val path = resourcesCache.path(url)
                    val available = path.isNullOrEmpty() || File(path).isFile
                    if (!available && missingPosition < 0) missingPosition = size
                    resourceUrls[++key] = url
                    add(ScriptResourceRow(key, resourceLabel(url), available))
                }
            }
            dependencies(R.string.requires, script.requires)
            dependencies(R.string.resources, script.resources.values.toList())
            add(SettingsHeadingRow(getString(R.string.settings_advanced)))
            add(SettingsRow(SOURCE, getString(R.string.edit_source_code)))
            add(SettingsRow(RESET, getString(R.string.action_reset)))
        }
        rows.submit(items)
        if (!scrolled) {
            scrolled = true
            if (arguments?.getBoolean("view_dependencies", false) == true && missingPosition >= 0) list.post { list.scrollToPosition(missingPosition) }
        }
    }
    private fun click(row: SettingsRow) {
        when (row.id) {
            INFO -> showScriptInfo()
            RUN_AT -> chooseRunAt()
            ADD_MATCH -> addPattern(true)
            ADD_EXCLUDE -> addPattern(false)
            SOURCE -> openSource()
            RESET -> ViaDialog(requireActivity()).title(R.string.action_reset).message(R.string.reset_script_message)
                .positive(android.R.string.ok) { _, _ -> overrides?.reset(); persist() }.negative(android.R.string.cancel).show()
            else -> {
                patterns[row.id]?.let(::editPattern)
                resourceUrls[row.id]?.let(::showResource)
            }
        }
    }
    private fun openSource() {
        val current = script ?: return
        parentFragmentManager.setFragmentResultListener(ScriptEditorFragment.RESULT, viewLifecycleOwner) { key, result ->
            parentFragmentManager.clearFragmentResult(key)
            val id = result.getInt("id")
            parentFragmentManager.setFragmentResult(RESULT, Bundle().apply { putInt("id", id) })
            loadScript()
            worker.execute { store.find(id)?.let(resourcesCache::ensure) }
        }
        (requireActivity() as Shell).navigate(ScriptEditorFragment.newInstance(current.id))
    }
    private fun chooseRunAt() {
        val current = script ?: return
        val state = overrides ?: return
        val values = intArrayOf(0, 1, 2, 4)
        val labels = values.map { if (it == 0) defaultRunAt(current) else runAt(it) }.toTypedArray()
        ViaDialog(requireActivity()).title(R.string.run_at).singleChoice(labels, values.indexOf(state.runAt).coerceAtLeast(0)) {
            state.runAt = values[it]; persist()
        }.show()
    }
    private fun addPattern(include: Boolean) {
        ViaDialog(requireActivity()).title(if (include) R.string.add_match else R.string.add_exclude)
            .input("https://*/*", getString(if (include) R.string.match else R.string.exclude), 1)
            .positive(android.R.string.ok) { _, result -> overrides?.add(result.edit?.firstOrNull(), include); persist() }
            .negative(android.R.string.cancel).show()
    }
    private fun editPattern(pattern: ScriptPattern) {
        val dialog = ViaDialog(requireActivity()).title(R.string.action_edit)
        if (pattern.custom) dialog.input(pattern.text, getString(if (pattern.include) R.string.match else R.string.exclude), 1)
        else dialog.message(pattern.text)
        dialog.neutral(R.string.action_delete) { overrides?.delete(pattern); persist() }
        if (pattern.custom) dialog.negative(android.R.string.cancel).positive(android.R.string.ok) { _, result ->
            overrides?.edit(pattern, result.edit?.firstOrNull()); persist()
        } else dialog.positive(android.R.string.ok)
        dialog.show()
    }
    private fun persist() {
        val current = script ?: return
        val value = overrides?.serialize()
        store.setUserOverrides(current.id, value)
        script = current.copy(userOverrides = value)
        bindRows()
    }
    private fun showScriptInfo() {
        val current = script ?: return
        val info = buildList {
            add(R.string.info_name to current.name)
            add(R.string.info_version to current.version)
            current.homepageUrl?.takeIf(String::isNotEmpty)?.let { add(R.string.info_homepage_url to it) }
            current.supportUrl?.takeIf(String::isNotEmpty)?.let { add(R.string.info_support_url to it) }
            current.downloadUrl?.takeIf(String::isNotEmpty)?.let { add(R.string.info_download_url to it) }
            add(R.string.info_updated to relativeTime(current.lastUpdatedAt))
            add(R.string.info_created to relativeTime(current.createdAt))
            if (current.content.isNotEmpty()) add(R.string.info_size to formatSize(current.content.length.toLong()))
        }
        ViaDialog(requireActivity()).title(R.string.script_info).message(infoMessage(info)).positive(android.R.string.ok).show()
    }
    private fun showResource(url: String) {
        val path = resourcesCache.path(url)
        val file = path?.takeIf(String::isNotEmpty)?.let(::File)
        if (file != null && !file.exists()) {
            bindRows()
            ViaDialog(requireActivity()).title(R.string.resource_info).message(R.string.resource_does_not_exist_message)
                .positive(R.string.menu_download) { _, _ -> downloadResource(url) }
                .negative(android.R.string.cancel).neutral(R.string.action_copy) { copyLink(url) }.show()
            return
        }
        val info = buildList {
            add(R.string.info_url to url)
            if (file != null) {
                add(R.string.info_file_path to file.absolutePath)
                add(R.string.info_updated to relativeTime(file.lastModified()))
                add(R.string.info_size to formatSize(file.length()))
            }
        }
        val dialog = ViaDialog(requireActivity()).title(R.string.resource_info).message(infoMessage(info)).positive(android.R.string.ok)
        if (file != null) dialog.neutral(R.string.view_downloads) {
            (requireActivity() as Shell).navigate(ScriptResourceViewerFragment.newInstance(file.absolutePath))
        }
        dialog.show()
    }
    private fun downloadResource(url: String) {
        ViaToast.show(requireContext(), R.string.download_pending)
        worker.execute {
            val complete = resourcesCache.ensure(url)
            onUi {
                bindRows()
                if (!complete) ViaToast.show(requireContext(), getString(R.string.file_download_failed,
                    dev.ujhhgtg.via.downloads.DownloadFiles.name(url, null, null)))
            }
        }
    }
    private fun copyLink(url: String) {
        (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("", url))
        ViaToast.show(requireContext(), R.string.toast_copy_url_successful)
    }
    private fun infoMessage(values: List<Pair<Int, String?>>): CharSequence = Html.fromHtml(values.joinToString("<br />") {
        "<b>${getString(it.first)}</b><br />${it.second.orEmpty()}"
    }, Html.FROM_HTML_MODE_LEGACY)
    private fun relativeTime(time: Long): String = if ((System.currentTimeMillis() - time) / 60_000 < 1) getString(R.string.just_now)
        else DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), 60_000, 65552).toString()
    private fun defaultRunAt(script: UserScript): String = runAt(script.runAt.value).let {
        if (it.isEmpty()) getString(R.string.default_set) else getString(R.string.default_description, it)
    }
    private fun runAt(value: Int) = when (value) { 1 -> "document-start"; 2 -> "document-end"; 4 -> "document-idle"; 8 -> "context-menu"; else -> "" }
    private fun resourceLabel(url: String): String {
        val start = if (URLUtil.isNetworkUrl(url)) url.indexOf("://") + 3 else 0
        val end = if (URLUtil.isNetworkUrl(url) && url.indexOf('/', start) == url.length - 1) url.length - 1 else url.length
        return url.substring(start, minOf(start + 256, end))
    }
    private fun onUi(action: () -> Unit) { activity?.runOnUiThread { if (view != null) action() } }
    override fun onDestroy() { worker.execute { store.close() }; worker.shutdown(); super.onDestroy() }

    companion object {
        const val RESULT = "editor_result"
        private const val ADD_MATCH = 1
        private const val ADD_EXCLUDE = 2
        private const val RUN_AT = 3
        private const val SOURCE = 4
        private const val INFO = 5
        private const val RESET = 6
        fun newInstance(id: Int, dependencies: Boolean = false) = ScriptDetailsFragment().apply {
            arguments = Bundle().apply { putInt("id", id); putBoolean("view_dependencies", dependencies) }
        }
        fun forScriptId(scriptId: String, dependencies: Boolean = false) = ScriptDetailsFragment().apply {
            arguments = Bundle().apply { putString("script_id", scriptId); putBoolean("view_dependencies", dependencies) }
        }
        private fun formatSize(size: Long): String {
            val (value, unit) = when {
                size >= 858_993_459.2 -> size / 1073741824.0 to "GB"
                size >= 838_860.8 -> size / 1048576.0 to "MB"
                size >= 819.2 -> size / 1024.0 to "KB"
                else -> size.toDouble() to "B"
            }
            return String.format(Locale.ROOT, "%.1f %s", value, unit)
        }
    }
}

internal data class ScriptPattern(val text: String, val include: Boolean, val custom: Boolean)

/** ta.b0.H/r/t/K/L, retaining original-vs-user rule provenance and inverse-rule deletion. */
internal class ScriptOverrides(private val script: UserScript) {
    private val originalIncludes = script.matches + script.includes
    private val originalExcludes = script.excludeMatches + script.excludes
    private val json = script.userOverrides?.let { runCatching { JSONObject(it) }.getOrNull() }
    private val includes = (UserScript.jsonStrings(json?.optString("matches")) + UserScript.jsonStrings(json?.optString("includes"))).toMutableList()
    private val excludes = (UserScript.jsonStrings(json?.optString("excludeMatches")) + UserScript.jsonStrings(json?.optString("excludes"))).toMutableList()
    var runAt = json?.optInt("runAt", 0) ?: 0

    fun rows(include: Boolean): List<ScriptPattern> {
        val negative = linkedMapOf<String, ScriptPattern>()
        originalExcludes.filterNot { it in includes }.forEach { negative.putIfAbsent(it, ScriptPattern(it, false, false)) }
        excludes.forEach { negative.putIfAbsent(it, ScriptPattern(it, false, true)) }
        if (!include) return negative.values.toList()
        val positive = linkedMapOf<String, ScriptPattern>()
        originalIncludes.filterNot { it in negative }.forEach { positive.putIfAbsent(it, ScriptPattern(it, true, false)) }
        includes.forEach { positive.putIfAbsent(it, ScriptPattern(it, true, true)) }
        return positive.values.toList()
    }
    fun add(raw: String?, include: Boolean) {
        val text = raw?.trim()?.takeIf(String::isNotEmpty) ?: return
        if (include) {
            if (text in includes) return
            excludes.remove(text)
            if (text !in originalIncludes) includes.add(text)
        } else {
            if (text in excludes) return
            includes.remove(text)
            if (text !in originalExcludes) excludes.add(text)
        }
    }
    fun delete(pattern: ScriptPattern) {
        if (!pattern.custom) add(pattern.text, !pattern.include)
        else if (pattern.include) includes.remove(pattern.text) else excludes.remove(pattern.text)
    }
    fun edit(pattern: ScriptPattern, raw: String?) {
        val text = raw?.trim()?.takeIf(String::isNotEmpty) ?: return
        if (text == pattern.text) return
        if (pattern.include) includes.remove(pattern.text) else excludes.remove(pattern.text)
        add(text, pattern.include)
    }
    fun reset() { includes.clear(); excludes.clear(); runAt = 0 }
    fun serialize(): String? {
        val value = JSONObject()
        if (runAt != 0 && runAt != script.runAt.value) value.put("runAt", runAt)
        if (includes.isNotEmpty()) value.put("matches", JSONArray(includes).toString())
        if (excludes.isNotEmpty()) value.put("excludeMatches", JSONArray(excludes).toString())
        return if (value.length() == 0) null else value.toString()
    }
}
