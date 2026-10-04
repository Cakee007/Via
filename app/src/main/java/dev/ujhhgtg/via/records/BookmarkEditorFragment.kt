package dev.ujhhgtg.via.records

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.data.BookmarkFolder
import dev.ujhhgtg.via.data.BookmarkItem
import dev.ujhhgtg.via.data.BookmarkRepository
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.SettingsPageFragment
import dev.ujhhgtg.via.settings.SettingsToolbar

/** a8.g1/o1: records editor with the full inline folder tree and optional homepage checkbox. */
class BookmarkEditorFragment : SettingsPageFragment() {
    private lateinit var database: BrowserDatabase
    private lateinit var repo: BookmarkRepository
    private lateinit var form: BookmarkEditorForm
    private var item: BookmarkItem? = null
    private var folder = BookmarkFolder("", null)
    private var restored: Bundle? = null
    private var folderCreated = false
    private var isNew = false
    private var lastSave = 0L
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        database = BrowserDatabase(requireContext()); repo = BookmarkRepository(database); restored = state
        val id = arguments?.getString("id")
        item = id?.takeIf(String::isNotEmpty)?.let(repo::findItem)
        isNew = item == null
        val initialFolder = item?.folderId?.takeIf(String::isNotEmpty) ?: arguments?.getString("folder").orEmpty()
        folder = repo.findFolder(state?.getString("folder") ?: initialFolder) ?: BookmarkFolder("", null)
        folderCreated = state?.getBoolean("folder_created") ?: false
    }
    override fun configureToolbar(toolbar: SettingsToolbar) {
        toolbar.setTitle(if (isNew) R.string.action_add_bookmark else R.string.title_edit_bookmark)
        toolbar.addAction(null, R.string.done) { save() }
    }
    override fun createContent(inflater: LayoutInflater, container: ViewGroup?): View {
        form = BookmarkEditorForm(requireContext(), true, arguments?.getBoolean("favorite_enabled") == true, ::chooseFolder, ::createFolder)
        form.name.setText(restored?.getString("title") ?: item?.title ?: arguments?.getString("title"))
        form.url!!.setText(restored?.getString("url") ?: item?.url ?: arguments?.getString("url") ?: "https://")
        form.favorite.isChecked = restored?.getBoolean("favorite") ?: false
        refreshFolders()
        if (restored?.getBoolean("expanded") == true) form.showTree(visible = true, animate = false)
        return form
    }
    private fun listenForFolder() {
        parentFragmentManager.setFragmentResultListener(BookmarkFolderEditorFragment.RESULT, viewLifecycleOwner) { key, result ->
            result.getString("id")?.let { repo.findFolder(it) }?.let { folderCreated = true; chooseFolder(it) }
            parentFragmentManager.clearFragmentResultListener(key)
        }
    }
    private fun chooseFolder(value: BookmarkFolder) { folder = value; refreshFolders() }
    private fun refreshFolders() {
        form.setFolder(folder)
        form.folderRows.submit(bookmarkFolderTree(repo.listFolders(), folder.id, BrowserPreferences(requireContext()).bookmarkOrder))
    }
    private fun createFolder() { listenForFolder(); (requireActivity() as Shell).navigate(BookmarkFolderEditorFragment.newInstance(parentFolderId = folder.id, creationEnabled = true)) }
    private fun save() {
        val title = form.name.text.toString(); val url = form.url!!.text.toString().trim()
        if (title.isEmpty()) { bookmarkRequired(form.name); return }
        if (url.isEmpty()) { bookmarkRequired(form.url!!); return }
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastSave < 300L) return
        lastSave = now
        val saved = saveEditedBookmark(requireContext(), database, item, folder.id, url, title, form.favoriteEnabled && form.favorite.isChecked) ?: return
        item = saved; folderCreated = false; isNew = false
        parentFragmentManager.setFragmentResult(RESULT, Bundle().apply { putString("id", saved.id) })
        parentFragmentManager.popBackStack()
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("folder", folder.id); out.putBoolean("folder_created", folderCreated)
        if (::form.isInitialized) { out.putString("title", form.name.text.toString()); out.putString("url", form.url!!.text.toString()); out.putBoolean("favorite", form.favorite.isChecked); out.putBoolean("expanded",
            form.tree.isVisible) }
        super.onSaveInstanceState(out)
    }
    override fun onHiddenChanged(hidden: Boolean) { super.onHiddenChanged(hidden); if (hidden && ::form.isInitialized) bookmarkKeyboard(form, false) }
    override fun onPause() { if (::form.isInitialized) bookmarkKeyboard(form, false); super.onPause() }
    override fun onDestroy() {
        if (folderCreated) parentFragmentManager.setFragmentResult(RESULT, Bundle().apply { putBoolean("folder_created", true) })
        database.close(); super.onDestroy()
    }
    companion object {
        const val RESULT = "a8.g1"
        fun newInstance(id: String, url: String?, title: String?, folder: String, favoriteEnabled: Boolean = false) = BookmarkEditorFragment().apply {
            arguments = Bundle().apply { putString("id", id); putString("url", url); putString("title", title); putString("folder", folder); putBoolean("favorite_enabled", favoriteEnabled) }
        }
    }
}
