package dev.ujhhgtg.via.platform


import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.skins.setSkinImageResource

/** c8.s6.D8/Va/Ha: supplied close/action icons, custom menu entries and URL-bearing callbacks. */
@SuppressLint("ViewConstructor")
class CustomTabToolbar(private val activity: Activity, private val launch: Intent, private val host: Host) : LinearLayout(activity) {
    interface Host {
        fun currentUrl(): String?
        fun onAction(action: String)
        fun hasScripts(): Boolean = false
    }

    private val title = TextView(activity)
    private val address = TextView(activity)
    private val suppliedAction = launch.getBundleExtra("android.support.customtabs.extra.ACTION_BUTTON_BUNDLE")
    val colorScheme: Int = launch.getIntExtra("androidx.browser.customtabs.extra.COLOR_SCHEME", 0)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val dark = if (colorScheme == 0) dev.ujhhgtg.via.data.BrowserPreferences(context).isNightMode else colorScheme == 2
        val ink = if (dark) Color.WHITE else Color.rgb(32, 32, 32)
        setBackgroundColor(Color.TRANSPARENT)
        val close = bitmap(launch.extras, "android.support.customtabs.extra.CLOSE_BUTTON_ICON")
        addView(button(close, R.drawable.close, activity.getString(R.string.action_close), ink) { activity.finish() }, LayoutParams(dp(48), dp(48)))
        val labels = LinearLayout(activity).apply { orientation = VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        labels.addView(title.apply { textSize = 16f; setSingleLine(); ellipsize = TextUtils.TruncateAt.END; setTextColor(ink) })
        labels.addView(address.apply { textSize = 12f; setSingleLine(); ellipsize = TextUtils.TruncateAt.END; setTextColor(Color.GRAY) })
        addView(labels, LayoutParams(0, dp(48), 1f))
        if (suppliedAction == null) {
            addView(button(null, R.drawable.share_nodes, activity.getString(R.string.action_share), ink) { host.onAction(SHARE) }, LayoutParams(dp(48), dp(48)))
        } else {
            val icon = bitmap(suppliedAction, "android.support.customtabs.customaction.ICON")
            val description = suppliedAction.getString("android.support.customtabs.customaction.DESCRIPTION").orEmpty()
            addView(button(icon, 0, description, ink) { send(pending(suppliedAction)) }, LayoutParams(dp(48), dp(48)))
        }
        addView(button(null, R.drawable.dots_vertical, activity.getString(R.string.desc_menu), ink) { showMenu() }, LayoutParams(dp(48), dp(48)))
    }

    fun update(pageTitle: String?, url: String?) {
        title.text = pageTitle?.takeIf { it.isNotEmpty() } ?: url.orEmpty()
        address.text = url?.let { it.toUri().host ?: it }.orEmpty()
    }

    /** c8.s6.Q7 / z.setContentColor also tints the custom-tab controls. */
    fun setContentColor(color: Int) {
        title.setTextColor(color)
        address.setTextColor(color)
        for (index in 0 until childCount) (getChildAt(index) as? ImageView)?.setColorFilter(color)
    }

    private fun showMenu() {
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        @Suppress("DEPRECATION")
        val entries = launch.getParcelableArrayListExtra<Bundle>("android.support.customtabs.extra.MENU_ITEMS").orEmpty()
        for (entry in entries) {
            val label = entry.getString("android.support.customtabs.customaction.MENU_ITEM_TITLE")
            val callback = pending(entry)
            if (label != null && callback != null) actions += label to { send(callback) }
        }
        fun action(label: String, value: String) { actions += label to { host.onAction(value) } }
        if (suppliedAction != null) action(activity.getString(R.string.action_share), SHARE)
        action(activity.getString(R.string.action_add_bookmark), BOOKMARK)
        action(activity.getString(R.string.action_find), FIND)
        action(activity.getString(R.string.action_translate), TRANSLATE)
        if (host.hasScripts()) action(activity.getString(R.string.settings_script), SCRIPTS)
        action(activity.getString(R.string.action_copy), COPY)
        action(activity.getString(R.string.open_in_via), OPEN_IN_BROWSER)
        AlertDialog.Builder(activity).setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }.show()
    }

    private fun send(callback: PendingIntent?) {
        if (callback == null) return
        val url = host.currentUrl()
        val fill = if (url.isNullOrEmpty()) null else Intent().setData(url.toUri())
        runCatching {
            if (Build.VERSION.SDK_INT < 34) callback.send(activity, 0, fill)
            else {
                val options = ActivityOptions.makeBasic()
                if (Build.VERSION.SDK_INT <= 35) options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                else options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
                callback.send(activity, 0, fill, null, null, null, options.toBundle())
            }
        }
    }

    private fun button(icon: Bitmap?, fallback: Int, description: String, ink: Int, clicked: () -> Unit) = ImageView(activity).apply {
        if (icon != null) setImageBitmap(icon) else if (fallback != 0) { setSkinImageResource(fallback); setColorFilter(ink) }
        setPadding(dp(12), dp(12), dp(12), dp(12)); scaleType = ImageView.ScaleType.CENTER_INSIDE
        contentDescription = description; isFocusable = true; setOnClickListener { clicked() }
    }
    @Suppress("DEPRECATION")
    private fun pending(bundle: Bundle?): PendingIntent? = bundle?.getParcelable("android.support.customtabs.customaction.PENDING_INTENT")
    @Suppress("DEPRECATION")
    private fun bitmap(bundle: Bundle?, key: String): Bitmap? = bundle?.getParcelable(key)

    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()

    companion object {
        const val SHARE = "share"
        const val BOOKMARK = "bookmark"
        const val FIND = "find"
        const val TRANSLATE = "translate"
        const val SCRIPTS = "scripts"
        const val COPY = "copy"
        const val OPEN_IN_BROWSER = "open_in_browser"
    }
}
