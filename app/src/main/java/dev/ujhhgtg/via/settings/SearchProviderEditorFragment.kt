package dev.ujhhgtg.via.settings

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.browser.UrlResolver
import dev.ujhhgtg.via.common.launchIo
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.SettingsData
import dev.ujhhgtg.via.data.SettingsDataRepository
import dev.ujhhgtg.via.search.SearchProviders
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import dev.ujhhgtg.via.ui.dp
import org.json.JSONObject
import java.util.Locale

/** hb.c1 and tb.c: native title/URL/shortcut fields and original validation/result contract. */
class SearchProviderEditorFragment : SettingsPageFragment() {
    private var editingId = 0
    private var item = SettingsData()
    private lateinit var preferences: BrowserPreferences
    private lateinit var titleInput: EditText
    private lateinit var urlInput: EditText
    private lateinit var shortcutInput: EditText
    private var shortcuts = mutableMapOf<Int, String>()
    private var initialDigest: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editingId = arguments?.getInt("id", 0) ?: 0
        preferences = BrowserPreferences(requireContext())
    }
    override fun configureToolbar(toolbar: SettingsToolbar) {
        toolbar.setTitle(if (editingId != 0) R.string.action_edit else R.string.action_new)
        toolbar.addAction(null, R.string.action_save) { save(false) }
    }
    private fun input(hint: Int) = EditText(requireContext()).apply {
        if (Build.VERSION.SDK_INT >= 35) isLocalePreferredLineHeightForMinimumUsed = false
        setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, 0xff000000.toInt()))
        setHintTextColor(settingsColor(context, R.attr.viaSecondaryTextColor, 0xff444444.toInt()))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
        setHint(hint); typeface = preferences.selectedTypeface()
        setBackgroundResource(R.drawable.via_dialog_input)
        setPadding(0, context.dp(8f), 0, context.dp(8f))
    }
    override fun createContent(inflater: LayoutInflater, container: ViewGroup?): View {
        val context = requireContext()
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(16f), context.dp(12f), context.dp(16f), 0)
        }
        titleInput = input(R.string.hint_title).apply {
            layoutParams = FrameLayout.LayoutParams(-2, -2)
            setSingleLine(); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            textDirection = View.TEXT_DIRECTION_LOCALE
            setBackgroundColor(0); setPadding(0, 0, 0, 0); inputType = 1; imeOptions = 5
        }
        body.addView(object : HorizontalScrollView(context) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                parent.requestDisallowInterceptTouchEvent(true)
                return super.dispatchTouchEvent(event)
            }
        }.apply {
            isHorizontalScrollBarEnabled = false; isVerticalScrollBarEnabled = false; isFillViewport = true
            overScrollMode = if (Build.VERSION.SDK_INT >= 32) View.OVER_SCROLL_IF_CONTENT_SCROLLS else View.OVER_SCROLL_NEVER
            setBackgroundResource(R.drawable.via_dialog_input)
            setPaddingRelative(paddingStart, context.dp(8f), paddingEnd, context.dp(8f))
            addView(titleInput)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = context.dp(12f) })
        urlInput = input(R.string.info_url).apply {
            inputType = 524289; imeOptions = 6; setLines(5); maxLines = 5; minLines = 5
            setHorizontallyScrolling(false); gravity = Gravity.TOP; textDirection = View.TEXT_DIRECTION_LTR
            setOnKeyListener { _, key, event ->
                if (key != KeyEvent.KEYCODE_ENTER) false else { if (event.action == KeyEvent.ACTION_UP) save(false); true }
            }
        }
        body.addView(urlInput, LinearLayout.LayoutParams(-1, -2))
        shortcutInput = input(R.string.search_shortcut).apply {
            setSingleLine(); inputType = 524289; imeOptions = 6
            setOnEditorActionListener { _, action, _ -> if (action == 6) { save(false); true } else false }
        }
        body.addView(shortcutInput, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(12f) })
        body.addView(TextView(context).apply {
            setText(R.string.search_shortcut_description)
            setTextColor(settingsColor(context, R.attr.viaSecondaryTextColor, 0xff444444.toInt()))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_summary_size).toFloat())
            typeface = preferences.selectedTypeface()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(6f) })
        if (editingId < 0) {
            titleInput.isEnabled = false
            titleInput.setTextColor(settingsColor(context, R.attr.viaSecondaryTextColor, 0xff444444.toInt()))
            urlInput.visibility = View.GONE
        }
        return body
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext().applicationContext
        viewLifecycleOwner.launchIo({
            BrowserDatabase(context).use { database ->
                val providers = SearchProviders(context, preferences, database)
                val existing = if (editingId > 0) SettingsDataRepository(database).find(editingId)
                    else providers.list().firstOrNull { it.id == editingId }?.let { SettingsData(id = it.id, title = it.name) }
                Pair(existing, providers.shortcuts())
            }
        }, { (existing, values) ->
                shortcuts = values.toMutableMap()
                if (existing != null) {
                    item = existing; titleInput.setText(existing.title); urlInput.setText(existing.content)
                    shortcutInput.setText(shortcuts[existing.id].orEmpty())
                    initialDigest = digest(existing.title, existing.content, shortcuts[existing.id].orEmpty())
                }
        }) { android.util.Log.w("ViaSettings", "Unable to read search engine", it) }
    }
    private fun titleText() = titleInput.text.toString().replace(Regex("[\\t\\n\\r\\u00a0]+"), " ").trim()
    private fun urlText(): String? = if (editingId < 0) null else urlInput.text.toString().trim().let {
        if (it.isNotEmpty() && ':' !in it) UrlResolver.normalizeInput(it) else it
    }
    private fun shortcutText() = shortcutInput.text.toString().trim().lowercase(Locale.ROOT)
    private fun unchanged(): Boolean = digest(titleText(), urlText(), shortcutText()).let { initialDigest == null && it.isEmpty() || it == initialDigest }
    override fun allowPredictiveBack() = unchanged()
    override fun onToolbarBack() = save(true)
    private fun save(confirm: Boolean) {
        if (unchanged()) { closeEditor(); return }
        if (confirm) {
            ViaDialog(requireActivity()).title(R.string.message).message(R.string.modified_content_has_not_been_saved_message)
                .positive(R.string.save_and_exit) { _, _ -> save(false) }.negative(R.string.exit) { closeEditor() }.show()
            return
        }
        val shortcut = shortcutText()
        if (shortcut.codePointCount(0, shortcut.length) > 16 || shortcut.any(Character::isWhitespace)) {
            shortcutInput.requestFocus(); toast(R.string.search_shortcut_invalid); return
        }
        if (shortcut.isNotEmpty() && shortcuts.any { it.key != editingId && it.value == shortcut }) {
            shortcutInput.requestFocus(); toast(R.string.search_shortcut_duplicate); return
        }
        if (editingId < 0) { finishSave(editingId, shortcut); return }
        val title = titleText(); val url = urlText().orEmpty()
        if (title.isEmpty()) { toast(R.string.title_can_not_be_empty); return }
        if (url.isEmpty()) { ViaToast.makeText(requireContext(), getString(R.string.is_required, urlInput.hint), ViaToast.LENGTH_SHORT).show(); return }
        val lower = url.lowercase(Locale.ROOT)
        if (url.length >= 10 && listOf("via.search", "v://search", "via://search").any(lower::contains)) { toast(R.string.url_is_invalid); return }
        val context = requireContext().applicationContext
        viewLifecycleOwner.launchIo({
            val now = System.currentTimeMillis()
            item = item.copy(title = title, content = url, lastUpdatedAt = now,
                createdAt = if (item.id > 0) item.createdAt else now, type = SettingsData.SEARCH_ENGINE)
            BrowserDatabase(context).use { SettingsDataRepository(it).save(item) }
        }, { finishSave(it, shortcut) }) { Log.w("ViaSettings", "Unable to save search engine", it) }
    }
    private fun finishSave(id: Int, shortcut: String) {
        shortcuts[id] = shortcut
        preferences.searchShortcuts = JSONObject(shortcuts.mapKeys { it.key.toString() }).toString()
        parentFragmentManager.setFragmentResult("engine_result", Bundle().apply { putInt("engine_result", id) })
        closeEditor()
    }
    private fun toast(resource: Int) = ViaToast.makeText(requireContext(), resource, ViaToast.LENGTH_SHORT).show()
    private fun closeEditor() = super.onToolbarBack()
    override fun onPause() {
        val edit = if (shortcutInput.hasFocus()) shortcutInput else urlInput
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(edit.windowToken, 0)
        super.onPause()
    }
    private fun digest(vararg values: String?): String {
        val source = values.joinToString("") { it ?: "__NULL__" }
        if (source.isEmpty()) return ""
        return java.security.MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }
    }
    companion object { fun newInstance(id: Int) = SearchProviderEditorFragment().apply { arguments = Bundle().apply { putInt("id", id) } } }
}
