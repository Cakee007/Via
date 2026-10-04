package dev.ujhhgtg.via.browser.filter

import android.content.Context
import android.content.Intent
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
import androidx.core.net.toUri
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.settings.SettingsPageFragment
import dev.ujhhgtg.via.settings.SettingsToolbar
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import dev.ujhhgtg.via.ui.widgets.FastScrollView
import java.security.MessageDigest
import java.util.concurrent.Executors

/** z7.v: source custom-filter editor, save-on-leave confirmation, validation and help actions. */
class CustomFiltersFragment : SettingsPageFragment() {
    private lateinit var input: EditText
    private lateinit var store: FilterStore
    private var initialDigest = ""
    private val worker = Executors.newSingleThreadExecutor()

    override fun configureToolbar(toolbar: SettingsToolbar) {
        toolbar.setTitle(R.string.custom_filters)
        toolbar.addAction(null, R.string.help) { help() }
        toolbar.addAction(null, R.string.action_save) { if (unchanged()) closeEditor() else save(::closeEditor) }
    }
    override fun createContent(inflater: LayoutInflater, container: ViewGroup?): View {
        val context = requireContext()
        fun units(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
        input = EditText(context).apply {
            setBackgroundColor(0); setPaddingRelative(units(12), units(16), units(12), units(16))
            if (Build.VERSION.SDK_INT >= 35) isLocalePreferredLineHeightForMinimumUsed = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO; inputType = 655361
            val colors = context.obtainStyledAttributes(R.styleable.ViaEditorColors)
            try { setTextColor(colors.getColor(R.styleable.ViaEditorColors_viaPrimaryTextColor, 0)); setHintTextColor(colors.getColor(R.styleable.ViaEditorColors_viaSecondaryTextColor, 0)) } finally { colors.recycle() }
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.night_preview_text_size).toFloat())
            gravity = Gravity.TOP; typeface = Typeface.MONOSPACE; setHint(R.string.one_filter_per_line); textDirection = View.TEXT_DIRECTION_LTR
        }
        return FastScrollView(context).apply {
            isFillViewport = true
            overScrollMode = if (Build.VERSION.SDK_INT >= 32) View.OVER_SCROLL_IF_CONTENT_SCROLLS else View.OVER_SCROLL_NEVER
            addView(input, FrameLayout.LayoutParams(-1, -2))
        }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        store = FilterStore(requireContext())
        worker.execute {
            val text = store.readCustom().trim()
            activity?.runOnUiThread { if (this.view != null) { initialDigest = digest(text); input.setText(text) } }
        }
    }
    override fun allowPredictiveBack() = unchanged()
    override fun onToolbarBack() = confirmLeave(::closeEditor)
    private fun closeEditor() = super.onToolbarBack()
    private fun unchanged() = digest(input.text.toString()) == initialDigest
    private fun confirmLeave(done: () -> Unit) {
        if (unchanged()) done()
        else ViaDialog(requireActivity()).title(R.string.message).message(R.string.modified_content_has_not_been_saved_message)
            .positive(R.string.save_and_exit) { _, _ -> save(done) }.negative(R.string.exit) { done() }.show()
    }
    private fun help() {
        ViaDialog(requireActivity()).title(R.string.help).message(R.string.simple_guide_of_writing_rule).positive(android.R.string.ok)
            .neutral(R.string.learn_more) { confirmLeave {
                startActivity(Intent(requireContext(), Shell::class.java).setData("https://help.eyeo.com/en/adblockplus/how-to-write-filters".toUri()))
            } }.show()
    }
    private fun save(done: () -> Unit) {
        val source = input.text.toString()
        worker.execute {
            val errors = runCatching {
                store.writeCustom(source)
                source.split('\n').filter { it.isNotEmpty() && it[0] != '!' && FilterEngine().addList(it) == 0 }
            }.getOrElse { return@execute }
            activity?.runOnUiThread {
                if (view == null) return@runOnUiThread
                (activity as? Shell)?.browserReloadPreferences()
                parentFragmentManager.setFragmentResult("filters_changed", Bundle())
                if (errors.isEmpty()) done()
                else ViaDialog(requireActivity()).title(R.string.title_warning)
                    .message((listOf(getString(R.string.following_filters_invalid_message)) + errors).joinToString("\n"))
                    .cancelable(false).canceledOnTouchOutside(false).positive(android.R.string.ok) { _, _ -> done() }.show()
            }
        }
    }
    private fun digest(text: String) = if (text.isEmpty()) "" else MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }
    override fun onPause() {
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        super.onPause()
    }
    override fun onDestroy() { worker.shutdown(); super.onDestroy() }
}
