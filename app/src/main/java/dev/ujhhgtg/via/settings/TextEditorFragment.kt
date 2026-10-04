package dev.ujhhgtg.via.settings

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import dev.ujhhgtg.via.ui.dp
import dev.ujhhgtg.via.ui.widgets.FastScrollView

/** hb.i1, with original q4.b scrolling and edit_text_result contract. */
class TextEditorFragment : SettingsPageFragment() {
    private lateinit var input: EditText
    private var initialDigest = ""
    override fun configureToolbar(toolbar: SettingsToolbar) {
        toolbar.setTitle(R.string.untitled)
        toolbar.addAction(null, R.string.action_save) { save(false) }
    }
    override fun createContent(inflater: LayoutInflater, container: ViewGroup?): View {
        val context = requireContext()
        input = EditText(context).apply {
            setBackgroundColor(0)
            setPadding(context.dp(16f), context.dp(16f), context.dp(16f), context.dp(16f))
            if (Build.VERSION.SDK_INT >= 35) isLocalePreferredLineHeightForMinimumUsed = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            inputType = 131073
            setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, 0xff000000.toInt()))
            setHintTextColor(settingsColor(context, R.attr.viaSecondaryTextColor, 0xff444444.toInt()))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
            gravity = Gravity.TOP
        }
        return FastScrollView(context).apply {
            isFillViewport = true
            overScrollMode = if (Build.VERSION.SDK_INT >= 32) View.OVER_SCROLL_IF_CONTENT_SCROLLS else View.OVER_SCROLL_NEVER
            addView(input, FrameLayout.LayoutParams(-1, -2))
        }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = arguments
        var code = false
        if (args != null) {
            toolbar.setTitle(args.getString("title"))
            val text = args.getString("text")
            input.setText(text); input.hint = args.getString("placeholder")
            initialDigest = digest(text)
            code = args.getBoolean("code", false)
        }
        input.textDirection = if (code) View.TEXT_DIRECTION_LOCALE else View.TEXT_DIRECTION_LTR
        if (code) { input.inputType = input.inputType or 524288; input.typeface = Typeface.MONOSPACE }
    }
    private fun unchanged() = digest(input.text.toString()) == initialDigest
    override fun allowPredictiveBack() = unchanged()
    override fun onToolbarBack() = save(true)
    private fun closeEditor() = super.onToolbarBack()
    private fun save(confirm: Boolean) {
        val text = input.text.toString()
        if (unchanged()) {
            if (!confirm) parentFragmentManager.setFragmentResult("edit_text_result", Bundle().apply { putString("text", text) })
            closeEditor(); return
        }
        if (confirm) {
            ViaDialog(requireActivity()).title(R.string.message).message(R.string.modified_content_has_not_been_saved_message)
                .positive(R.string.save_and_exit) { _, _ -> save(false) }.negative(R.string.exit) { closeEditor() }.show()
        } else {
            parentFragmentManager.setFragmentResult("edit_text_result", Bundle().apply { putString("text", text) })
            closeEditor()
        }
    }
    override fun onPause() {
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        super.onPause()
    }
    private fun digest(value: String?): String = if (value.isNullOrEmpty()) "" else
        java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }
    companion object {
        fun newInstance(title: String?, text: String?, placeholder: String?, code: Boolean) = TextEditorFragment().apply {
            arguments = Bundle().apply {
                title?.takeIf(String::isNotEmpty)?.let { putString("title", it) }
                text?.takeIf(String::isNotEmpty)?.let { putString("text", it) }
                placeholder?.takeIf(String::isNotEmpty)?.let { putString("placeholder", it) }
                if (code) putBoolean("code", true)
            }
        }
    }
}
