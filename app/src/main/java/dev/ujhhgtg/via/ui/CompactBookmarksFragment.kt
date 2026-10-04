package dev.ujhhgtg.via.ui

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.promeg.pinyinhelper.Pinyin
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.skins.SkinResources
import dev.ujhhgtg.via.data.BookmarkRepository
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.SettingsRecyclerView
import java.util.Locale

/** a8.k / cb.b: the compact folder-aware bookmark chooser used by a bookmark-menu long press. */
class CompactBookmarksFragment : DialogFragment() {
    private data class Row(val title: String, val order: Int, val folder: String? = null, val url: String? = null, val parent: Boolean = false)
    private data class Cache(val folder: String, val position: Int, val offset: Int, val expires: Long)
    private lateinit var database: BrowserDatabase
    private lateinit var bookmarks: BookmarkRepository
    private lateinit var preferences: BrowserPreferences
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var title: TextView
    private val rows = ArrayList<Row>()
    private var folder = ""
    private var selection: String? = null
    private var restore: Cache? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setStyle(STYLE_NO_TITLE, R.style.BrowserMenuWindow)
        database = BrowserDatabase(requireContext()); bookmarks = BookmarkRepository(database); preferences = BrowserPreferences(requireContext())
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = inflater.inflate(R.layout.bookmark_dialog, container, false)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        title = view.findViewById(R.id.bookmark_dialog_title)
        title.setTypeface(preferences.selectedTypeface(), android.graphics.Typeface.BOLD)
        empty = view.findViewById<TextView>(R.id.bookmark_dialog_empty).apply { text = "¯\\_(ツ)_/¯"; contentDescription = getString(R.string.empty_hint); typeface = preferences.selectedTypeface() }
        list = view.findViewById<RecyclerView>(android.R.id.list).apply {
            layoutManager = LinearLayoutManager(context); itemAnimator = DefaultItemAnimator(); overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            edgeEffectFactory = SettingsRecyclerView.StretchEdgeEffectFactory()
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x30808080 }
                override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                    val first = parent.getChildAt(0) ?: return
                    if (parent.getChildAdapterPosition(first) > 0 || first.top < 0) canvas.drawRect(parent.paddingLeft.toFloat(), 0f, (parent.width - parent.paddingRight).toFloat(), parent.context.dp(1f).toFloat(), paint)
                }
            })
            adapter = Adapter()
        }
        view.findViewById<View>(R.id.bookmark_dialog_open).setOnClickListener {
            savePosition()
            val state = cache
            requireActivity().supportFragmentManager.setFragmentResult(MANAGE_RESULT, Bundle().apply {
                putString("folder", folder); putInt("position", state?.position ?: 0); putInt("offset", state?.offset ?: 0)
            })
            dismiss()
        }
        restore = if (arguments?.getBoolean("restore_state", false) == true) cache?.takeIf { it.expires >= SystemClock.elapsedRealtime() / 1000 } else null
        load(restore?.folder ?: arguments?.getString("folder").orEmpty())
    }
    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            val background = requireContext().obtainStyledAttributes(intArrayOf(R.attr.viaDialogBackground))
            try { window.setBackgroundDrawable(background.getDrawable(0)) } finally { background.recycle() }
            val shortSide = minOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
            window.setLayout(minOf(shortSide * 4 / 5, requireContext().dp(450f)), minOf(shortSide * 4 / 5, requireContext().dp(600f)))
            window.setGravity(Gravity.CENTER)
        }
    }
    private fun load(requested: String) {
        val stored = bookmarks.findFolder(requested)
        folder = stored?.id.orEmpty()
        title.text = if (folder.isEmpty()) getString(R.string.action_bookmarks) else stored?.title?.takeIf { it.isNotBlank() } ?: getString(R.string.untitled)
        rows.clear()
        if (folder.isNotEmpty()) rows += Row("..", 0, bookmarks.findFolder(stored?.parentFolderId.orEmpty())?.id.orEmpty(), parent = true)
        rows += bookmarks.listFolders(folder).map { Row(it.title.orEmpty(), it.ordering, folder = it.id) }
        rows += bookmarks.listItems(folder).map { Row(it.title.orEmpty(), it.ordering, url = it.url) }
        rows.sortWith { a, b -> when {
            a.parent != b.parent -> if (a.parent) -1 else 1
            (a.folder != null) != (b.folder != null) -> if (a.folder != null) -1 else 1
            preferences.bookmarkOrder == 1 -> b.order - a.order
            preferences.bookmarkOrder == 2 -> compareTitles(a.title, b.title)
            else -> a.order - b.order
        } }
        list.adapter?.notifyDataSetChanged()
        list.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE; empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        restore?.let { previous ->
            restore = null
            if (requested == folder && rows.isNotEmpty()) (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(previous.position.coerceIn(0, rows.lastIndex), previous.offset)
        }
    }
    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = rows.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(TextView(requireContext()).apply {
            layoutParams = RecyclerView.LayoutParams(-1, -2)
            setPadding(context.dp(16f), context.dp(12f), context.dp(16f), context.dp(12f))
            background = ContextCompat.getDrawable(context, R.drawable.flat_ripple)
            setTextColor(color(R.attr.viaPrimaryTextColor)); gravity = Gravity.CENTER_VERTICAL
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.night_preview_text_size).toFloat())
            compoundDrawablePadding = context.dp(16f); textDirection = View.TEXT_DIRECTION_LOCALE; typeface = preferences.selectedTypeface()
            ellipsize = null; setSingleLine(true); setFadingEdgeLength(context.dp(24f)); isHorizontalFadingEdgeEnabled = true
        })
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = rows[position]
            val icon = if (row.folder != null) SkinResources.drawable(requireContext(), R.drawable.folder)?.mutate()?.apply { setTint(color(R.attr.viaSubtleColor)) }
                else favicon(row.url.orEmpty()) ?: SkinResources.drawable(requireContext(), R.drawable.star)?.mutate()?.apply { setTint(color(R.attr.viaSubtleColor)) }
            val size = requireContext().dp(20f); icon?.setBounds(0, 0, size, size)
            holder.text.setCompoundDrawablesRelative(icon, null, null, null)
            holder.text.text = if (row.title.length > 256) row.title.substring(0, 256) + "..." else row.title
            holder.text.setOnClickListener { if (row.folder != null) load(row.folder) else { selection = row.url; dismiss() } }
        }
    }
    private class Holder(val text: TextView) : RecyclerView.ViewHolder(text)
    private fun favicon(url: String): Drawable? {
        // a8.k's compact chooser uses y0.d + Drawable.createFromPath, not the x0 bitmap LRU.
        return dev.ujhhgtg.via.home.HomeIcons.file(requireContext(), url)?.let { Drawable.createFromPath(it.path) }
    }
    private fun savePosition() {
        if (!::list.isInitialized) return
        val manager = list.layoutManager as? LinearLayoutManager ?: return
        val first = manager.getChildAt(0)
        cache = Cache(folder, first?.let(manager::getPosition) ?: 0, first?.top ?: 0, SystemClock.elapsedRealtime() / 1000 + 180)
    }
    override fun onPause() { savePosition(); super.onPause() }
    override fun onDismiss(dialog: DialogInterface) {
        selection?.let { selected -> requireActivity().supportFragmentManager.setFragmentResult(RESULT, Bundle().apply { putStringArray("urls", arrayOf(selected)); putIntArray("wheres", intArrayOf(0)) }) }
        super.onDismiss(dialog)
    }
    override fun onDestroy() { database.close(); super.onDestroy() }
    private fun color(attribute: Int) = requireContext().obtainStyledAttributes(intArrayOf(attribute)).let { try { it.getColor(0, 0) } finally { it.recycle() } }
    companion object {
        const val RESULT = "result"
        const val MANAGE_RESULT = "bookmark_dialog_manage"
        private var cache: Cache? = null
        fun newInstance() = CompactBookmarksFragment().apply { arguments = Bundle().apply { putBoolean("restore_state", true) } }
        private fun compareTitles(first: String, second: String): Int {
            if (first.isEmpty() || second.isEmpty()) return first.length - second.length
            for (i in 0 until minOf(first.length, second.length)) {
                val order = Pinyin.toPinyin(first[i]).uppercase(Locale.ROOT).compareTo(Pinyin.toPinyin(second[i]).uppercase(Locale.ROOT))
                if (order != 0) return order
            }
            return first.length - second.length
        }
    }
}
