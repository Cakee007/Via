package dev.ujhhgtg.via.browser

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.LocaleList
import dev.ujhhgtg.via.browser.filter.FilterEngine
import dev.ujhhgtg.via.browser.filter.FilterRequest
import dev.ujhhgtg.via.browser.script.ScriptBridge
import dev.ujhhgtg.via.browser.script.ScriptManager
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.SiteConfiguration
import dev.ujhhgtg.via.engine.EngineConfig
import dev.ujhhgtg.via.engine.EnginePage
import dev.ujhhgtg.via.engine.Engines
import dev.ujhhgtg.via.engine.FileChooserRequest
import dev.ujhhgtg.via.engine.FormResubmissionRequest
import dev.ujhhgtg.via.engine.FullscreenRequest
import dev.ujhhgtg.via.engine.HttpAuthRequest
import dev.ujhhgtg.via.engine.InterceptDecision
import dev.ujhhgtg.via.engine.JsDialogRequest
import dev.ujhhgtg.via.engine.LoadError
import dev.ujhhgtg.via.engine.LocationRequest
import dev.ujhhgtg.via.engine.MediaPermissionRequest
import dev.ujhhgtg.via.engine.PageEvents
import dev.ujhhgtg.via.engine.PageSettings
import dev.ujhhgtg.via.engine.PopupRequest
import dev.ujhhgtg.via.engine.SslErrorRequest
import java.util.Locale

/**
 * Applies Via's recovered shell policy to one [EnginePage], which it creates and owns.
 *
 * Engine-neutral: the backend reports page events here, and this class decides settings, navigation,
 * interception and injection. The class owns no activity views. A host supplies callbacks for
 * navigation, popups, downloads, permissions, file selection, and fullscreen video, which lets a tab
 * controller create popup pages without coupling the policy to UI code.
 */
class PageController(
    private val context: Context,
    private val preferences: BrowserPreferences,
    private val callbacks: Callbacks = Callbacks.NONE,
    private val siteConfiguration: (String) -> SiteConfiguration? = { null },
    private val filterEngine: FilterEngine? = null,
    private val scripts: ScriptManager? = null,
    private val userAgentForId: (Int) -> String? = { null },
    private val pageBridgeSecret: String = java.util.UUID.randomUUID().toString(),
    private val allowBlockedPage: (String) -> Boolean = { false },
    initialUrl: String? = null,
) {
    private var defaultUserAgent: String? = null
    // Interception runs on the engine's network thread. Publish page context from main-thread callbacks.
    @Volatile private var currentPageUrl: String? = null
    private var requestContextInitialized = false
    private var mediaPaused = false
    private var popupNavigationPending = false
    private var popupReferer: String? = null
    private var popupRefererPending = false
    private val interceptor = RequestInterceptor(context, preferences, siteConfiguration, filterEngine, allowBlockedPage,
        onRequestBlocked = { callbacks.onRequestBlocked(it) },
        onResourceRecorded = { hasMedia ->
            // e8.j.F reports availability only for the visible page. The host applies
            // s6.B's preference/state check before animating its address-bar control.
            val view = page.view
            view.post { if (view.isShown) callbacks.onResourceAvailabilityChanged(page, hasMedia) }
        })
    private val resourceLog get() = interceptor.resourceLog
    private var viaBridge: ViaBridge? = null
    private val injection by lazy {
        DocumentInjection(context, preferences, siteConfiguration, filterEngine, scripts, pageBridgeSecret) { callbacks.onReaderCheckRequested() }
    }

    private val events = Events()
    val page: EnginePage = Engines.backend.createPage(context, events)

    init {
        configure(initialUrl, installBindings = true)
    }

    fun resources(): List<BrowserResource> = resourceLog.snapshot()
    fun hasMediaResources(): Boolean = resourceLog.hasMedia()
    fun clearResources() = resourceLog.clear()

    interface Callbacks {
        fun onPageStarted(page: EnginePage, url: String) = Unit
        fun onReaderCheckRequested() = Unit
        fun onResourceAvailabilityChanged(page: EnginePage, hasMedia: Boolean) = Unit
        fun onRequestBlocked(url: String) = Unit
        fun onPageFinished(page: EnginePage, url: String, title: String?) = Unit
        fun onReceivedTitle(page: EnginePage, title: String) = Unit
        fun onReceivedIcon(page: EnginePage, icon: Bitmap?) = Unit
        fun onReceivedTouchIconUrl(page: EnginePage, url: String) = Unit
        fun onProgressChanged(page: EnginePage, progress: Int) = Unit
        fun onDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?, size: Long) = Unit
        fun onNavigationRequest(page: EnginePage, url: String, isRedirect: Boolean, isPopup: Boolean): Boolean = false
        fun onExternalUrl(page: EnginePage, url: String) = Unit
        fun onInternalUrl(page: EnginePage, url: String) = onExternalUrl(page, url)
        fun onError(page: EnginePage, error: LoadError) = Unit
        fun onHttpAuth(page: EnginePage, request: HttpAuthRequest) = request.cancel()
        fun onSslError(page: EnginePage, request: SslErrorRequest) = request.cancel()
        fun onCreateWindow(source: EnginePage, request: PopupRequest) = request.deny()
        fun onCloseWindow(page: EnginePage) = Unit
        fun onOpenScriptTab(page: EnginePage, url: String, active: Boolean, insert: Int) = Unit
        fun onBridgeCommand(page: EnginePage, command: Int): Int = 0
        fun onBridgeDownload(page: EnginePage, url: String, name: String?, mime: String?) = Unit
        fun onBridgeMessage(page: EnginePage, token: String, json: String) = Unit
        fun onBridgeRecord(page: EnginePage, url: String, mime: String?) = Unit
        fun onBridgeToast(page: EnginePage, text: String) = Unit
        fun onBridgeAddon(page: EnginePage, id: String) = Unit
        fun onInstalledAddonIds(page: EnginePage): String = "[]"
        fun onGeolocationPrompt(request: LocationRequest) = request.respond(allow = false, retain = false)
        fun onGeolocationHidePrompt() = Unit
        fun onPermissionRequest(request: MediaPermissionRequest) = request.deny()
        fun onPermissionRequestCanceled(request: MediaPermissionRequest) = Unit
        fun onShowFullscreen(request: FullscreenRequest) = request.exited()
        fun onHideFullscreen() = Unit
        fun onFormResubmission(page: EnginePage, request: FormResubmissionRequest) = request.cancel()
        fun onFileChooser(page: EnginePage, request: FileChooserRequest): Boolean = false

        companion object {
            val NONE = object : Callbacks {}
        }
    }

    private fun configure(url: String?, installBindings: Boolean) {
        val flags = preferences.webFlags
        // e8.i.L takes cookie acceptance from the global webflag mask; SiteConf only overrides
        // JavaScript, image loading, user agent, and text zoom in e8.i.M.
        page.configure(EngineConfig(
            multipleWindows = flags and 4_194_304 != 0,
            safeBrowsing = flags and 2_097_152 == 0,
            acceptCookies = flags and 8_192 != 0,
            thirdPartyCookies = flags and 16_384 != 0,
            remoteDebugging = flags and 4_096 != 0,
            darkening = preferences.isNightMode && preferences.nightCss,
        ))
        if (defaultUserAgent == null) defaultUserAgent = UserAgentPolicy.browserDefault(page.userAgent)
        applyPageSettings(url)

        if (!installBindings) return

        val gm = scripts?.bridge(page, object : ScriptBridge.Callbacks {
            override fun onDownload(url: String, name: String) {
                if (url.isEmpty()) return
                val disposition = if (name.isEmpty()) "attachment" else
                    "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
                val mime = name.substringAfterLast('.', "").takeIf { it.isNotEmpty() }
                    ?.let { dev.ujhhgtg.via.downloads.DownloadMimeTypes.mime(it, "application/octet-stream") }
                callbacks.onDownload(url, page.userAgent, disposition, mime, 0)
            }
            override fun onOpenTab(url: String, active: Boolean, insert: Int) {
                if (url.isNotEmpty()) callbacks.onOpenScriptTab(page, url, active, insert)
            }
        })
        val bridge = ViaBridge(page, object : ViaBridge.Callbacks {
            override fun command(page: EnginePage, command: Int) = callbacks.onBridgeCommand(page, command)
            override fun download(page: EnginePage, url: String, name: String?, mime: String?) = callbacks.onBridgeDownload(page, url, name, mime)
            override fun message(page: EnginePage, token: String, json: String) = callbacks.onBridgeMessage(page, token, json)
            override fun record(page: EnginePage, url: String, mime: String?) = callbacks.onBridgeRecord(page, url, mime)
            override fun toast(page: EnginePage, text: String) = callbacks.onBridgeToast(page, text)
            override fun addon(page: EnginePage, id: String) = callbacks.onBridgeAddon(page, id)
            override fun installedAddonIds(page: EnginePage) = callbacks.onInstalledAddonIds(page)
        }, pageBridgeSecret)
        viaBridge = bridge
        page.installBridges(bridge, gm)
    }

    fun reloadPreferences(url: String? = page.url) {
        // ua.n1 -> r4.a.t reapplies settings to every live tab, but the
        // page events and JavaScript bridges already belong to this controller.
        configure(url, installBindings = false)
    }

    /** c8.ua.n1(false,true): rebind darkening and the external page's injected CSS in place. */
    fun applyNightTheme(dark: Boolean) {
        applyPageSettings(page.url)
        page.setDarkening(dark && preferences.nightCss)
        val url = page.url
        if (!url.isNullOrEmpty() && !url.startsWith("file://", true) && page.progress >= 100) {
            // w9.k.x1 uses the CSS fallback only below API 29. The other
            // path removes stale injected CSS, retaining the document and JS state.
            injection.removeNightCss(page)
        }
    }

    /** c8.s6.f5 and t4.b.setReferer: mark the transport's first navigation and next explicit load. */
    fun preparePopupWindow(referer: String) {
        popupNavigationPending = true
        popupReferer = referer.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
        popupRefererPending = popupReferer != null
    }

    fun load(url: String, referer: String? = null) {
        applyPageSettings(url)
        // r4.d.H -> t4.b.setReferer retains the preceding document for p4.j's
        // first interception, independently of the one-shot HTTP Referer header.
        if (referer != null) {
            popupReferer = referer.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
            popupRefererPending = popupReferer != null
        }
        val source = referer ?: popupReferer.takeIf { popupRefererPending }
        popupRefererPending = false
        page.load(url, RequestHeaderPolicy.headers(url, preferences.webFlags, source, languageTags()))
    }

    fun pause() = page.pause()

    fun resume() = page.resume()

    /** r4.d.S pauses a tab's media and restores only the elements it paused on reselection. */
    fun deactivate() {
        if (page.progress < 100) page.stopLoading()
        page.evaluate("(function(){for(var c=document.querySelectorAll(\"video, audio\"),a,b=0;b<c.length;b++)a=c[b],a.paused||(a.pause(),a.setAttribute(\"via-data-playing\",\"true\"))})();")
        mediaPaused = true
        pause()
    }

    fun activate() {
        resume()
        if (mediaPaused) {
            page.evaluate("(function(){for(var c=document.querySelectorAll('video[via-data-playing=\"true\"], audio[via-data-playing=\"true\"]'),a,b=0;b<c.length;b++)a=c[b],a.play(),a.removeAttribute(\"via-data-playing\")})();")
            mediaPaused = false
        }
    }

    fun destroy() {
        viaBridge = null
        page.destroy()
    }

    private inner class Events : PageEvents {
        override fun onPageStarted(url: String) {
            resourceLog.startPage()
            callbacks.onResourceAvailabilityChanged(page, false)
            // p4.j.o records the actual top URL. e8.i has no page-start override:
            // settings are applied only by its real navigation setup B/E.
            requestContextInitialized = true
            currentPageUrl = url
            callbacks.onPageStarted(page, url)
            viaBridge?.let { bridge -> page.evaluate("window.__VIA_SECRET__=${org.json.JSONObject.quote(bridge.secret)};") }
            if (url.startsWith("http") && preferences.disableWebRtc) {
                page.evaluate("(function(){var a=window;try{delete a.RTCPeerConnection,delete a.webkitRTCPeerConnection,delete a.mozRTCPeerConnection}catch(b){}})();")
            }
            installDocumentCoordinator()
        }

        override fun onPageFinished(url: String) {
            viaBridge?.let { bridge -> page.evaluate("window.__VIA_SECRET__=${org.json.JSONObject.quote(bridge.secret)};") }
            callbacks.onPageFinished(page, url, page.title)
        }

        override fun onProgressChanged(progress: Int) = callbacks.onProgressChanged(page, progress)
        override fun onReceivedTitle(title: String) = callbacks.onReceivedTitle(page, title)
        override fun onReceivedIcon(icon: Bitmap?) = callbacks.onReceivedIcon(page, icon)
        override fun onReceivedTouchIconUrl(url: String) = callbacks.onReceivedTouchIconUrl(page, url)

        override fun onRequest(url: String, rangeFromStart: Boolean, request: (topUrl: String?) -> FilterRequest): InterceptDecision {
            // p4.j.b seeds the first interception from the popup's referer until onPageStarted.
            if (!requestContextInitialized) {
                requestContextInitialized = true
                currentPageUrl = popupReferer
            }
            val topUrl = currentPageUrl
            return interceptor.intercept(url, topUrl, rangeFromStart) { request(topUrl) }
        }

        override fun onUrlRequest(url: String): InterceptDecision? = interceptor.injectedResource(url)

        override fun onNavigation(url: String, mainFrame: Boolean, isRedirect: Boolean): Boolean =
            handleNavigation(url, mainFrame, isRedirect)

        override fun onDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?, size: Long) =
            callbacks.onDownload(url, userAgent, contentDisposition, mimeType, size)
        override fun onError(error: LoadError) = callbacks.onError(page, error)
        override fun onHttpAuth(request: HttpAuthRequest) = callbacks.onHttpAuth(page, request)
        override fun onSslError(request: SslErrorRequest) = callbacks.onSslError(page, request)
        override fun onFormResubmission(request: FormResubmissionRequest) = callbacks.onFormResubmission(page, request)
        override fun onJsDialog(request: JsDialogRequest): Boolean = JavaScriptDialogs.show(page.view, request)

        override fun onCreateWindow(request: PopupRequest) {
            // p4.c -> e8.c0.e: policy owns the request until the user accepts or the
            // action toast closes. A popup page must not exist before acceptance.
            if (page.view.isShown) callbacks.onCreateWindow(page, request)
        }

        override fun onCloseWindow() = callbacks.onCloseWindow(page)
        override fun onGeolocationPrompt(request: LocationRequest) = callbacks.onGeolocationPrompt(request)
        override fun onGeolocationHidePrompt() = callbacks.onGeolocationHidePrompt()
        override fun onPermissionRequest(request: MediaPermissionRequest) = callbacks.onPermissionRequest(request)
        override fun onPermissionRequestCanceled(request: MediaPermissionRequest) = callbacks.onPermissionRequestCanceled(request)
        override fun onShowFullscreen(request: FullscreenRequest) = callbacks.onShowFullscreen(request)
        override fun onHideFullscreen() = callbacks.onHideFullscreen()
        override fun onFileChooser(request: FileChooserRequest): Boolean = callbacks.onFileChooser(page, request)
    }

    private fun handleNavigation(url: String, mainFrame: Boolean = true, isRedirect: Boolean = false): Boolean {
        if (url.isBlank()) return false
        // s6.g9 installs its page-redirection interceptor before e8.i's
        // popup/site/retained-history decisions, including engine callbacks.
        if (callbacks.onNavigationRequest(page, url, isRedirect, popupNavigationPending)) return true
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
                        page.view.postDelayed({
                            page.stopLoading()
                            load(url, popupReferer)
                        }, 20L)
                        return true
                    }
                    applyPageSettings(url)
                }
                return false
            }
        }
        if (UrlResolver.isInternal(url)) {
            callbacks.onInternalUrl(page, url)
            return true
        }
        if (UrlResolver.isExternalScheme(url)) {
            callbacks.onExternalUrl(page, url)
            return true
        }
        if (mainFrame) {
            var settingsUrl = url
            if (isRedirect) {
                val sourceUrl = page.url
                val sourceSite = sourceUrl?.let(DocumentPolicy::authority)?.let(siteConfiguration)
                val targetSite = siteConfiguration(DocumentPolicy.authority(url))
                // e8.i.E: an automatic redirect to an unconfigured site retains
                // the source site's settings, except explicit Android-phone UA
                // without a site desktop override. Request filtering still follows
                // the destination page, as p4.j's top-page URL does in the original.
                if (sourceSite?.isEnabled == true && targetSite?.isEnabled != true &&
                    (sourceSite.userAgentChoice != -1 || sourceSite.desktopMode(0))) {
                    settingsUrl = sourceUrl
                }
            }
            applyPageSettings(settingsUrl)
        }
        return false
    }

    private fun applyPageSettings(url: String?) {
        val file = url?.startsWith("file://", true) == true
        val site = if (file) null else UrlResolver.siteKey(url)?.let(siteConfiguration)?.takeIf { it.isEnabled }
        val flags = preferences.webFlags
        fun siteFlag(bit: Int, global: Boolean): Boolean =
            if (site != null && site.enabledFlags and bit != 0) site.flags and bit != 0 else global
        val choice = site?.userAgentChoice?.takeIf { it != -1000 } ?: preferences.userAgentChoice
        val globalAgent = UserAgentPolicy.resolve(preferences.userAgentChoice, preferences.userAgent,
            defaultUserAgent, flags and 2048 != 0, preferences.duaChoice, preferences.duaString,
            preferences.webFlags2 and 1 != 0)
        page.apply(PageSettings(
            javaScript = file || siteFlag(2, preferences.javascriptEnabled()),
            images = file || siteFlag(4, globalImagesEnabled(flags)),
            userAgent = if (site == null) globalAgent else UserAgentPolicy.resolve(
                choice, if (choice > 0) userAgentForId(choice) else site.customUserAgent ?: globalAgent,
                defaultUserAgent, siteFlag(8, flags and 2048 != 0),
                preferences.duaChoice, preferences.duaString, preferences.webFlags2 and 1 != 0),
            textZoom = if (file) 100 else site?.textZoomOverride?.takeIf { it > 0 } ?: preferences.textSize,
        ))
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

    /** c8.s6.L and i6.s/r: JavaScript announces phases; native policy decides each injection. */
    private fun installDocumentCoordinator() = injection.installCoordinator(page, page.id)

    /** c8.s6.va: native action101 runAt=1(head),2(DOMContentLoaded),4(load). */
    fun injectDocumentPhase(runAt: Int): Boolean = injection.inject(page, runAt)
}
