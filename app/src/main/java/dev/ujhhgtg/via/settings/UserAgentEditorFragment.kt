package dev.ujhhgtg.via.settings

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
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
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.common.launchIo
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.SettingsData
import dev.ujhhgtg.via.data.SettingsDataRepository
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import dev.ujhhgtg.via.ui.dp

/** hb.v1 and its original l1 horizontally scrolling title field. */
class UserAgentEditorFragment : SettingsPageFragment() {
    private var editingId = 0
    private var item = SettingsData()
    private lateinit var titleInput: EditText
    private lateinit var agentInput: EditText
    private var initialDigest: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editingId = arguments?.getInt("id", 0) ?: 0
    }
    override fun configureToolbar(toolbar: SettingsToolbar) {
        toolbar.setTitle(if (editingId > 0) R.string.action_edit else R.string.action_new)
        toolbar.addAction(null, R.string.action_save) { save(false) }
    }

    override fun createContent(inflater: LayoutInflater, container: ViewGroup?): View {
        val context = requireContext()
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(16f), context.dp(12f), context.dp(16f), 0)
        }
        titleInput = EditText(context).apply {
            layoutParams = FrameLayout.LayoutParams(-2, -2)
            setSingleLine(); maxLines = 1; setLines(1); ellipsize = TextUtils.TruncateAt.END
            textDirection = View.TEXT_DIRECTION_LOCALE
            setBackgroundColor(0); setPadding(0, 0, 0, 0)
            if (Build.VERSION.SDK_INT >= 35) isLocalePreferredLineHeightForMinimumUsed = false
            inputType = 1; imeOptions = 5
            setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, 0xff000000.toInt()))
            setHintTextColor(settingsColor(context, R.attr.viaSecondaryTextColor, 0xff444444.toInt()))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
            setHint(R.string.hint_title); typeface = BrowserPreferences(context).selectedTypeface()
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
        agentInput = EditText(context).apply {
            setBackgroundResource(R.drawable.via_dialog_input)
            if (Build.VERSION.SDK_INT >= 35) isLocalePreferredLineHeightForMinimumUsed = false
            imeOptions = 6; inputType = 524289
            setLines(5); maxLines = 5; minLines = 5
            setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, 0xff000000.toInt()))
            setHintTextColor(settingsColor(context, R.attr.viaSecondaryTextColor, 0xff444444.toInt()))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
            setHint(R.string.agent); setHorizontallyScrolling(false); gravity = Gravity.TOP
            textDirection = View.TEXT_DIRECTION_LTR; typeface = BrowserPreferences(context).selectedTypeface()
            setPadding(0, context.dp(8f), 0, context.dp(8f))
        }
        body.addView(agentInput, LinearLayout.LayoutParams(-1, -2))
        return body
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        agentInput.setOnKeyListener { _, key, event ->
            if (key != KeyEvent.KEYCODE_ENTER) false
            else { if (event.action == KeyEvent.ACTION_UP) save(false); true }
        }
        val context = requireContext().applicationContext
        if (editingId > 0) viewLifecycleOwner.launchIo({
            BrowserDatabase(context).use { SettingsDataRepository(it).find(editingId)!! }
        }, { existing ->
            item = existing; titleInput.setText(existing.title); agentInput.setText(existing.content)
            initialDigest = digest(existing.title, existing.content)
        }) { android.util.Log.w("ViaSettings", "Unable to read user agent", it) }
    }

    private fun titleText() = buildString {
        titleInput.text.forEach { char ->
            if (!Character.isWhitespace(char)) append(if (char.code == 160) ' ' else char)
            else if (char != '\r') append(' ')
        }
    }.trim()
    private fun agentText() = agentInput.text.toString().replace(Regex("[\\t\\n\\r]+"), " ").trim()
    private fun unchanged(): Boolean {
        val current = digest(titleText(), agentText())
        return initialDigest == null && current.isEmpty() || current == initialDigest
    }
    override fun allowPredictiveBack() = unchanged()
    override fun onToolbarBack() = save(true)

    private fun save(confirm: Boolean) {
        val title = titleText()
        val agent = agentText()
        if (unchanged()) { closeEditor(); return }
        if (confirm) {
            ViaDialog(requireActivity()).title(R.string.message).message(R.string.modified_content_has_not_been_saved_message)
                .positive(R.string.save_and_exit) { _, _ -> save(false) }.negative(R.string.exit) { closeEditor() }.show()
            return
        }
        if (title.isEmpty()) { ViaToast.makeText(requireContext(), R.string.title_can_not_be_empty, ViaToast.LENGTH_SHORT).show(); return }
        if (agent.isEmpty()) { ViaToast.makeText(requireContext(), R.string.user_agent_can_not_be_empty, ViaToast.LENGTH_SHORT).show(); return }
        item = item.copy(title = title, content = agent)
        val context = requireContext().applicationContext
        viewLifecycleOwner.launchIo({
            val now = System.currentTimeMillis()
            BrowserDatabase(context).use { db ->
                val repository = SettingsDataRepository(db)
                if (item.id > 0) { item = item.copy(lastUpdatedAt = now); repository.save(item) > 0 }
                else {
                    item = item.copy(lastUpdatedAt = now, createdAt = now, type = SettingsData.USER_AGENT)
                    val id = repository.save(item); item = item.copy(id = id); id > 0
                }
            }
        }, {
                parentFragmentManager.setFragmentResult("ua_result", Bundle().apply { putInt("ua_result", item.id) })
                closeEditor()
        }) { android.util.Log.w("ViaSettings", "Unable to save user agent", it) }
    }

    private fun closeEditor() = super.onToolbarBack()
    override fun onPause() {
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(agentInput.windowToken, 0)
        super.onPause()
    }
    private fun digest(vararg values: String?): String {
        val source = values.joinToString("") { it ?: "__NULL__" }
        if (source.isEmpty()) return ""
        return java.security.MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }
    }
    companion object {
        fun newInstance(id: Int) = UserAgentEditorFragment().apply { arguments = Bundle().apply { putInt("id", id) } }
    }
}
