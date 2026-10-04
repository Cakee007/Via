package dev.ujhhgtg.via.records

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.common.GeneratedDocumentState
import dev.ujhhgtg.via.data.BookmarkFolder
import dev.ujhhgtg.via.data.BookmarkRepository
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.SettingsPageFragment
import dev.ujhhgtg.via.settings.SettingsToolbar
import java.util.UUID

/** a8.d0/m0: create/edit a folder, including nested creation and cycle-safe parent selection. */
class BookmarkFolderEditorFragment : SettingsPageFragment() {
    private lateinit var database: BrowserDatabase
    private lateinit var repo: BookmarkRepository
    private lateinit var form: BookmarkEditorForm
    private var editing: BookmarkFolder? = null
    private var parentFolder = BookmarkFolder("", null)
    private var restored: Bundle? = null
    private var lastSave = 0L
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); restored = state
        database = BrowserDatabase(requireContext()); repo = BookmarkRepository(database)
        editing = arguments?.getString("id")?.let(repo::findFolder)
        val initialParent = arguments?.getString("parent_folder_id") ?: editing?.parentFolderId.orEmpty()
        parentFolder = repo.findFolder(state?.getString("parent") ?: initialParent) ?: BookmarkFolder("", null)
    }
    override fun configureToolbar(toolbar: SettingsToolbar) {
        toolbar.setTitle(if (editing == null) R.string.new_folder else R.string.title_edit_folder)
        toolbar.addAction(null, R.string.done) { save() }
    }
    override fun createContent(inflater: LayoutInflater, container: ViewGroup?): View {
        form = BookmarkEditorForm(requireContext(), false,
            favoriteEnabled = false,
            choose = ::chooseParent,
            create = if (arguments?.getBoolean("creation_enabled") == true) ::createFolder else null
        )
        form.name.setText(restored?.getString("title") ?: editing?.title.orEmpty())
        refreshFolders()
        if (restored?.getBoolean("expanded") == true) form.showTree(true, false)
        return form
    }
    private fun listenForFolder() {
        parentFragmentManager.setFragmentResultListener(RESULT, viewLifecycleOwner) { key, result ->
            result.getString("id")?.let(repo::findFolder)?.let(::chooseParent)
            parentFragmentManager.clearFragmentResultListener(key)
        }
    }
    private fun chooseParent(folder: BookmarkFolder) { parentFolder = folder; refreshFolders() }
    private fun refreshFolders() {
        form.setFolder(parentFolder)
        form.folderRows.submit(bookmarkFolderTree(repo.listFolders(), parentFolder.id, BrowserPreferences(requireContext()).bookmarkOrder))
    }
    private fun createFolder() { listenForFolder(); (requireActivity() as Shell).navigate(newInstance(parentFolderId = parentFolder.id, creationEnabled = true)) }
    private fun save() {
        val title = form.name.text.toString()
        if (title.isEmpty()) { bookmarkRequired(form.name); return }
        val elapsed = android.os.SystemClock.elapsedRealtime()
        if (elapsed - lastSave < 300L) return
        lastSave = elapsed
        // m0.q's smali divides by 0x64 (100), retained for source database compatibility.
        val now = System.currentTimeMillis() / 100L
        val old = editing
        val chosenParent = if (old != null && parentFolder.id in repo.descendantFolderIds(old.id)) old.parentFolderId else parentFolder.id
        val folder = old?.copy(title = normalizeBookmarkTitle(title), parentFolderId = chosenParent, lastUpdatedAt = now)
            ?: BookmarkFolder(UUID.randomUUID().toString(), normalizeBookmarkTitle(title), chosenParent, ordering = nextBookmarkFolderOrder(database), createdAt = now, lastUpdatedAt = now)
        if (!repo.saveFolder(folder)) return
        GeneratedDocumentState.mark(GeneratedDocumentState.BOOKMARKS)
        parentFragmentManager.setFragmentResult(RESULT, Bundle().apply { putString("id", folder.id) })
        parentFragmentManager.popBackStack()
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("parent", parentFolder.id)
        if (::form.isInitialized) { out.putString("title", form.name.text.toString()); out.putBoolean("expanded", form.tree.isVisible) }
        super.onSaveInstanceState(out)
    }
    override fun onHiddenChanged(hidden: Boolean) { super.onHiddenChanged(hidden); if (hidden && ::form.isInitialized) bookmarkKeyboard(form, false) }
    override fun onPause() { if (::form.isInitialized) bookmarkKeyboard(form, false); super.onPause() }
    override fun onDestroy() { database.close(); super.onDestroy() }
    companion object {
        const val RESULT = "a8.d0"
        fun newInstance(id: String? = null, parentFolderId: String? = null, creationEnabled: Boolean = false) = BookmarkFolderEditorFragment().apply {
            arguments = Bundle().apply { putString("id", id); putString("parent_folder_id", parentFolderId); putBoolean("creation_enabled", creationEnabled) }
        }
    }
}

internal fun normalizeBookmarkTitle(value: String) = buildString {
    value.forEach { if (Character.isWhitespace(it)) { if (it != '\r') append(' ') } else append(if (it == '\u00a0') ' ' else it) }
}
