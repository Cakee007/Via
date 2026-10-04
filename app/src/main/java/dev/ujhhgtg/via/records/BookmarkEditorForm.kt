package dev.ujhhgtg.via.records

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputFilter
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BookmarkFolder
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.SettingsRecyclerView
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.skins.setSkinImageResource
import dev.ujhhgtg.via.ui.dp

/** a8.g1.X2 and a8.d0.X2 share this form, including the inline folder tree. */
@SuppressLint("ViewConstructor")
internal class BookmarkEditorForm(
    context: Context,
    includeUrl: Boolean,
    val favoriteEnabled: Boolean,
    choose: (BookmarkFolder) -> Unit,
    create: (() -> Unit)?,
) : RelativeLayout(context) {
    private val nameBox = BookmarkInput(context, R.string.hint_title, 524289, EditorInfo.IME_ACTION_NEXT).apply { id = generateViewId() }
    val name get() = nameBox.edit
    private val urlBox = if (includeUrl) BookmarkInput(context, R.string.hint_url, 16, EditorInfo.IME_ACTION_DONE).apply { id = generateViewId() } else null
    val url get() = urlBox?.edit
    val folder = TextView(context).apply {
        id = generateViewId(); setHint(R.string.hint_folder); setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END
        textDirection = TEXT_DIRECTION_LOCALE; typeface = BrowserPreferences(context).selectedTypeface()
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
        setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, 0xff000000.toInt()))
        setPaddingRelative(0, context.dp(10f), 0, context.dp(10f)); setBackgroundResource(R.drawable.bookmark_folder_input)
    }
    val favorite = CheckBox(context).apply {
        id = generateViewId(); setText(R.string.action_add_to_homepage)
        context.obtainStyledAttributes(intArrayOf(R.attr.viaChoiceIndicator)).let { try { buttonDrawable = it.getDrawable(0) } finally { it.recycle() } }
        setTextColor(settingsColor(context, R.attr.viaSecondaryTextColor, 0xff777777.toInt()))
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_summary_size).toFloat())
        typeface = BrowserPreferences(context).selectedTypeface(); setPadding(context.dp(8f), context.dp(8f), context.dp(8f), context.dp(8f))
        visibility = if (favoriteEnabled) VISIBLE else GONE
    }
    private val folderIcon = ImageView(context).apply { id = generateViewId(); setSkinImageResource(R.drawable.folder); setColorFilter(settingsColor(context, R.attr.viaSubtleColor, 0)) }
    private val expand = ImageView(context).apply {
        id = generateViewId(); setSkinImageResource(R.drawable.menu_next); setColorFilter(settingsColor(context, R.attr.viaSubtleColor, 0))
        if (includeUrl) setPaddingRelative(context.dp(13f), 0, context.dp(13f), 0) else setPadding(context.dp(10f), context.dp(10f), context.dp(10f), context.dp(10f))
        setBackgroundResource(R.drawable.rounded_rect_ripple)
    }
    val folderRows = BookmarkFolderAdapter(true, choose, create)
    val tree = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context); itemAnimator = DefaultItemAnimator()
        edgeEffectFactory = SettingsRecyclerView.StretchEdgeEffectFactory(); overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        visibility = GONE; alpha = 0f; adapter = folderRows
    }
    init {
        setPaddingRelative(context.dp(16f), context.dp(12f), context.dp(16f), 0)
        addView(nameBox, LayoutParams(-1, -2).apply { bottomMargin = context.dp(12f) })
        urlBox?.let { box ->
            box.edit.filters = arrayOf(InputFilter.LengthFilter(Int.MAX_VALUE))
            addView(box, LayoutParams(-1, -2).apply { addRule(BELOW, nameBox.id); addRule(ALIGN_START, nameBox.id); addRule(ALIGN_END, nameBox.id); bottomMargin = context.dp(12f) })
        }
        addView(folderIcon, LayoutParams(context.dp(if (includeUrl) 22f else 20f), -2).apply { addRule(ALIGN_TOP, folder.id); addRule(ALIGN_BOTTOM, folder.id); marginEnd = context.dp(16f) })
        addView(folder, LayoutParams(-1, -2).apply {
            addRule(END_OF, folderIcon.id); addRule(START_OF, expand.id); addRule(BELOW, (urlBox ?: nameBox).id); bottomMargin = context.dp(12f)
        })
        addView(favorite, LayoutParams(-1, -2).apply { addRule(BELOW, folder.id); bottomMargin = context.dp(12f) })
        addView(expand, LayoutParams(context.dp(if (includeUrl) 48f else 42f), -2).apply { addRule(ALIGN_PARENT_END); addRule(ALIGN_TOP, folder.id); addRule(ALIGN_BOTTOM, folder.id) })
        addView(tree, LayoutParams(-1, -2).apply { addRule(ALIGN_START, nameBox.id); addRule(ALIGN_END, nameBox.id); addRule(BELOW, folder.id) })
        folder.setOnClickListener { showTree(tree.visibility != VISIBLE) }; expand.setOnClickListener { showTree(tree.visibility != VISIBLE) }
    }
    fun setFolder(value: BookmarkFolder) { folder.text = if (value.id.isEmpty()) context.getString(R.string.root_folder) else value.title }
    fun showTree(visible: Boolean, animate: Boolean = true) {
        val duration = if (animate) 140L else 0L
        val easing = AccelerateDecelerateInterpolator()
        if (visible) {
            expand.animate().rotationBy(180f).setDuration(duration).setInterpolator(easing).start()
            tree.visibility = VISIBLE; tree.animate().alpha(1f).setDuration(duration).setInterpolator(easing).start()
            if (favoriteEnabled) favorite.animate().alpha(0f).setDuration(duration).setInterpolator(easing).withEndAction { favorite.visibility = GONE }.start()
        } else {
            expand.animate().rotation(0f).setDuration(duration).setInterpolator(easing).start()
            tree.animate().alpha(0f).setDuration(duration).setInterpolator(easing).withEndAction { tree.visibility = GONE }.start()
            if (favoriteEnabled) { favorite.visibility = VISIBLE; favorite.animate().alpha(1f).setDuration(duration).setInterpolator(easing).start() }
        }
    }
}
