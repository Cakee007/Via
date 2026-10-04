package dev.ujhhgtg.via.records

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.common.GeneratedDocumentState
import dev.ujhhgtg.via.data.BookmarkFolder
import dev.ujhhgtg.via.data.BookmarkItem
import dev.ujhhgtg.via.data.BookmarkRepository
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.Favorite
import dev.ujhhgtg.via.data.FavoritesRepository
import dev.ujhhgtg.via.skins.SkinResources
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import java.text.Collator

/** cb.e0/h0/k1: folder navigation, details, ordering and batch selection. */
class RecordsBookmarksFragment : Fragment(), RecordsBackHandler {
    private lateinit var database: BrowserDatabase
    private lateinit var repo: BookmarkRepository
    private lateinit var preferences: BrowserPreferences
    private lateinit var page: RecordsPageLayout
    private val adapter = RecordsRowAdapter()
    private lateinit var touch: ItemTouchHelper
    private var folder = ""
    private var query = ""
    private var editing = false
    override val recordsHasTransientState get() = editing || query.isNotEmpty() || folder.isNotEmpty()
    private val selected = linkedSetOf<String>()
    private val host get() = requireActivity() as Shell
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val result = runCatching { requireContext().contentResolver.openInputStream(uri)?.use { repo.importHtml(it) } ?: 0 }
            GeneratedDocumentState.mark(GeneratedDocumentState.BOOKMARKS)
            result.onSuccess { ViaToast.makeText(requireContext(), getString(R.string.bookmarks_imported, it), ViaToast.LENGTH_SHORT).show(); render() }
                .onFailure { ViaToast.makeText(requireContext(), R.string.toast_operation_failed, ViaToast.LENGTH_SHORT).show() }
        }
    }
    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        if (uri != null) {
            val success = runCatching { requireContext().contentResolver.openOutputStream(uri)?.use { repo.exportHtml(it) } != null }.getOrDefault(false)
            ViaToast.makeText(requireContext(), if (success) R.string.bookmarks_exported_successfully else R.string.toast_operation_failed, ViaToast.LENGTH_SHORT).show()
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); database = BrowserDatabase.shared(requireContext()); repo = BookmarkRepository(database); preferences = BrowserPreferences(requireContext())
        folder = state?.getString("folder") ?: arguments?.getString("folder").orEmpty()
        query = state?.getString("query") ?: arguments?.getString("query").orEmpty()
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        page = RecordsPageLayout(requireContext(), query) { query = it; editing = false; selected.clear(); render() }
        page.list.adapter = adapter
        touch = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun isLongPressDragEnabled() = false
            override fun getMovementFlags(recyclerView: RecyclerView, holder: RecyclerView.ViewHolder) = makeMovementFlags(if (editing && query.isEmpty() && preferences.bookmarkOrder != 2) ItemTouchHelper.UP or ItemTouchHelper.DOWN else 0, 0)
            override fun onMove(recyclerView: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = adapter.rows().getOrNull(source.bindingAdapterPosition)?.dragKey ?: return false
                val to = adapter.rows().getOrNull(target.bindingAdapterPosition)?.dragKey ?: return false
                if (from.substringBefore(':') != to.substringBefore(':')) return false
                adapter.swap(source.bindingAdapterPosition, target.bindingAdapterPosition); return true
            }
            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) = Unit
            override fun clearView(recyclerView: RecyclerView, holder: RecyclerView.ViewHolder) { super.clearView(recyclerView, holder); persistOrder() }
        }).also { it.attachToRecyclerView(page.list) }
        return page
    }
    override fun onViewCreated(view: View, state: Bundle?) { super.onViewCreated(view, state); render() }
    override fun onResume() { super.onResume(); if (::page.isInitialized) render() }

    private var generation = 0L

    /**
     * cb.e0's data flow: the folder/count/list queries and favicon files run
     * on the IO scheduler, the row assembly lands on the main thread, and a
     * superseded token is dropped instead of touching the view.
     */
    private fun render() {
        if (!::page.isInitialized) return
        val token = ++generation
        page.post { (parentFragment as? RecordsContainerFragment)?.updateRecordsNavigation() }
        val folderId = folder
        val currentQuery = query
        val details = preferences.bookmarkViewMode == 1
        val order = preferences.bookmarkOrder
        val editingSnapshot = editing
        val selectedSnapshot = selected.toSet()
        val context = requireContext()
        RecordsLoading.load(token, {
            val folderRecord = repo.findFolder(folderId)
            val hint = if (folderId.isEmpty()) context.getString(R.string.search_hint) else context.getString(R.string.scoped_search, folderRecord?.title.orEmpty())
            val rows = mutableListOf<RecordsRow>()
            if (folderId.isNotEmpty() && currentQuery.isEmpty()) rows += RecordsRow("..", icon = SkinResources.drawable(context, R.drawable.folder), onClick = { enterFolder(folderRecord?.parentFolderId.orEmpty()) }, bookmark = true, iconSize = 22, description = context.getString(R.string.operation_goback))
            val counts = if (details) repo.childCounts() else emptyMap()
            val collator = Collator.getInstance()
            val folders = (if (currentQuery.isEmpty()) repo.listFolders(folderId) else repo.searchFolders(currentQuery, folderId)).let { values ->
                when (order) { 1 -> values.sortedByDescending { it.ordering }; 2 -> values.sortedWith { a, b -> collator.compare(a.title.orEmpty(), b.title.orEmpty()) }; else -> values }
            }
            val items = (if (currentQuery.isEmpty()) repo.listItems(folderId) else repo.search(currentQuery, folderId)).let { values ->
                when (order) { 1 -> values.sortedByDescending { it.ordering }; 2 -> values.sortedWith { a, b -> collator.compare(a.title.orEmpty(), b.title.orEmpty()) }; else -> values }
            }
            folders.forEach { item ->
                val key = "folder:${item.id}"; val title = item.title.orEmpty()
                rows += RecordsRow(title, if (details) context.resources.getQuantityString(R.plurals.items, counts[item.id] ?: 0, counts[item.id] ?: 0) else null,
                    icon = SkinResources.drawable(context, R.drawable.folder), onClick = { if (editingSnapshot) toggle(key) else enterFolder(item.id) },
                    onLongClick = { anchor -> if (editingSnapshot) toggle(key) else folderActions(anchor, item); true }, dragKey = key,
                    selected = if (editingSnapshot) key in selectedSnapshot else null, bookmark = true, iconSize = 22, description = context.getString(R.string.desc_bookmark_folder, title))
            }
            items.forEach { item ->
                val key = "item:${item.id}"
                rows += RecordsRow(item.title.orEmpty().ifEmpty { item.url }, if (details) recordDisplayUrl(item.url) else null,
                    SkinResources.drawable(context, R.drawable.star), faviconUrl = item.url, onClick = { if (editingSnapshot) toggle(key) else host.openRecordFromRecords(item.url, 0) },
                    onLongClick = { anchor -> if (editingSnapshot) toggle(key) else bookmarkActions(anchor, item); true }, dragKey = key,
                    selected = if (editingSnapshot) key in selectedSnapshot else null, bookmark = true, tintIcon = true, iconSize = 22)
            }
            FaviconRows(rows, hint)
        }, { result ->
            if (token != generation || !isAdded) return@load
            page.search.hint = result.hint
            val drag: ((RecyclerView.ViewHolder) -> Unit)? = if (editing && query.isEmpty() && preferences.bookmarkOrder != 2) { holder -> touch.startDrag(holder) } else null
            val rows = result.rows.map { it.copy(onDragStart = drag) }
            val selectable = rows.mapNotNull { it.dragKey }.toSet(); selected.retainAll(selectable)
            if (selectable.isEmpty()) editing = false
            adapter.faviconLoader = { url -> favicon(context, url) }
            adapter.submit(rows); page.setEmpty(rows.isEmpty()); showActions(selectable)
        })
    }

    private data class FaviconRows(val rows: List<RecordsRow>, val hint: String)
    private fun showActions(keys: Set<String>) {
        if (!editing) page.actions.show(listOf(RecordsActionBar.Action(getString(R.string.more), click = ::more)), listOf(RecordsActionBar.Action(getString(R.string.action_edit), keys.isNotEmpty()) { editing = true; render() }))
        else page.actions.show(listOf(
            RecordsActionBar.Action(getString(if (selected.size == keys.size && selected.isNotEmpty()) R.string.cancel_all else R.string.select_all)) { if (selected.size == keys.size) selected.clear() else selected.addAll(keys); render() },
            RecordsActionBar.Action(getString(R.string.move), selected.isNotEmpty()) { chooseFolder { target -> var changed = false; selected.forEach {
                val moved = if (it.startsWith("folder:")) repo.moveFolder(it.substringAfter(':'), target) else repo.moveItem(it.substringAfter(':'), target)
                changed = moved || changed
            }; if (changed) GeneratedDocumentState.mark(GeneratedDocumentState.BOOKMARKS); finishEdit() } },
            RecordsActionBar.Action(if (selected.isEmpty()) getString(R.string.action_delete) else getString(R.string.delete_hint, selected.size), selected.isNotEmpty(), true) { deleteSelected() },
            RecordsActionBar.Action(getString(R.string.open), selected.isNotEmpty()) { selectedUrls().forEach { host.openRecordFromRecords(it, 2) }; finishEdit() }
        ), listOf(RecordsActionBar.Action(getString(R.string.done)) { finishEdit() }))
    }
    private fun toggle(key: String) { if (!selected.add(key)) selected.remove(key); render() }
    private fun finishEdit() { editing = false; selected.clear(); render() }
    private fun enterFolder(id: String) { folder = id; query = ""; page.search.setText(""); finishEdit(); page.list.scrollToPosition(0) }
    override fun handleRecordsBack(): Boolean = when { editing -> { finishEdit(); true }; query.isNotEmpty() -> { page.search.setText(""); page.search.clearFocus(); page.search.hideKeyboard(); true }; folder.isNotEmpty() -> { enterFolder(repo.findFolder(folder)?.parentFolderId.orEmpty()); true }; else -> false }
    private fun more(anchor: View) {
        val labels = mutableListOf(getString(R.string.action_add_bookmark), getString(R.string.new_folder), getString(R.string.sort_order), getString(if (preferences.bookmarkViewMode == 1) R.string.hide_details else R.string.show_details))
        if (folder.isEmpty()) labels.addAll(listOf(getString(R.string.import_bookmarks), getString(R.string.export_bookmarks)))
        ViaDialog(requireActivity()).items(labels.toTypedArray(), onClick = { when (it) {
            0 -> openBookmarkEditor(null)
            1 -> newFolder()
            2 -> ViaDialog(requireActivity()).singleChoice(arrayOf(getString(R.string.sort_by_oldest_first), getString(R.string.sort_by_newest_first), getString(R.string.sort_by_alphabetical_order)), preferences.bookmarkOrder) { order -> if (preferences.bookmarkOrder != order) { preferences.bookmarkOrder = order; GeneratedDocumentState.mark(GeneratedDocumentState.BOOKMARKS) }; render() }.showAnchored(anchor, -anchor.width, 0)
            3 -> { preferences.bookmarkViewMode = if (preferences.bookmarkViewMode == 1) 0 else 1; render() }
            4 -> importFile.launch(arrayOf("text/html"))
            5 -> exportFile.launch("Via_${getString(R.string.action_bookmarks)}_${java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())}.html")
        } }).showAnchored(anchor, -anchor.width, 0)
    }
    private fun persistOrder() {
        var folders = adapter.rows().mapNotNull { it.dragKey?.takeIf { key -> key.startsWith("folder:") }?.substringAfter(':') }
        var items = adapter.rows().mapNotNull { it.dragKey?.takeIf { key -> key.startsWith("item:") }?.substringAfter(':') }
        if (preferences.bookmarkOrder == 1) { folders = folders.reversed(); items = items.reversed() }
        repo.reorderFolders(folders); repo.reorderItems(items)
        GeneratedDocumentState.mark(GeneratedDocumentState.BOOKMARKS)
    }
    private fun bookmarkActions(anchor: View, item: BookmarkItem) = showBookmarkPopup(anchor, item, host,
        onEdit = { openBookmarkEditor(item) },
        onDelete = { if (repo.deleteItems(item.id) > 0) GeneratedDocumentState.mark(GeneratedDocumentState.BOOKMARKS); render() },
        onAddToHomepage = { addFavorite(item.url, item.title) })
    private fun folderActions(anchor: View, item: BookmarkFolder) {
        ViaDialog(requireActivity()).items(arrayOf(getString(R.string.action_open_in_background), getString(R.string.action_open_in_new), getString(R.string.action_edit), getString(R.string.action_add_to_homepage), getString(R.string.action_delete)), onClick = { index -> when (index) {
            0, 1 -> repo.descendantFolderIds(item.id).flatMap { repo.listItems(it) }.forEach { host.openRecordFromRecords(it.url, if (index == 0) 2 else 1) }
            2 -> openFolderEditor(item)
            3 -> addFavorite("v://bookmarks/?folder=${item.id}&folderName=${android.net.Uri.encode(item.title.orEmpty())}", item.title)
            // cb.e0.o3: the folder confirmation names the folder and warns that its bookmarks go too.
            4 -> ViaDialog(requireActivity()).title(R.string.msg_delete_folder).message(getString(R.string.delete_bookmark_folder_message, item.title.orEmpty())).positive(android.R.string.ok) { _, _ -> if (repo.deleteFolders(item.id) > 0) GeneratedDocumentState.mark(GeneratedDocumentState.BOOKMARKS); render() }.negative(android.R.string.cancel).show()
        } }).showAnchored(anchor)
    }
    private fun addFavorite(url: String, title: String?) { FavoritesRepository(database).save(Favorite(url = url, title = title)); ViaToast.makeText(requireContext(), R.string.added_favorite_hint, ViaToast.LENGTH_SHORT).show() }
    private fun selectedUrls(): List<String> = selected.flatMap { key -> if (key.startsWith("item:")) listOfNotNull(repo.findItem(key.substringAfter(':'))?.url) else repo.descendantFolderIds(key.substringAfter(':')).flatMap { repo.listItems(it).map(BookmarkItem::url) } }.distinct()
    private fun deleteSelected() = ViaDialog(requireActivity()).title(R.string.action_delete).message(R.string.message_delete_following_items).positive(android.R.string.ok) { _, _ ->
        val deleted = repo.deleteItems(*selected.filter { it.startsWith("item:") }.map { it.substringAfter(':') }.toTypedArray()) +
            repo.deleteFolders(*selected.filter { it.startsWith("folder:") }.map { it.substringAfter(':') }.toTypedArray())
        if (deleted > 0) GeneratedDocumentState.mark(GeneratedDocumentState.BOOKMARKS)
        finishEdit()
    }.negative(android.R.string.cancel).show()
    /** cb.e0.P3/T2: one result listener per editor invocation. */
    private fun openBookmarkEditor(item: BookmarkItem?) {
        host.supportFragmentManager.setFragmentResultListener(BookmarkEditorFragment.RESULT, viewLifecycleOwner) { key, result ->
            if (result.getString("id") != null || result.getBoolean("folder_created")) render()
            host.supportFragmentManager.clearFragmentResultListener(key)
        }
        host.navigate(BookmarkEditorFragment.newInstance(item?.id.orEmpty(), item?.url, item?.title, item?.folderId ?: folder))
    }
    /** cb.e0.Q3/m3: creation is offered inside the editor only when editing an existing folder. */
    private fun openFolderEditor(item: BookmarkFolder?) {
        host.supportFragmentManager.setFragmentResultListener(BookmarkFolderEditorFragment.RESULT, viewLifecycleOwner) { key, result ->
            if (result.getString("id") != null) render()
            host.supportFragmentManager.clearFragmentResultListener(key)
        }
        host.navigate(BookmarkFolderEditorFragment.newInstance(item?.id, item?.parentFolderId ?: folder, item != null))
    }
    private fun newFolder() = openFolderEditor(null)
    private fun chooseFolder(done: (String) -> Unit) {
        val excluded = selected.filter { it.startsWith("folder:") }.flatMap { repo.descendantFolderIds(it.substringAfter(':')) }.toSet()
        val folders = listOf(BookmarkFolder("", getString(R.string.root_folder))) + repo.listFolders().filter { it.id !in excluded }
        ViaDialog(requireActivity()).title(R.string.select_folder).items(folders.map { it.title.orEmpty() }.toTypedArray(), onClick = { done(folders[it].id) }).show()
    }
    private fun favicon(context: Context, url: String): Drawable? {
        // cb.h0.o deliberately passes i0.f (without the port) to x0.
        val host = dev.ujhhgtg.via.browser.DocumentPolicy.host(url)
        return dev.ujhhgtg.via.home.HomeIcons.load(context, host)?.let { it.toDrawable(context.resources) }
    }
    override fun onSaveInstanceState(out: Bundle) { out.putString("folder", folder); out.putString("query", query); super.onSaveInstanceState(out) }

    companion object { fun newInstance(folder: String = "", query: String = "") = RecordsBookmarksFragment().apply { arguments = Bundle().apply { putString("folder", folder); putString("query", query) } } }
}
