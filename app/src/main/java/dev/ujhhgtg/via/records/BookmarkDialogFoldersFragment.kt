package dev.ujhhgtg.via.records

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.EditText
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.common.GeneratedDocumentState
import dev.ujhhgtg.via.data.BookmarkFolder
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.SettingsRecyclerView
import dev.ujhhgtg.via.skins.setSkinImageResource

/** b8.n0: searchable tree plus the animated inline new-folder row. */
class BookmarkDialogFoldersFragment : Fragment() {
    private val owner get() = requireParentFragment() as BookmarkEditDialogFragment
    private lateinit var list: RecyclerView
    private lateinit var rows: BookmarkFolderAdapter
    private lateinit var header: View
    private lateinit var search: EditText
    private lateinit var searchButton: ImageView
    private lateinit var addButton: ImageView
    private lateinit var creation: View
    private lateinit var name: EditText
    private var queryJob: Runnable? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View = inflater.inflate(R.layout.bookmark_folder_picker, container, false)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        applyBookmarkFont(view)
        header = view.findViewById(R.id.bookmark_folder_title)
        search = view.findViewById(R.id.bookmark_folder_search)
        searchButton = view.findViewById<ImageView>(R.id.bookmark_folder_search_button).apply { setSkinImageResource(R.drawable.search); setOnClickListener { showSearch(search.visibility != View.VISIBLE) } }
        creation = view.findViewById(R.id.bookmark_folder_creation)
        name = view.findViewById(R.id.bookmark_folder_new_name)
        addButton = view.findViewById<ImageView>(R.id.bookmark_folder_add).apply {
            setSkinImageResource(R.drawable.plus)
            setOnClickListener { if (search.isVisible) showSearch(false) else showCreation(creation.visibility != View.VISIBLE) }
        }
        view.findViewById<ImageView>(R.id.bookmark_folder_back).apply {
            setSkinImageResource(R.drawable.favorite_back)
            setOnClickListener { if (creation.isVisible) showCreation(false); owner.showPage(0) }
        }
        view.findViewById<ImageView>(R.id.bookmark_folder_new_icon).setSkinImageResource(R.drawable.bookmark_new_folder, "ic_folder_add")
        view.findViewById<View>(R.id.bookmark_folder_new_ok).setOnClickListener { createFolder() }
        name.setOnKeyListener { _, code, _ -> if (code == KeyEvent.KEYCODE_ENTER) { createFolder(); true } else false }
        list = view.findViewById<RecyclerView>(android.R.id.list).apply {
            layoutManager = LinearLayoutManager(context); itemAnimator = DefaultItemAnimator()
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS; edgeEffectFactory = SettingsRecyclerView.StretchEdgeEffectFactory()
            // b8.n0 -> r3.a/c6.c draws only the upper scroll-boundary line.
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.via_control_stroke) }
                override fun onDrawOver(canvas: android.graphics.Canvas, parent: RecyclerView, state: RecyclerView.State) {
                    val first = parent.getChildAt(0) ?: return
                    if (parent.getChildAdapterPosition(first) > 0 || first.top < 0) canvas.drawRect(
                        parent.paddingLeft.toFloat(), 0f, (parent.width - parent.paddingRight).toFloat(),
                        (resources.displayMetrics.density + .5f).toInt().toFloat(), paint)
                }
            })
        }
        rows = BookmarkFolderAdapter(false, owner::chooseFolder)
        list.adapter = rows
        search.setText(owner.folderSearch)
        search.addTextChangedListener(bookmarkTextWatcher { query ->
            owner.folderSearch = query
            queryJob?.let(search::removeCallbacks)
            queryJob = Runnable { if (isAdded && this.view != null) refreshFolders() }.also { search.postDelayed(it, 100) }
        })
        refreshFolders()
    }
    internal fun refreshFolders() {
        if (!::rows.isInitialized) return
        val query = owner.folderSearch
        val values = if (query.isNotEmpty() && query.trim().isEmpty()) emptyList() else if (query.isNotEmpty()) {
            // o9.g.z: SQL LIKE semantics and database order, not a locale substring substitute.
            owner.database.readableDatabase.query("bookmark_folders", arrayOf("_id", "title", "parent_folder_id", "ordering", "created_at", "last_updated_at"),
                "title like ? or title like ?", arrayOf("%_${query.trim()}%", "${query.trim()}%"), null, null, null).use { cursor ->
                buildList { while (cursor.moveToNext()) {
                    val folder = BookmarkFolder(cursor.getString(0), cursor.getString(1), cursor.getString(2).orEmpty(), cursor.getInt(3), cursor.getLong(4), cursor.getLong(5))
                    add(BookmarkFolderRow(folder, 0, folder.id == owner.folder.id))
                } }
            }
        } else bookmarkFolderTree(owner.repo.listFolders(), owner.folder.id, BrowserPreferences(requireContext()).bookmarkOrder)
        rows.verticalPadding = 14 - if (values.size > 6) minOf((values.size - 6) / 2, 8) else 0
        rows.submit(values)
        list.visibility = if (values.isEmpty()) View.GONE else View.VISIBLE
        view?.findViewById<View>(R.id.bookmark_folder_empty)?.visibility = if (values.isEmpty()) View.VISIBLE else View.GONE
        val selected = values.indexOfFirst { it.selected }
        if (selected >= 0) list.post { list.scrollToPosition(selected) }
    }
    private fun createFolder() {
        bookmarkKeyboard(addButton, false)
        val title = name.text.toString()
        if (title.isEmpty()) { bookmarkRequired(name); return }
        val folder = owner.repo.addFolder(normalizeBookmarkTitle(title), owner.folder.id, nextBookmarkFolderOrder(owner.database))
        if (owner.repo.findFolder(folder.id) != null) {
            GeneratedDocumentState.mark(GeneratedDocumentState.BOOKMARKS)
            owner.chooseFolder(folder)
        }
        name.setText(""); showCreation(false)
    }
    private fun showSearch(visible: Boolean) {
        if (creation.isVisible) showCreation(false)
        if (visible) {
            header.animate().alpha(0f).setDuration(100).withEndAction { header.visibility = View.GONE }.start()
            searchButton.animate().alpha(0f).setDuration(100).withEndAction { searchButton.visibility = View.GONE }.start()
            search.alpha = 0f; search.visibility = View.VISIBLE; search.animate().alpha(1f).setDuration(100).start()
            addButton.animate().rotation(45f).setDuration(80).start(); bookmarkKeyboard(search, true)
        } else {
            search.setText(""); bookmarkKeyboard(search, false)
            header.visibility = View.VISIBLE; header.animate().alpha(1f).setDuration(100).start()
            searchButton.visibility = View.VISIBLE; searchButton.animate().alpha(1f).setDuration(100).start()
            search.animate().alpha(0f).setDuration(100).withEndAction { search.visibility = View.GONE }.start()
            addButton.animate().rotation(0f).setDuration(80).start()
        }
    }
    private fun showCreation(visible: Boolean) {
        val height = resources.getDimensionPixelSize(R.dimen.menu_footer_height).toFloat()
        val easing = PathInterpolator(.2f, .2f, .8f, .8f)
        if (visible) {
            creation.alpha = 0f; creation.visibility = View.VISIBLE; list.translationY = -height
            addButton.animate().rotation(45f).setDuration(80).start()
            creation.animate().alpha(1f).setDuration(150).setInterpolator(easing).start()
            list.animate().translationY(0f).setDuration(150).setInterpolator(easing).start()
        } else {
            addButton.animate().rotation(0f).setDuration(80).start()
            creation.animate().alpha(0f).setDuration(150).setInterpolator(easing).withEndAction { creation.visibility = View.GONE }.start()
            list.animate().translationY(-height).setDuration(150).setInterpolator(easing).withEndAction { list.translationY = 0f }.start()
        }
    }
    override fun onDestroyView() { queryJob?.let(search::removeCallbacks); super.onDestroyView() }
}
