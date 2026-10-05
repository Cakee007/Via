package dev.ujhhgtg.via.engine.webview

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.view.View
import android.webkit.CookieManager
import android.webkit.CookieSyncManager
import android.webkit.GeolocationPermissions
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewFeature
import dev.ujhhgtg.via.engine.BrowserBackend
import dev.ujhhgtg.via.engine.CookieAccess
import dev.ujhhgtg.via.engine.EnginePage
import dev.ujhhgtg.via.engine.PageEvents
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File

/** System WebView: the default backend. Owns every process-global android.webkit call. */
object WebViewBackend : BrowserBackend {
    override val id = "webview"

    override val capabilities = dev.ujhhgtg.via.engine.Capabilities()

    override val engineMajorVersion: Int
        get() = WebView.getCurrentWebViewPackage()?.versionName?.substringBefore('.', "")?.toIntOrNull() ?: 0

    /** w9.r.f: the loaded WebView package is missing or has no name. */
    override fun isAvailable(): Boolean = !WebView.getCurrentWebViewPackage()?.packageName.isNullOrEmpty()

    override fun versionInfo(): List<Pair<String, String>> = WebView.getCurrentWebViewPackage()?.let {
        listOf("WebView Impl" to it.packageName, "WebView Version" to "${it.versionName}(${it.longVersionCode})")
    }.orEmpty()

    /** The WebView provider package, for looking up its own string resources. */
    val providerPackage: String? get() = WebView.getCurrentWebViewPackage()?.packageName

    override fun onProcessStart(app: Application, processName: String?, isMainProcess: Boolean) {
        // BrowserApp.k leaves the main process on the normal app_webview profile.
        if (!isMainProcess) { if (!processName.isNullOrEmpty()) WebView.setDataDirectorySuffix(processName); return }
        // Preserve the profile written by the earlier reconstruction's unconditional suffix.
        val previous = File(app.applicationInfo.dataDir, "app_webview_${app.packageName}")
        val current = File(app.applicationInfo.dataDir, "app_webview")
        if (previous.isDirectory && !current.exists()) runCatching { previous.copyRecursively(current) }
    }

    override fun defaultUserAgent(context: Context): String = WebSettings.getDefaultUserAgent(context)

    /** Applies the common WebView policy recovered from Via's `s4.b.f` and `e8.i`. */
    @SuppressLint("SetJavaScriptEnabled", "WebSettingsDeprecated")
    override fun createPage(context: Context, events: PageEvents): EnginePage {
        val webView = GestureWebView(context).apply {
            id = View.generateViewId()
            setBackgroundColor(0)
            isFocusable = true
            isScrollbarFadingEnabled = true
            isSaveEnabled = true
            scrollBarSize = 10
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
        }
        webView.settings.apply {
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
            setGeolocationEnabled(true)
            @Suppress("DEPRECATION")
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        webView.webViewClient = PageWebViewClient(events)
        webView.webChromeClient = PageChromeClient(events)
        webView.setDownloadListener { url, userAgent, disposition, mime, size ->
            events.onDownload(url, userAgent, disposition, mime, size)
        }
        return WebViewPage.of(webView)
    }

    override fun clearCache(context: Context) {
        WebView(context).apply { clearCache(true); clearSslPreferences(); destroy() }
        arrayOf("app_webview/Default/Service Worker/CacheStorage", "app_webview/Default/Service Worker/ScriptCache",
            "app_webview/Default/GPUCache", "app_webview/BrowserMetrics").forEach { relative ->
            File(context.dataDir, relative).deleteRecursively()
        }
    }

    @Suppress("DEPRECATION")
    override fun clearFormData(context: Context) = WebViewDatabase.getInstance(context).clearFormData()

    override fun clearStorage(context: Context) = WebStorage.getInstance().deleteAllData()

    @Suppress("DEPRECATION")
    override fun clearCookies(context: Context) {
        WebViewDatabase.getInstance(context).apply { clearFormData(); clearHttpAuthUsernamePassword() }
        CookieSyncManager.createInstance(context)
        CookieManager.getInstance().apply { removeAllCookies(null); flush() }
    }

    override fun clearLocationPermissions() = GeolocationPermissions.getInstance().clearAll()

    override val cookies: CookieAccess = object : CookieAccess {
        override suspend fun get(url: String): String? = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
        override fun flush() = CookieManager.getInstance().flush()

        /** z8.b0.t/p/q/r/u: delete from Chromium's cookie DB, else expire every visible cookie. */
        override suspend fun clearForSite(context: Context, url: String): Boolean {
            val success = clearCookieDatabase(context, url) || expireCookies(url)
            if (success) flush()
            return success
        }
    }

    private fun clearCookieDatabase(context: Context, url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        val domains = ArrayList<String>()
        var host = parsed.host
        val root = parsed.topPrivateDomain()
        while (true) {
            domains += host; domains += ".$host"
            if (host == root) break
            val dot = host.indexOf('.')
            if (root == null || dot < 0) break
            host = host.substring(dot + 1)
        }
        val file = File(context.dataDir, "app_webview/Default/Cookies")
        return file.isFile && runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
                database.beginTransaction()
                try { domains.forEach { database.delete("cookies", "host_key = ?", arrayOf(it)) }; database.setTransactionSuccessful() } finally { database.endTransaction() }
            }
            true
        }.getOrDefault(false)
    }

    private fun expireCookies(url: String): Boolean = runCatching {
        val parsed = url.toHttpUrlOrNull() ?: return false
        val paths = ArrayList<String>().apply {
            add(""); var path = ""
            parsed.encodedPathSegments.forEach { path += "/$it"; add(path) }
        }
        val domains = ArrayList<String>().apply {
            var host = parsed.host; add(host)
            parsed.topPrivateDomain()?.let { root -> while (host != root) { host = host.substring(host.indexOf('.') + 1); add(host) } }
            add("")
        }
        val manager = CookieManager.getInstance()
        val infos = if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) CookieManagerCompat.getCookieInfo(manager, url)
            else manager.getCookie(url)?.takeIf(String::isNotEmpty)?.split(';').orEmpty()
        infos.forEach { info ->
            val parts = info.split(';').map { it.split('=', limit = 2).let { pair -> pair[0].trim() to pair.getOrNull(1)?.trim() } }
            val name = parts.first().first
            val path = parts.firstOrNull { it.first.equals("path", true) }
            val domain = parts.firstOrNull { it.first.equals("domain", true) }
            if (path == null || domain == null) domains.forEach { host -> paths.forEach { route -> manager.setCookie(url, "$name=;Domain=$host;Path=$route;Max-Age=0", null) } }
            else {
                manager.setCookie(url, "$name=;Path=${path.second};Max-Age=0", null)
                manager.setCookie(url, "$name=;Domain=${domain.second};Path=${path.second};Max-Age=0", null)
            }
        }
        true
    }.getOrDefault(false)
}
