package dev.ujhhgtg.via.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.http.SslError
import android.os.LocaleList
import android.os.Message
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import dev.ujhhgtg.via.browser.filter.FilterEngine
import dev.ujhhgtg.via.browser.filter.description
import dev.ujhhgtg.via.browser.script.ScriptBridge
import dev.ujhhgtg.via.browser.script.ScriptManager
import dev.ujhhgtg.via.browser.script.ScriptRunAt
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.SiteConfiguration
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale

/**
 * Configures one Android WebView with Via's recovered shell policy.
 *
 * The class owns no activity views. A host supplies callbacks for navigation, popups, downloads,
 * permissions, file selection, and fullscreen video, which keeps browser behavior testable and
 * lets a tab controller create popup WebViews without coupling the engine to UI code.
 */
class BrowserEngine(
    private val context: Context,
    private val preferences: BrowserPreferences,
    private val callbacks: Callbacks = Callbacks.NONE,
    private val siteConfiguration: (String) -> SiteConfiguration? = { null },
    private val filterEngine: FilterEngine? = null,
    private val scripts: ScriptManager? = null,
    private val userAgentForId: (Int) -> String? = { null },
    private val pageBridgeSecret: String = java.util.UUID.randomUUID().toString(),
    private val allowBlockedPage: (String) -> Boolean = { false },
) {
    private var defaultUserAgent: String? = null
    // Interception runs on Chromium's IO thread. Publish page context from main-thread callbacks.
    @Volatile private var currentPageUrl: String? = null
    private var requestContextInitialized = false
    private var mediaPaused = false
    private var popupNavigationPending = false
    private var popupReferer: String? = null
    private var popupRefererPending = false
    private val resourceLog = ResourceLog()
    private var viaBridge: ViaBridge? = null
    private val documentScripts by lazy { DocumentScripts(context) }
    private val documentReader by lazy { dev.ujhhgtg.via.reader.ReaderMode(context) }

    fun resources(): List<BrowserResource> = resourceLog.snapshot()
    fun hasMediaResources(): Boolean = resourceLog.hasMedia()
    fun clearResources() = resourceLog.clear()

    interface Callbacks {
        fun onPageStarted(webView: WebView, url: String) = Unit
        fun onReaderCheckRequested() = Unit
        fun onResourceAvailabilityChanged(webView: WebView, hasMedia: Boolean) = Unit
        fun onRequestBlocked(url: String) = Unit
        fun onPageFinished(webView: WebView, url: String, title: String?) = Unit
        fun onReceivedTitle(webView: WebView, title: String) = Unit
        fun onReceivedIcon(webView: WebView, icon: Bitmap?) = Unit
        fun onReceivedTouchIconUrl(view: WebView, url: String, precomposed: Boolean) = Unit
        fun onProgressChanged(webView: WebView, progress: Int) = Unit
        fun onDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?, size: Long) = Unit
        fun onNavigationRequest(webView: WebView, url: String, isRedirect: Boolean, isPopup: Boolean): Boolean = false
        fun onExternalUrl(webView: WebView, url: String) = Unit
        fun onInternalUrl(webView: WebView, url: String) = onExternalUrl(webView, url)
        fun onError(webView: WebView, request: WebResourceRequest?, error: WebResourceError?) = Unit
        fun onHttpAuth(webView: WebView, handler: HttpAuthHandler, host: String, realm: String?) = Unit
        fun onSslError(webView: WebView, handler: SslErrorHandler, error: SslError) { handler.cancel() }
        fun onCreateWindow(source: WebView, isDialog: Boolean, userGesture: Boolean, message: Message) = message.sendToTarget()
        fun onCloseWindow(webView: WebView) = Unit
        fun onOpenScriptTab(webView: WebView, url: String, active: Boolean, insert: Int) = Unit
        fun onBridgeCommand(webView: WebView, command: Int): Int = 0
        fun onBridgeDownload(webView: WebView, url: String, name: String?, mime: String?) = Unit
        fun onBridgeMessage(webView: WebView, token: String, json: String) = Unit
        fun onBridgeRecord(webView: WebView, url: String, mime: String?) = Unit
        fun onBridgeToast(webView: WebView, text: String) = Unit
        fun onBridgeAddon(webView: WebView, id: String) = Unit
        fun onInstalledAddonIds(webView: WebView): String = "[]"
        fun onGeolocationPrompt(origin: String, callback: GeolocationPermissions.Callback) =
            callback.invoke(origin, false, false)
        fun onGeolocationHidePrompt() = Unit
        fun onPermissionRequest(request: PermissionRequest) = request.deny()
        fun onPermissionRequestCanceled(request: PermissionRequest) = Unit
        fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) = callback.onCustomViewHidden()
        fun onHideCustomView() = Unit
        fun onFormResubmission(webView: WebView, dontResend: Message, resend: Message) = dontResend.sendToTarget()
        fun onFileChooser(
            webView: WebView,
            callback: ValueCallback<Array<Uri>>,
            params: WebChromeClient.FileChooserParams,
        ): Boolean = false

        companion object {
            val NONE = object : Callbacks {}
        }
    }

    /** Applies the common WebView policy recovered from Via's `s4.b.f` and `e8.i`. */
    @SuppressLint("SetJavaScriptEnabled", "WebSettingsDeprecated")
    fun configure(webView: WebView, url: String? = null) = configure(webView, url, installBindings = true)

    @SuppressLint("SetJavaScriptEnabled", "WebSettingsDeprecated")
    private fun configure(webView: WebView, url: String?, installBindings: Boolean) {
        webView.setBackgroundColor(0)
        webView.isFocusable = true
        webView.isScrollbarFadingEnabled = true
        webView.isSaveEnabled = true
        webView.scrollBarSize = 10

        val settings = webView.settings
        WebViewCapabilities.configure(webView, preferences)
        if (defaultUserAgent == null) defaultUserAgent = UserAgentPolicy.browserDefault(settings.userAgentString)
        settings.apply {
            @Suppress("DEPRECATION")
            setEnableSmoothTransition(true)
            mediaPlaybackRequiresUserGesture = false
            domStorageEnabled = true
            allowFileAccess = true
            setSupportZoom(true)
            javaScriptCanOpenWindowsAutomatically = true
            defaultTextEncodingName = "UTF-8"
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            allowContentAccess = true
            minimumFontSize = 1
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportMultipleWindows(preferences.webFlags and 4_194_304 != 0)
            setGeolocationEnabled(true)
            @Suppress("DEPRECATION")
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            runCatching { safeBrowsingEnabled = preferences.webFlags and 2_097_152 == 0 }
        }

        webView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
        applyPageSettings(webView, url)

        val cookies = CookieManager.getInstance()
        // e8.i.L takes cookie acceptance from the global webflag mask; SiteConf only overrides
        // JavaScript, image loading, user agent, and text zoom in e8.i.M.
        val cookiesEnabled = preferences.webFlags and 8_192 != 0
        cookies.setAcceptCookie(cookiesEnabled)
        cookies.setAcceptThirdPartyCookies(webView, cookiesEnabled && preferences.webFlags and 16_384 != 0)
        WebView.setWebContentsDebuggingEnabled(preferences.webFlags and 4_096 != 0)

        if (!installBindings) return

        // These platform bridges are legacy attack surfaces and are removed by Via before its
        // own page bridge is attached. Community code intentionally exposes no implicit bridge.
        webView.removeJavascriptInterface("searchBoxJavaBridge_")
        webView.removeJavascriptInterface("accessibility")
        webView.removeJavascriptInterface("accessibilityTraversal")
        scripts?.attach(webView, object : ScriptBridge.Callbacks {
            override fun onDownload(url: String, name: String) {
                if (url.isEmpty()) return
                val disposition = if (name.isEmpty()) "attachment" else
                    "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
                val mime = name.substringAfterLast('.', "").takeIf { it.isNotEmpty() }
                    ?.let { dev.ujhhgtg.via.downloads.DownloadMimeTypes.mime(it, "application/octet-stream") }
                callbacks.onDownload(url, webView.settings.userAgentString, disposition, mime, 0)
            }
            override fun onOpenTab(url: String, active: Boolean, insert: Int) {
                if (url.isNotEmpty()) callbacks.onOpenScriptTab(webView, url, active, insert)
            }
        })

        webView.webViewClient = client
        webView.webChromeClient = chromeClient
        val bridge = ViaBridge(webView, object : ViaBridge.Callbacks {
            override fun command(webView: WebView, command: Int) = callbacks.onBridgeCommand(webView, command)
            override fun download(webView: WebView, url: String, name: String?, mime: String?) = callbacks.onBridgeDownload(webView, url, name, mime)
            override fun message(webView: WebView, token: String, json: String) = callbacks.onBridgeMessage(webView, token, json)
            override fun record(webView: WebView, url: String, mime: String?) {
                callbacks.onBridgeRecord(webView, url, mime)
            }
            override fun toast(webView: WebView, text: String) = callbacks.onBridgeToast(webView, text)
            override fun addon(webView: WebView, id: String) = callbacks.onBridgeAddon(webView, id)
            override fun installedAddonIds(webView: WebView) = callbacks.onInstalledAddonIds(webView)
        }, pageBridgeSecret)
        viaBridge = bridge
        webView.addJavascriptInterface(bridge, "via")
        webView.addJavascriptInterface(bridge, "via_page")
        webView.setDownloadListener { url, userAgent, disposition, mime, size ->
            callbacks.onDownload(url, userAgent, disposition, mime, size)
        }
    }

    fun reloadPreferences(webView: WebView, url: String? = webView.url) {
        // ua.n1 -> r4.a.t reapplies WebSettings to every live tab, but the
        // WebView clients and JavaScript bridges already belong to this engine.
        configure(webView, url, installBindings = false)
    }

    /** c8.ua.n1(false,true): rebind darkening and the external page's injected CSS in place. */
    fun applyNightTheme(webView: WebView, dark: Boolean) {
        applyPageSettings(webView, webView.url)
        WebViewCapabilities.applyNightTheme(webView, preferences, dark)
        val url = webView.url
        if (!url.isNullOrEmpty() && !url.startsWith("file://", true) && webView.progress >= 100) {
            // w9.k.x1 uses the CSS fallback only below API 29. The other
            // path removes stale injected CSS, retaining the document and JS state.
            webView.evaluateJavascript(documentScripts.night(false), null)
        }
    }

    /** c8.s6.f5 and t4.b.setReferer: mark the transport's first navigation and next explicit load. */
    fun preparePopupWindow(referer: String) {
        popupNavigationPending = true
        popupReferer = referer.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
        popupRefererPending = popupReferer != null
    }

    fun load(webView: WebView, url: String, referer: String? = null) {
        applyPageSettings(webView, url)
        // r4.d.H -> t4.b.setReferer retains the preceding document for p4.j's
        // first interception, independently of the one-shot HTTP Referer header.
        if (referer != null) {
            popupReferer = referer.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
            popupRefererPending = popupReferer != null
        }
        val source = referer ?: popupReferer.takeIf { popupRefererPending }
        popupRefererPending = false
        val headers = RequestHeaderPolicy.headers(url, preferences.webFlags, source, languageTags())
        if (headers.isEmpty()) webView.loadUrl(url) else webView.loadUrl(url, headers)
    }

    fun pause(webView: WebView) = webView.onPause()

    fun resume(webView: WebView) = webView.onResume()

    /** r4.d.S pauses a tab's media and restores only the elements it paused on reselection. */
    fun deactivate(webView: WebView) {
        if (webView.progress < 100) webView.stopLoading()
        webView.evaluateJavascript("(function(){for(var c=document.querySelectorAll(\"video, audio\"),a,b=0;b<c.length;b++)a=c[b],a.paused||(a.pause(),a.setAttribute(\"via-data-playing\",\"true\"))})();", null)
        mediaPaused = true
        pause(webView)
    }

    fun activate(webView: WebView) {
        resume(webView)
        if (mediaPaused) {
            webView.evaluateJavascript("(function(){for(var c=document.querySelectorAll('video[via-data-playing=\"true\"], audio[via-data-playing=\"true\"]'),a,b=0;b<c.length;b++)a=c[b],a.play(),a.removeAttribute(\"via-data-playing\")})();", null)
            mediaPaused = false
        }
    }

    fun destroy(webView: WebView) {
        runCatching {
            viaBridge = null
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

    private val client = object : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            resourceLog.startPage()
            callbacks.onResourceAvailabilityChanged(view, false)
            // p4.j.o records the actual top URL. e8.i has no page-start override:
            // WebSettings are applied only by its real navigation setup B/E.
            requestContextInitialized = true
            currentPageUrl = url
            callbacks.onPageStarted(view, url)
            viaBridge?.let { bridge -> view.evaluateJavascript("window.__VIA_SECRET__=${org.json.JSONObject.quote(bridge.secret)};", null) }
            if (url.startsWith("http") && preferences.disableWebRtc) {
                view.evaluateJavascript("(function(){var a=window;try{delete a.RTCPeerConnection,delete a.webkitRTCPeerConnection,delete a.mozRTCPeerConnection}catch(b){}})();", null)
            }
            installDocumentCoordinator(view)
            super.onPageStarted(view, url, favicon)
        }

        override fun onPageFinished(view: WebView, url: String) {
            viaBridge?.let { bridge -> view.evaluateJavascript("window.__VIA_SECRET__=${org.json.JSONObject.quote(bridge.secret)};", null) }
            callbacks.onPageFinished(view, url, view.title)
            super.onPageFinished(view, url)
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url.toString()
            // p4.j.b seeds the first interception from the popup's referer until onPageStarted.
            if (!requestContextInitialized) {
                requestContextInitialized = true
                currentPageUrl = popupReferer
            }
            val topUrl = currentPageUrl
            val intercepted = injectedResource(url) ?: localFileResponse(url)
            val blocked = intercepted == null && filteringEnabled(topUrl) && !url.startsWith("file://", true) &&
                filterEngine?.shouldBlock(request, topUrl) == true
            // ua.U1 counts matched requests before E0 checks the session's continue-loading hosts.
            if (blocked) callbacks.onRequestBlocked(url)
            // ua.E0 consults ua.q only for the top URL, after the filter has matched; subresources still block normally.
            val bypass = blocked && (topUrl == null || url == topUrl) && allowBlockedPage(url)
            val response = intercepted ?: if (blocked && !bypass) {
                if (topUrl == null || url == topUrl) blockedPageResponse(url)
                else blockedResponse(url)
            } else null
            // e8.j.F records the result of the entire interception chain, including
            // virtual resources, and excludes only generated documents/blocker.css.
            recordResource(view, url, response != null, request.requestHeaders?.get("Range")?.startsWith("bytes=0-") == true)
            return response ?: super.shouldInterceptRequest(view, request)
        }

        @Deprecated("Deprecated in Java")
        @Suppress("OverridingDeprecatedMember")
        override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
            injectedResource(url)?.let { return it }
            return null
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url?.toString().orEmpty()
            val redirect = request.isRedirect || !request.hasGesture()
            return handleNavigation(view, url, request.isForMainFrame, redirect)
        }

        @Deprecated("Deprecated in Java")
        @Suppress("OverridingDeprecatedMember")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = handleNavigation(view, url,
            isRedirect = view.hitTestResult.type == WebView.HitTestResult.UNKNOWN_TYPE)

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) callbacks.onError(view, request, error)
            super.onReceivedError(view, request, error)
        }

        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String?) {
            callbacks.onHttpAuth(view, handler, host, realm)
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            callbacks.onSslError(view, handler, error)
        }

        override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) =
            callbacks.onFormResubmission(view, dontResend, resend)
    }

    private val chromeClient = object : WebChromeClient() {
        override fun getDefaultVideoPoster(): Bitmap = createBitmap(1, 1)

        override fun onJsAlert(view: WebView, url: String, message: String, result: android.webkit.JsResult): Boolean =
            JavaScriptDialogs.alert(view, url, message, result)

        override fun onJsBeforeUnload(view: WebView, url: String, message: String, result: android.webkit.JsResult): Boolean =
            JavaScriptDialogs.beforeUnload(view, message, result)

        override fun onJsConfirm(view: WebView, url: String, message: String, result: android.webkit.JsResult): Boolean =
            JavaScriptDialogs.confirm(view, url, message, result)

        override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String?, result: android.webkit.JsPromptResult): Boolean =
            JavaScriptDialogs.prompt(view, url, message, defaultValue, result)

        override fun onProgressChanged(view: WebView, progress: Int) {
            callbacks.onProgressChanged(view, progress)
            super.onProgressChanged(view, progress)
        }

        override fun onReceivedTitle(view: WebView, title: String) {
            callbacks.onReceivedTitle(view, title)
            super.onReceivedTitle(view, title)
        }

        override fun onReceivedIcon(view: WebView, icon: Bitmap?) = callbacks.onReceivedIcon(view, icon)

        override fun onReceivedTouchIconUrl(view: WebView, url: String, precomposed: Boolean) =
            callbacks.onReceivedTouchIconUrl(view, url, precomposed)

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            // p4.c -> e8.c0.e: policy owns the Message until the user accepts or the
            // action toast closes. A popup WebView must not exist before acceptance.
            if (view.isShown) callbacks.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
            return true
        }

        override fun onCloseWindow(window: WebView) = callbacks.onCloseWindow(window)

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) =
            callbacks.onGeolocationPrompt(origin, callback)

        override fun onGeolocationPermissionsHidePrompt() = callbacks.onGeolocationHidePrompt()

        override fun onPermissionRequest(request: PermissionRequest) = callbacks.onPermissionRequest(request)

        override fun onPermissionRequestCanceled(request: PermissionRequest) = callbacks.onPermissionRequestCanceled(request)

        override fun onShowCustomView(view: View, callback: CustomViewCallback) = callbacks.onShowCustomView(view, callback)

        @Deprecated("Deprecated in Java")
        @Suppress("OverridingDeprecatedMember")
        override fun onShowCustomView(view: View, requestedOrientation: Int, callback: CustomViewCallback) =
            callbacks.onShowCustomView(view, callback)

        override fun onHideCustomView() = callbacks.onHideCustomView()

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean = callbacks.onFileChooser(webView, filePathCallback, fileChooserParams)
    }

    private fun handleNavigation(view: WebView, url: String, mainFrame: Boolean = true, isRedirect: Boolean = false): Boolean {
        if (url.isBlank()) return false
        // s6.g9 installs its page-redirection interceptor before e8.i's
        // popup/site/retained-history decisions, including WebView callbacks.
        if (callbacks.onNavigationRequest(view, url, isRedirect, popupNavigationPending)) return true
        // e8.i.E consumes c8.s6.f5's first-navigation marker. Chromium needs one
        // explicit reload when an existing site record changes the popup's UA.
        if (mainFrame && popupNavigationPending) {
            popupNavigationPending = false
            if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
                val domain = url.substringAfter("://").substringBefore('/').substringBefore(':')
                val site = siteConfiguration(domain)
                if (site != null && !site.isEmpty) {
                    val customAgent = site.userAgentChoice != -1000 || !site.customUserAgent.isNullOrEmpty() || site.overrides(8)
                    if (customAgent) {
                        view.postDelayed({
                            view.stopLoading()
                            load(view, url, popupReferer)
                        }, 20L)
                        return true
                    }
                    applyPageSettings(view, url)
                }
                return false
            }
        }
        if (UrlResolver.isInternal(url)) {
            callbacks.onInternalUrl(view, url)
            return true
        }
        if (UrlResolver.isExternalScheme(url)) {
            callbacks.onExternalUrl(view, url)
            return true
        }
        if (mainFrame) {
            var settingsUrl = url
            if (isRedirect) {
                val sourceUrl = view.url
                val sourceSite = sourceUrl?.let(DocumentPolicy::authority)?.let(siteConfiguration)
                val targetSite = siteConfiguration(DocumentPolicy.authority(url))
                // e8.i.E: an automatic redirect to an unconfigured site retains
                // the source site's WebSettings, except explicit Android-phone UA
                // without a site desktop override. Request filtering still follows
                // the destination page, as p4.j's top-page URL does in the original.
                if (sourceSite?.isEnabled == true && targetSite?.isEnabled != true &&
                    (sourceSite.userAgentChoice != -1 || sourceSite.desktopMode(0))) {
                    settingsUrl = sourceUrl
                }
            }
            applyPageSettings(view, settingsUrl)
        }
        return false
    }

    /** ua.U1/b1 reads the live global and per-site switch for the actual top document. */
    private fun filteringEnabled(url: String?): Boolean {
        val file = url?.startsWith("file://", true) == true
        val site = if (file) null else url?.let(DocumentPolicy::authority)?.let(siteConfiguration)?.takeIf { it.isEnabled }
        return !file &&
            (site?.adBlocking(preferences.webFlags) ?: (preferences.webFlags and 1 != 0))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun applyPageSettings(view: WebView, url: String?) {
        val file = url?.startsWith("file://", true) == true
        val site = if (file) null else UrlResolver.siteKey(url)?.let(siteConfiguration)?.takeIf { it.isEnabled }
        val flags = preferences.webFlags
        fun siteFlag(bit: Int, global: Boolean): Boolean =
            if (site != null && site.enabledFlags and bit != 0) site.flags and bit != 0 else global
        val images = file || siteFlag(4, globalImagesEnabled(flags))
        val settings = view.settings
        settings.javaScriptEnabled = file || siteFlag(2, preferences.javascriptEnabled())
        settings.loadsImagesAutomatically = images
        settings.blockNetworkImage = !images
        val choice = site?.userAgentChoice?.takeIf { it != -1000 } ?: preferences.userAgentChoice
        val globalAgent = UserAgentPolicy.resolve(preferences.userAgentChoice, preferences.userAgent,
            defaultUserAgent, flags and 2048 != 0, preferences.duaChoice, preferences.duaString,
            preferences.webFlags2 and 1 != 0)
        // Setting null restores the platform default, so a custom UA never leaks to the next site.
        settings.userAgentString = if (site == null) globalAgent else UserAgentPolicy.resolve(
            choice, if (choice > 0) userAgentForId(choice) else site.customUserAgent ?: globalAgent,
            defaultUserAgent, siteFlag(8, flags and 2048 != 0),
            preferences.duaChoice, preferences.duaString, preferences.webFlags2 and 1 != 0)
        settings.textZoom = if (file) 100 else site?.textZoomOverride?.takeIf { it > 0 } ?: preferences.textSize
        WebViewCapabilities.applyUserAgentMetadata(view)
    }

    private fun globalImagesEnabled(flags: Int): Boolean {
        if (flags and 32 == 0) return flags and 16 != 0
        // w9.p.H/x + s6.O5 classify by metering, not transport type. The
        // automatic mode permits images when offline or on an unmetered network.
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)
        return capabilities == null || capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun languageTags(): String? {
        if (preferences.language.isNullOrEmpty()) return null
        fun tag(locale: Locale): String {
            val language = when (locale.language) {
                "in" -> "id"; "iw" -> "he"; "ji" -> "yi"; "jw" -> "jv"; "tl" -> "fil"
                else -> locale.language
            }
            if (language == "no" && locale.country == "NO" && locale.variant == "NY") return "nn-NO"
            return if (locale.country.isEmpty()) language else "$language-${locale.country}"
        }
        val locales = LocaleList.getAdjustedDefault()
        return (0 until locales.size()).joinToString(",") { tag(locales[it]) }
    }

    /** e8.e0 permits page assets, but refuses other app-private file types. */
    private fun localFileResponse(url: String): WebResourceResponse? {
        if (!url.startsWith("file://", true)) return null
        val path = url.toUri().path ?: return null
        val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension in setOf("html", "htm", "css", "png", "js", "mht", "pdf", "jpg", "jpeg", "ttf", "otf", "woff", "woff2")) return null
        val directory = context.dataDir
        fun canonical(file: File): String = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        return if (canonical(File(path)).startsWith(canonical(directory)))
            WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(byteArrayOf())) else null
    }

    /** ua.E0 queries the display rule with b5.c.k(url, null), separately from the request match. */
    private fun blockedPageResponse(url: String): WebResourceResponse {
        val rule = filterEngine?.matchingRule(ResourceDocumentActions.filterRequest(url))?.description().orEmpty()
        return WebResourceResponse("text/html", "UTF-8",
            ByteArrayInputStream(BlockedPageDocument.html(context, url, rule).toByteArray(Charsets.UTF_8))).apply {
            responseHeaders = mapOf(
                "Cache-Control" to "no-cache",
                "Access-Control-Allow-Origin" to "*",
                // E0 uses the same headers as its blocker stylesheet response.
                "Content-Type" to "text/css",
            )
        }
    }

    private fun blockedResponse(url: String): WebResourceResponse {
        // z8.b0.F retains the leading dot; preserve E0's literal comparison as well.
        val filename = url.substringBeforeLast('?').substringAfterLast('/')
        val dot = filename.lastIndexOf('.')
        val extension = if (dot >= 0) filename.substring(dot).takeIf { it.length in 2..6 }.orEmpty() else ""
        if (extension.length > 1 && "html|htm|css|js".contains(extension))
            return emptyBlockedResponse
        return imageBlockedResponse
    }

    private fun recordResource(view: WebView, url: String, blocked: Boolean, rangeFromStart: Boolean = false) {
        if (!ResourceDocument.isInternalPage(context, url) && !UrlResolver.isInternal(url) && !url.endsWith("via_inject_blocker.css")) {
            val hasMedia = resourceLog.add(url, blocked, rangeFromStart)
            // e8.j.F reports availability only for the visible WebView. The host applies
            // s6.B's preference/state check before animating its address-bar control.
            view.post { if (view.isShown) callbacks.onResourceAvailabilityChanged(view, hasMedia) }
        }
    }

    /** c8.s6.L and i6.s/r: JavaScript announces phases; native policy decides each injection. */
    fun installDocumentCoordinator(view: WebView) {
        val url = view.url ?: return
        if (!view.settings.javaScriptEnabled || url.isEmpty() || url.startsWith("file://", true)) return
        view.evaluateJavascript(documentScripts.bootstrap(pageBridgeSecret, view.id), null)
    }

    /** c8.s6.va: native action101 runAt=1(head),2(DOMContentLoaded),4(load). */
    fun injectDocumentPhase(view: WebView, runAt: Int): Boolean {
        if (!view.settings.javaScriptEnabled) return false
        val url = view.url?.takeIf(String::isNotEmpty) ?: return false
        if (url.startsWith("file://", true)) return false
        val host = DocumentPolicy.host(url)
        if (host.isEmpty()) return false
        val enabled = preferences.scriptsEnabled
        if (runAt == 4) {
            view.evaluateJavascript(documentScripts.marker(pageBridgeSecret), null)
            callbacks.onReaderCheckRequested()
            return !enabled || scripts?.injectPhase(view, url, ScriptRunAt.IDLE) == true
        }
        if (runAt == 1) {
            val site = siteConfiguration(DocumentPolicy.authority(url))?.takeIf { it.isEnabled }
            val flags = preferences.webFlags
            val source = StringBuilder()
            val blocking = site?.adBlocking(flags) ?: (flags and 1 != 0)
            if (blocking && UrlResolver.isHttpUrl(url)) {
                source.append(documentScripts.blockerLink(host))
                val css = documentScripts.blockerStyle(filterEngine?.cosmeticCss(url))
                if (css.isNotEmpty()) view.evaluateJavascript(css, null)
            }
            val desktop = site?.desktopMode(flags) ?: (flags and 2048 != 0)
            if (host != "music.163.com" && host != "taobao.com" && desktop) source.append(documentScripts.desktopViewport())
            else if (flags and 524288 != 0) source.append(documentScripts.source("viewport-unlock"))
            val clipboard = site?.clipboardMode?.takeIf { it != 0 } ?: when {
                flags and 262144 != 0 -> 3
                flags and 131072 != 0 -> 2
                else -> 1
            }
            source.append(documentScripts.clipboard(clipboard))
            source.append(documentScripts.downloadLinks(pageBridgeSecret))
            source.append(documentScripts.source("blob-cache"))
            source.append(documentScripts.source("print"))
            source.append(documentScripts.source("notification"))
            if (flags and 1073741824 == 0) source.append(documentScripts.source("vibration"))
            source.append(documentScripts.passwordCapture(pageBridgeSecret))
            val font = preferences.uiFont
            if (font.isNotEmpty()) source.append(documentScripts.font(font))
            if (source.isNotEmpty()) view.evaluateJavascript(source.toString(), null)
            return !enabled || scripts?.injectPhase(view, url, ScriptRunAt.START) == true
        }
        val injected = enabled && scripts?.injectPhase(view, url, ScriptRunAt.END) == true
        documentReader.prepare(view) { prepared -> if (prepared) callbacks.onReaderCheckRequested() }
        return injected
    }

    /** c8.ua.E0/s9.d.f: virtual resources are resolved without reading a WebView on Chromium's IO thread. */
    fun injectedResource(url: String): WebResourceResponse? {
        val fontName = preferences.uiFont
        val encoded = if (fontName.isEmpty()) "" else java.net.URLEncoder.encode(fontName, "UTF-8").replace("+", "%20")
        if (encoded.isNotEmpty() && url.endsWith(encoded)) {
            val file = File(preferences.fontDirectory, fontName)
            if (!file.isFile) return null
            return runCatching {
                val mime = dev.ujhhgtg.via.downloads.DownloadMimeTypes.mime(file.extension, "font/ttf") ?: "font/ttf"
                WebResourceResponse(mime, "UTF-8", file.inputStream()).apply {
                    responseHeaders = mapOf("Cache-Control" to "immutable", "Access-Control-Allow-Origin" to "*", "Content-Type" to mime)
                }
            }.getOrNull()
        }
        if (!url.endsWith("via_inject_blocker.css")) return null
        val css = filterEngine?.cosmeticCss(url).orEmpty()
        if (css.isEmpty()) return emptyBlockedResponse
        return WebResourceResponse("text/css", "UTF-8", ByteArrayInputStream(css.toByteArray(Charsets.UTF_8))).apply {
            responseHeaders = mapOf("Cache-Control" to "no-cache", "Access-Control-Allow-Origin" to "*", "Content-Type" to "text/css")
        }
    }

    companion object {
        // u4.a.c/d are process-wide response objects, including their streams.
        // Recreating the GIF for every hit changes later blocked documents into image pages.
        private val emptyBlockedResponse = WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(byteArrayOf()))
        private val imageBlockedResponse = WebResourceResponse("image/gif", "UTF-8", ByteArrayInputStream(
            android.util.Base64.decode("R0lGODlhAQABAID/AMDAwAAAACH5BAEAAAAALAAAAAABAAEAAAICRAEAOw==", android.util.Base64.DEFAULT)))
    }
}
