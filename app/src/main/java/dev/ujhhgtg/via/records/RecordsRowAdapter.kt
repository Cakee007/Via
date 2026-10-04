package dev.ujhhgtg.via.records

import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.skins.SkinResources
import dev.ujhhgtg.via.ui.dp

/** cb.h0 uses layout/x; history and offline pages use b6.f.f. */
internal data class RecordsRow(
    val title: String,
    val subtitle: String? = null,
    val icon: Drawable? = null,
    /** The page url whose stored favicon is decoded lazily at bind time (x0.c().d). */
    val faviconUrl: String? = null,
    val onClick: (() -> Unit)? = null,
    val onLongClick: ((View) -> Boolean)? = null,
    val dragKey: String? = null,
    val onDragStart: ((RecyclerView.ViewHolder) -> Unit)? = null,
    val empty: Boolean = false,
    val selected: Boolean? = null,
    val bookmark: Boolean = false,
    val timestamp: Long = 0L,
    val tintIcon: Boolean = true,
    val iconSize: Int = 20,
    val multilineTitle: Boolean = false,
    val description: String? = null,
)

internal class RecordsRowAdapter : RecyclerView.Adapter<RecordsRowAdapter.Holder>() {
    // cb.h0 caches the two fallback drawables for the life of the delegate.
    private var folderFallback: Drawable? = null
    private var pageFallback: Drawable? = null
    var faviconLoader: ((String) -> Drawable?)? = null
    private var rows: List<RecordsRow> = emptyList()
    /**
     * y5.f's DiffUtil dispatch (androidx.recyclerview.widget.f.b): row moves
     * and changes animate through the item animator instead of resetting the
     * list, which is how the source's folder transitions look.
     */
    fun submit(next: List<RecordsRow>) {
        val previous = rows
        rows = next
        androidx.recyclerview.widget.DiffUtil.calculateDiff(object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize() = previous.size
            override fun getNewListSize() = next.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                val a = previous[oldPos]; val b = next[newPos]
                if (a.dragKey != null || b.dragKey != null) return a.dragKey == b.dragKey
                return a.title == b.title && a.subtitle == b.subtitle
            }
            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                val a = previous[oldPos]; val b = next[newPos]
                return a.title == b.title && a.subtitle == b.subtitle && a.selected == b.selected &&
                    a.bookmark == b.bookmark && a.timestamp == b.timestamp && a.tintIcon == b.tintIcon &&
                    a.iconSize == b.iconSize && a.multilineTitle == b.multilineTitle && a.description == b.description &&
                    a.faviconUrl == b.faviconUrl && a.icon === b.icon
            }
        }).dispatchUpdatesTo(this)
    }
    fun swap(from: Int, to: Int) { val copy = rows.toMutableList(); val item = copy.removeAt(from); copy.add(to, item); rows = copy; notifyItemMoved(from, to) }
    fun rows(): List<RecordsRow> = rows
    override fun getItemCount() = rows.size
    override fun getItemViewType(position: Int) = if (rows[position].bookmark) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        if (viewType == 1) {
            val view = android.view.LayoutInflater.from(context).inflate(R.layout.records_bookmark_row, parent, false)
            val title = view.findViewById<TextView>(R.id.records_bookmark_title)
            val subtitle = view.findViewById<TextView>(R.id.records_bookmark_subtitle)
            title.typeface = BrowserPreferences(context).selectedTypeface(); subtitle.typeface = BrowserPreferences(context).selectedTypeface()
            return Holder(view, view.findViewById(R.id.records_bookmark_icon), title, subtitle,
                view.findViewById(R.id.records_bookmark_drag), view.findViewById(R.id.records_bookmark_checkbox))
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.flat_ripple)
            isClickable = true; isFocusable = true
            layoutParams = RecyclerView.LayoutParams(-1, -2)
        }
        val icon = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        row.addView(icon, LinearLayout.LayoutParams(context.dp(20f), context.dp(32f)).apply { marginEnd = context.dp(16f) })
        val text = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        fun label(size: Float, color: Int) = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size); setTextColor(color)
            typeface = BrowserPreferences(context).selectedTypeface()
            setSingleLine(); ellipsize = null; isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(context.dp(24f)); textDirection = View.TEXT_DIRECTION_LOCALE
        }
        val title = label(14f, recordColor(context, R.attr.viaPrimaryTextColor))
        val subtitle = label(12f, recordColor(context, R.attr.viaSecondaryTextColor, Color.GRAY))
        text.addView(title, LinearLayout.LayoutParams(-1, -2))
        text.addView(subtitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(2f) })
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        val drag = ImageView(context).apply {
            setImageResource(R.drawable.drag_handle); setColorFilter(recordColor(context, R.attr.viaSubtleColor))
            setPadding(context.dp(15f), context.dp(15f), context.dp(15f), context.dp(15f))
            contentDescription = context.getString(R.string.hold_and_drag_to_rearrange_items)
        }
        row.addView(drag, LinearLayout.LayoutParams(context.dp(48f), context.dp(54f)).apply { marginStart = context.dp(16f); marginEnd = context.dp(16f) })
        val checkbox = CheckBox(context).apply {
            isClickable = false; isFocusable = false; minWidth = 0; minimumWidth = 0
            val attrs = context.obtainStyledAttributes(intArrayOf(R.attr.viaChoiceIndicator))
            try { buttonDrawable = attrs.getDrawable(0) } finally { attrs.recycle() }
        }
        row.addView(checkbox, LinearLayout.LayoutParams(context.dp(20f), context.dp(20f)))
        return Holder(row, icon, title, subtitle, drag, checkbox)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        val context = holder.itemView.context
        holder.itemView.minimumHeight = if (row.bookmark) context.dp(54f) else 0
        holder.itemView.setPadding(context.dp(16f), if (row.bookmark) 0 else context.dp(10f), context.dp(16f), if (row.bookmark) 0 else context.dp(10f))
        holder.icon.layoutParams.width = context.dp(row.iconSize.toFloat())
        holder.icon.layoutParams.height = context.dp(if (row.bookmark) 22f else 32f)
        if (row.bookmark) {
            // RecordsBookmarksFragment gives only item rows a faviconUrl; folder
            // and parent-folder rows have none, corresponding to cb.a.a()/b().
            // cb.h0.o creates each fallback once and colors the Drawable directly.
            // Neither mutate() nor an ImageView filter belongs to this path:
            // BitmapDrawable's shared resource state retains later folder-tree
            // tint changes exactly as in the original delegate.
            val drawable = if (row.faviconUrl == null) {
                folderFallback ?: SkinResources.drawable(context, R.drawable.folder, "ic_folder")?.also {
                    it.colorFilter = PorterDuffColorFilter(recordColor(context, R.attr.viaSubtleColor), PorterDuff.Mode.SRC_IN)
                    folderFallback = it
                }
            } else {
                faviconLoader?.invoke(row.faviconUrl) ?: pageFallback
                    ?: SkinResources.drawable(context, R.drawable.star, "ic_bookmark")?.also {
                        it.colorFilter = PorterDuffColorFilter(recordColor(context, R.attr.viaSubtleColor), PorterDuff.Mode.SRC_IN)
                        pageFallback = it
                    }
            }
            holder.icon.setImageDrawable(drawable)
        } else {
            // History and saved-page delegates keep their own binding behavior.
            val favicon = row.faviconUrl?.let { faviconLoader?.invoke(it) }
            holder.icon.setImageDrawable(favicon ?: row.icon)
            if (favicon == null && row.tintIcon) holder.icon.setColorFilter(recordColor(context, R.attr.viaSubtleColor)) else holder.icon.clearColorFilter()
        }
        holder.title.setSingleLine(!row.multilineTitle)
        holder.title.text = if (row.title.length > 256 && !row.multilineTitle) row.title.take(256) + "..." else row.title
        holder.subtitle.text = row.subtitle.orEmpty()
        holder.subtitle.visibility = if (row.subtitle.isNullOrEmpty()) View.GONE else View.VISIBLE
        holder.drag.visibility = if (row.onDragStart != null) View.VISIBLE else View.GONE
        holder.drag.setOnTouchListener { _, event -> if (event.actionMasked == MotionEvent.ACTION_DOWN) row.onDragStart?.invoke(holder); true }
        holder.icon.setOnTouchListener(if (row.onDragStart == null) null else View.OnTouchListener { _, event -> if (event.actionMasked == MotionEvent.ACTION_DOWN) row.onDragStart.invoke(holder); true })
        holder.checkbox.visibility = if (row.selected == null) View.GONE else View.VISIBLE
        holder.checkbox.isChecked = row.selected == true
        holder.itemView.setOnClickListener { row.onClick?.invoke() }
        holder.itemView.setOnLongClickListener { row.onLongClick?.invoke(holder.itemView) ?: false }
        if (row.bookmark) {
            holder.itemView.contentDescription = null
            holder.title.contentDescription = row.description
        } else holder.itemView.contentDescription = row.description ?: row.title
    }
    class Holder(view: View, val icon: ImageView, val title: TextView, val subtitle: TextView, val drag: ImageView, val checkbox: CheckBox) : RecyclerView.ViewHolder(view)
}
