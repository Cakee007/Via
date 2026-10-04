package dev.ujhhgtg.via.home

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.browser.UrlResolver
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.Favorite
import dev.ujhhgtg.via.data.FavoritesRepository
import dev.ujhhgtg.via.common.GeneratedDocumentState
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dp
import java.io.File
import java.util.concurrent.Executors

/** qa.e1/v0/o/f1: activity-level favorite manager, its grid and inline editor. */
class FavoriteSortSheet : Fragment() {
    private lateinit var database: BrowserDatabase
    private lateinit var repository: FavoritesRepository
    private lateinit var preferences: BrowserPreferences
    private lateinit var design: HomeDesign
    private lateinit var pager: ViewPager2
    private lateinit var grid: RecyclerView
    private lateinit var editor: View
    private lateinit var titleInput: EditText
    private lateinit var urlInput: EditText
    private lateinit var icon: ImageView
    private lateinit var clearIcon: ImageView
    private val favorites = mutableListOf<Favorite>()
    private val gridAdapter = GridAdapter()
    private val worker = Executors.newSingleThreadExecutor()
    private var measuredCount = 0
    private var originalSoftInput = 0
    private var target: Favorite? = null
    private var pickedIcon: String? = null
    private var lastSave = 0L
    private var heightAnimation: ValueAnimator? = null

    private val picker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data?.takeIf { result.resultCode == Activity.RESULT_OK } ?: return@registerForActivityResult
        val host = requireActivity()
        worker.execute {
            // qa.o.U2: the temporary image belongs to the icon directory as well.
            val file = File(HomeFavoriteIcons.directory(host), "cache.png")
            val copied = runCatching {
                host.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use(input::copyTo) }
            }.getOrNull()
            host.runOnUiThread { if (copied != null && view != null) { pickedIcon = file.path; showIcon() } }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = BrowserPreferences(requireContext())
        GeneratedDocumentState.initialize(preferences)
        design = HomeDesign(favoriteBits = preferences.favoritesInfo)
        database = BrowserDatabase(requireContext())
        repository = FavoritesRepository(database)
        measuredCount = arguments?.getInt("count", 0) ?: 0
        originalSoftInput = requireActivity().window.attributes.softInputMode
        requireActivity().window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        val gridPage = createGrid(context)
        editor = inflater.inflate(R.layout.favorite_sort_editor, container, false)
        titleInput = editor.findViewById(R.id.favorite_title)
        urlInput = editor.findViewById(R.id.favorite_url)
        icon = editor.findViewById(R.id.favorite_icon)
        clearIcon = editor.findViewById(R.id.favorite_icon_clear)
        applyTypeface(editor)
        editor.findViewById<View>(R.id.favorite_back).setOnClickListener { backToGrid() }
        editor.findViewById<View>(R.id.favorite_done).setOnClickListener { saveEditor() }
        icon.setOnClickListener {
            if (target != null && clearIcon.isGone) {
                try { picker.launch(Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)) }
                catch (_: android.content.ActivityNotFoundException) { }
            }
        }
        icon.setOnLongClickListener { pickedIcon = ""; showIcon(); true }
        clearIcon.setOnClickListener { pickedIcon = ""; showIcon() }
        if (design.favoriteIconDisabled) { icon.visibility = View.GONE; clearIcon.visibility = View.GONE }
        val pages = listOf(gridPage, editor)
        pager = ViewPager2(context).apply {
            isUserInputEnabled = false
            setOnClickListener { }
            adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun getItemCount() = 2
                override fun getItemViewType(position: Int) = position
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val frame = FrameLayout(parent.context).apply {
                        layoutParams = ViewGroup.LayoutParams(-1, -1)
                        addView(pages[viewType], FrameLayout.LayoutParams(-1, -1))
                    }
                    return object : RecyclerView.ViewHolder(frame) {}
                }
                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
            }
            offscreenPageLimit = 1
            val radius = resources.getDimensionPixelSize(R.dimen.menu_corner_radius).toFloat()
            background = GradientDrawable().apply {
                setColor(settingsColor(context, R.attr.viaSurfaceColor, Color.WHITE))
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            }
        }
        return FrameLayout(context).apply {
            setBackgroundColor(resources.getColor(R.color.popup_scrim, context.theme))
            setOnClickListener { close() }
            addView(pager, FrameLayout.LayoutParams(-1, context.dp(300f), Gravity.BOTTOM))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = close()
        })
        resizeSheet(Resources.getSystem().configuration)
        ViewCompat.setOnApplyWindowInsetsListener(pager) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(pager)
        val host = requireActivity()
        worker.execute {
            val loaded = repository.list()
            host.runOnUiThread {
                if (this.view != null) { favorites.clear(); favorites.addAll(loaded); gridAdapter.notifyDataSetChanged() }
            }
        }
    }

    /** qa.e1.b3 and smali: original measured-height clamp and short delayed adjustment. */
    private fun resizeSheet(configuration: Configuration) {
        heightAnimation?.cancel()
        val context = requireContext()
        val toolbar = context.dp(54f)
        val screen = context.dp(configuration.screenHeightDp.toFloat())
        val requested = arguments?.getInt("height", 0)?.takeIf { it != 0 } ?: (screen / 2 + toolbar)
        val bounded = requested.coerceIn(toolbar + context.dp(168f), screen * 3 / 5 + toolbar)
        val animate = requested != bounded && kotlin.math.abs(requested - bounded) <= context.dp(96f)
        pager.layoutParams = pager.layoutParams.apply { height = if (animate) requested else bounded }
        if (animate) pager.postDelayed({
            if (view != null) heightAnimation = ValueAnimator.ofInt(requested, bounded).apply {
                duration = (kotlin.math.abs(requested - bounded) / 512f * 100f + 80f).toLong()
                interpolator = PathInterpolator(.2f, .2f, .8f, .8f)
                addUpdateListener { pager.layoutParams = pager.layoutParams.apply { height = it.animatedValue as Int } }
                start()
            }
        }, 100L)
    }

    private fun createGrid(context: Context): View = RelativeLayout(context).apply {
        val heading = TextView(context).apply {
            id = View.generateViewId(); setText(R.string.action_manage_favorites); gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_toolbar_title_size).toFloat())
            setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, Color.BLACK))
            typeface = Typeface.create(preferences.selectedTypeface(), Typeface.BOLD)
            setSingleLine(true); ellipsize = android.text.TextUtils.TruncateAt.END
            isHorizontalFadingEdgeEnabled = false
        }
        addView(heading, RelativeLayout.LayoutParams(-2, context.dp(48f)).apply { addRule(RelativeLayout.CENTER_HORIZONTAL) })
        addView(TextView(context).apply {
            setText(R.string.done); gravity = Gravity.CENTER; typeface = preferences.selectedTypeface()
            setSingleLine(true); ellipsize = android.text.TextUtils.TruncateAt.END
            isHorizontalFadingEdgeEnabled = false
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
            setTextColor(settingsColor(context, R.attr.viaAccentColor, Color.BLUE))
            setPaddingRelative(context.dp(16f), 0, context.dp(16f), 0)
            setOnClickListener { close() }
        }, RelativeLayout.LayoutParams(-2, -2).apply {
            addRule(RelativeLayout.ALIGN_TOP, heading.id); addRule(RelativeLayout.ALIGN_BOTTOM, heading.id)
            addRule(RelativeLayout.ALIGN_PARENT_END)
        })
        grid = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 4)
            itemAnimator = DefaultItemAnimator().apply { removeDuration = 100L }
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            adapter = gridAdapter
        }
        addView(grid, RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.BELOW, heading.id) })
        applyGridMetrics(Resources.getSystem().configuration)
        var start = -1
        var end = -1
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(51, 0) {
            override fun onMove(recyclerView: RecyclerView, source: RecyclerView.ViewHolder, destination: RecyclerView.ViewHolder): Boolean {
                val from = source.bindingAdapterPosition; val to = destination.bindingAdapterPosition
                if (from !in favorites.indices || to !in favorites.indices) return false
                favorites.add(to, favorites.removeAt(from)); gridAdapter.notifyItemMoved(from, to); end = to
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    start = viewHolder.bindingAdapterPosition; end = start
                } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE && start >= 0) {
                    if (start != end) {
                        val ids = favorites.map { it.id }
                        GeneratedDocumentState.mark(GeneratedDocumentState.HOME_CONTENT)
                        worker.execute { repository.reorder(ids) }
                    }
                    start = -1
                }
            }
        }).attachToRecyclerView(grid)
    }

    /** qa.v0.j3: search visibility reserves one column, passed DOM count is a lower bound. */
    private fun applyGridMetrics(configuration: Configuration) {
        val context = requireContext()
        val cell = context.dp(design.favoriteWidth.toFloat()) + context.dp(18f)
        val width = context.dp(configuration.screenWidthDp.toFloat())
        val maximum = context.dp(540f) / cell
        var count = maximum / 2
        val extra = if (preferences.customInfo and 2 == 0) 1 else 0
        for (candidate in maximum downTo 0) if (width >= (candidate + extra) * cell) { count = candidate; break }
        count = maxOf(count, measuredCount, 1)
        val padding = maxOf(0, (width - cell * count) / 2)
        grid.setPadding(padding, 0, padding, 0)
        (grid.layoutManager as GridLayoutManager).spanCount = count
    }

    private fun editFavorite(position: Int) {
        val selected = favorites.getOrNull(position) ?: return
        target = selected; pickedIcon = null
        titleInput.setText(selected.title); urlInput.setText(selected.url)
        showIcon()
        pager.setCurrentItem(1, true)
    }
    private fun backToGrid() { pager.setCurrentItem(0, true) }
    private fun showIcon() {
        if (design.favoriteIconDisabled) return
        val context = requireContext()
        val size = context.dp(42f)
        val radius = (size * (preferences.favoritesInfo shr 14 and 127) / 100f / 2f).toInt()
        val image = if (pickedIcon == null) HomeFavoriteIcons.load(context, target?.url, size, radius)
            else pickedIcon?.takeIf { it.isNotEmpty() }?.let { HomeFavoriteIcons.loadFile(it, size, size, radius) }
        icon.setImageDrawable(image?.toDrawable(resources) ?: HomeFavoriteIcons.placeholder(context, radius * 2))
        clearIcon.visibility = if (image == null) View.GONE else View.VISIBLE
    }
    private fun saveEditor() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSave < 300L) return
        lastSave = now
        val selected = target ?: return
        val name = titleInput.text.toString()
        val url = urlInput.text.toString().trim()
        val missing = if (name.isEmpty()) titleInput else if (url.isEmpty()) urlInput else null
        if (missing != null) {
            shakeFavoriteInput(missing)
            ViaToast.makeText(requireContext(), getString(R.string.is_required, missing.hint), ViaToast.LENGTH_SHORT).show()
            return
        }
        val updated = selected.copy(title = name, url = UrlResolver.normalizeInput(url, preferences.searchUrl) ?: url)
        val iconPath = pickedIcon
        val host = requireActivity()
        worker.execute {
            val id = repository.save(updated)
            if (id > 0 && iconPath != null) {
                if (iconPath.isEmpty()) HomeFavoriteIcons.remove(host, updated.url)
                else HomeFavoriteIcons.adoptCache(host, iconPath, updated.url)
            }
            host.runOnUiThread {
                if (view == null) return@runOnUiThread
                if (id < 0) { ViaToast.makeText(host, getString(R.string.toast_operation_failed), ViaToast.LENGTH_SHORT).show(); return@runOnUiThread }
                if (id > 0) pickedIcon = null
                GeneratedDocumentState.mark(GeneratedDocumentState.HOME_CONTENT)
                val position = favorites.indexOfFirst { it.id == selected.id }
                if (position >= 0) { favorites[position] = updated.copy(id = id); gridAdapter.notifyItemChanged(position) }
                backToGrid()
            }
        }
    }
    private fun removeFavorite(position: Int) {
        val removed = favorites.getOrNull(position) ?: return
        favorites.removeAt(position); gridAdapter.notifyItemRemoved(position)
        GeneratedDocumentState.mark(GeneratedDocumentState.HOME_CONTENT)
        worker.execute { repository.deleteById(removed.id) }
        val host = requireActivity()
        ViaToast.show(host, getString(R.string.favorite_deleted, removed.title), ViaToast.LENGTH_LONG, getString(R.string.undo)) {
            worker.execute {
                // qa.v0.h3 copies into the six-argument n9.b constructor, whose ID is zero.
                val id = repository.save(removed.copy(id = 0))
                host.runOnUiThread {
                    if (id > 0 && view != null) {
                        GeneratedDocumentState.mark(GeneratedDocumentState.HOME_CONTENT)
                        val index = position.coerceAtMost(favorites.size)
                        favorites.add(index, removed.copy(id = id)); gridAdapter.notifyItemInserted(index)
                    }
                }
            }
        }
    }
    private fun close() { if (!parentFragmentManager.isStateSaved) parentFragmentManager.popBackStack() }
    override fun onPause() {
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(pager.windowToken, 0)
        super.onPause()
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        arguments?.clear(); measuredCount = 0
        super.onConfigurationChanged(newConfig)
        resizeSheet(newConfig); applyGridMetrics(newConfig)
    }
    override fun onDestroyView() { heightAnimation?.cancel(); heightAnimation = null; super.onDestroyView() }
    override fun onDestroy() {
        parentFragmentManager.setFragmentResult("favoriteChanged", Bundle())
        if (originalSoftInput != 0) activity?.window?.setSoftInputMode(originalSoftInput)
        worker.execute { database.close() }; worker.shutdown()
        super.onDestroy()
    }
    private fun applyTypeface(view: View) {
        if (view is TextView) view.setTypeface(preferences.selectedTypeface(), view.typeface?.style ?: Typeface.NORMAL)
        if (view is ViewGroup) for (index in 0 until view.childCount) applyTypeface(view.getChildAt(index))
    }
    private inner class GridAdapter : RecyclerView.Adapter<GridAdapter.Cell>() {
        inner class Cell(view: View) : RecyclerView.ViewHolder(view) {
            val tile: TextView = view.findViewById(R.id.favorite_tile)
            val label: TextView = view.findViewById(R.id.favorite_label)
            val delete: ImageView = view.findViewById(R.id.favorite_delete)
        }
        override fun getItemCount() = favorites.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Cell = Cell(
            LayoutInflater.from(parent.context).inflate(R.layout.favorite_sort_cell, parent, false)
        ).apply {
            applyTypeface(itemView)
            tile.layoutParams = tile.layoutParams.apply { width = parent.context.dp(design.favoriteWidth.toFloat()); height = parent.context.dp(design.favoriteHeight.toFloat()) }
            label.layoutParams = label.layoutParams.apply { width = parent.context.dp(design.favoriteWidth.toFloat()) }
            if (design.favoriteColorDisabled) tile.setTextColor(settingsColor(parent.context, R.attr.viaPrimaryTextColor, Color.BLACK))
            itemView.setOnClickListener { editFavorite(bindingAdapterPosition) }
            delete.setOnClickListener { removeFavorite(bindingAdapterPosition) }
        }
        override fun onBindViewHolder(holder: Cell, position: Int) {
            val context = holder.itemView.context
            val item = favorites[position]
            val width = context.dp(design.favoriteWidth.toFloat()); val height = context.dp(design.favoriteHeight.toFloat())
            val radius = (minOf(width, height) * (preferences.favoritesInfo shr 14 and 127) / 100f / 2f).toInt()
            val image = if (design.favoriteIconDisabled) null else HomeFavoriteIcons.load(context, item.url, width, height, radius)
            val name = item.title.orEmpty()
            holder.label.text = name
            holder.tile.text = if (image == null && name.isNotEmpty()) String(Character.toChars(name.codePointAt(0))) else ""
            holder.tile.background = image?.toDrawable(resources)
                ?: if (design.favoriteColorDisabled) null else
                    GradientDrawable().apply { setColor(HomeDesign.favoriteColorInt(item.url)); cornerRadius = radius.toFloat() }
        }
    }
    companion object {
        fun newInstance(count: Int, height: Int) = FavoriteSortSheet().apply {
            arguments = Bundle().apply { putInt("count", count); putInt("height", height) }
        }
    }
}
