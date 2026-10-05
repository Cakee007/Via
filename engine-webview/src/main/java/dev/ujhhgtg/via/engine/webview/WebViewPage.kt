package dev.ujhhgtg.via.engine.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.http.SslCertificate
import android.os.Bundle
import android.print.PrintDocumentAdapter
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import dev.ujhhgtg.via.engine.webview.R
import dev.ujhhgtg.via.engine.ContextMenuHandler
import dev.ujhhgtg.via.engine.ContextTarget
import dev.ujhhgtg.via.engine.EngineConfig
import dev.ujhhgtg.via.engine.EnginePage
import dev.ujhhgtg.via.engine.PageBridge
import dev.ujhhgtg.via.engine.ScriptChannel
import dev.ujhhgtg.via.engine.PageSettings
import dev.ujhhgtg.via.engine.SelectionAction

/** [EnginePage] over a system WebView. One instance per WebView, see [of]. */
class WebViewPage private constructor(val webView: WebView) : EnginePage {
    override val view get() = webView
    override val url: String? get() = webView.url
    override val title: String? get() = webView.title
    override val progress: Int get() = webView.progress
    override val favicon: Bitmap? get() = webView.favicon
    override val certificate: SslCertificate? get() = webView.certificate
    override val userAgent: String? get() = webView.settings.userAgentString
    override val javaScriptEnabled: Boolean get() = webView.settings.javaScriptEnabled

    @SuppressLint("SetJavaScriptEnabled", "WebSettingsDeprecated")
    override fun configure(config: EngineConfig) {
        WebViewCapabilities.configure(webView, config.darkening)
        webView.settings.apply {
            setSupportMultipleWindows(config.multipleWindows)
            runCatching { safeBrowsingEnabled = config.safeBrowsing }
        }
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(config.acceptCookies)
        cookies.setAcceptThirdPartyCookies(webView, config.acceptCookies && config.thirdPartyCookies)
        WebView.setWebContentsDebuggingEnabled(config.remoteDebugging)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun apply(settings: PageSettings) {
        webView.settings.apply {
            javaScriptEnabled = settings.javaScript
            loadsImagesAutomatically = settings.images
            blockNetworkImage = !settings.images
            // Setting null restores the platform default, so a custom UA never leaks to the next site.
            userAgentString = settings.userAgent
            textZoom = settings.textZoom
        }
        WebViewCapabilities.applyUserAgentMetadata(webView)
    }

    override fun setDarkening(enabled: Boolean) = WebViewCapabilities.applyNightTheme(webView, enabled)

    override fun installBridges(via: PageBridge, scripts: ScriptChannel?) = WebViewBridges.install(webView, via, scripts)

    override fun load(url: String, headers: Map<String, String>) =
        if (headers.isEmpty()) webView.loadUrl(url) else webView.loadUrl(url, headers)
    override fun reload() = webView.reload()
    override fun stopLoading() = webView.stopLoading()

    override val canGoBack get() = webView.canGoBack()
    override val canGoForward get() = webView.canGoForward()
    override fun goBack() = webView.goBack()
    override fun goForward() = webView.goForward()

    override val scrollX get() = webView.scrollX
    override val scrollY get() = webView.scrollY
    override fun scrollTo(x: Int, y: Int) = webView.scrollTo(x, y)

    override fun saveState(out: Bundle): Boolean = webView.saveState(out) != null
    override fun restoreState(state: Bundle): String? = webView.restoreState(state)?.let { it.currentItem?.url.orEmpty() }

    override fun destroy() {
        runCatching {
            // Destroying an attached WebView leaves a dead surface on screen until the host swaps it out.
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.settings.javaScriptEnabled = false
            webView.setWebViewClient(WebViewClient())
            webView.webChromeClient = null
            webView.tag = null
            webView.clearHistory()
            // [DIVERGED FROM ORIGINAL] BRO WHAT THE FUCK
            // webView.clearCache(false)
            webView.onPause()
            webView.removeAllViews()
            @Suppress("DEPRECATION")
            webView.destroyDrawingCache()
            webView.destroy()
        }
    }

    override fun evaluate(script: String, callback: ((String) -> Unit)?) =
        webView.evaluateJavascript(script, callback?.let { block -> android.webkit.ValueCallback<String> { block(it ?: "null") } })

    override fun find(query: String, listener: ((Int, Int, Boolean) -> Unit)?) {
        if (listener == null) webView.setFindListener(null)
        else webView.setFindListener { active, total, done -> listener(active, total, done) }
        if (query.isEmpty()) webView.clearMatches() else webView.findAllAsync(query)
    }
    override fun findNext(forward: Boolean) = webView.findNext(forward)

    override fun pageUp(toEdge: Boolean) { webView.pageUp(toEdge) }
    override fun pageDown(toEdge: Boolean) { webView.pageDown(toEdge) }

    override fun setTextZoom(percent: Int) { webView.settings.textZoom = percent }

    /** s4.b.e: a 1×1 canvas clips WebView.draw; the source does not scale or translate it. */
    override fun sampleTopLeftPixel(callback: (Int) -> Unit) {
        var bitmap: Bitmap? = null
        val color = try {
            val sampled = createBitmap(1, 1)
            bitmap = sampled
            webView.draw(Canvas(sampled))
            sampled[0, 0]
        } catch (error: Exception) {
            error.printStackTrace()
            0
        } finally {
            bitmap?.recycle()
        }
        callback(color)
    }

    override val edgeGestureReady: Boolean get() = (webView as? GestureWebView)?.edgeGestureReady == true

    override fun canScrollHorizontally(direction: Int) = webView.canScrollHorizontally(direction)

    override fun setSelectionActions(actions: List<SelectionAction>, onClick: ((Int, String) -> Unit)?) {
        val web = webView as? GestureWebView ?: return
        web.actionItems = listOf(GestureWebView.ActionItem(0, webSearchTitle(), 1)) +
            actions.map { GestureWebView.ActionItem(it.id, it.title, if (it.hide) 1 else 0) }
        web.onActionItemClick = onClick?.let { block -> { item, text -> block(item.id, text) } }
    }

    /** e8.i.C: the WebView provider's own "Web search" selection entry, which Via replaces with its Search. */
    @SuppressLint("DiscouragedApi")
    private fun webSearchTitle(): String {
        val provider = WebViewBackend.providerPackage ?: return "Web search"
        val resources = webView.resources
        var id = resources.getIdentifier("websearch", "string", provider)
        if (id <= 0) id = resources.getIdentifier("websearch", "string", "android")
        return if (id > 0) runCatching { resources.getString(id) }.getOrDefault("Web search") else "Web search"
    }

    override fun finishSelection() { (webView as? GestureWebView)?.finishActionMode() }

    private var contextMenu: ContextMenuHandler? = null
    private var pendingPress: IntArray? = null
    private var contextMenuArmed = false

    /** c8.xa: requestFocusNodeHref delivers (url, src, title) on the main looper. */
    private val hrefHandler by lazy {
        object : android.os.Handler(android.os.Looper.getMainLooper()) {
            override fun handleMessage(message: android.os.Message) {
                val data = message.data ?: return
                if (data.isEmpty) return
                val link = data.getString("url")
                contextMenuArmed = !link.isNullOrEmpty()
                val press = pendingPress ?: return
                pendingPress = null
                contextMenu?.onContextMenu(ContextTarget(link, data.getString("src"), data.getString("title"), press[0], press[1]))
            }
        }
    }

    /**
     * e8.i/t4.b/c8.s6$g: s6.N0.onLongPress requests the hit element as soon as the press is long enough.
     * The reply arms the long-click listener before Chromium reaches performLongClick, so a link or image
     * long click is consumed and WebView never starts its own drag-and-drop or selection for it.
     */
    private val probe by lazy {
        android.view.GestureDetector(webView.context, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(event: android.view.MotionEvent) {
                pendingPress = intArrayOf(event.rawX.toInt(), event.rawY.toInt())
                webView.requestFocusNodeHref(hrefHandler.obtainMessage())
            }
        })
    }

    override fun setContextMenuHandler(handler: ContextMenuHandler?) {
        contextMenu = handler
        val web = webView as? GestureWebView
        if (handler == null) {
            web?.touchObserver = null
            webView.setOnLongClickListener(null)
            return
        }
        web?.touchObserver = { event -> if (contextMenu?.enabled == true) probe.onTouchEvent(event) }
        // e8.i.I -> s6.Q: the long click is handled exactly when the last hit was a link.
        webView.setOnLongClickListener { contextMenuArmed }
    }

    override fun pause() = webView.onPause()
    override fun resume() = webView.onResume()

    override fun createPrintAdapter(title: String): PrintDocumentAdapter = webView.createPrintDocumentAdapter(title)

    override fun saveArchive(path: String, callback: (String?) -> Unit) = webView.saveWebArchive(path, false) { callback(it) }

    override fun equals(other: Any?) = other is WebViewPage && other.webView === webView
    override fun hashCode() = System.identityHashCode(webView)

    companion object {
        /**
         * The page wrapper for [webView], created once and kept in a view tag. The wrapper then lives exactly
         * as long as its WebView, so weak maps keyed on pages behave like the old maps keyed on WebViews.
         */
        fun of(webView: WebView): WebViewPage = webView.getTag(R.id.engine_page) as? WebViewPage
            ?: WebViewPage(webView).also { webView.setTag(R.id.engine_page, it) }
    }
}
