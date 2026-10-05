package dev.ujhhgtg.via.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.browser.script.ScriptStore
import dev.ujhhgtg.via.data.BookmarkRepository
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.FavoritesRepository
import dev.ujhhgtg.via.data.SiteConfigurationRepository
import dev.ujhhgtg.via.skins.SkinResources
import dev.ujhhgtg.via.skins.setSkinImageResource
import dev.ujhhgtg.via.ui.dialog.ViaDialogFragment
import kotlin.math.ceil

/** i8.k, h8.a/c and mark.via.common.widget.m1, retaining the original menu result contract. */
class BrowserMenuDialog : ViaDialogFragment() {
    /** The source's process-local states, which are supplied by its browser fragment. */
    data class RuntimeState(val incognito: Boolean, val fullscreen: Boolean, val gameMode: Boolean, val readerState: Int = 0)
    interface Host { fun menuRuntimeState(): RuntimeState? = null }
    private data class Item(val id: Int, val icon: Int = 0, var title: String? = null, var enabled: Boolean = true, var active: Boolean = false, var description: String? = null)
    private var width = ViewGroup.LayoutParams.MATCH_PARENT
    private var gravity = Gravity.BOTTOM
    private var url: String? = null
    private var title: String? = null
    private var resultMark = 0
    private var resultFlags = 0
    private var rows = 1
    private val items = ArrayList<Item>()
    private lateinit var preferences: BrowserPreferences
    private lateinit var grid: RecyclerView
    private lateinit var indicator: MenuIndicator
    private lateinit var cancel: ImageView
    private lateinit var exit: ImageView
    private val night get() = preferences.isNightMode
    private val ink get() = themeColor(R.attr.viaPrimaryTextColor)
    private val iconInk get() = themeColor(R.attr.viaSubtleColor)
    private val accent get() = themeColor(R.attr.viaAccentColor)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        arguments?.let {
            width = it.getInt("width", width); gravity = it.getInt("gravity", gravity)
            url = it.getString("url"); title = it.getString("title")
        }
        preferences = BrowserPreferences(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val top = gravity and Gravity.TOP == Gravity.TOP
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        grid = RecyclerView(context)
        indicator = MenuIndicator(context, themeColor(R.attr.viaDisabledTextColor))
        val footer = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        footer.addView(Space(context), LinearLayout.LayoutParams(0, -2, 3f))
        exit = footerButton(R.drawable.power, getString(R.string.exit))
        cancel = footerButton(R.drawable.menu_next, getString(android.R.string.cancel))
        footer.addView(exit, LinearLayout.LayoutParams(0, -1, 1f))
        footer.addView(cancel, LinearLayout.LayoutParams(0, -1, 1f))
        val gridParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = marginDp(12f) }
        val indicatorParams = LinearLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            topMargin = marginDp(if (top) 4f else 2f); bottomMargin = marginDp(if (top) 2f else 4f)
        }
        val footerParams = LinearLayout.LayoutParams(-1, resources.getDimensionPixelSize(R.dimen.menu_footer_height))
        if (!top) { body.addView(grid, gridParams); body.addView(indicator, indicatorParams); body.addView(footer, footerParams) }
        else { body.addView(footer, footerParams); body.addView(indicator, indicatorParams); body.addView(grid, gridParams); cancel.rotation = 180f }
        return body
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cancel.setOnClickListener { dismiss() }
        exit.setOnClickListener { resultMark = 21; resultFlags = 1; dismiss() }
        updateItems()
        rows = if (items.size > 5) 2 else 1
        val source = items.toList()
        items.clear()
        val size = rows * 5
        val pages = ceil(source.size.toDouble() / size).toInt()
        repeat(pages) { page -> repeat(5) { column -> repeat(rows) { row -> items += source.getOrNull(page * size + column + row * 5) ?: Item(22, enabled = false) } } }
        grid.layoutManager = GridLayoutManager(requireContext(), rows, RecyclerView.HORIZONTAL, false)
        grid.itemAnimator = null
        grid.setHasFixedSize(true)
        grid.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        grid.edgeEffectFactory = dev.ujhhgtg.via.settings.SettingsRecyclerView.StretchEdgeEffectFactory()
        grid.adapter = MenuAdapter()
        grid.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            val remainder = view.width % 5
            if (view.paddingEnd != remainder) view.setPaddingRelative(view.paddingStart, view.paddingTop, remainder, view.paddingBottom)
        }
        indicator.count = pages
        indicator.current = 0
        if (pages > 1) BrowserMenuSnapHelper(1).apply {
            attachToRecyclerView(grid)
            listener = object : BrowserMenuSnapHelper.Listener {
                override fun onAligned(position: Int) { indicator.current = ceil(position.toDouble() / (rows * 5)).toInt() }
                override fun onTarget(position: Int) { indicator.current = ceil(position.toDouble() / (rows * 5)).toInt() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            // i8.k.R1 delegates to k8.a before applying the edge placement. Its
            // blur-backed window background must remain the same drawable instance.
            window.attributes = window.attributes.apply {
                width = this@BrowserMenuDialog.width; height = ViewGroup.LayoutParams.WRAP_CONTENT
                gravity = this@BrowserMenuDialog.gravity; dimAmount = .2f
                windowAnimations = if (gravity and Gravity.TOP == Gravity.TOP) R.style.BrowserMenuTop else R.style.BrowserMenuBottom
            }
        }
    }

    override fun onDestroy() {
        parentFragmentManager.setFragmentResult(RESULT, Bundle().apply { putInt("mark", resultMark); putInt("flags", resultFlags) })
        super.onDestroy()
    }

    /** i8.k.y3 updates the reader item when detection completes while the menu remains open. */
    fun updateReaderState(state: Int) {
        val index = items.indexOfFirst { it.id == 36 }
        if (index >= 0 && items[index].enabled) { items[index].active = state == 3; if (::grid.isInitialized) grid.adapter?.notifyItemChanged(index) }
    }

    private fun updateItems() {
        items.clear()
        val displayed = preferences.displayedMenus?.let { if (it.isEmpty()) emptyList() else it.split(',').mapNotNull(String::toIntOrNull) } ?: OriginalMenu.defaults
        val seen = HashSet<Int>()
        for (id in displayed) {
            if (!seen.add(id)) return
            if (id in OriginalMenu.removed) continue
            val entry = OriginalMenu.entries[id]
            items += if (entry == null) Item(22) else Item(id, entry.icon, getString(entry.titleRes))
        }
        val runtime = (parentFragment as? Host)?.menuRuntimeState() ?: (activity as? Host)?.menuRuntimeState()
        fun item(id: Int) = items.firstOrNull { it.id == id }
        fun toggle(id: Int, value: Boolean) { item(id)?.apply { active = value; description = getString(if (value) R.string.is_on else R.string.is_off, title.orEmpty()) } }
        val page = url.orEmpty()
        val file = page.startsWith("file://", true)
        val network = android.webkit.URLUtil.isNetworkUrl(page)
        val generated = dev.ujhhgtg.via.search.UrlInputText.isInternalDocument(page, requireContext().filesDir.path)
        toggle(1, night)
        toggle(5, runtime?.incognito ?: (preferences.webFlags and 64 != 0))
        BrowserDatabase(requireContext()).use { database ->
            item(7)?.apply { active = BookmarkRepository(database).findByUrl(page) != null; title = getString(if (active) R.string.bookmark_added else R.string.action_add_bookmark) }
            item(40)?.apply { active = FavoritesRepository(database).findByUrl(page) != null; title = getString(if (active) R.string.favorite_added else R.string.action_add_favorite) }
            toggle(26, dev.ujhhgtg.via.browser.DocumentPolicy.authority(page).takeIf(String::isNotEmpty)?.let { SiteConfigurationRepository(database).get(it)?.isEnabled } == true)
        }
        toggle(8, preferences.webFlags and 2048 != 0)
        item(12)?.enabled = !file
        item(14)?.enabled = network
        item(15)?.enabled = network || file
        toggle(16, runtime?.fullscreen ?: (preferences.appFlags and 1 != 0))
        item(17)?.apply {
            val wifi = preferences.webFlags and 32 != 0
            val images = preferences.webFlags and 16 != 0
            active = wifi || !images
            title = getString(if (wifi) R.string.image_auto else if (!images) R.string.image_off else R.string.image_on)
        }
        item(19)?.apply { active = preferences.userAgentChoice != 0; if (active) description = getString(R.string.is_set, title.orEmpty()) }
        // p5.a.C tests every loaded pattern, including disabled scripts (q5.a.e/r5.d.j).
        toggle(27, network && preferences.scriptsEnabled && ScriptStore(requireContext()).use { store -> store.list().any { it.id >= 0 && it.appliesTo(page) } })
        item(29)?.apply { active = preferences.textSize != 100; enabled = !file; description = getString(R.string.is_set, title.orEmpty()) }
        item(30)?.apply {
            enabled = Build.VERSION.SDK_INT < 36 || resources.configuration.smallestScreenWidthDp < 600
            val choice = if (enabled) preferences.screenOrientation - 1 else 0
            active = choice != 0
            title = getString(when (choice) { 1 -> R.string.orientation_portrait; 2 -> R.string.orientation_landscape; else -> R.string.orientation })
        }
        toggle(28, preferences.adBlocking)
        item(23)?.enabled = preferences.adBlocking && preferences.javascriptEnabled() && network
        for (id in intArrayOf(32, 33, 36, 37, 38)) item(id)?.enabled = !generated
        item(38)?.active = runtime?.gameMode ?: false
        item(36)?.active = runtime?.readerState == 3
    }

    private inner class MenuAdapter : RecyclerView.Adapter<MenuHolder>() {
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuHolder = MenuHolder(TextView(requireContext()).apply {
            layoutParams = RecyclerView.LayoutParams((parent.measuredWidth / 5).takeIf { it > 0 } ?: context.dp(64f), -2)
            background = ContextCompat.getDrawable(context, R.drawable.rounded_rect_ripple)
            setPaddingRelative(context.dp(5f), context.dp(15f), context.dp(5f), context.dp(15f))
            compoundDrawablePadding = context.dp(2f)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.menu_text_size).toFloat())
            gravity = Gravity.CENTER; setLines(2); maxLines = 2; ellipsize = TextUtils.TruncateAt.END; typeface = Typeface.DEFAULT
            isFocusable = true
        })
        override fun onBindViewHolder(holder: MenuHolder, position: Int) {
            val item = items[position]; val text = holder.text
            if (item.id == 22) { text.visibility = View.GONE; return }
            text.visibility = View.VISIBLE
            text.text = item.title; text.contentDescription = item.description ?: item.title
            text.setTextColor(if (item.active) accent else ink)
            text.setTypeface(preferences.selectedTypeface(), if (item.active) Typeface.BOLD else Typeface.NORMAL)
            val icon = SkinResources.drawable(requireContext(), item.icon)!!.mutate()
            icon.setTint(if (item.active) accent else iconInk)
            val size = resources.getDimensionPixelSize(R.dimen.menu_icon_size)
            icon.setBounds(0, 0, size, size); text.setCompoundDrawables(null, icon, null, null)
            text.isEnabled = item.enabled; text.alpha = if (item.enabled) 1f else .35f
            text.setOnClickListener {
                if (item.id != 9) { select(item, 1); return@setOnClickListener }
                val manager = grid.layoutManager as GridLayoutManager
                val last = manager.findLastCompletelyVisibleItemPosition()
                if (last == RecyclerView.NO_POSITION || items.size <= rows * 5) return@setOnClickListener
                var nextPage = indicator.current + 1
                var target = last + rows * 5
                if (target >= items.size) { target = 0; nextPage = 0 }
                grid.scrollToPosition(target); indicator.current = nextPage
                dialog?.window?.decorView?.let { decor -> android.animation.ObjectAnimator.ofFloat(decor, "translationY", 0f, requireContext().dp(10f).toFloat() * (if (gravity and Gravity.BOTTOM == Gravity.BOTTOM) 1 else -1), 0f).apply { duration = 280; start() } }
            }
            text.setOnLongClickListener { select(item, 2); true }
        }
    }
    private fun themeColor(attribute: Int): Int = requireContext().obtainStyledAttributes(intArrayOf(attribute)).let { try { it.getColor(0, 0) } finally { it.recycle() } }

    private fun marginDp(value: Float) = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()

    private fun select(item: Item, action: Int) {
        resultMark = item.id; resultFlags = (if (item.enabled) 0 else 4) or action or (if (item.active) 8 else 0)
        dismiss()
    }
    private class MenuHolder(val text: TextView) : RecyclerView.ViewHolder(text)
    private fun footerButton(icon: Int, description: String) = ImageView(requireContext()).apply {
        setSkinImageResource(icon); setColorFilter(iconInk)
        val padding = resources.getDimensionPixelSize(R.dimen.menu_control_padding)
        setPaddingRelative(padding, padding, padding, padding)
        background = ContextCompat.getDrawable(context, R.drawable.rounded_rect_ripple)
        contentDescription = description; isFocusable = true
    }
    /** Pixel dimensions and RTL drawing are the original m1 implementation. */
    private class MenuIndicator(context: Context, private val selectedColor: Int) : View(context) {
        var count = 0
            set(value) { if (field != value) { field = value; invalidate() } }
        var current = 0
            set(value) { if (field != value) { field = value; invalidate() } }
        private val radius = context.dp(2f)
        private val gap = context.dp(4f)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            val width = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) radius * 2 * count + (count - 1).coerceAtLeast(0) * gap + paddingLeft + paddingRight else MeasureSpec.getSize(widthMeasureSpec)
            val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) radius * 2 + paddingTop + paddingBottom else MeasureSpec.getSize(heightMeasureSpec)
            setMeasuredDimension(width, height)
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (count <= 1) return
            val centerY = (measuredHeight + paddingTop - paddingBottom) / 2f
            val width = measuredWidth + paddingLeft - paddingRight
            var x = (width - radius * count * 2 - (count - 1).coerceAtLeast(0) * gap) / 2f + radius
            val selected = if (layoutDirection == LAYOUT_DIRECTION_RTL) count - 1 - current else current
            repeat(count) { index ->
                paint.color = if (index == selected) selectedColor else 0x40808080
                canvas.drawCircle(x, centerY, radius.toFloat(), paint); x += gap + radius * 2
            }
        }
    }
    companion object {
        const val RESULT = "menu_result"
        fun newInstance(url: String?, title: String?, width: Int = ViewGroup.LayoutParams.MATCH_PARENT, gravity: Int = Gravity.BOTTOM) = BrowserMenuDialog().apply {
            arguments = Bundle().apply { putString("url", url); putString("title", title); putInt("width", width); putInt("gravity", gravity) }
        }
    }
}
