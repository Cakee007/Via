package dev.ujhhgtg.via.engine.gecko

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.net.toUri
import dev.ujhhgtg.via.engine.BrowserBackend
import dev.ujhhgtg.via.engine.Capabilities
import dev.ujhhgtg.via.engine.CookieAccess
import dev.ujhhgtg.via.engine.EngineConfig
import dev.ujhhgtg.via.engine.EnginePage
import dev.ujhhgtg.via.engine.PageEvents
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/** GeckoView: one runtime per process, with Via's built-in extension for scripts and page bridges. */
object GeckoBackend : BrowserBackend {
    override val id = "gecko"

    override val capabilities = Capabilities(
        syncScriptBridge = false,
        syncGmXhr = true,
        mhtArchive = false,
        sslProceed = false,
        perPageTextZoom = false,
        algorithmicDarkening = false,
        userAgentMetadata = false,
        quickBackSegments = false,
        webExtensions = true,
    )

    private const val TAG = "ViaGecko"
    internal const val EXTENSION_ID = "engine@via.ujhhgtg.dev"
    private const val EXTENSION_URI = "resource://android/assets/via-engine/"
    internal const val NATIVE_APP = "via"

    private val main = Handler(Looper.getMainLooper())
    private var runtime: GeckoRuntime? = null
    private var extension: WebExtension? = null
    private var backgroundReady = false
    private val extensionWaiters = mutableListOf<(WebExtension) -> Unit>()

    override val engineMajorVersion: Int
        get() = Regex("Firefox/(\\d+)").find(GeckoSession.getDefaultUserAgent())?.groupValues?.get(1)?.toIntOrNull() ?: 0

    override fun isAvailable(): Boolean = true

    override fun versionInfo(): List<Pair<String, String>> =
        listOf("GeckoView Version" to (Regex("Firefox/([\\d.]+)").find(GeckoSession.getDefaultUserAgent())?.groupValues?.get(1) ?: "?"))

    /** Gecko child processes run Application.onCreate too; GeckoView sets them up itself. */
    override fun onProcessStart(app: Application, processName: String?, isMainProcess: Boolean) = Unit

    override fun defaultUserAgent(context: Context): String = GeckoSession.getDefaultUserAgent()

    override fun createPage(context: Context, events: PageEvents): EnginePage = GeckoPage(context, runtime(context), events)

    /** The process runtime, created on first use. Main thread only. */
    internal fun runtime(context: Context): GeckoRuntime = runtime ?: GeckoRuntime.create(
        context.applicationContext,
        GeckoRuntimeSettings.Builder()
            .consoleOutput(true)
            .aboutConfigEnabled(true)
            // addons.mozilla.org's "Add to Firefox" uses navigator.mozAddonManager.
            .extensionsWebAPIEnabled(true)
            .build(),
    ).also { created ->
        runtime = created
        GeckoExtensions.attach(created)
        created.webExtensionController.ensureBuiltIn(EXTENSION_URI, EXTENSION_ID).accept({ installed ->
            if (installed == null) return@accept
            installed.setMessageDelegate(background, NATIVE_APP)
            extension = installed
            extensionReady()
        }, { error -> Log.e(TAG, "Cannot install the built-in extension", error) })
    }

    /** Calls [block] with the built-in extension once it is installed. Main thread only. */
    internal fun withExtension(block: (WebExtension) -> Unit) {
        val installed = extension
        if (installed != null && backgroundReady) block(installed) else extensionWaiters.add(block)
    }

    private fun extensionReady() {
        val installed = extension ?: return
        if (!backgroundReady) return
        val waiting = extensionWaiters.toList()
        extensionWaiters.clear()
        waiting.forEach { it(installed) }
    }

    internal fun applyConfig(config: EngineConfig) {
        applyColorScheme(config.darkening)
        val settings = runtime?.settings ?: return
        if (settings.remoteDebuggingEnabled != config.remoteDebugging) settings.remoteDebuggingEnabled = config.remoteDebugging
        val cookies = when {
            !config.acceptCookies -> ContentBlocking.CookieBehavior.ACCEPT_NONE
            config.thirdPartyCookies -> ContentBlocking.CookieBehavior.ACCEPT_ALL
            else -> ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY
        }
        val blocking = settings.contentBlocking
        if (blocking.cookieBehavior != cookies) blocking.cookieBehavior = cookies
        val safeBrowsing = if (config.safeBrowsing) ContentBlocking.SafeBrowsing.DEFAULT else ContentBlocking.SafeBrowsing.NONE
        if (blocking.safeBrowsingCategories != safeBrowsing) blocking.setSafeBrowsing(safeBrowsing)
    }

    internal fun applyColorScheme(dark: Boolean) {
        runtime?.settings?.preferredColorScheme = if (dark) GeckoRuntimeSettings.COLOR_SCHEME_DARK else GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
    }

    /** Text zoom is runtime-wide in Gecko. */
    internal fun applyTextZoom(percent: Int) {
        val settings = runtime?.settings ?: return
        val factor = percent / 100f
        if (settings.fontSizeFactor != factor) settings.fontSizeFactor = factor
    }

    // Background-script port: requests that need extension APIs, such as cookies.
    private var backgroundPort: WebExtension.Port? = null
    private val nextRequest = AtomicInteger(1)
    private val pendingReplies = HashMap<Int, (Any?) -> Unit>()

    private val background = object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            backgroundPort = port
            port.setDelegate(object : WebExtension.PortDelegate {
                override fun onPortMessage(message: Any, port: WebExtension.Port) {
                    val json = message as? JSONObject ?: return
                    when (json.optString("type")) {
                        "ready" -> { backgroundReady = true; extensionReady() }
                        "reply" -> pendingReplies.remove(json.optInt("id"))?.invoke(json.opt("value")?.takeUnless { it == JSONObject.NULL })
                        "request", "injection" -> decide(json, port)
                    }
                }
                override fun onDisconnect(port: WebExtension.Port) {
                    if (backgroundPort === port) { backgroundPort = null; backgroundReady = false }
                    pendingReplies.values.toList().forEach { it(null) }
                    pendingReplies.clear()
                }
            })
        }
    }

    // Pages by id, for attributing requests the background script sees by tab.
    private val pages = HashMap<Int, java.lang.ref.WeakReference<GeckoPage>>()
    private val decisions = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "ViaGeckoRequests") }

    internal fun register(page: GeckoPage) { pages[page.id] = java.lang.ref.WeakReference(page) }
    internal fun unregister(page: GeckoPage) { pages.remove(page.id) }
    internal fun livePages(): List<GeckoPage> = pages.values.mapNotNull { it.get() }

    override val extensions: dev.ujhhgtg.via.engine.ExtensionManager get() = GeckoExtensions

    /**
     * Decides one request off the main thread. A tab's first document arrives before its content script
     * registered the tab, so an unknown tab is matched by the top-level URL a page is about to load.
     */
    private fun decide(request: JSONObject, port: WebExtension.Port) {
        val url = request.optString("url")
        val type = request.optString("requestType")
        val page = pages[request.optInt("page", -1)]?.get()
            ?: pages.values.mapNotNull { it.get() }.lastOrNull { type == "main_frame" && it.expectsDocument(url) }
        val reply = JSONObject().put("type", "decision").put("id", request.optInt("id")).put("page", page?.id ?: -1)
        if (page == null) { port.postMessage(reply.put("action", "allow")); return }
        if (request.optString("type") == "injection") {
            reply.put("payload", page.injectionPayload(url))
            port.postMessage(reply)
            return
        }
        decisions.execute {
            val decision = runCatching { page.intercept(url, type) }.getOrElse { error ->
                Log.w(TAG, "Request decision failed for $url", error)
                GeckoPage.Interception.Allow
            }
            when (decision) {
                GeckoPage.Interception.Allow -> reply.put("action", "allow")
                GeckoPage.Interception.Cancel -> reply.put("action", "cancel")
                is GeckoPage.Interception.Redirect -> reply.put("action", "redirect").put("url", decision.url)
            }
            main.post { port.postMessage(reply) }
        }
    }

    /** Sends [message] to the background script and passes its reply value (or null) to [reply] on the main thread. */
    private fun askBackground(message: JSONObject, reply: (Any?) -> Unit) = main.post {
        val port = backgroundPort ?: return@post reply(null)
        val id = nextRequest.getAndIncrement()
        pendingReplies[id] = reply
        port.postMessage(message.put("id", id))
    }

    private fun storage(context: Context): StorageController = runtime(context).storageController

    private fun clear(context: Context, flags: Long) { main.post { storage(context).clearData(flags) } }

    override fun clearCache(context: Context) = clear(context, StorageController.ClearFlags.ALL_CACHES)
    override fun clearFormData(context: Context) = clear(context, StorageController.ClearFlags.AUTH_SESSIONS)
    override fun clearStorage(context: Context) = clear(context, StorageController.ClearFlags.DOM_STORAGES)
    override fun clearCookies(context: Context) =
        clear(context, StorageController.ClearFlags.COOKIES or StorageController.ClearFlags.AUTH_SESSIONS)
    override fun clearLocationPermissions() {
        main.post { runtime?.storageController?.clearData(StorageController.ClearFlags.PERMISSIONS) }
    }

    override val cookies: CookieAccess = object : CookieAccess {
        override suspend fun get(url: String): String? =
            suspendCancellableCoroutine { continuation ->
                askBackground(
                    JSONObject().put("type", "cookies").put("url", url)
                ) { value -> continuation.resume(value as? String) }
            }

        override fun flush() = Unit

        override suspend fun clearForSite(context: Context, url: String): Boolean {
            val host = url.toUri().host?.takeIf(String::isNotEmpty) ?: return false
            return suspendCancellableCoroutine { continuation ->
                main.post {
                    storage(context).clearDataFromBaseDomain(
                        host,
                        StorageController.ClearFlags.COOKIES
                    )
                        .accept({ continuation.resume(true) }, { continuation.resume(false) })
                }
            }
        }
    }

    internal fun <T> result(value: T): GeckoResult<T> = GeckoResult.fromValue(value)

    /** Delegate work that reads Via's databases, such as visited-link queries. */
    internal val historyExecutor: java.util.concurrent.Executor =
        java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "ViaGeckoHistory") }
}
