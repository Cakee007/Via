package dev.ujhhgtg.via.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.search.SearchProvider
import dev.ujhhgtg.via.skins.setSkinImageResource

/**
 * mark.via.common.widget.m: the 40dp engine quick-switch strip shown above the
 * bottom toolbar while the current page is one of the providers' search-result
 * pages. A horizontal list of engine label pills plus a trailing "search
 * settings" shortcut (c8.s6$b opens hb.b6); picking an engine re-runs the same
 * query on it through the c8.s6$a callback.
 */
class SearchStrip(context: Context) : FrameLayout(context) {
    interface Callback {
        fun onEngineSelected(provider: SearchProvider)
        fun onEditSelected()
    }

    private val list = RecyclerView(context)
    private val rows = mutableListOf<SearchProvider>()
    private var matched = -1
    private var controlTint = 0
    private var textColor = 0
    private var callback: Callback? = null

    init {
        list.apply {
            // m.a: clipToPadding false, horizontal list, and the x(6dp, 4dp)
            // spacing decoration (top/bottom 6dp, left/right 4dp).
            clipToPadding = false
            itemAnimator = DefaultItemAnimator()
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            setPadding(dp(12f), 0, dp(12f), 0)
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(outRect: android.graphics.Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                    outRect.set(dp(4f), dp(6f), dp(4f), dp(6f))
                }
            })
            adapter = Adapter()
        }
        addView(list, LayoutParams(-1, -1))
    }

    fun setCallback(value: Callback?) { callback = value }

    /** m.l: controlTint filters the settings shortcut, textColor the labels. */
    fun setColors(controlColor: Int, labelColor: Int) {
        if (controlTint == controlColor && textColor == labelColor) return
        controlTint = controlColor; textColor = labelColor
        list.adapter?.notifyItemRangeChanged(0, rows.size + 1)
    }

    /** m.m: replace the provider list, diffed like the source's f.b dispatch. */
    fun submit(providers: List<SearchProvider>) {
        val old = rows.toList()
        rows.clear(); rows.addAll(providers)
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = rows.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) = old[oldPos].id == rows[newPos].id
            override fun areContentsTheSame(oldPos: Int, newPos: Int) = old[oldPos] == rows[newPos]
        }).dispatchUpdatesTo(list.adapter!!)
    }

    /**
     * m.n (through c8.s6.Cb): re-match the url; the selection change notifies
     * the old and new positions so the DefaultItemAnimator change cross-fade
     * carries the pill color transition. True while the page is one of the
     * engines' result pages.
     */
    fun rematch(url: String?): Boolean {
        val next = url?.let(::matchIndex) ?: -1
        if (next == matched) return matched >= 0
        val previous = matched
        matched = next
        if (previous >= 0) list.adapter?.notifyItemChanged(previous)
        if (next >= 0) { list.adapter?.notifyItemChanged(next); list.scrollToPosition(next) }
        return matched >= 0
    }

    /** i6.g0.g: the query this page carries for the engine it came from. */
    fun matchedQuery(url: String?): String? {
        val index = url?.let(::matchIndex) ?: return null
        if (index < 0) return null
        return extractQuery(url, rows[index].template)
    }

    private fun matchIndex(url: String): Int = rows.indexOfFirst { extractQuery(url, it.template)?.isNotEmpty() == true }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val selectedStroke = themedColor(R.attr.viaAccentColor)

        override fun getItemCount() = rows.size + 1
        override fun getItemViewType(position: Int) = if (position < rows.size) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 0) {
                val label = TextView(parent.context).apply {
                    // m$a$d: wrap width, match height, 12dp horizontal padding,
                    // 12sp default typeface, 96dp max width, centered single
                    // line, and the x8.h.f pill background (dimen p radius).
                    layoutParams = LayoutParams(-2, -1)
                    setPadding(dp(12f), 0, dp(12f), 0)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    maxWidth = dp(96f)
                    ellipsize = TextUtils.TruncateAt.END
                    setTypeface(Typeface.DEFAULT)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    background = pillBackground()
                }
                object : RecyclerView.ViewHolder(label) {}
            } else {
                val edit = ImageView(parent.context).apply {
                    // m$b$d: 48dp wide, 4dp padding all around, the rounded
                    // pressed ripple, and the m$b/g6.y.S control tint.
                    layoutParams = LayoutParams(dp(48f), -1)
                    setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
                    background = ContextCompat.getDrawable(context, R.drawable.rounded_rect_ripple)
                    setSkinImageResource(R.drawable.search_settings)
                    contentDescription = context.getString(R.string.search_settings)
                }
                object : RecyclerView.ViewHolder(edit) {}
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder.itemView is TextView) {
                val label = holder.itemView as TextView
                val provider = rows[position]
                label.text = provider.name
                label.isSelected = position == matched
                // m$a.q -> x8.h.g: selected/checked show the accent, the rest
                // the passed ink.
                label.setTextColor(android.content.res.ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_enabled, android.R.attr.state_selected),
                        intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked),
                        intArrayOf(),
                    ),
                    intArrayOf(selectedStroke, selectedStroke, textColor)))
                label.setOnClickListener {
                    if (position != matched) callback?.onEngineSelected(provider)
                }
            } else {
                val edit = holder.itemView as ImageView
                edit.setColorFilter(controlTint)
                edit.setOnClickListener { callback?.onEditSelected() }
            }
        }
    }

    /** x8.h.f: pill outline, dimen ae radius, accent stroke while selected. */
    private fun pillBackground(): StateListDrawable {
        val selectedStroke = themedColor(R.attr.viaAccentColor)
        fun pill(strokeColor: Int, strokeWidth: Int, fill: Int = 0): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                // x8.h.f uses dimen ae (18dp), the same radius as the ripple.
                cornerRadius = resources.getDimension(R.dimen.menu_corner_radius)
                if (fill != 0) setColor(fill)
                setStroke(strokeWidth, strokeColor)
            }
        val idle = pill(0x30808080, dp(1f))
        val selected = pill(selectedStroke, dp(1f))
        val pressed = pill(0, 0, 0x30808080)
        val focused = pill(0x60808080, dp(3f))
        return StateListDrawable().apply {
            // x8.h.f state order, verbatim: idle excludes pressed so the
            // pressed fill below is actually reachable.
            addState(intArrayOf(-android.R.attr.state_pressed, -android.R.attr.state_checked, -android.R.attr.state_selected, -android.R.attr.state_focused), idle)
            addState(intArrayOf(-android.R.attr.state_pressed, android.R.attr.state_checked, -android.R.attr.state_focused), selected)
            addState(intArrayOf(-android.R.attr.state_pressed, android.R.attr.state_selected, -android.R.attr.state_focused), selected)
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(android.R.attr.state_focused), focused)
        }
    }

    private fun themedColor(attribute: Int): Int = context.obtainStyledAttributes(intArrayOf(attribute)).let {
        try { it.getColor(0, 0) } finally { it.recycle() }
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density + .5f).toInt()

    companion object {
        // i6.g0.g: the host check falls back to these permissive domains, so
        // m.baidu.com matches www.baidu.com templates and cn.bing.com matches
        // www.bing.com ones.
        private val HOST_FAMILIES = listOf(".baidu.com", ".bing.com", "/www.google.com", ".sogou.com")

        /**
         * i6.g0.g: the query this page carries for the engine it came from.
         * Host check by origin prefix or permissive family; the value comes
         * from the template's query parameter name (or the literal prefix),
         * decoded.
         */
        fun extractQuery(url: String, template: String): String? {
            if (url.isEmpty() || template.isEmpty()) return null
            val templateHostSlash = template.indexOf('/', 8)
            val urlHostSlash = url.indexOf('/', 8)
            var hostEnd: Int
            if (templateHostSlash > 0) {
                if (urlHostSlash < 0) return null
                if (url.startsWith(template.substring(0, templateHostSlash - 1))) {
                    hostEnd = urlHostSlash
                } else {
                    // v7.m.B = lastIndexOf: the family may sit anywhere left
                    // of the host slash (m.baidu.com vs www.bing.com etc.).
                    val inFamily = HOST_FAMILIES.any { template.lastIndexOf(it, templateHostSlash) > 0 && url.lastIndexOf(it, urlHostSlash) > 0 }
                    if (!inFamily) return null
                    hostEnd = urlHostSlash
                }
                // baidu: the home page must not match the /s? search template.
                if (url.lastIndexOf(".baidu.com", hostEnd) > 0 && template.indexOf("/s?", templateHostSlash) > 0 && url.indexOf("/s?", hostEnd) < 0) return null
            }
            var placeholder = template.indexOf("%s")
            if (placeholder < 0) placeholder = template.indexOf("%S")
            if (placeholder < 0) placeholder = template.indexOf("%@")
            // i6.g0.g: a placeholder-less template counts as one ending right
            // after its literal head, which selects the query branch when a
            // '?' precedes that end.
            if (placeholder < 0) placeholder = template.length
            val queryStart = template.indexOf('?')
            if (placeholder == 0 && template.length == 2) return url
            return if (queryStart !in 0..placeholder) {
                // Prefix and path placeholders: match the literal head, take
                // the rest up to the template tail or the next ?/#.
                val head = template.substring(0, placeholder)
                if (!url.startsWith(head)) return null
                if (placeholder != template.length && placeholder != template.length - 2) {
                    val tail = template.substring(placeholder + 2)
                    val valueEnd = url.indexOf(tail, head.length)
                    if (valueEnd < 0) return null
                    decode(url.substring(head.length, valueEnd))
                } else {
                    var valueEnd = url.indexOf('?', head.length)
                    if (valueEnd < 0) valueEnd = url.indexOf('#', head.length)
                    if (valueEnd < 0) valueEnd = url.length
                    decode(url.substring(head.length, valueEnd))
                }
            } else {
                // Query placeholder: find the value by the parameter name, so
                // extra parameters the site appended do not break the match.
                var ampersand = template.lastIndexOf('&', placeholder)
                ampersand = if (ampersand > queryStart) ampersand else queryStart
                val needle = template.substring(ampersand + 1, placeholder)
                if (needle.isEmpty() || needle.length + 1 >= url.length) return null
                var value = findParameterValue(url, needle)
                if (value == null && template.indexOf(".baidu.com") > 0) value = findParameterValue(url, "wd=")
                if (value == null) return null
                if (placeholder != template.length && placeholder != template.length - 2) {
                    val tail = template.substring(placeholder + 2)
                    val valueEnd = value.indexOf(tail)
                    if (valueEnd < 0) return null
                    decode(value.substring(0, valueEnd))
                } else {
                    decode(value)
                }
            }
        }

        /** i6.i0.h: the value of "?needle" / "&needle", up to the next & or #. */
        private fun findParameterValue(url: String, needle: String): String? {
            var from = 0
            while (from + needle.length <= url.length) {
                val at = url.indexOf(needle, from)
                if (at < 0) return null
                if (at > 0 && (url[at - 1] == '&' || url[at - 1] == '?')) {
                    var end = url.indexOf('&', at + needle.length)
                    val hash = url.indexOf('#', at + needle.length)
                    if (hash in 0..end || end < 0) end = if (hash >= 0) hash else url.length
                    return url.substring(at + needle.length, end)
                }
                from = at + needle.length
            }
            return null
        }

        /** i6.i.c: UTF-8 url decoding, kept only when non-blank. */
        private fun decode(value: String): String? = try {
            java.net.URLDecoder.decode(value, "UTF-8").trim().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            value.trim().takeIf { it.isNotEmpty() }
        }

        /** The search URL for a query on a provider, mirroring the template contract. */
        fun buildSearchUrl(provider: SearchProvider, query: String, encode: (String) -> String = { Uri.encode(it) }): String {
            val template = provider.template
            val placeholder = intArrayOf(template.indexOf("%@"), template.indexOf("%s"), template.indexOf("%S")).filter { it >= 0 }.minOrNull()
                ?: return template + encode(query.trim())
            return template.substring(0, placeholder) + encode(query.trim()) + template.substring(placeholder + 2)
        }
    }
}
