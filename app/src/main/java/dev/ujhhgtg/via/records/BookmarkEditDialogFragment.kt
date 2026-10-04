package dev.ujhhgtg.via.records

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.common.GeneratedDocumentState
import dev.ujhhgtg.via.data.BookmarkFolder
import dev.ujhhgtg.via.data.BookmarkItem
import dev.ujhhgtg.via.data.BookmarkRepository
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialogFragment

/** b8.n/y: the dedicated bookmark dialog and its fixed-height folder-selection page. */
class BookmarkEditDialogFragment : ViaDialogFragment() {
    internal lateinit var database: BrowserDatabase
    internal lateinit var repo: BookmarkRepository
    internal var item: BookmarkItem? = null
    internal var folder = BookmarkFolder("", null)
    internal var titleText = ""
    internal var urlText = ""
    internal var addHome = false
    internal var folderSearch = ""
    private lateinit var pager: ViewPager2
    private var page = 0
    private var editorHeight = 0
    private var lastAction = 0L

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        database = BrowserDatabase(requireContext()); repo = BookmarkRepository(database)
        val id = state?.getString("id") ?: arguments?.getString("id")
        val url = arguments?.getString("url")
        item = if (id != null) repo.findItem(id) else url?.let(repo::findByUrl)
        titleText = state?.getString("title") ?: item?.title ?: arguments?.getString("title").orEmpty()
        urlText = state?.getString("url") ?: item?.url ?: url ?: "https://"
        val itemFolder = item?.folderId?.takeIf(String::isNotEmpty)?.let(repo::findFolder)
        folder = if (state?.containsKey("folder") == true) repo.findFolder(state.getString("folder").orEmpty()) ?: BookmarkFolder("", null)
            else itemFolder ?: BookmarkDialogFolderCache.get()?.let(repo::findFolder) ?: BookmarkFolder("", null)
        addHome = state?.getBoolean("favorite") ?: false
        folderSearch = state?.getString("folder_search").orEmpty()
        page = state?.getInt("page") ?: 0
        editorHeight = state?.getInt("height") ?: 0
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = ViewPager2(requireContext()).apply {
        id = R.id.bookmark_edit_pager
        layoutParams = ViewGroup.LayoutParams(-1, if (editorHeight > 0) editorHeight else -1)
        isUserInputEnabled = false
        adapter = object : FragmentStateAdapter(this@BookmarkEditDialogFragment) {
            override fun getItemCount() = 2
            override fun createFragment(position: Int): Fragment = if (position == 0) BookmarkDialogFieldsFragment() else BookmarkDialogFoldersFragment()
        }
        pager = this
        setCurrentItem(page, false)
    }
    internal fun setEditorHeight(height: Int) {
        if (height <= 0 || height == editorHeight) return
        editorHeight = height
        pager.layoutParams = pager.layoutParams.apply { this.height = height }
    }
    internal fun showPage(position: Int) { page = position; pager.setCurrentItem(position, true) }
    internal fun chooseFolder(value: BookmarkFolder) {
        folder = value
        (childFragmentManager.findFragmentByTag("f0") as? BookmarkDialogFieldsFragment)?.updateFolder()
        (childFragmentManager.findFragmentByTag("f1") as? BookmarkDialogFoldersFragment)?.refreshFolders()
        showPage(0)
    }
    internal fun save(title: String, url: String, favorite: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAction < 300L) return
        lastAction = now
        val wasNew = item == null
        val saved = saveEditedBookmark(requireContext(), database, item, folder.id, url, title, favorite) ?: return
        item = saved
        if (wasNew) BookmarkDialogFolderCache.set(folder.id)
        ViaToast.show(requireContext(), R.string.message_add_bookmark)
        publish(saved.id); dismiss()
    }
    internal fun delete() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAction < 300L) return
        lastAction = now
        val current = item ?: return
        if (repo.deleteItems(current.id) <= 0) return
        GeneratedDocumentState.mark(GeneratedDocumentState.BOOKMARKS)
        ViaToast.show(requireContext(), getString(R.string.item_has_been_deleted, current.title))
        publish(current.id); dismiss()
    }
    private fun publish(id: String) {
        requireActivity().supportFragmentManager.setFragmentResult(RESULT, Bundle().apply {
            putString("id", id); putInt("position", arguments?.getInt("position", -1) ?: -1)
        })
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("id", item?.id); out.putString("title", titleText); out.putString("url", urlText)
        out.putString("folder", folder.id); out.putBoolean("favorite", addHome); out.putString("folder_search", folderSearch)
        out.putInt("page", page); out.putInt("height", editorHeight)
        super.onSaveInstanceState(out)
    }
    override fun onDestroy() { database.close(); super.onDestroy() }
    companion object {
        const val RESULT = "bookmarkDialogResult2"
        fun newInstance(id: String? = null, url: String? = null, title: String? = null, position: Int = -1) = BookmarkEditDialogFragment().apply {
            arguments = Bundle().apply { putString("id", id); putString("url", url); putString("title", title); putInt("position", position) }
        }
    }
}

/** b8.h, with the literal layout f rather than the records editor. */
class BookmarkDialogFieldsFragment : Fragment() {
    private val owner get() = requireParentFragment() as BookmarkEditDialogFragment
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = inflater.inflate(R.layout.bookmark_edit_dialog, container, false)
    override fun onViewCreated(view: View, state: Bundle?) {
        applyBookmarkFont(view)
        val title = view.findViewById<EditText>(R.id.bookmark_title_input)
        val url = view.findViewById<EditText>(R.id.bookmark_url_input)
        val favorite = view.findViewById<CheckBox>(R.id.bookmark_add_home)
        url.filters = arrayOf(android.text.InputFilter.LengthFilter(Int.MAX_VALUE))
        title.setText(owner.titleText); url.setText(owner.urlText); favorite.isChecked = owner.addHome
        title.addTextChangedListener(bookmarkTextWatcher { owner.titleText = it })
        url.addTextChangedListener(bookmarkTextWatcher { owner.urlText = it })
        favorite.setOnCheckedChangeListener { _, value -> owner.addHome = value }
        view.findViewById<TextView>(R.id.bookmark_edit_title).setText(if (owner.item == null) R.string.action_add_bookmark else R.string.title_edit_bookmark)
        view.findViewById<View>(R.id.bookmark_edit_delete).apply { visibility = if (owner.item == null) View.GONE else View.VISIBLE; setOnClickListener { owner.delete() } }
        view.findViewById<View>(R.id.bookmark_edit_cancel).setOnClickListener { owner.dismiss() }
        view.findViewById<View>(R.id.bookmark_folder_choice).setOnClickListener { owner.showPage(1) }
        view.findViewById<View>(R.id.bookmark_edit_ok).setOnClickListener {
            val label = title.text.toString().trim(); val target = url.text.toString().trim()
            if (label.isEmpty()) bookmarkRequired(title)
            else if (target.isEmpty()) bookmarkRequired(url)
            else owner.save(label, target, favorite.isChecked)
        }
        updateFolder()
        view.post { if (isAdded) owner.setEditorHeight(view.height) }
    }
    internal fun updateFolder() {
        view?.findViewById<TextView>(R.id.bookmark_folder_choice)?.text = if (owner.folder.id.isEmpty()) getString(R.string.root_folder) else owner.folder.title
    }
}

internal fun bookmarkTextWatcher(action: (String) -> Unit) = object : android.text.TextWatcher {
    override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = action(text?.toString().orEmpty())
    override fun afterTextChanged(editable: android.text.Editable?) = Unit
}
