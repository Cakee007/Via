package dev.ujhhgtg.via.records

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import dev.ujhhgtg.via.ui.ViaToast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dev.ujhhgtg.via.BrowserFileProvider
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.tools.PdfViewerFragment
import dev.ujhhgtg.via.tools.SavedPageTools
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** eb.s: searchable dated offline records with single-file and batch ZIP export. */
class RecordsSavedPagesFragment : Fragment(), RecordsBackHandler {
    private lateinit var page: RecordsPageLayout
    private val adapter = RecordsRowAdapter()
    private var query = ""
    private var editing = false
    override val recordsHasTransientState get() = editing || query.isNotEmpty()
    private val selected = linkedSetOf<String>()
    private var files = emptyList<File>()
    private var exporting = emptyList<File>()
    private val host get() = requireActivity() as Shell
    private val exportFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) exportTo(uri)
    }
    override fun onCreate(state: Bundle?) { super.onCreate(state); query = state?.getString("query") ?: arguments?.getString("query").orEmpty(); exporting = state?.getStringArrayList("exporting")?.map(::File).orEmpty() }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        page = RecordsPageLayout(requireContext(), query) { query = it; editing = false; selected.clear(); render() }
        page.list.adapter = adapter; page.list.addItemDecoration(RecordsDateDecoration(adapter))
        return page
    }
    override fun onViewCreated(view: View, state: Bundle?) { super.onViewCreated(view, state); render() }
    override fun onResume() { super.onResume(); if (::page.isInitialized) render() }
    private var generation = 0L
    private fun render() {
        if (!::page.isInitialized) return
        val token = ++generation
        page.post { (parentFragment as? RecordsContainerFragment)?.updateRecordsNavigation() }
        val context = requireContext()
        val editingSnapshot = editing
        val selectedSnapshot = selected.toSet()
        val currentQuery = query
        RecordsLoading.load(token, {
            // SavedPageTools.list runs on the IO scheduler (directory scan).
            val loaded = SavedPageTools.list(context).filter { (it.extension.equals("mht", true) || it.extension.equals("pdf", true)) && it.nameWithoutExtension.contains(currentQuery, true) }
            loaded to loaded.map { file -> RecordsRow(file.nameWithoutExtension, fileSize(file.length()), icon = ContextCompat.getDrawable(context, if (file.extension.equals("pdf", true)) R.drawable.document else R.drawable.page),
                onClick = { if (editingSnapshot) toggle(file) else open(file) }, onLongClick = { anchor -> if (editingSnapshot) toggle(file) else actions(anchor, file); true }, dragKey = file.path,
                selected = if (editingSnapshot) file.path in selectedSnapshot else null, timestamp = file.lastModified(), iconSize = 24, multilineTitle = true) }
        }, { (loaded, rows) ->
            if (token != generation || !isAdded) return@load
            files = loaded
            selected.retainAll(files.map { it.path }.toSet())
            if (files.isEmpty()) editing = false
            adapter.submit(rows)
            page.setEmpty(files.isEmpty())
        })
        if (!editing) page.actions.show(right = listOf(RecordsActionBar.Action(getString(R.string.action_edit), files.isNotEmpty()) { editing = true; render() }))
        else page.actions.show(listOf(
            RecordsActionBar.Action(getString(if (selected.size == files.size && selected.isNotEmpty()) R.string.cancel_all else R.string.select_all)) { if (selected.size == files.size) selected.clear() else selected.addAll(files.map { it.path }); render() },
            RecordsActionBar.Action(if (selected.isEmpty()) getString(R.string.action_delete) else getString(R.string.delete_hint, selected.size), selected.isNotEmpty(), true) { confirmDelete(files.filter { it.path in selected }) },
            RecordsActionBar.Action(getString(R.string.export), selected.isNotEmpty()) { chooseExport(files.filter { it.path in selected }) }
        ), listOf(RecordsActionBar.Action(getString(R.string.done)) { finishEdit() }))
    }
    private fun toggle(file: File) { if (!selected.add(file.path)) selected.remove(file.path); render() }
    private fun finishEdit() { editing = false; selected.clear(); render() }
    override fun handleRecordsBack(): Boolean = when { editing -> { finishEdit(); true }; query.isNotEmpty() -> { page.search.setText(""); page.search.clearFocus(); page.search.hideKeyboard(); true }; else -> false }
    private fun open(file: File, mode: Int = 0) {
        if (file.extension.equals("pdf", true)) host.navigate(PdfViewerFragment.newInstance(Uri.fromFile(file), file.name))
        else host.openRecordFromRecords(Uri.fromFile(file).toString(), mode)
    }
    private fun actions(anchor: View, file: File) {
        val pdf = file.extension.equals("pdf", true)
        val labels = if (pdf) arrayOf(getString(R.string.action_preview), getString(R.string.open_with), getString(R.string.action_share), getString(R.string.export), getString(R.string.action_delete))
            else arrayOf(getString(R.string.action_open_in_background), getString(R.string.action_open_in_new), getString(R.string.action_share), getString(R.string.export), getString(R.string.action_delete))
        ViaDialog(requireActivity()).items(labels, onClick = { which -> when (which) {
            0 -> open(file, if (pdf) 0 else 2)
            1 -> if (pdf) external(file, false) else open(file, 1)
            2 -> external(file, true)
            3 -> chooseExport(listOf(file))
            4 -> confirmDelete(listOf(file))
        } }).showAnchored(anchor)
    }
    private fun external(file: File, share: Boolean) {
        val uri = BrowserFileProvider.uri(requireContext(), file)
        val intent = if (share) Intent(Intent.ACTION_SEND).setType(mime(file)).putExtra(Intent.EXTRA_STREAM, uri) else Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime(file))
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = android.content.ClipData.newRawUri(file.name, uri)
        runCatching { startActivity(Intent.createChooser(intent, getString(if (share) R.string.action_share else R.string.open_with))) }
            .onFailure { ViaToast.makeText(requireContext(), R.string.toast_operation_failed, ViaToast.LENGTH_SHORT).show() }
    }
    private fun confirmDelete(targets: List<File>) {
        if (targets.isEmpty()) return
        val message = if (targets.size == 1) getString(R.string.delete_item_message, targets[0].nameWithoutExtension) else getString(R.string.delete_items_message, targets.size)
        ViaDialog(requireActivity()).title(R.string.action_delete).message(message).positive(android.R.string.ok) { _, _ -> targets.forEach(File::delete); finishEdit() }.negative(android.R.string.cancel).show()
    }
    private fun chooseExport(targets: List<File>) {
        if (targets.isEmpty()) return
        exporting = targets
        val name = if (targets.size == 1) targets[0].name else "${getString(R.string.action_saved_pages)}_${java.text.SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(java.util.Date())}.zip"
        exportFile.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(if (targets.size == 1) mime(targets[0]) else "application/zip").putExtra(Intent.EXTRA_TITLE, name))
    }
    private fun exportTo(uri: Uri) {
        val context = requireContext(); val targets = exporting.toList()
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) { runCatching {
                val output = context.contentResolver.openOutputStream(uri) ?: return@runCatching false
                output.use { stream ->
                    if (targets.size == 1) targets[0].inputStream().use { it.copyTo(stream) }
                    else ZipOutputStream(stream).use { zip ->
                        val used = mutableSetOf<String>()
                        targets.forEach { file ->
                            var name = file.name; var suffix = 1
                            while (!used.add(name)) { name = "${file.nameWithoutExtension} (${suffix++}).${file.extension}" }
                            zip.putNextEntry(ZipEntry(name).apply { time = file.lastModified() }); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                        }
                    }
                }; true
            }.getOrDefault(false) }
            ViaToast.makeText(context, if (success) R.string.export_successfully else R.string.toast_operation_failed, ViaToast.LENGTH_SHORT).show()
        }
    }
    private fun mime(file: File) = if (file.extension.equals("pdf", true)) "application/pdf" else "multipart/related"
    private fun fileSize(bytes: Long): String {
        val (divisor, unit) = when { bytes >= 858993459.2 -> 1073741824.0 to "GB"; bytes >= 838860.8 -> 1048576.0 to "MB"; bytes >= 819.2 -> 1024.0 to "KB"; else -> 1.0 to "B" }
        return String.format(Locale.ROOT, "%.1f %s", bytes / divisor, unit)
    }
    override fun onSaveInstanceState(out: Bundle) { out.putString("query", query); out.putStringArrayList("exporting", ArrayList(exporting.map { it.path })); super.onSaveInstanceState(out) }
    companion object { fun newInstance(query: String = "") = RecordsSavedPagesFragment().apply { arguments = Bundle().apply { putString("query", query) } } }
}
