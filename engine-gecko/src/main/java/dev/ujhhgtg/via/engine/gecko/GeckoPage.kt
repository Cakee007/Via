package dev.ujhhgtg.via.engine.gecko

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.http.SslCertificate
import android.os.Bundle
import android.print.PrintDocumentAdapter
import android.util.Log
import android.view.View
import androidx.core.graphics.get
import dev.ujhhgtg.via.engine.ContextMenuHandler
import dev.ujhhgtg.via.engine.ContextTarget
import dev.ujhhgtg.via.engine.EngineConfig
import dev.ujhhgtg.via.engine.EnginePage
import dev.ujhhgtg.via.engine.FullscreenRequest
import dev.ujhhgtg.via.engine.InterceptDecision
import dev.ujhhgtg.via.engine.LoadError
import dev.ujhhgtg.via.engine.PageBridge
import dev.ujhhgtg.via.engine.PageEvents
import dev.ujhhgtg.via.engine.PageSettings
import dev.ujhhgtg.via.engine.PopupRequest
import dev.ujhhgtg.via.engine.ResourceRequest
import dev.ujhhgtg.via.engine.ScriptChannel
import dev.ujhhgtg.via.engine.SelectionAction
import dev.ujhhgtg.via.engine.SslErrorRequest
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.ScreenLength
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse

/**
 * [EnginePage] over a GeckoSession shown in a [GestureGeckoView]. Script evaluation and page bridges go
 * through the built-in extension's content script, which connects a port for each top-level document.
 */
internal class GeckoPage(
    private val context: Context,
    private val runtime: GeckoRuntime,
    private val events: PageEvents,
) : EnginePage {
    override val view: View
        field = GestureGeckoView(context).apply { id = View.generateViewId() }

    private var session: GeckoSession
    private var settings: PageSettings? = null
    private var sessionState: GeckoSession.SessionState? = null
    private var destroyed = false
    /** Logical URL of a generated top-level response loaded through Gecko's data loader. */
    @Volatile private var generatedLogicalUrl: String? = null
    /** Tab-specific extension actions by extension id; [GeckoExtensions] merges them with the defaults. */
    internal val browserActions = HashMap<String, WebExtension.Action>()
    internal val pageActions = HashMap<String, WebExtension.Action>()
    /** The session currently shown, for extension delegate registration. */
    internal val currentSession: GeckoSession? get() = if (destroyed) null else session

    override var url: String? = null; private set
    override var title: String? = null; private set
    override var progress: Int = 100; private set
    override var favicon: Bitmap? = null; private set
    override var certificate: SslCertificate? = null; private set
    override val userAgent: String get() = session.settings.userAgentOverride ?: GeckoSession.getDefaultUserAgent()
    override val javaScriptEnabled: Boolean get() = session.settings.allowJavascript
    override val preloadsDocumentScripts = true
    override var canGoBack = false; private set
    override var canGoForward = false; private set
    override var scrollX = 0; private set
    override var scrollY = 0; private set

    private fun newSession(): GeckoSession = GeckoSession(GeckoSessionSettings.Builder()
        .usePrivateMode(false)
        .suspendMediaWhenInactive(false)
        .build()).also { created ->
        created.navigationDelegate = navigation
        created.progressDelegate = progressDelegate
        created.contentDelegate = content
        created.historyDelegate = history
        created.scrollDelegate = scroll
        created.promptDelegate = GeckoPrompts(this, events)
        created.permissionDelegate = GeckoPermissions(context, events)
        GeckoExtensions.registerSession(this, created)
        settings?.let { applyTo(created, it) }
        selectionActions?.let { created.selectionActionDelegate = it }
        GeckoBackend.withExtension { extension ->
            if (!destroyed) created.webExtensionController.setMessageDelegate(extension, scripts, GeckoBackend.NATIVE_APP)
        }
    }

    // --- Settings ---

    override fun configure(config: EngineConfig) {
        multipleWindows = config.multipleWindows
        GeckoBackend.applyConfig(config)
    }

    private var multipleWindows = true
    private var popupUserGesture = false

    override fun apply(settings: PageSettings) {
        this.settings = settings
        applyTo(session, settings)
        GeckoBackend.applyTextZoom(settings.textZoom)
    }

    private fun applyTo(target: GeckoSession, settings: PageSettings) {
        val current = target.settings
        if (current.allowJavascript != settings.javaScript) current.allowJavascript = settings.javaScript
        if (current.userAgentOverride != settings.userAgent) current.userAgentOverride = settings.userAgent
        current.viewportMode = if (settings.desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
    }

    override fun setDarkening(enabled: Boolean) = GeckoBackend.applyColorScheme(enabled)

    override fun setTextZoom(percent: Int) = GeckoBackend.applyTextZoom(percent)

    // --- Navigation ---

    override fun load(url: String, headers: Map<String, String>) {
        generatedLogicalUrl = null
        if (url.startsWith("javascript:", ignoreCase = true)) {
            evaluate(android.net.Uri.decode(url.substring("javascript:".length)))
            return
        }
        expectedDocument = url
        val loader = GeckoSession.Loader().uri(url)
            // App-initiated loads skip onLoadRequest, as WebView.loadUrl skips shouldOverrideUrlLoading.
            .flags(GeckoSession.LOAD_FLAGS_BYPASS_LOAD_URI_DELEGATE)
        headers.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.let { loader.referrer(it.value) }
        val rest = headers.filterKeys { !it.equals("Referer", ignoreCase = true) }
        if (rest.isNotEmpty()) loader.additionalHeaders(rest).headerFilter(GeckoSession.HEADER_FILTER_UNRESTRICTED_UNSAFE)
        // A cold runtime installs the extension asynchronously. Its bridges and filters must exist
        // before the first document, including Via's file-backed home page, starts loading.
        GeckoBackend.withExtension { if (!destroyed) session.load(loader) }
    }

    override fun reload() = session.reload()
    override fun stopLoading() = session.stop()
    override fun goBack() = session.goBack()
    override fun goForward() = session.goForward()

    override fun scrollTo(x: Int, y: Int) =
        session.panZoomController.scrollTo(ScreenLength.fromPixels(x.toDouble()), ScreenLength.fromPixels(y.toDouble()))

    override fun pageUp(toEdge: Boolean) {
        if (toEdge) session.panZoomController.scrollToTop()
        else session.panZoomController.scrollBy(ScreenLength.zero(), ScreenLength.fromVisualViewportHeight(-0.9))
    }

    override fun pageDown(toEdge: Boolean) {
        if (toEdge) session.panZoomController.scrollToBottom()
        else session.panZoomController.scrollBy(ScreenLength.zero(), ScreenLength.fromVisualViewportHeight(0.9))
    }

    private val navigation = object : GeckoSession.NavigationDelegate {
        override fun onLocationChange(session: GeckoSession, url: String?,
            perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
            this@GeckoPage.url = logicalUrl(url)
        }

        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { this@GeckoPage.canGoBack = canGoBack }
        override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) { this@GeckoPage.canGoForward = canGoForward }

        override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> {
            if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) popupUserGesture = request.hasUserGesture
            if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW && !multipleWindows) {
                // WebView without multiple-window support opens new-window links in place.
                load(request.uri)
                return GeckoBackend.result(AllowOrDeny.DENY)
            }
            val cancel = events.onNavigation(request.uri, mainFrame = true, isRedirect = request.isRedirect || !request.hasUserGesture)
            if (!cancel) expectedDocument = request.uri
            return GeckoBackend.result(if (cancel) AllowOrDeny.DENY else AllowOrDeny.ALLOW)
        }

        override fun onSubframeLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> {
            val cancel = events.onNavigation(request.uri, mainFrame = false, isRedirect = request.isRedirect || !request.hasUserGesture)
            return GeckoBackend.result(if (cancel) AllowOrDeny.DENY else AllowOrDeny.ALLOW)
        }

        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
            val result = GeckoResult<GeckoSession>()
            events.onCreateWindow(Popup(result, popupUserGesture))
            return result
        }

        override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String> {
            val failed = uri.orEmpty()
            if (error.category == WebRequestError.ERROR_CATEGORY_SECURITY) {
                events.onSslError(object : SslErrorRequest(failed, sslKind(error), setOf(sslKind(error)), error.certificate?.let { SslCertificate(it) }) {
                    override fun proceed() = Unit
                    override fun cancel() = Unit
                })
            } else events.onError(LoadError(failed, "GET", loadErrorKind(error)))
            return GeckoBackend.result(GeckoErrorPages.page(context, failed, error))
        }
    }

    private val history = object : GeckoSession.HistoryDelegate {
        override fun onVisited(session: GeckoSession, url: String, lastVisitedURL: String?, flags: Int): GeckoResult<Boolean> {
            val skipped = GeckoSession.HistoryDelegate.VISIT_REDIRECT_SOURCE or
                GeckoSession.HistoryDelegate.VISIT_REDIRECT_SOURCE_PERMANENT or GeckoSession.HistoryDelegate.VISIT_UNRECOVERABLE_ERROR
            val recordable = flags and GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL != 0 && flags and skipped == 0
            return GeckoBackend.result(recordable && events.onVisited(url))
        }

        // Pages with many links query Via's database in batches; keep that off the UI thread.
        override fun getVisited(session: GeckoSession, urls: Array<String>): GeckoResult<BooleanArray> {
            val result = GeckoResult<BooleanArray>()
            GeckoBackend.historyExecutor.execute {
                result.complete(runCatching { events.getVisited(urls) }.getOrElse { BooleanArray(urls.size) })
            }
            return result
        }
    }

    /** Holds Gecko's new-window request until the tab controller attaches a page from this backend. */
    private class Popup(private val result: GeckoResult<GeckoSession>, userGesture: Boolean) : PopupRequest(isDialog = false, userGesture = userGesture) {
        private var done = false
        override val canAttach = true
        override fun attach(page: EnginePage) {
            if (done) return
            done = true
            result.complete((page as GeckoPage).adoptPopupSession())
        }
        override fun deny() {
            if (done) return
            done = true
            result.complete(null)
        }
    }

    /** Swaps this unused page onto a fresh, unopened session, which Gecko opens for the new window. */
    internal fun adoptPopupSession(): GeckoSession {
        val previous = session
        val created = newSession()
        session = created
        view.setSession(created)
        previous.close()
        return created
    }

    private fun sslKind(error: WebRequestError) = when (error.code) {
        WebRequestError.ERROR_SECURITY_BAD_CERT -> SslErrorRequest.Kind.UNTRUSTED
        else -> SslErrorRequest.Kind.INVALID
    }

    private fun loadErrorKind(error: WebRequestError) = when (error.code) {
        WebRequestError.ERROR_UNKNOWN_HOST -> LoadError.Kind.HOST_LOOKUP
        WebRequestError.ERROR_CONNECTION_REFUSED, WebRequestError.ERROR_LOCAL_NETWORK_ACCESS_DENIED -> LoadError.Kind.CONNECT
        WebRequestError.ERROR_NET_TIMEOUT -> LoadError.Kind.TIMEOUT
        WebRequestError.ERROR_NET_INTERRUPT, WebRequestError.ERROR_NET_RESET -> LoadError.Kind.IO
        WebRequestError.ERROR_UNKNOWN -> LoadError.Kind.UNKNOWN
        else -> LoadError.Kind.OTHER
    }

    private val progressDelegate = object : GeckoSession.ProgressDelegate {
        override fun onPageStart(session: GeckoSession, url: String) {
            favicon = null
            certificate = null
            progress = 0
            val logical = logicalUrl(url) ?: url
            this@GeckoPage.url = logical
            documentPending = true
            events.onPageStarted(logical)
        }

        override fun onPageStop(session: GeckoSession, success: Boolean) {
            progress = 100
            // The new document's port may have connected before this page start arrived; ports of unloaded
            // documents disconnect, so a live port is the current one. Without one (about:, error pages) the queue drops.
            if (documentPending) {
                documentPending = false
                flushQueue(port)
            }
            url?.let(events::onPageFinished)
        }

        override fun onProgressChange(session: GeckoSession, progress: Int) {
            this@GeckoPage.progress = progress
            events.onProgressChanged(progress)
        }

        override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) {
            certificate = securityInfo.certificate?.let { SslCertificate(it) }
        }

        override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
            this@GeckoPage.sessionState = sessionState
        }
    }

    private val content = object : GeckoSession.ContentDelegate {
        override fun onTitleChange(session: GeckoSession, title: String?) {
            this@GeckoPage.title = title
            if (title != null) events.onReceivedTitle(title)
        }

        override fun onCloseRequest(session: GeckoSession) = events.onCloseWindow()

        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
            if (fullScreen) enterFullscreen() else exitFullscreen()
        }

        override fun onContextMenu(session: GeckoSession, screenX: Int, screenY: Int, element: GeckoSession.ContentDelegate.ContextElement) {
            val handler = contextMenu?.takeIf { it.enabled } ?: return
            val src = element.srcUri?.takeIf { element.type != GeckoSession.ContentDelegate.ContextElement.TYPE_NONE }
            handler.onContextMenu(ContextTarget(element.linkUri, src, element.linkText ?: element.title,
                view.lastTouchRawX, view.lastTouchRawY))
        }

        override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
            val headers = response.headers
            fun header(name: String) = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
            events.onDownload(response.uri, userAgent, header("Content-Disposition"),
                header("Content-Type")?.substringBefore(';')?.trim(), header("Content-Length")?.toLongOrNull() ?: -1,
                response.body)
        }

        override fun onCrash(session: GeckoSession) = recover()
        override fun onKill(session: GeckoSession) = recover()
    }

    /** The content process died: reopen the session on its last known state. */
    private fun recover() {
        if (destroyed) return
        Log.w(TAG, "Content process ended; restoring $url")
        val previous = session
        val created = newSession()
        session = created
        created.open(runtime)
        view.setSession(created)
        previous.close()
        sessionState?.let(created::restoreState) ?: url?.let { load(it) }
    }

    private val scroll = object : GeckoSession.ScrollDelegate {
        override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
            this@GeckoPage.scrollX = scrollX
            this@GeckoPage.scrollY = scrollY
        }
    }

    // --- Fullscreen: the session moves to a separate view the host can place anywhere. ---

    private var fullscreenView: GeckoView? = null

    private fun enterFullscreen() {
        if (fullscreenView != null) return
        val target = GeckoView(context)
        view.releaseSession()
        target.setSession(session)
        fullscreenView = target
        events.onShowFullscreen(FullscreenRequest(target) { session.exitFullScreen() })
    }

    private fun exitFullscreen() {
        val target = fullscreenView ?: return
        fullscreenView = null
        events.onHideFullscreen()
        target.releaseSession()
        view.setSession(session)
    }

    // --- Scripts: one content-script port per top-level document. ---

    private var port: WebExtension.Port? = null
    private var bridge: PageBridge? = null
    private var scriptChannel: ScriptChannel? = null
    /** Ports for subframes share the page's bridge, but never receive page-wide evaluations. */
    private val framePorts = LinkedHashSet<WebExtension.Port>()
    /** Set from page start until the new document's port connects; evaluations wait for that document. */
    private var documentPending = false
    private val queue = mutableListOf<JSONObject>()
    private var nextEval = 1
    private val callbacks = HashMap<Int, (String) -> Unit>()

    override fun installBridges(via: PageBridge, scripts: ScriptChannel?) {
        bridge = via
        scriptChannel?.observeValues(null)
        scriptChannel = scripts
        scripts?.observeValues { script, name, value, oldValue, origin ->
            view.post {
                val message = JSONObject().put("type", "value").put("script", script)
                    .put("name", name).put("value", value ?: JSONObject.NULL)
                    .put("oldValue", oldValue ?: JSONObject.NULL)
                    .put("remote", origin !== scriptChannel)
                port?.postMessage(message)
                framePorts.toList().forEach { it.postMessage(message) }
            }
        }
    }

    override fun evaluate(script: String, callback: ((String) -> Unit)?) {
        val message = JSONObject().put("type", "eval").put("code", script)
        if (callback != null) {
            val id = nextEval++
            callbacks[id] = callback
            message.put("id", id)
        }
        val target = port
        if (target == null || documentPending) queue += message else target.postMessage(message)
    }

    /** Sends queued evaluations to [target], or answers them with "null" when the document has no port. */
    private fun flushQueue(target: WebExtension.Port?) {
        val pending = queue.toList()
        queue.clear()
        pending.forEach { message ->
            if (target != null) target.postMessage(message)
            else if (message.has("id")) callbacks.remove(message.getInt("id"))?.invoke("null")
        }
    }

    private val scripts = object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            val topLevel = port.sender.isTopLevel
            if (topLevel) {
                this@GeckoPage.port?.let { old -> if (old !== port) old.setDelegate(null) }
                this@GeckoPage.port = port
            } else framePorts += port
            port.setDelegate(portDelegate)
            port.postMessage(injectionPayload(port.sender.url, topLevel).put("type", "state"))
            if (topLevel) {
                documentPending = false
                flushQueue(port)
            }
        }
    }

    private val portDelegate = object : WebExtension.PortDelegate {
        override fun onPortMessage(message: Any, port: WebExtension.Port) {
            val json = message as? JSONObject ?: return
            val topLevel = port === this@GeckoPage.port
            if (!topLevel && port !in framePorts) return
            when (json.optString("type")) {
                "result" -> callbacks.remove(json.optInt("id"))?.invoke(json.optString("value", "null"))
                // Gecko content scripts execute the complete payload themselves. The native phase
                // callback is only for the top document's reader-mode bookkeeping.
                "phase" -> if (topLevel) events.onDocumentPhase(json.optInt("phase"))
                "touchIcon" -> if (topLevel) events.onReceivedTouchIconUrl(json.optString("url"))
                "icon" -> {
                    if (!topLevel) return
                    val data = json.optString("data")
                    if (data.length <= 700_000) runCatching {
                        val bytes = android.util.Base64.decode(data, android.util.Base64.DEFAULT)
                        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        val options = android.graphics.BitmapFactory.Options().apply {
                            inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 128).coerceAtLeast(1)
                        }
                        favicon = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        events.onReceivedIcon(favicon)
                    }
                }
                "cmd" -> bridge?.cmd(json.optInt("command"))
                "bridge" -> {
                    val via = bridge ?: return
                    val args = json.optJSONArray("args")
                    fun arg(index: Int): String? = args?.takeIf { index < it.length() && !it.isNull(index) }?.optString(index)
                    when (json.optString("method")) {
                        "download" -> via.download(arg(0), arg(1), arg(2))
                        "postMessage" -> via.postMessage(arg(0), arg(1))
                        "record" -> via.record(arg(0), arg(1))
                        "addon" -> via.addon(arg(0))
                        "toast" -> via.toast(arg(0))
                    }
                }
                // Asynchronous GM replies (XHR callbacks) go back to the frame that made the call.
                "gm" -> scriptChannel?.call(json.optString("message").takeIf { !json.isNull("message") },
                    json.optString("secret").takeIf { !json.isNull("secret") },
                    if (topLevel) null else { code -> view.post { if (port in framePorts) port.postMessage(JSONObject().put("type", "eval").put("code", code)) } })
            }
        }

        override fun onDisconnect(port: WebExtension.Port) {
            if (this@GeckoPage.port === port) this@GeckoPage.port = null
            framePorts.remove(port)
        }
    }

    /** Built before response headers are released, for synchronous document_start injection. */
    fun injectionPayload(url: String, mainFrame: Boolean = true): JSONObject = JSONObject().put("page", id)
        .put("commands", JSONObject().put("515", bridge?.cmd(515) ?: 0))
        .put("addons", bridge?.getInstalledAddonID() ?: "[]")
        .put("gm", scriptChannel?.snapshot(url)?.let(::JSONObject))
        .put("phases", JSONObject().apply {
            events.documentScripts(url, mainFrame).forEach { (phase, sources) -> put(phase.toString(), org.json.JSONArray(sources)) }
        })

    // --- Find ---

    override fun find(query: String, listener: ((Int, Int, Boolean) -> Unit)?) {
        val finder = session.finder
        if (query.isEmpty()) { finder.clear(); return }
        finder.displayFlags = GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL
        finder.find(query, 0).accept { result -> if (result != null) listener?.invoke(result.current - 1, result.total, true) }
        findListener = listener
    }

    private var findListener: ((Int, Int, Boolean) -> Unit)? = null

    override fun findNext(forward: Boolean) {
        session.finder.find(null, if (forward) 0 else GeckoSession.FINDER_FIND_BACKWARDS)
            .accept { result -> if (result != null) findListener?.invoke(result.current - 1, result.total, true) }
    }

    // --- Rendering and input ---

    override fun sampleTopLeftPixel(callback: (Int) -> Unit) {
        view.capturePixels().accept({ bitmap ->
            val color = bitmap?.let { runCatching { it[0, 0] }.getOrDefault(0).also { _ -> it.recycle() } } ?: 0
            callback(color)
        }, { callback(0) })
    }

    override val edgeGestureReady: Boolean get() = view.edgeGestureReady

    override fun canScrollHorizontally(direction: Int) = view.canScrollPageHorizontally(direction)

    override fun setSelectionActions(actions: List<SelectionAction>, onClick: ((Int, String) -> Unit)?) {
        val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .firstOrNull { it is Activity } as? Activity ?: return
        selectionActions = GeckoSelectionActions(activity, actions, onClick)
        session.selectionActionDelegate = selectionActions
    }

    override fun finishSelection() {
        selectionActions?.finish()
    }

    private var selectionActions: GeckoSelectionActions? = null

    private var contextMenu: ContextMenuHandler? = null

    override fun setContextMenuHandler(handler: ContextMenuHandler?) { contextMenu = handler }

    // Extensions see the selected tab as active (tabs.query, per-tab actions).
    override fun pause() {
        session.setActive(false)
        runtime.webExtensionController.setTabActive(session, false)
    }
    override fun resume() {
        session.setActive(true)
        runtime.webExtensionController.setTabActive(session, true)
    }

    override fun createPrintAdapter(title: String): PrintDocumentAdapter = GeckoPrintAdapter(title, session.saveAsPdf())

    override fun saveArchive(path: String, callback: (String?) -> Unit) = callback(null)

    // --- State ---

    override fun saveState(out: Bundle): Boolean {
        val state = sessionState ?: return false
        out.putString(KEY_STATE, state.toString())
        return true
    }

    override fun restoreState(state: Bundle): String? {
        val restored = state.getString(KEY_STATE)?.let { GeckoSession.SessionState.fromString(it) } ?: return null
        GeckoBackend.withExtension { if (!destroyed) session.restoreState(restored) }
        sessionState = restored
        return restored.getOrNull(restored.currentIndex)?.uri.orEmpty()
    }

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        scriptChannel?.observeValues(null)
        GeckoBackend.unregister(this)
        browserActions.clear()
        pageActions.clear()
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        port?.setDelegate(null)
        framePorts.forEach { it.setDelegate(null) }
        framePorts.clear()
        port = null
        queue.clear()
        callbacks.clear()
        view.releaseSession()
        session.close()
    }

    override fun equals(other: Any?) = other === this
    override fun hashCode() = System.identityHashCode(this)

    // --- Requests ---

    /** The top-level URL this page is about to load; matches the first request of a tab not yet registered. */
    @Volatile private var expectedDocument: String? = null

    fun expectsDocument(url: String) = expectedDocument == url

    sealed interface Interception {
        data object Allow : Interception
        data object Cancel : Interception
        class Redirect(val url: String) : Interception
    }

    /** Called on the request thread. */
    fun intercept(url: String, type: String): Interception {
        if (settings?.images == false && (type == "image" || type == "imageset")) return Interception.Cancel
        val mainFrame = type == "main_frame"
        val decision = events.onRequest(ResourceRequest(url, mainFrame, type = requestTypes[type] ?: ResourceRequest.Type.OTHER))
        return when (decision) {
            InterceptDecision.Allow -> Interception.Allow
            // Firefox content blockers cancel; the page sees a failed load instead of WebView's empty response.
            InterceptDecision.BlockEmpty, InterceptDecision.BlockImage -> Interception.Cancel
            is InterceptDecision.Serve -> {
                var body = runCatching { decision.open().use { it.readBytes() } }.getOrNull() ?: return Interception.Cancel
                if (mainFrame) {
                    // Gecko has no public response-body delegate. Load the generated response through
                    // the data loader; Via's page policy and history keep seeing the requested URL.
                    if (decision.mime.startsWith("text/html", true)) body = withBaseUrl(body, url)
                    view.post {
                        if (destroyed) return@post
                        generatedLogicalUrl = url
                        session.load(GeckoSession.Loader().data(body, decision.mime)
                            .flags(GeckoSession.LOAD_FLAGS_BYPASS_LOAD_URI_DELEGATE))
                    }
                    Interception.Cancel
                } else Interception.Redirect("data:${decision.mime};base64," + android.util.Base64.encodeToString(body, android.util.Base64.NO_WRAP))
            }
        }
    }

    /** Maps the data: document of a generated response to its logical URL; any other document ends that mapping. */
    private fun logicalUrl(actual: String?): String? {
        val logical = generatedLogicalUrl
        if (logical != null && actual?.startsWith("data:", ignoreCase = true) == true) return logical
        generatedLogicalUrl = null
        return actual
    }

    private fun withBaseUrl(body: ByteArray, url: String): ByteArray {
        val source = body.toString(Charsets.UTF_8)
        if (source.contains("<base", ignoreCase = true)) return body
        val escaped = url.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
        val base = "<base href=\"$escaped\">"
        val head = Regex("<head\\b[^>]*>", RegexOption.IGNORE_CASE).find(source)
        val result = if (head != null) source.substring(0, head.range.last + 1) + base + source.substring(head.range.last + 1)
        else base + source
        return result.toByteArray(Charsets.UTF_8)
    }

    // Last, so every delegate above is initialized before the first session uses it.
    init {
        session = newSession().also { it.open(runtime); view.setSession(it) }
        GeckoBackend.register(this)
    }

    companion object {
        private const val TAG = "ViaGecko"

        /** webRequest.ResourceType names. */
        private val requestTypes = mapOf(
            "main_frame" to ResourceRequest.Type.DOCUMENT, "sub_frame" to ResourceRequest.Type.SUBDOCUMENT,
            "script" to ResourceRequest.Type.SCRIPT, "stylesheet" to ResourceRequest.Type.STYLESHEET,
            "image" to ResourceRequest.Type.IMAGE, "imageset" to ResourceRequest.Type.IMAGE,
            "media" to ResourceRequest.Type.MEDIA, "font" to ResourceRequest.Type.FONT,
            "xmlhttprequest" to ResourceRequest.Type.XHR, "websocket" to ResourceRequest.Type.WEBSOCKET,
        )
        private const val KEY_STATE = "gecko_session_state"
    }
}
