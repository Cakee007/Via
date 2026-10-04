package dev.ujhhgtg.via.search

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.skins.setSkinImageResource
import dev.ujhhgtg.via.common.TransientState
import dev.ujhhgtg.via.common.WindowInsetsHelper
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import dev.ujhhgtg.via.ui.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import java.util.Locale

/** tb.k0. This fragment is added above the browser; the browser is not hidden. */
class UrlInputFragment : Fragment() {
    private lateinit var database: BrowserDatabase
    private lateinit var preferences: BrowserPreferences
    private lateinit var providers: SearchProviders
    private lateinit var repository: SuggestionRepository
    private val remote = RemoteSuggestions()
    private lateinit var root: RelativeLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var editor: UrlInputEditText
    private lateinit var engine: ImageView
    private lateinit var badge: TextView
    private lateinit var clear: ImageView
    private lateinit var expand: ImageView
    private lateinit var go: ImageView
    private lateinit var toolbarBackground: View
    private lateinit var list: RecyclerView
    private lateinit var adapter: SuggestionAdapter
    private val rows = mutableListOf<SearchSuggestion>()
    private var baseline = ""
    private var changed = false
    private var activeEngine = 0
    private var generation = 0
    private var displayedGeneration = 0
    private var firstWasEmpty = false
    private var lastEmpty = true
    private val onTop get() = (arguments?.getInt("uiflags") ?: 0) and 1 != 0
    private val rounded get() = (arguments?.getInt("uiflags") ?: 0) and 4 != 0
    private var back: OnBackPressedCallback? = null
    private val childLifecycle = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentDestroyed(manager: FragmentManager, fragment: Fragment) {
            if (fragment is ExpandedUrlInputFragment && view != null) root.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        database = BrowserDatabase(requireContext())
        preferences = BrowserPreferences(requireContext())
        repository = SuggestionRepository(database)
        providers = SearchProviders(requireContext(), preferences, database)
    }
    private fun color(attribute: Int): Int = requireContext().obtainStyledAttributes(intArrayOf(attribute)).let { values -> try { values.getColor(0, 0) } finally { values.recycle() } }
    private fun dp(value: Int) = requireContext().dp(value.toFloat())
    private fun icon(resource: Int, label: Int): ImageView = ImageView(requireContext()).apply {
        id = View.generateViewId(); setSkinImageResource(resource); contentDescription = getString(label)
        setColorFilter(color(R.attr.viaSubtleColor)); setPadding(dp(13), dp(13), dp(13), dp(13))
        setBackgroundResource(R.drawable.rounded_rect_ripple)
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        root = RelativeLayout(context).apply { layoutParams = ViewGroup.LayoutParams(-1, -1); contentDescription = getString(android.R.string.cancel) }
        toolbar = LinearLayout(context).apply { id = View.generateViewId(); orientation = LinearLayout.HORIZONTAL }
        toolbar.layoutParams = RelativeLayout.LayoutParams(-1, -2).apply { addRule(if (onTop) RelativeLayout.ALIGN_PARENT_TOP else RelativeLayout.ALIGN_PARENT_BOTTOM) }
        engine = icon(R.drawable.search, R.string.search)
        badge = TextView(context).apply {
            gravity = Gravity.CENTER; setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.search_badge_text_size).toFloat()); setTextColor(color(R.attr.viaSubtleColor)); visibility = View.GONE
            typeface = preferences.selectedTypeface(); setPadding(dp(6), dp(2), dp(6), dp(2))
            background = GradientDrawable().apply { setStroke(dp(1), color(R.attr.viaSubtleColor)); cornerRadius = dp(4).toFloat() }
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_VERTICAL; marginEnd = dp(4) }
        }
        clear = icon(R.drawable.close, R.string.desc_clear_text)
        expand = icon(R.drawable.search_expand, R.string.desc_expand_text)
        go = icon(R.drawable.search_go, R.string.search_hint)
        editor = UrlInputEditText(context).apply {
            setHint(R.string.search_hint); imeOptions = 2; inputType = 524289; gravity = Gravity.CENTER_VERTICAL
            setSelectAllOnFocus(true); setTextColor(color(R.attr.viaPrimaryTextColor)); setHintTextColor(color(R.attr.viaPrimaryTextColor))
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO; setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.search_input_text_size).toFloat()); setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(Int.MAX_VALUE)); setBackgroundColor(Color.TRANSPARENT)
            typeface = preferences.selectedTypeface(); layoutParams = FrameLayout.LayoutParams(-2, dp(48)).apply { gravity = Gravity.CENTER_VERTICAL }
        }
        val scroll = HorizontalScrollView(context).apply {
            isFillViewport = true; isHorizontalScrollBarEnabled = false; isHorizontalFadingEdgeEnabled = true; setFadingEdgeLength(dp(12))
            addView(editor); layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
        }
        list = RecyclerView(context).apply {
            setBackgroundColor(color(R.attr.viaBackgroundColor)); isVerticalScrollBarEnabled = true
            isVerticalFadingEdgeEnabled = true; setFadingEdgeLength(dp(32)); itemAnimator = DefaultItemAnimator()
            layoutManager = LinearLayoutManager(context).apply { reverseLayout = !onTop; stackFromEnd = !onTop }
            layoutParams = RelativeLayout.LayoutParams(-1, -2).apply {
                addRule(if (onTop) RelativeLayout.BELOW else RelativeLayout.ABOVE, toolbar.id)
                if (onTop) bottomMargin = dp(72) else topMargin = dp(72)
            }
        }
        toolbarBackground = View(context).apply {
            isClickable = true; isFocusable = false; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setBackgroundColor(color(R.attr.viaBackgroundColor))
            layoutParams = RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.ALIGN_TOP, toolbar.id); addRule(RelativeLayout.ALIGN_BOTTOM, toolbar.id) }
        }
        toolbar.addView(engine); toolbar.addView(badge); toolbar.addView(scroll); toolbar.addView(clear); toolbar.addView(expand); toolbar.addView(go)
        root.addView(toolbarBackground); root.addView(list); root.addView(toolbar)
        return root
    }
    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        adapter = SuggestionAdapter(rows, onTop, ::select, ::fill, ::longClick)
        list.adapter = adapter
        val args = arguments ?: Bundle()
        val initialEngine = args.getInt("search_engine")
        if (initialEngine != 0 && initialEngine != preferences.searchMode) activateEngine(initialEngine)
        baseline = when {
            !args.getString("title").isNullOrEmpty() -> { changed = true; args.getString("title").orEmpty() }
            !args.getString("keyword").isNullOrEmpty() -> args.getString("keyword").orEmpty()
            else -> args.getString("url").orEmpty().let { url ->
                if (url.isEmpty() || UrlInputText.isInternalDocument(url, requireContext().filesDir.path)) ""
                else UrlInputText.extractSearch(url, preferences.effectiveSearchUrl()) ?: UrlInputText.display(url)
            }
        }
        val draft = TransientState.get("URL_INPUT_CACHE")?.takeIf { it.getString("url").isNullOrEmpty() && baseline.isEmpty() || it.getString("url") == baseline }
        if (draft != null) activateEngine(draft.getInt("active_search_engine"))
        if (args.getBoolean("incognito")) editor.imeOptions = editor.imeOptions or 17301504
        editor.setText(state?.getString("text") ?: draft?.getString("title")?.takeIf(String::isNotEmpty) ?: baseline)
        if (state != null) activateEngine(state.getInt("activeEngine"))
        clear.visibility = if (editor.length() == 0) View.GONE else View.VISIBLE
        editor.onDeleteAtStart = Runnable { if (activeEngine != 0) clearEngine() }
        editor.setOnKeyListener { _, key, event ->
            when (key) {
                KeyEvent.KEYCODE_DEL if event.action == KeyEvent.ACTION_DOWN && editor.selectionStart == 0 && editor.selectionEnd == 0 && activeEngine != 0 -> {
                    clearEngine(); true
                }
                KeyEvent.KEYCODE_ENTER if event.action == KeyEvent.ACTION_UP -> {
                    submit(); true
                }
                else -> false
            }
        }
        editor.setOnEditorActionListener { _, action, event -> if (action == 2 && event == null) { submit(); true } else false }
        clear.setOnClickListener { clear.visibility = View.GONE; rows.clear(); adapter.notifyDataSetChanged(); editor.setText(""); updateEmpty() }
        // tb.k0.M3 is bound to both the tap and long-press paths of the provider
        // glyph.  The long press is useful when the editor has a shortcut badge:
        // it must still open the persisted engine selector rather than becoming a
        // no-op.
        engine.setOnClickListener { chooseEngine() }
        engine.setOnLongClickListener { chooseEngine(); true }
        expand.setOnClickListener { expandEditor() }; go.setOnClickListener { submit() }
        root.setOnClickListener { dismissKeepingDraft() }
        back = object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { dismissKeepingDraft() } }
            .also { requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it) }
        parentFragmentManager.registerFragmentLifecycleCallbacks(childLifecycle, false)
        parentFragmentManager.setFragmentResultListener("resultUrl", viewLifecycleOwner) { _, result ->
            result.getString("resultUrl")?.let { value ->
                editor.setText(value)
                if (result.getBoolean("resultGo")) submit()
                else editor.post {
                    showKeyboard()
                    val selection = result.getIntArray("resultSelection")
                    if (selection == null || selection.size != 2 || selection[0] == -1) editor.setSelection(editor.length())
                }
            }
            parentFragmentManager.clearFragmentResult("resultUrl")
        }
        style(args)
        applyInsets()
        editor.post(::showKeyboard)
        bindSuggestions()
    }
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun bindSuggestions() {
        viewLifecycleOwner.lifecycleScope.launch {
            var queryJob: Job? = null
            TextChanges(editor).map { text ->
                clear.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
                extractShortcut(text.toString())
            }.distinctUntilChanged().debounce(200).collect { text ->
                queryJob?.cancel()
                queryJob = launch {
                    generation++
                    fun display(result: List<SearchSuggestion>) {
                        if (generation != displayedGeneration) {
                            displayedGeneration = generation
                            if (result.isEmpty()) { firstWasEmpty = true; return }
                            rows.clear(); firstWasEmpty = false
                        }
                        if (firstWasEmpty) rows.clear()
                        rows.addAll(result); adapter.notifyDataSetChanged(); list.scrollToPosition(0); updateEmpty()
                    }
                    SearchCalculator.evaluate(text)?.let {
                        display(listOf(SearchSuggestion(it, type = SearchSuggestion.CALCULATION)))
                        return@launch
                    }
                    val flags = preferences.searchSuggestion
                    val provider = providers.suggestionProvider(if (activeEngine == 0) preferences.searchMode else activeEngine)
                    val openTabs = tabs()
                    val language = Locale.getDefault().toLanguageTag()
                    try {
                        coroutineScope {
                            launch { display(withContext(Dispatchers.IO) { repository.local(text, flags, openTabs) }) }
                            launch { display(withContext(Dispatchers.IO) {
                                remote.query(text, provider, flags and 16 != 0, language).map { SearchSuggestion(it, type = SearchSuggestion.REMOTE) }
                            }) }
                        }
                    } catch (error: CancellationException) { throw error }
                    catch (error: Exception) { android.util.Log.e("UrlInputFragment", "Suggestions failed", error) }
                }
            }
        }
    }
    private fun fill(value: String) { editor.setText(value); editor.setSelection(editor.length()) }
    private fun select(row: SearchSuggestion) {
        when {
            row.type == SearchSuggestion.TAB -> emitInput("VIA-SWITCH-TAB:${row.tabId}")
            row.type == SearchSuggestion.CALCULATION -> fill(row.input)
            activeEngine != 0 && (row.type == SearchSuggestion.REMOTE || row.type == SearchSuggestion.QUERY_HISTORY) -> emitEngine(activeEngine, row.input)
            else -> emitInput(row.input)
        }
    }
    private fun longClick(row: SearchSuggestion): Boolean {
        if (row.type != SearchSuggestion.QUERY_HISTORY) return false
        ViaDialog(requireActivity()).title(R.string.clear_search_history).message(R.string.clear_search_history_message)
            .positive(android.R.string.ok) { _, _ -> repository.clearQueries(); editor.setText(null as CharSequence?) }
            .negative(android.R.string.cancel).show()
        return true
    }
    private fun activateEngine(id: Int) {
        val name = providers.list().firstOrNull { it.id == id }?.name.orEmpty()
        if (name.isEmpty() || id == 0) return
        activeEngine = id; badge.text = if (name.length > 16) name.substring(0, 16) + "..." else name; badge.visibility = View.VISIBLE
    }
    private fun clearEngine() { activeEngine = 0; badge.text = null; badge.visibility = View.GONE }
    private fun extractShortcut(value: String): String {
        if (activeEngine != 0) return value
        val shortcut = providers.shortcut(value) ?: return value
        activateEngine(shortcut.first)
        editor.setText(shortcut.second); editor.setSelection(shortcut.second.length)
        return shortcut.second
    }
    private fun chooseEngine() {
        hideKeyboard()
        val options = providers.list()
        val current = preferences.searchMode
        ViaDialog(requireActivity()).title(R.string.search)
            .singleChoice(options.map { it.name }.toTypedArray(), options.indexOfFirst { it.id == current }) { index ->
                if (options[index].id != current) { providers.selectDefault(options[index].id); remote.clearCache(); changed = true }
            }.show()
    }
    private fun submit() {
        val input = editor.text.toString()
        TransientState.remove("URL_INPUT_CACHE")
        if (activeEngine != 0 && input.isNotEmpty()) emitEngine(activeEngine, input) else emitInput(input)
    }
    private fun emitEngine(id: Int, query: String) {
        hideKeyboard(); parentFragmentManager.popBackStack()
        parentFragmentManager.setFragmentResult("input", Bundle().apply { putInt("input_action", 2); putInt("input_engine", id); putString("input_query", query); putString("input", query) })
    }
    private fun emitInput(input: String) {
        hideKeyboard(); parentFragmentManager.popBackStack()
        if (changed || baseline != input) parentFragmentManager.setFragmentResult("input", Bundle().apply { putString("input", input) })
    }
    private fun dismissKeepingDraft() {
        val value = editor.text.toString()
        if (value.isEmpty() || value == baseline) TransientState.remove("URL_INPUT_CACHE")
        else TransientState.builder().name("URL_INPUT_CACHE").expiresAfter(180)
            .putString("url", baseline).putString("title", value).putInt("active_search_engine", activeEngine).save()
        parentFragmentManager.popBackStack()
    }
    private fun expandEditor() {
        root.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS; root.clearFocus()
        parentFragmentManager.beginTransaction().setReorderingAllowed(true)
            .setCustomAnimations(R.anim.search_enter, R.anim.search_hold, R.anim.search_hold, R.anim.search_exit)
            .add(R.id.fragment_container, ExpandedUrlInputFragment.newInstance(editor.text.toString(), intArrayOf(editor.selectionStart, editor.selectionEnd)))
            .addToBackStack(null).commit()
    }
    private fun tabs(): List<SearchTab> = arguments?.getParcelableArrayList<Bundle>("tabs").orEmpty().map {
        SearchTab(it.getLong("id"), it.getString("url").orEmpty(), it.getString("title").orEmpty())
    }
    private fun showKeyboard() { editor.requestFocus(); (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT) }
    private fun hideKeyboard() { if (::editor.isInitialized) (editor.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(editor.windowToken, 0) }
    private fun updateEmpty() { val empty = rows.isEmpty(); if (lastEmpty != empty) { lastEmpty = empty; roundToolbar() } }
    private fun shape(color: Int, topRadius: Float, bottomRadius: Float) = GradientDrawable().apply {
        setColor(color); cornerRadii = floatArrayOf(topRadius, topRadius, topRadius, topRadius, bottomRadius, bottomRadius, bottomRadius, bottomRadius)
    }
    private fun roundToolbar() {
        if (!rounded) return
        toolbarBackground.background = shape(color(R.attr.viaBackgroundColor), if (onTop || rows.isEmpty()) dp(18).toFloat() else 0f, if (!onTop || rows.isEmpty()) dp(18).toFloat() else 0f)
    }
    private fun style(args: Bundle) {
        if (args.getInt("uiflags") and 2 != 0) {
            val bg = args.getInt("bg"); val light = Color.red(bg) * .299 + Color.green(bg) * .587 + Color.blue(bg) * .114 >= 192
            val text = if (light) 0xde000000.toInt() else Color.WHITE
            val icons = if (light) 0xff232323.toInt() else Color.WHITE
            listOf(engine, clear, expand, go).forEach { it.setColorFilter(icons) }
            editor.setTextColor(text); editor.setHintTextColor(text); badge.setTextColor(text)
            badge.background = GradientDrawable().apply { setStroke(dp(1), text); cornerRadius = dp(4).toFloat() }
            toolbarBackground.setBackgroundColor(bg)
        } else roundToolbar()
        list.background = shape(color(R.attr.viaBackgroundColor), if (onTop) 0f else dp(18).toFloat(), if (onTop) dp(18).toFloat() else 0f)
        val corner = if (rounded) dp(18).toFloat() else 0f
        root.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_enabled, android.R.attr.state_focused), shape(1887469696, if (onTop) corner else 0f, if (onTop) 0f else corner))
            addState(intArrayOf(android.R.attr.state_enabled), shape(0x40808080, if (onTop) corner else 0f, if (onTop) 0f else corner))
        }
    }
    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            if (!WindowInsetsHelper.isFullscreen(insets, view)) {
                WindowInsetsHelper.applySystemBars(insets, true, view, if (rounded) null else if (onTop) toolbar else null, if (rounded) null else if (onTop) null else toolbar)
            } else {
                val bars = insets.getInsets(WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.systemBars())
                if (bars == androidx.core.graphics.Insets.NONE) WindowInsetsHelper.applySystemBars(insets, false, view, if (onTop) toolbar else null, if (onTop) null else toolbar)
                else if (Resources.getSystem().configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) { view.setPadding(bars.left, 0, bars.right, 0); toolbar.setPadding(0, 0, 0, 0) }
                else {
                    val upper = IntArray(4); val lower = IntArray(4); val edge = dp(16)
                    insets.displayCutout?.boundingRects?.forEach { rect ->
                        if (rect.top <= edge) {
                            if (rect.left < edge * 2) upper[0] = maxOf(upper[0], rect.right)
                            else if (rect.right >= view.measuredWidth - edge * 2) upper[2] = maxOf(upper[2], view.measuredWidth - rect.left)
                            else upper[1] = maxOf(upper[1], rect.bottom)
                        } else if (insets.getInsets(WindowInsetsCompat.Type.ime()).bottom <= 0 && rect.bottom >= view.measuredHeight - edge) {
                            if (rect.left < edge * 2) lower[0] = maxOf(lower[0], rect.right)
                            else if (rect.right >= view.measuredWidth - edge * 2) lower[2] = maxOf(lower[2], view.measuredWidth - rect.left)
                            else lower[3] = maxOf(lower[3], rect.height())
                        }
                    }
                    val padding = if (onTop) upper else lower
                    toolbar.setPadding(padding[0], padding[1], padding[2], padding[3]); view.setPadding(0, 0, 0, 0)
                }
            }
            insets
        }
    }
    override fun onPause() { hideKeyboard(); super.onPause() }
    override fun onSaveInstanceState(out: Bundle) { if (::editor.isInitialized) { out.putString("text", editor.text.toString()); out.putInt("activeEngine", activeEngine) }; super.onSaveInstanceState(out) }
    override fun onDestroyView() { parentFragmentManager.unregisterFragmentLifecycleCallbacks(childLifecycle); back = null; super.onDestroyView() }
    override fun onDestroy() { database.close(); super.onDestroy() }

    companion object {
        fun newInstance(title: String? = null, url: String? = null, keyword: String? = null, searchEngine: Int = 0,
                        top: Boolean = true, colored: Boolean = false, background: Int = 0, rounded: Boolean = false,
                        incognito: Boolean = false, tabs: List<SearchTab> = emptyList()) = UrlInputFragment().apply {
            arguments = Bundle().apply {
                putInt("uiflags", (if (top) 1 else 0) or (if (colored) 2 else 0) or (if (rounded) 4 else 0))
                putString("title", title); putString("url", url); putString("keyword", keyword); putInt("search_engine", searchEngine); putInt("bg", background); putBoolean("incognito", incognito)
                putParcelableArrayList("tabs", ArrayList(tabs.map { tab -> Bundle().apply { putLong("id", tab.id); putString("url", tab.url); putString("title", tab.title) } }))
            }
        }
    }
}
