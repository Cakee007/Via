package dev.ujhhgtg.via.ui

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.skins.setSkinImageResource

/** f8.h/o: toolbar-anchored find pane, with WebView's match count and 180-second query cache. */
class FindInPageFragment : BrowserOverlayFragment() {
    interface Host {
        fun findPageText(query: String)
        fun findPageNext(forward: Boolean)
        fun closePageFind()
    }
    private lateinit var input: EditText
    private lateinit var counter: TextView
    private val host get() = parentFragment as? Host
    override val scrimEnabled = false
    override fun paneTitle(): CharSequence = getString(R.string.action_find)

    override fun createContent(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        val font = BrowserPreferences(context).selectedTypeface()
        input = EditText(context).apply {
            setSingleLine(); setLines(1); textDirection = View.TEXT_DIRECTION_LOCALE
            inputType = 1; setSelectAllOnFocus(true)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            setBackgroundColor(0); setPadding(0, 0, 0, 0)
            if (Build.VERSION.SDK_INT >= 35) isLocalePreferredLineHeightForMinimumUsed = false
            setHint(R.string.action_find); typeface = font
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
            setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, 0xff000000.toInt()))
            setHintTextColor(settingsColor(context, R.attr.viaSecondaryTextColor, 0xff444444.toInt()))
        }
        val field = object : HorizontalScrollView(context) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                parent.requestDisallowInterceptTouchEvent(true)
                return super.dispatchTouchEvent(event)
            }
        }.apply {
            isFillViewport = true; isHorizontalScrollBarEnabled = false; isVerticalScrollBarEnabled = false
            overScrollMode = if (Build.VERSION.SDK_INT >= 32) View.OVER_SCROLL_IF_CONTENT_SCROLLS else View.OVER_SCROLL_NEVER
            setBackgroundResource(R.drawable.via_dialog_input)
            setPaddingRelative(context.dp(4f), context.dp(8f), context.dp(4f), context.dp(8f))
            addView(input, FrameLayout.LayoutParams(-2, -2))
        }
        counter = TextView(context).apply {
            gravity = Gravity.CENTER_VERTICAL; typeface = font
            setPadding(context.dp(4f), context.dp(4f), context.dp(4f), context.dp(4f))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_summary_size).toFloat())
            setTextColor(settingsColor(context, R.attr.viaSecondaryTextColor, 0xff444444.toInt()))
        }
        fun button(icon: Int, key: String, label: Int, action: () -> Unit) = ImageView(context).apply {
            setSkinImageResource(icon, key)
            setColorFilter(settingsColor(context, R.attr.viaSubtleColor, 0xff666666.toInt()))
            setPadding(context.dp(13f), context.dp(13f), context.dp(13f), context.dp(13f))
            setBackgroundResource(R.drawable.rounded_rect_ripple)
            contentDescription = getString(label); isClickable = true; isFocusable = true
            setOnClickListener { action() }
        }
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(16f), 0, context.dp(16f), 0)
            isClickable = true; isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(field, LinearLayout.LayoutParams(0, -2, 1f))
            addView(counter, LinearLayout.LayoutParams(-2, -1))
            addView(button(R.drawable.menu_previous, "ic_prev", R.string.find_pre) { host?.findPageNext(false) }, LinearLayout.LayoutParams(context.dp(48f), context.dp(48f)))
            addView(button(R.drawable.menu_next, "ic_next", R.string.find_next) { host?.findPageNext(true) }, LinearLayout.LayoutParams(context.dp(48f), context.dp(48f)))
            addView(button(R.drawable.close, "ic_close", android.R.string.cancel) { host?.closePageFind() }, LinearLayout.LayoutParams(context.dp(48f), context.dp(48f)))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        input.requestFocus()
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { host?.findPageText(s?.toString().orEmpty()) }
        })
        val query = savedInstanceState?.getString("query") ?: arguments?.getString("TEXT")?.takeIf(String::isNotEmpty)
            ?: cachedQuery.takeIf { SystemClock.elapsedRealtime() < cacheExpiry }.orEmpty()
        if (query.isNotEmpty()) input.setText(query)
    }
    override fun focusContent() { input.requestFocus(); input.performAccessibilityAction(64, null) }
    fun setQuery(query: String?) { if (::input.isInitialized) input.setText(query.orEmpty()) }
    fun updateMatches(active: Int, total: Int, done: Boolean) {
        if (!::input.isInitialized || !done) return
        counter.visibility = if (input.length() == 0 || active == -1) View.GONE else View.VISIBLE
        if (counter.isVisible) counter.text = "${if (total > 0) active + 1 else active}/$total"
    }
    override fun onSaveInstanceState(outState: Bundle) { if (::input.isInitialized) outState.putString("query", input.text.toString()); super.onSaveInstanceState(outState) }
    override fun onDestroyView() {
        if (::input.isInitialized) { cachedQuery = input.text.toString(); cacheExpiry = SystemClock.elapsedRealtime() + 180_000 }
        host?.findPageText("")
        super.onDestroyView()
    }
    companion object {
        const val TAG = "find_in_page"
        private var cachedQuery = ""
        private var cacheExpiry = 0L
        fun newInstance(width: Int, gravity: Int, height: Int, homepage: Boolean, text: String? = null) = FindInPageFragment().apply {
            arguments = Bundle().apply { putInt("width", width); putInt("height", height); putInt("gravity", gravity); putBoolean("homepage", homepage); putString("TEXT", text) }
        }
    }
}
