package dev.ujhhgtg.via.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.browser.script.ScriptStore
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.search.UrlInputText
import dev.ujhhgtg.via.settings.SettingsRecyclerView
import dev.ujhhgtg.via.skins.setSkinImageResource
import dev.ujhhgtg.via.ui.BrowserOverlayFragment
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dp
import kotlin.math.ceil

/** f8.w with g8.u/l/j/f bindings. It is an inline browser Fragment, not a Dialog window. */
class ReaderPageToolsFragment : BrowserOverlayFragment() {
    private data class Row(val id: Int, val title: Int = 0, val value: Int = 0)
    private lateinit var list: RecyclerView
    private lateinit var preferences: BrowserPreferences
    private val rows = ArrayList<Row>()
    private var readerState = 0
    private var more = false
    private val pageUrl get() = arguments?.getString("url").orEmpty()
    private val pageTitle get() = arguments?.getString("title").orEmpty()
    private val flags get() = arguments?.getInt("flag") ?: 0
    private val dark get() = preferences.isNightMode

    override fun paneTitle() = getString(R.string.site_info)
    override fun createContent(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View = RecyclerView(requireContext()).apply {
        layoutParams = ViewGroup.LayoutParams(-1, -2); layoutManager = LinearLayoutManager(context)
        itemAnimator = DefaultItemAnimator(); overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        edgeEffectFactory = SettingsRecyclerView.StretchEdgeEffectFactory()
        list = this
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = BrowserPreferences(requireContext())
        readerState = savedInstanceState?.getInt("reader_state") ?: arguments?.getInt("reader_state") ?: 0
        more = savedInstanceState?.getBoolean("more") ?: false
        list.adapter = Adapter()
        refresh()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("reader_state", readerState); outState.putBoolean("more", more); super.onSaveInstanceState(outState) }
    override fun focusContent() { list.scrollToPosition(0); list.requestFocus(); list.performAccessibilityAction(64, null) }
    fun updateReaderState(state: Int) {
        arguments?.putInt("reader_state", state)
        if (readerState != state) { readerState = state; if (::preferences.isInitialized) refresh() }
    }
    private fun refresh() {
        val previous = rows.toList()
        val next = ArrayList<Row>()
        next += Row(0)
        val shown = !more && readerState == 3
        val network = isNetworkPage(pageUrl)
        if (shown) {
            next += Row(COLORS, value = preferences.readerThemeColor)
            next += Row(TEXT_SIZE, value = preferences.readerTextSize)
        } else {
            if (flags and 1 != 0) next += Row(1, R.string.view_certificate)
            if (network) next += Row(2, R.string.action_view_cookies)
            // f8.w.e3 adds site settings for every page, including generated documents.
            next += Row(3, if (flags and 2 != 0) R.string.site_conf_enabled else R.string.site_conf)
            if (preferences.scriptsEnabled && network &&
                ScriptStore(requireContext()).use { store -> store.list().any { it.id >= 0 && it.appliesTo(pageUrl) } }) {
                next += Row(4, R.string.view_scripts)
            }
        }
        if (readerState != 0) next += Row(7, if (readerState == 3) R.string.hide_reader else R.string.show_reader)
        if (shown) next += Row(8, R.string.more_options)
        // f8.w.k3/g8.n retain the header when the reader rows change. In particular,
        // expanding More must leave the URL's expansion and pressed state intact.
        val changes = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = previous.size
            override fun getNewListSize() = next.size
            override fun areItemsTheSame(old: Int, new: Int): Boolean = previous[old].let { before ->
                val after = next[new]
                before.id == after.id && (before.id != COLORS && before.id != TEXT_SIZE || before.value == after.value)
            }
            override fun areContentsTheSame(old: Int, new: Int) = previous[old] == next[new]
        })
        rows.clear(); rows.addAll(next)
        list.adapter?.let { changes.dispatchUpdatesTo(it) }
    }

    private fun emit(action: Int) = parentFragmentManager.setFragmentResult(RESULT, Bundle().apply {
        putInt("action", action); putBoolean("show_reader", readerState != 3); putString("url", pageUrl)
    })
    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = when (rows[position].id) { 0 -> 0; COLORS -> COLORS; TEXT_SIZE -> TEXT_SIZE; else -> 1 }
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder {
            val context = requireContext()
            val view = when (type) {
                0 -> header()
                COLORS -> ReaderColorPalette(context, preferences.readerThemeColor, dark) { preferences.readerThemeColor = it; emit(STYLE_CHANGED) }
                TEXT_SIZE -> ReaderTextSizeControl(context, preferences.readerTextSize) { preferences.readerTextSize = it; emit(STYLE_CHANGED) }
                else -> TextView(context).apply {
                    setTextColor(color(R.attr.viaAccentColor)); setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.night_preview_text_size).toFloat())
                    setLines(1); maxLines = 1; ellipsize = TextUtils.TruncateAt.END; textDirection = View.TEXT_DIRECTION_LOCALE; typeface = preferences.selectedTypeface()
                    setBackgroundResource(R.drawable.flat_ripple); setPaddingRelative(units(16), units(12), units(16), units(12))
                }
            }
            view.layoutParams = RecyclerView.LayoutParams(-1, if (type == TEXT_SIZE) context.dp(48f) else -2)
            return Holder(view)
        }
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = rows[position]
            when (val view = holder.itemView) {
                is ReaderColorPalette -> view.select(row.value)
                is ReaderTextSizeControl -> view.setTextSize(row.value)
            }
            if (getItemViewType(position) == 1) (holder.itemView as TextView).apply {
                setText(row.title)
                setOnClickListener { if (row.id == 8) { more = true; refresh() } else emit(row.id) }
                setOnLongClickListener { if (row.id != 8) false else { emit(EDIT_CSS); true } }
            }
        }
    }
    private class Holder(view: View) : RecyclerView.ViewHolder(view)

    /** g8.u's header: title, URL expansion, history and QR controls, and long-press clipboard. */
    private fun header(): RelativeLayout {
        val context = requireContext()
        val body = RelativeLayout(context)
        val size = resources.getDimensionPixelSize(R.dimen.menu_footer_height)
        val iconPadding = resources.getDimensionPixelSize(R.dimen.menu_control_padding)
        val qr = ImageView(context).apply {
            id = View.generateViewId(); setSkinImageResource(R.drawable.reader_page_qr); setColorFilter(color(R.attr.viaSubtleColor))
            setPaddingRelative(iconPadding, 0, iconPadding, 0); contentDescription = getString(R.string.qr_code)
            setBackgroundResource(R.drawable.circle_ripple); setOnClickListener { emit(6) }
        }
        val history = ImageView(context).apply {
            id = View.generateViewId(); setSkinImageResource(R.drawable.clock); setColorFilter(color(R.attr.viaSubtleColor))
            setPaddingRelative(iconPadding, 0, iconPadding, 0); contentDescription = getString(R.string.action_history)
            setBackgroundResource(R.drawable.circle_ripple); setOnClickListener { emit(5) }
        }
        val title = TextView(context).apply {
            id = View.generateViewId(); text = pageTitle.ifEmpty { getString(R.string.untitled) }
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_toolbar_title_size).toFloat())
            setLineSpacing(context.dp(2f).toFloat(), 1f); setTextColor(color(R.attr.viaPrimaryTextColor)); maxLines = 2
            ellipsize = TextUtils.TruncateAt.END; textDirection = View.TEXT_DIRECTION_LOCALE; typeface = preferences.selectedTypeface()
            setPaddingRelative(units(16), units(12), units(4), units(2))
            setOnClickListener { }
            setOnLongClickListener { if (pageTitle.isNotEmpty()) copy(pageTitle, R.string.toast_copy_text_successful); true }
        }
        val address = TextView(context).apply {
            id = View.generateViewId(); textSize = 14f; setLineSpacing(context.dp(2f).toFloat(), 1f)
            setTextColor(color(R.attr.viaSecondaryTextColor)); maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            textDirection = View.TEXT_DIRECTION_LOCALE; typeface = preferences.selectedTypeface()
            setPaddingRelative(units(4), units(2), units(4), units(2)); setBackgroundResource(R.drawable.focus_outline)
            val display = UrlInputText.display(pageUrl).take(8192)
            text = SpannableString(display).apply {
                if (flags and 1 != 0 && display.startsWith("https://", true)) setSpan(ForegroundColorSpan(0xff238e45.toInt()), 0, 5, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                val begin = display.indexOf("://") + 3
                val end = display.indexOf('/', begin)
                if (begin < end) setSpan(ForegroundColorSpan(title.textColors.defaultColor), begin, end, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            }
            setOnClickListener {
                if (maxLines <= 2) {
                    maxLines = minOf(12, maxOf(3, ceil(text.length / 40.0).toInt()))
                    textSize = if (maxLines >= 8) 12f else if (maxLines >= 5) 13f else 14f
                } else { maxLines = 2; textSize = 14f }
            }
            setOnLongClickListener { copy(pageUrl, R.string.toast_copy_url_successful); true }
        }
        body.addView(qr, RelativeLayout.LayoutParams(size, size).apply { addRule(RelativeLayout.ALIGN_PARENT_END); addRule(RelativeLayout.CENTER_VERTICAL) })
        body.addView(history, RelativeLayout.LayoutParams(size, size).apply { addRule(RelativeLayout.CENTER_VERTICAL); addRule(RelativeLayout.START_OF, qr.id); alignWithParent = true })
        body.addView(address, RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.START_OF, history.id); addRule(RelativeLayout.BELOW, title.id); marginStart = units(12); bottomMargin = units(8) })
        body.addView(title, RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.START_OF, history.id) })
        if (pageUrl.isEmpty() || UrlInputText.isInternalDocument(pageUrl, context.filesDir.path)) { address.visibility = View.GONE; history.visibility = View.GONE; qr.visibility = View.GONE }
        return body
    }
    private fun copy(value: String, notice: Int) {
        (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("", value))
        ViaToast.makeText(requireContext(), notice, ViaToast.LENGTH_SHORT).show()
    }
    private fun color(attribute: Int) = requireContext().obtainStyledAttributes(intArrayOf(attribute)).let { try { it.getColor(0, 0) } finally { it.recycle() } }
    private fun units(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
    companion object {
        const val RESULT = "reader_page_tools"
        const val STYLE_CHANGED = 9
        const val EDIT_CSS = 10
        private const val COLORS = 100
        private const val TEXT_SIZE = 101
        /** i6.i0.s/m/n accept scheme case without normalizing the displayed URL. */
        fun isNetworkPage(url: String?) = url != null &&
            (url.length > 6 && url.startsWith("http://", true) || url.length > 7 && url.startsWith("https://", true))
        /** i6.i0.b/f/o, including its original three-label country-domain rule. */
        fun historyQuery(url: String): String {
            val separator = url.indexOf("://")
            if (separator < 0) return ""
            val host = url.substring(separator + 3).substringBefore('/').substringBefore(':')
            if (host.isEmpty() || host.startsWith('[') || ':' in host || (host.count { it == '.' } == 3 && host.all { it == '.' || it in '0'..'9' })) return host
            val last = host.lastIndexOf('.')
            val before = host.lastIndexOf('.', last - 1)
            if (last < 0 || before < 0) return host
            val suffix = host.substring(last + 1).lowercase(java.util.Locale.ROOT)
            val second = host.substring(before + 1, last).lowercase(java.util.Locale.ROOT)
            if (suffix != "io" || second != "github") {
                if (suffix in setOf("com", "net", "org", "gov", "co", "edu")) return host.substring(before + 1)
            }
            val third = host.lastIndexOf('.', before - 1)
            return if (third >= 0) host.substring(third + 1) else host
        }
        fun newInstance(url: String?, title: String?, flags: Int, width: Int, gravity: Int, homepage: Boolean = false, readerState: Int = 0) = ReaderPageToolsFragment().apply {
            arguments = Bundle().apply { putString("url", url); putString("title", title); putInt("flag", flags); putInt("width", width); putInt("gravity", gravity); putBoolean("homepage", homepage); putInt("reader_state", readerState) }
        }
    }
}
