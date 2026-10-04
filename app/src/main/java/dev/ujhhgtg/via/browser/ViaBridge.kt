package dev.ujhhgtg.via.browser

import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.util.UUID

/** c8.s6.u/e8.i: the page-facing Via bridge is installed before navigation. */
class ViaBridge(private val view: WebView, private val callbacks: Callbacks, val secret: String = UUID.randomUUID().toString()) {
    interface Callbacks {
        fun command(webView: WebView, command: Int): Int = 0
        fun download(webView: WebView, url: String, name: String?, mime: String?) = Unit
        fun message(webView: WebView, token: String, json: String) = Unit
        fun record(webView: WebView, url: String, mime: String?) = Unit
        fun toast(webView: WebView, text: String) = Unit
        fun addon(webView: WebView, id: String) = Unit
        fun installedAddonIds(webView: WebView): String = "[]"
    }
    @JavascriptInterface fun cmd(command: Int): Int = callbacks.command(view, command)
    /** c8.s6.u.download(secret, sourceUrl, downloadedData), not (url, filename, MIME). */
    @JavascriptInterface fun download(token: String?, url: String?, data: String?) {
        if (token == secret && !url.isNullOrEmpty()) callbacks.download(view, url, null, data)
    }
    @JavascriptInterface fun postMessage(token: String?, json: String?) { if (token == secret && !json.isNullOrBlank()) callbacks.message(view, token, json) }
    @JavascriptInterface fun record(url: String?, selector: String?) { if (!url.isNullOrEmpty() && !url.startsWith("file://")) callbacks.record(view, url, selector) }
    @JavascriptInterface fun addon(id: String?) { if (!id.isNullOrBlank()) callbacks.addon(view, id) }
    @JavascriptInterface fun getInstalledAddonID(): String = callbacks.installedAddonIds(view)
    @JavascriptInterface fun toast(text: String?) { if (!text.isNullOrBlank()) callbacks.toast(view, text) }
}
