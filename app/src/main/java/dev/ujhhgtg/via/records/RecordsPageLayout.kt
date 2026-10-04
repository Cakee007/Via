package dev.ujhhgtg.via.records

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.SettingsRecyclerView
import dev.ujhhgtg.via.ui.attachClearTextButton
import dev.ujhhgtg.via.ui.dp

/** mark.via.common.widget.r0 and z8.r3.b: shared search/list/empty/action structure. */
@SuppressLint("ViewConstructor")
internal class RecordsPageLayout(context: Context, query: String, changed: (String) -> Unit) : LinearLayout(context) {
    val search = EditText(context).apply {
        if (android.os.Build.VERSION.SDK_INT >= 35) isLocalePreferredLineHeightForMinimumUsed = false
        textDirection = TEXT_DIRECTION_LOCALE
        setSingleLine(); hint = context.getString(R.string.search_hint); setText(query)
        inputType = android.text.InputType.TYPE_CLASS_TEXT; imeOptions = EditorInfo.IME_ACTION_DONE
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
        typeface = BrowserPreferences(context).selectedTypeface()
        setTextColor(recordColor(context, R.attr.viaPrimaryTextColor))
        setHintTextColor(recordColor(context, R.attr.viaDisabledTextColor, Color.GRAY))
        background = ContextCompat.getDrawable(context, R.drawable.widget_search_background)
        setPadding(context.dp(16f), context.dp(10f), context.dp(16f), context.dp(10f))
        compoundDrawablePadding = context.dp(12f)
        attachClearTextButton(recordColor(context, R.attr.viaSubtleColor))
        setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { clearFocus(); hideKeyboard(); true } else false
        }
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = changed(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }
    val list = SettingsRecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context)
        addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy != 0 && search.hasFocus()) { search.clearFocus(); search.hideKeyboard() }
            }
        })
    }
    private val empty = TextView(context).apply {
        text = "¯\\_(ツ)_/¯"; contentDescription = context.getString(R.string.empty_hint)
        gravity = Gravity.CENTER
        setPadding(0, context.dp(20f), 0, context.dp(20f))
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_empty_state_size).toFloat())
        setTextColor(recordColor(context, R.attr.viaSecondaryTextColor, Color.GRAY))
        visibility = GONE
    }
    val actions = RecordsActionBar(context)
    init {
        orientation = VERTICAL
        addView(search, LayoutParams(-1, -2).apply { setMargins(context.dp(14f), context.dp(10f), context.dp(14f), context.dp(10f)) })
        addView(list, LayoutParams(-1, 0, 1f))
        addView(empty, LayoutParams(-1, 0, 1f))
        addView(actions, LayoutParams(-1, -2))
    }
    fun setEmpty(value: Boolean) { list.visibility = if (value) GONE else VISIBLE; empty.visibility = if (value) VISIBLE else GONE }
}

internal fun View.hideKeyboard() = (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(windowToken, 0)
interface RecordsBackHandler { val recordsHasTransientState: Boolean; fun handleRecordsBack(): Boolean }

/** i6.g0.d: remove a web scheme and a sole trailing root slash, capped at 256 chars. */
internal fun recordDisplayUrl(url: String): String {
    if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return url.take(256)
    val start = url.indexOf("://") + 3
    val end = if (url.indexOf('/', start) == url.lastIndex) url.lastIndex else url.length
    return url.substring(start, end).take(256)
}
