package dev.ujhhgtg.via.platform

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.net.toUri
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.skins.setSkinImageResource
import dev.ujhhgtg.via.ui.dialog.ViaDialog

/**
 * com.tuyafeng.support.widget.z as built by c8.s6.D8: navigation (close), title,
 * spacer, the supplied or share action, the overflow button and a two-pixel divider.
 */
@SuppressLint("ViewConstructor")
class CustomTabToolbar(
    private val activity: Activity,
    private val launch: Intent,
    private val host: Host,
) : LinearLayout(activity) {
    interface Host {
        /** c8.s6.G8: the selected tab's URL, unfiltered. */
        fun currentUrl(): String?
        fun onAction(action: String)
        /** c8.s6.Va's 997 condition. */
        fun hasScripts(): Boolean = false
    }

    private val typeface = BrowserPreferences(activity).selectedTypeface()
    private val spacer = Space(activity)
    private var title: TextView? = null
    private var menuButton: View? = null
    private var actionIsShare = false
    private val divider = Paint(Paint.ANTI_ALIAS_FLAG)
    private var dividerColor = DIVIDER_COLOR

    init {
        // z.h
        setWillNotDraw(false)
        orientation = HORIZONTAL
        addView(spacer, LayoutParams(0, dp(54f)).apply { weight = 1f })
        // a4 -> s6.N5
        setTitle(activity.getString(R.string.untitled))

        // s6.D8: a supplied close icon, otherwise the skinned ic_close; W4 finishes the activity.
        val close = bitmap(launch.extras, CLOSE_BUTTON_ICON)
        addView(item(close, R.drawable.close, activity.getString(R.string.navigation_up)) { activity.finish() }, 0)

        val supplied = launch.getBundleExtra(ACTION_BUTTON_BUNDLE)
        if (supplied != null) {
            val icon = bitmap(supplied, CUSTOM_ACTION_ICON)
            val description = supplied.getString(CUSTOM_ACTION_DESCRIPTION)
            val callback = pending(supplied)
            addView(item(icon, 0, description) { send(callback) })
        } else {
            actionIsShare = true
            addView(item(null, R.drawable.share_nodes, activity.getString(R.string.action_share)) { host.onAction(SHARE) })
        }
        menuButton = item(null, R.drawable.dots_vertical, activity.getString(R.string.more)) { showMenu(it) }
        addView(menuButton)
    }

    /** s6.F: z.setTitle(i0.f(url)). */
    fun update(@Suppress("UNUSED_PARAMETER") pageTitle: String?, url: String?) {
        setTitle(hostOf(url))
    }

    /** z.setTitle: the title view is created lazily in front of the spacer. */
    fun setTitle(text: CharSequence?) {
        val view = title ?: TextView(activity).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.custom_tab_title_size).toFloat())
            setTextColor(themedColor(R.attr.viaPrimaryTextColor, Color.BLACK))
            setSingleLine()
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(0, -2).apply { gravity = Gravity.CENTER_VERTICAL; weight = 24f }
            textDirection = TEXT_DIRECTION_LOCALE
            setTypeface(Typeface.create(this@CustomTabToolbar.typeface, Typeface.BOLD))
            title = this
            addView(this, indexOfChild(spacer))
        }
        if (text == null) { view.visibility = GONE; return }
        view.text = text
        view.visibility = VISIBLE
    }

    /** z.setContentColor (c8.s6.Q7). */
    fun setContentColor(color: Int) {
        for (i in 0 until childCount) when (val child = getChildAt(i)) {
            is TextView -> child.setTextColor(color)
            is ImageView -> child.setColorFilter(color)
        }
    }

    /** z.setDividerColor */
    fun setDividerColor(color: Int) {
        if (color == dividerColor) return
        dividerColor = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        divider.color = dividerColor
        divider.style = Paint.Style.FILL
        canvas.drawRect(0f, measuredHeight - 2f, measuredWidth.toFloat(), measuredHeight.toFloat(), divider)
    }

    /** s6.Va and its v -> s6.l5 dispatcher. */
    private fun showMenu(anchor: View) {
        val entries = mutableListOf<Pair<String, () -> Unit>>()
        @Suppress("DEPRECATION")
        launch.getParcelableArrayListExtra<Bundle>(MENU_ITEMS)?.forEach { entry ->
            val label = entry.getString(CUSTOM_MENU_TITLE)
            val callback = pending(entry)
            if (label != null && callback != null) entries += label to { send(callback) }
        }
        if (!actionIsShare) entries += activity.getString(R.string.action_share) to { host.onAction(SHARE) }
        entries += activity.getString(R.string.action_add_bookmark) to { host.onAction(BOOKMARK) }
        entries += activity.getString(R.string.action_find) to { host.onAction(FIND) }
        entries += activity.getString(R.string.action_translate) to { host.onAction(TRANSLATE) }
        if (host.hasScripts()) entries += activity.getString(R.string.view_scripts) to { host.onAction(SCRIPTS) }
        entries += activity.getString(R.string.action_copy) to { host.onAction(COPY) }
        entries += activity.getString(R.string.open_in_via) to { host.onAction(OPEN_IN_BROWSER) }
        ViaDialog(activity).items(entries.map { it.first }.toTypedArray(), onClick = { index -> entries.getOrNull(index)?.second?.invoke() })
            .showAnchored(anchor, anchor.width, dp(4f))
    }

    /** s6.Ha: the current URL is supplied as the fill-in Intent data. */
    private fun send(callback: PendingIntent?) {
        if (callback == null) return
        runCatching {
            val url = host.currentUrl()
            val fill = if (url != null && url.isNotEmpty()) Intent().setData(url.toUri()) else null
            if (Build.VERSION.SDK_INT < 34) callback.send(activity, 0, fill)
            else {
                val options = ActivityOptions.makeBasic()
                if (Build.VERSION.SDK_INT <= 35) options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                else options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
                callback.send(activity, 0, fill, null, null, null, options.toBundle())
            }
        }
    }

    /** z.f: an icon item, or an all-caps text item when no drawable is available. */
    private fun item(icon: Bitmap?, fallback: Int, description: String?, clicked: (View) -> Unit): View {
        val hasIcon = icon != null || fallback != 0
        val view: View = if (hasIcon) ImageView(activity).apply {
            if (icon != null) setImageBitmap(icon) else setSkinImageResource(fallback)
            setColorFilter(themedColor(R.attr.viaSubtleColor, Color.BLACK))
            contentDescription = description
        } else TextView(activity).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.custom_tab_action_text_size).toFloat())
            setTextColor(themedColor(R.attr.viaPrimaryTextColor, Color.BLACK))
            text = description
            setSingleLine()
            maxLines = 1
            setLines(1)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            isAllCaps = true
            setTypeface(this@CustomTabToolbar.typeface)
        }
        val padding = resources.getDimensionPixelSize(R.dimen.custom_tab_item_padding)
        view.setPadding(padding, 0, padding, 0)
        view.setBackgroundResource(R.drawable.rounded_rect_ripple)
        view.setOnClickListener(clicked)
        val width = if (hasIcon) resources.getDimensionPixelSize(R.dimen.custom_tab_item_width) else -2
        val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics).toInt()
        view.layoutParams = LayoutParams(width, -1).apply { setMargins(margin, margin, margin, margin) }
        return view
    }

    private fun themedColor(attribute: Int, fallback: Int): Int = activity.obtainStyledAttributes(intArrayOf(attribute)).let {
        try { it.getColor(0, fallback) } finally { it.recycle() }
    }
    private fun dp(value: Float) = (resources.displayMetrics.density * value + .5f).toInt()

    @Suppress("DEPRECATION")
    private fun pending(bundle: Bundle?): PendingIntent? = bundle?.getParcelable(PENDING_INTENT)
    @Suppress("DEPRECATION")
    private fun bitmap(bundle: Bundle?, key: String): Bitmap? = bundle?.getParcelable(key)

    companion object {
        private const val CLOSE_BUTTON_ICON = "android.support.customtabs.extra.CLOSE_BUTTON_ICON"
        private const val ACTION_BUTTON_BUNDLE = "android.support.customtabs.extra.ACTION_BUTTON_BUNDLE"
        private const val CUSTOM_ACTION_ICON = "android.support.customtabs.customaction.ICON"
        private const val CUSTOM_ACTION_DESCRIPTION = "android.support.customtabs.customaction.DESCRIPTION"
        private const val PENDING_INTENT = "android.support.customtabs.customaction.PENDING_INTENT"
        private const val MENU_ITEMS = "android.support.customtabs.extra.MENU_ITEMS"
        private const val CUSTOM_MENU_TITLE = "android.support.customtabs.customaction.MENU_ITEM_TITLE"
        /** x7.m.c */
        private const val DIVIDER_COLOR = 0x30808080
        const val SHARE = "share"
        const val BOOKMARK = "bookmark"
        const val FIND = "find"
        const val TRANSLATE = "translate"
        const val SCRIPTS = "scripts"
        const val COPY = "copy"
        const val OPEN_IN_BROWSER = "open_in_browser"

        /** i6.i0.f: the authority between "://" and the next '/', without a port. */
        fun hostOf(url: String?): String {
            if (url == null) return ""
            val scheme = url.indexOf("://")
            if (scheme == -1) return ""
            val start = scheme + 3
            val end = url.indexOf('/', start).let { if (it == -1) url.length else it }
            val authority = url.substring(start, end)
            val colon = authority.indexOf(':')
            return if (colon != -1) authority.substring(0, colon) else authority
        }

        /** r9.k.e with its built-in "wls" fallback (v.qq.com, film.qq.com). */
        fun scriptsBlocked(url: String?): Boolean {
            if (url.isNullOrEmpty()) return false
            val domain = if (url.indexOf('/') < 0) url else {
                val scheme = url.indexOf("://")
                if (scheme < 0) return false
                url.substring(scheme + 3).substringBefore('/').lowercase(java.util.Locale.ROOT).ifEmpty { return false }
            }
            return SCRIPT_BLOCKLIST.any { entry ->
                val at = domain.indexOf(entry)
                !(at < 0 || at > 0 && domain[at - 1] != '.' || entry.length + at < domain.length && domain[at + entry.length] != '.')
            }
        }
        private val SCRIPT_BLOCKLIST = listOf("v.qq.com", "film.qq.com")
    }
}
