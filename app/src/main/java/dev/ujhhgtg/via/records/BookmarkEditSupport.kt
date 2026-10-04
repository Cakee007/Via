package dev.ujhhgtg.via.records

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.database.sqlite.transaction
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.promeg.pinyinhelper.Pinyin
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.browser.UrlResolver
import dev.ujhhgtg.via.common.GeneratedDocumentState
import dev.ujhhgtg.via.data.BookmarkFolder
import dev.ujhhgtg.via.data.BookmarkItem
import dev.ujhhgtg.via.data.BookmarkRepository
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.Favorite
import dev.ujhhgtg.via.data.FavoritesRepository
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.skins.SkinResources
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dp
import java.util.Locale
import java.util.UUID

internal data class BookmarkFolderRow(val folder: BookmarkFolder, val depth: Int, val selected: Boolean)

/** a8.d: order siblings first, then insert their descendants after each parent. */
internal fun bookmarkFolderTree(folders: List<BookmarkFolder>, selected: String, order: Int): List<BookmarkFolderRow> {
    val remaining = folders.sortedWith { first, second -> when (order) {
        1 -> second.ordering.compareTo(first.ordering)
        2 -> compareBookmarkTitles(first.title, second.title)
        else -> first.ordering.compareTo(second.ordering)
    } }.toMutableList()
    val rows = arrayListOf(BookmarkFolderRow(BookmarkFolder("", null), 0, selected.isEmpty()))
    var index = 0
    while (index < rows.size) {
        val parent = rows[index]
        for (position in remaining.lastIndex downTo 0) {
            val folder = remaining[position]
            if (folder.parentFolderId == parent.folder.id) {
                rows.add(index + 1, BookmarkFolderRow(folder, parent.depth + 1, folder.id == selected))
                remaining.removeAt(position)
            }
        }
        index++
    }
    return rows
}

private fun compareBookmarkTitles(first: String?, second: String?): Int {
    if (first == null) return if (second == null) 0 else 1
    if (second == null) return -1
    for (index in 0 until minOf(first.length, second.length)) {
        val value = Pinyin.toPinyin(first[index]).uppercase(Locale.ROOT)
            .compareTo(Pinyin.toPinyin(second[index]).uppercase(Locale.ROOT))
        if (value != 0) return value
    }
    return first.length - second.length
}

/** a8.f/n: the same tree rows are used by both full-screen editors and b8.n0. */
internal class BookmarkFolderAdapter(
    private val rounded: Boolean,
    private val choose: (BookmarkFolder) -> Unit,
    private val create: (() -> Unit)? = null,
) : RecyclerView.Adapter<BookmarkFolderAdapter.Holder>() {
    private var rows = emptyList<BookmarkFolderRow>()
    var verticalPadding = 14
    fun submit(value: List<BookmarkFolderRow>) {
        val old = rows
        val changes = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size + if (create == null) 0 else 1
            override fun getNewListSize() = value.size + if (create == null) 0 else 1
            override fun areItemsTheSame(a: Int, b: Int) = if (a == old.size || b == value.size) a == old.size && b == value.size else old[a].folder.id == value[b].folder.id
            override fun areContentsTheSame(a: Int, b: Int) = if (a == old.size || b == value.size) a == old.size && b == value.size else old[a] == value[b]
        })
        rows = value
        changes.dispatchUpdatesTo(this)
    }
    override fun getItemCount() = rows.size + if (create == null) 0 else 1
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(TextView(parent.context).apply {
        layoutParams = RecyclerView.LayoutParams(-1, -2)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
        setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, 0xff000000.toInt()))
        typeface = BrowserPreferences(context).selectedTypeface(); textDirection = View.TEXT_DIRECTION_LOCALE
    })
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val text = holder.text; val context = text.context
        val row = rows.getOrNull(position)
        text.text = if (row == null) context.getString(R.string.new_folder) else if (row.folder.id.isEmpty()) context.getString(R.string.root_folder) else row.folder.title
        val inset = context.dp(16f)
        val padding = context.dp(if (row == null) 14f else verticalPadding.toFloat())
        text.setPaddingRelative(inset * minOf(10, (row?.depth ?: 0) + 1), padding, inset, padding)
        text.compoundDrawablePadding = context.dp(if (row == null) 18f else 16f)
        val size = context.resources.getDimensionPixelSize(R.dimen.favicon_size)
        val icon = SkinResources.drawable(context, if (row == null) R.drawable.bookmark_new_folder else R.drawable.folder, if (row == null) "ic_folder_add" else "ic_folder")?.apply {
            // a8.f/n -> support.widget.d.c -> g6.h.a leaves the resource state shared.
            setBounds(0, 0, size, size)
            colorFilter = PorterDuffColorFilter(text.currentTextColor, PorterDuff.Mode.SRC_IN)
        }
        text.setCompoundDrawablesRelative(icon, null, null, null)
        text.setSingleLine(true)
        if (row == null) { text.ellipsize = android.text.TextUtils.TruncateAt.END; text.isHorizontalFadingEdgeEnabled = false }
        else { text.ellipsize = null; text.setFadingEdgeLength(context.dp(24f)); text.isHorizontalFadingEdgeEnabled = true }
        text.background = if (row?.selected == true) {
            val selected = context.getColor(R.color.bookmark_folder_selected)
            if (rounded) GradientDrawable().apply { setColor(selected); cornerRadius = context.resources.getDimensionPixelSize(R.dimen.menu_corner_radius).toFloat() }
            else selected.toDrawable()
        } else ContextCompat.getDrawable(context, if (rounded) R.drawable.rounded_rect_ripple else R.drawable.flat_ripple)
        text.setOnClickListener { if (row == null) create?.invoke() else choose(row.folder) }
        text.setOnLongClickListener { false }
    }
    class Holder(val text: TextView) : RecyclerView.ViewHolder(text)
}

/** Original mark.via.common.widget.l1; the horizontally scrolling edit keeps its own padding zero. */
@SuppressLint("ViewConstructor")
internal class BookmarkInput(context: Context, hint: Int, input: Int, ime: Int) : HorizontalScrollView(context) {
    val edit = EditText(context).apply {
        id = generateViewId(); setSingleLine(); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        textDirection = TEXT_DIRECTION_LOCALE; setBackgroundColor(0); setPadding(0, 0, 0, 0)
        inputType = input; imeOptions = ime; setHint(hint); gravity = android.view.Gravity.CENTER_VERTICAL
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
        setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, 0xff000000.toInt()))
        setHintTextColor(settingsColor(context, R.attr.viaSecondaryTextColor, 0xff777777.toInt()))
        typeface = BrowserPreferences(context).selectedTypeface()
        if (android.os.Build.VERSION.SDK_INT >= 35) isLocalePreferredLineHeightForMinimumUsed = false
    }
    init {
        isHorizontalScrollBarEnabled = false; isVerticalScrollBarEnabled = false; isFillViewport = true
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        setPaddingRelative(0, context.dp(10f), 0, context.dp(10f)); setBackgroundResource(R.drawable.via_dialog_input)
        addView(edit, LayoutParams(-2, -2))
    }
    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        parent.requestDisallowInterceptTouchEvent(true); return super.dispatchTouchEvent(event)
    }
}

internal fun applyBookmarkFont(view: View) {
    if (view is TextView) {
        view.typeface = Typeface.create(BrowserPreferences(view.context).selectedTypeface(), view.typeface?.style ?: Typeface.NORMAL)
        if (view is EditText && android.os.Build.VERSION.SDK_INT >= 35) view.isLocalePreferredLineHeightForMinimumUsed = false
    }
    if (view is ViewGroup) for (index in 0 until view.childCount) applyBookmarkFont(view.getChildAt(index))
}

internal fun bookmarkRequired(edit: EditText) {
    ObjectAnimator.ofFloat(edit, "translationX", 0f, edit.context.dp(24f).toFloat(), 0f).apply {
        repeatCount = 2; repeatMode = android.animation.ValueAnimator.RESTART; duration = 280
        interpolator = PathInterpolator(.2f, .2f, .8f, .8f); start()
    }
    ViaToast.show(edit.context, edit.context.getString(R.string.is_required, edit.hint))
}

internal fun bookmarkKeyboard(view: View, visible: Boolean) {
    val keyboard = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    if (visible) { view.requestFocus(); keyboard.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT) }
    else keyboard.hideSoftInputFromWindow(view.windowToken, 0)
}

/** The dialog's editbookmarkcache has a 180-second lifetime and is not a persistent preference. */
internal object BookmarkDialogFolderCache {
    private var id = ""
    private var until = 0L
    fun get() = id.takeIf { until != 0L && SystemClock.elapsedRealtime() / 1000L <= until }
    fun set(value: String) { id = value; until = SystemClock.elapsedRealtime() / 1000L + 180L }
}

internal fun saveEditedBookmark(context: Context, database: BrowserDatabase, item: BookmarkItem?, folder: String, rawUrl: String, title: String, addHome: Boolean): BookmarkItem? {
    val url = UrlResolver.normalizeInput(rawUrl, BrowserPreferences(context).effectiveSearchUrl()) ?: rawUrl
    if (addHome) {
        FavoritesRepository(database).save(Favorite(url = url, title = title))
        GeneratedDocumentState.mark(GeneratedDocumentState.HOME_CONTENT)
    }
    val repo = BookmarkRepository(database)
    val now = System.currentTimeMillis() / 1000L
    val changed: BookmarkItem
    if (item == null) {
        if (url.isEmpty() || url.length > 1048576) return null
        // Returning before the block ends skips setTransactionSuccessful, so the insert rolls back.
        changed = database.writableDatabase.transaction {
            // o9.g.o deletes the previous URL before computing this folder's MAX.
            repo.deleteByUrl(url)
            val order = rawQuery("SELECT MAX(ordering) FROM bookmark_items WHERE folder_id = ?", arrayOf(folder))
                .use { if (it.moveToFirst()) it.getInt(0) + 1 else 0 }
            val created = BookmarkItem(UUID.randomUUID().toString(), url, normalizeBookmarkTitle(title), folder, order, now, now)
            if (!repo.save(created)) return null
            created
        }
    } else {
        changed = item.copy(url = url, title = normalizeBookmarkTitle(title), folderId = folder, lastUpdatedAt = now)
        if (!repo.save(changed)) return null
    }
    GeneratedDocumentState.mark(GeneratedDocumentState.BOOKMARKS)
    return changed
}

/** o9.g.g orders new folders globally, unlike bookmark items' per-folder order. */
internal fun nextBookmarkFolderOrder(database: BrowserDatabase): Int = database.readableDatabase
    .rawQuery("select max(ordering) from bookmark_folders", null).use { if (it.moveToFirst()) it.getInt(0) + 1 else 0 }
