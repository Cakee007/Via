package dev.ujhhgtg.via.sync

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.settings.settingsRipple
import dev.ujhhgtg.via.ui.dialog.ViaDialogFragment
import dev.ujhhgtg.via.ui.dp

/** hb.d8: agreement documents open above the sign-in dialog and retain web back navigation. */
class CloudPolicyDialogFragment : ViaDialogFragment() {
    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        return FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            val web = WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT); isFocusable = true
                isScrollbarFadingEnabled = true; isSaveEnabled = true; scrollBarSize = 10
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        request.url.scheme?.lowercase() !in setOf("http", "https", "file")
                    @Deprecated("Deprecated in Java",
                        ReplaceWith("!url.startsWith(\"http://\", true) && !url.startsWith( \"https://\", true ) && !url.startsWith(\"file://\", true)")
                    )
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                        !url.startsWith("http://", true) && !url.startsWith("https://", true) && !url.startsWith("file://", true)
                }
                settings.apply {
                    domStorageEnabled = true; allowFileAccess = true; allowContentAccess = true
                    setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
                    defaultTextEncodingName = "UTF-8"; useWideViewPort = true; minimumFontSize = 1
                    cacheMode = WebSettings.LOAD_DEFAULT; loadWithOverviewMode = false
                    javaScriptEnabled = true; javaScriptCanOpenWindowsAutomatically = false
                    mediaPlaybackRequiresUserGesture = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                loadUrl(requireArguments().getString("url").orEmpty())
            }
            webView = web
            addView(web, FrameLayout.LayoutParams(-1, -1).apply { topMargin = context.dp(14f); bottomMargin = context.dp(48f) })
            addView(TextView(context).apply {
                setText(android.R.string.ok); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
                setTextColor(settingsColor(context, R.attr.viaAccentColor, 0xff6f8de1.toInt()))
                background = settingsRipple(context); setOnClickListener { dismiss() }
            }, FrameLayout.LayoutParams(-1, context.dp(48f), Gravity.BOTTOM))
        }
    }

    override fun onStart() {
        super.onStart()
        webView?.onResume()
        dialog?.apply {
            setCanceledOnTouchOutside(false)
            setOnKeyListener { _, keyCode, event ->
                if (keyCode != KeyEvent.KEYCODE_BACK || event.action != KeyEvent.ACTION_UP) false
                else { if (webView?.canGoBack() == true) webView?.goBack() else dismiss(); true }
            }
            window?.attributes = window?.attributes?.apply {
                width = minOf((resources.displayMetrics.widthPixels * .95).toInt(), requireContext().dp(400f))
                height = -1; gravity = Gravity.CENTER
            }
        }
    }
    override fun onStop() { webView?.onPause(); super.onStop() }
    override fun onDestroyView() { webView?.destroy(); webView = null; super.onDestroyView() }

    companion object {
        fun newInstance(url: String) = CloudPolicyDialogFragment().apply { arguments = Bundle().apply { putString("url", url) } }
    }
}
