package dev.ujhhgtg.via.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.settingsColor
import kotlin.system.exitProcess

/** ra.h: the full-screen notice shown instead of the browser when no WebView provider is loaded. */
class WebViewMissingFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        fun dp(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
        val primary = settingsColor(context, R.attr.viaPrimaryTextColor, Color.BLACK)
        val body = resources.getDimensionPixelSize(R.dimen.webview_missing_body_size).toFloat()
        val crashedApp = (arguments?.getInt(KEY_CODE) ?: 0) != CODE_MISSING_WEBVIEW
        val message = TextView(context).apply {
            id = View.generateViewId()
            maxWidth = dp(300); minWidth = dp(120)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, body); setTextColor(primary); gravity = Gravity.CENTER
            setText(if (crashedApp) R.string.crash_hint else R.string.missing_webview)
        }
        val title = TextView(context).apply {
            id = View.generateViewId()
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.webview_missing_title_size).toFloat())
            setTextColor(primary); setText(R.string.oops); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        }
        val radius = resources.getDimension(R.dimen.menu_corner_radius)
        fun surface(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
        val action = TextView(context).apply {
            id = View.generateViewId()
            // g6.g().c(ae).h(accent).j(dark accent).a(): a ripple over the accent surface.
            background = RippleDrawable(ColorStateList.valueOf(settingsColor(context, R.attr.viaDarkAccentColor, Color.GRAY)),
                surface(settingsColor(context, R.attr.viaAccentColor, Color.BLUE)), surface(Color.BLACK))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, body)
            setTextColor(settingsColor(context, R.attr.viaAccentTextColor, Color.WHITE)); gravity = Gravity.CENTER
            setText(if (crashedApp) R.string.crash_restart else R.string.install)
            setOnClickListener { if (crashedApp) restart() else openWebViewStore() }
        }
        val close = TextView(context).apply {
            id = View.generateViewId()
            setBackgroundResource(R.drawable.rounded_rect_ripple)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, body); setTextColor(primary); setText(R.string.crash_close); gravity = Gravity.CENTER
            setOnClickListener { requireActivity().finish(); System.exit(0) }
        }
        val root = RelativeLayout(context).apply { layoutParams = ViewGroup.LayoutParams(-1, -1) }
        fun alignedTo(anchor: View) = RelativeLayout.LayoutParams(-2, -2).apply {
            addRule(RelativeLayout.ALIGN_START, anchor.id); addRule(RelativeLayout.ALIGN_END, anchor.id)
        }
        root.addView(title, alignedTo(message).apply { addRule(RelativeLayout.ABOVE, message.id); marginStart = dp(16); marginEnd = dp(16) })
        root.addView(message, RelativeLayout.LayoutParams(-2, -2).apply {
            addRule(RelativeLayout.CENTER_VERTICAL); addRule(RelativeLayout.CENTER_HORIZONTAL)
            marginStart = dp(32); marginEnd = dp(32); topMargin = dp(12); bottomMargin = dp(24)
        })
        root.addView(action, alignedTo(message).apply { addRule(RelativeLayout.BELOW, message.id) })
        root.addView(close, alignedTo(message).apply { addRule(RelativeLayout.BELOW, action.id); topMargin = dp(8) })
        // x8.g.f: every text view takes the selected UI font, keeping its style.
        val font = BrowserPreferences(context).selectedTypeface()
        listOf(title, message, action, close).forEach { it.typeface = Typeface.create(font, it.typeface?.style ?: Typeface.NORMAL) }
        return root
    }

    /** z8.f.g: the store listing, falling back to a web store through the app chooser (z8.b0.O). */
    private fun openWebViewStore() {
        val activity = requireActivity()
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$WEBVIEW_PACKAGE".toUri()).apply { component = null; selector = null }
        try { activity.startActivity(market) } catch (_: ActivityNotFoundException) {
            val china = resources.configuration.locales[0]?.country == "CN"
            val web = (if (china) "http://coolapk.com/apk/" else "https://play.google.com/store/apps/details?id=") + WEBVIEW_PACKAGE
            activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, web.toUri()), getString(R.string.title_app_chooser)))
        }
    }

    /** z8.f.k */
    private fun restart() {
        val context = requireContext()
        context.startActivity(Intent.makeRestartActivityTask(ComponentName(context, Shell::class.java)).setPackage(context.packageName))
        exitProcess(0)
    }

    companion object {
        const val CODE_MISSING_WEBVIEW = 1
        private const val KEY_CODE = "code"
        private const val WEBVIEW_PACKAGE = "com.google.android.webview"

        fun newInstance(code: Int) = WebViewMissingFragment().apply { arguments = bundleOf(KEY_CODE to code) }

        /** w9.r.f: the loaded WebView package is missing or has no name. */
        fun webViewMissing(): Boolean = !dev.ujhhgtg.via.engine.Engines.backend.isAvailable()
    }
}
