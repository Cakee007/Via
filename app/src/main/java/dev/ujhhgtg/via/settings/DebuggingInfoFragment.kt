package dev.ujhhgtg.via.settings

import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.TextView
import dev.ujhhgtg.via.BuildConfig
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.ui.dp
import dev.ujhhgtg.via.ui.widgets.FastScrollView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** hb.n0/z8.f.d diagnostics body. */
class DebuggingInfoFragment : SettingsPageFragment() {
    override fun configureToolbar(toolbar: SettingsToolbar) = toolbar.setTitle(R.string.debugging_info)
    override fun createContent(inflater: LayoutInflater, container: ViewGroup?): View {
        val context = requireContext(); val metrics = resources.displayMetrics
        val info = buildString {
            append("Device: ${Build.BRAND} ${Build.MODEL}(${Build.PRODUCT})\n")
            append("Package Name: ${context.packageName}\n")
            append("App Version: ${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})\n")
            append("Snapshot Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
            append("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(" ")}\n")
            append("Display Size: ${metrics.heightPixels}x${metrics.widthPixels}\n")
            append("Android Version: ${Build.VERSION.RELEASE} API${Build.VERSION.SDK_INT}\n")
            append("Language: ${Locale.getDefault().toLanguageTag()}\n")
            WebView.getCurrentWebViewPackage()?.let {
                append("WebView Impl: ${it.packageName}\n")
                val version = it.longVersionCode
                append("WebView Version: ${it.versionName}($version)\n")
            }
        }.trim()
        return FastScrollView(context).apply {
            isFillViewport = true
            overScrollMode = if (Build.VERSION.SDK_INT >= 32) View.OVER_SCROLL_IF_CONTENT_SCROLLS else View.OVER_SCROLL_NEVER
            addView(TextView(context).apply {
                text = "$info\n\n"; setTextIsSelectable(true); typeface = Typeface.MONOSPACE; textDirection = View.TEXT_DIRECTION_LTR
                setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, 0xff000000.toInt()))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_summary_size).toFloat())
                setPadding(context.dp(16f), context.dp(16f), context.dp(16f), context.dp(16f))
            }, FrameLayout.LayoutParams(-1, -2))
        }
    }
}
