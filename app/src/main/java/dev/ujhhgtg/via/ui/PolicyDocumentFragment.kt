package dev.ujhhgtg.via.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.common.WindowInsetsHelper
import dev.ujhhgtg.via.settings.SettingsToolbar

/** y7.g: agreement document stays inside Shell, with a browser history and an OK footer. */
class PolicyDocumentFragment : Fragment() {
    private var web: WebView? = null
    private lateinit var toolbar: SettingsToolbar
    private lateinit var progress: PageProgress
    private var back: OnBackPressedCallback? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val paper = context.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
        try { body.background = paper.getDrawable(0) } finally { paper.recycle() }
        toolbar = SettingsToolbar(context, ::goBack)
        toolbar.titleView.text = arguments?.getString("title") ?: getString(R.string.untitled)
        body.addView(toolbar, LinearLayout.LayoutParams(-1, -2))
        val content = FrameLayout(context)
        web = WebView(context).also { content.addView(it, FrameLayout.LayoutParams(-1, -1).apply { bottomMargin = context.dp(48f) }) }
        val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            setBackgroundColor(Color.TRANSPARENT); max = 100; progress = 0; isIndeterminate = false
            val fill = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(-10894619, -5863456)).apply { cornerRadius = context.dp(2f).toFloat() }
            progressDrawable = LayerDrawable(arrayOf(GradientDrawable().apply { setColor(Color.TRANSPARENT) }, ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL))).apply {
                setId(0, android.R.id.background); setId(1, android.R.id.progress)
            }
        }
        content.addView(bar, FrameLayout.LayoutParams(-1, context.dp(2f)))
        progress = PageProgress(bar)
        val accent = context.obtainStyledAttributes(intArrayOf(R.attr.viaAccentColor))
        val textColor = try { accent.getColor(0, Color.TRANSPARENT) } finally { accent.recycle() }
        content.addView(TextView(context).apply {
            setText(android.R.string.ok); setTextColor(textColor); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD; textSize = 14f
            setBackgroundResource(R.drawable.flat_ripple)
            setOnClickListener { parentFragmentManager.popBackStack() }
        }, FrameLayout.LayoutParams(-1, context.dp(48f), Gravity.BOTTOM))
        body.addView(content, LinearLayout.LayoutParams(-1, -1))
        WindowInsetsHelper.apply(body)
        return SwipeBackLayout(context).apply { attach(this@PolicyDocumentFragment, body); setEdgeSize(-2); setScrollThresholdSize(context.dp(200f).toFloat()) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val target = arguments?.getString("url")
        if (target.isNullOrEmpty()) { parentFragmentManager.popBackStack(); return }
        val browser = requireNotNull(web)
        browser.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, value: Int) = progress.update(value)
            override fun onReceivedTitle(view: WebView, title: String) { toolbar.titleView.text = title }
        }
        browser.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, icon: Bitmap?) = progress.update(0)
            override fun onPageFinished(view: WebView, url: String) { progress.update(100); (this@PolicyDocumentFragment.view as? SwipeBackLayout)?.setGestureEnabled(!view.canGoBack()) }
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = !allowed(url)
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = !allowed(request.url.toString())
        }
        browser.isFocusable = true; browser.setBackgroundColor(Color.TRANSPARENT); browser.isScrollbarFadingEnabled = true
        browser.isSaveEnabled = true; browser.scrollBarSize = 10; browser.isHorizontalScrollBarEnabled = false
        browser.settings.apply {
            runCatching { javaClass.getMethod("setEnableSmoothTransition", Boolean::class.javaPrimitiveType).invoke(this, true) }
            mediaPlaybackRequiresUserGesture = true; domStorageEnabled = true; allowFileAccess = true; databaseEnabled = true; saveFormData = true
            setSupportZoom(true); defaultTextEncodingName = "UTF-8"; useWideViewPort = true; builtInZoomControls = true; displayZoomControls = false
            allowContentAccess = true; minimumFontSize = 1; allowFileAccessFromFileURLs = false; allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT; loadWithOverviewMode = false; javaScriptEnabled = true; javaScriptCanOpenWindowsAutomatically = false
        }
        CookieManager.getInstance().setAcceptCookie(true)
        browser.loadUrl(target)
        back = object : OnBackPressedCallback(true) { override fun handleOnBackPressed() = goBack() }.also {
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it)
        }
    }
    private fun allowed(url: String) = android.webkit.URLUtil.isNetworkUrl(url) || url.startsWith("file://", true)
    private fun goBack() { web?.let { if (it.canGoBack()) { it.goBack(); return } }; parentFragmentManager.popBackStack() }
    override fun onPause() { web?.onPause(); back?.isEnabled = false; super.onPause() }
    override fun onResume() { super.onResume(); web?.onResume(); back?.isEnabled = !isHidden }
    override fun onHiddenChanged(hidden: Boolean) { super.onHiddenChanged(hidden); back?.isEnabled = !hidden }
    override fun onDestroyView() { web?.destroy(); web = null; back = null; super.onDestroyView() }
    companion object {
        fun newInstance(url: String, title: String) = PolicyDocumentFragment().apply { arguments = Bundle().apply { putString("url", url); putString("title", title) } }
    }
}
