package dev.ujhhgtg.via.engine.webview

import android.webkit.JavascriptInterface
import android.webkit.WebView
import dev.ujhhgtg.via.engine.PageBridge
import dev.ujhhgtg.via.engine.ScriptChannel

/** Exposes the engine-neutral page bridges to WebView JavaScript through addJavascriptInterface. */
internal object WebViewBridges {
    private class Via(private val bridge: PageBridge) {
        @JavascriptInterface fun cmd(command: Int): Int = bridge.cmd(command)
        @JavascriptInterface fun download(token: String?, url: String?, data: String?) = bridge.download(token, url, data)
        @JavascriptInterface fun postMessage(token: String?, json: String?) = bridge.postMessage(token, json)
        @JavascriptInterface fun record(url: String?, selector: String?) = bridge.record(url, selector)
        @JavascriptInterface fun addon(id: String?) = bridge.addon(id)
        @JavascriptInterface fun getInstalledAddonID(): String = bridge.getInstalledAddonID()
        @JavascriptInterface fun toast(text: String?) = bridge.toast(text)
    }

    private class Gm(private val bridge: ScriptChannel) {
        @JavascriptInterface fun call(message: String?, secret: String?): String? = bridge.call(message, secret)
    }

    fun install(webView: WebView, via: PageBridge, scripts: ScriptChannel?) {
        webView.removeJavascriptInterface("searchBoxJavaBridge_")
        webView.removeJavascriptInterface("accessibility")
        webView.removeJavascriptInterface("accessibilityTraversal")
        scripts?.let {
            webView.addJavascriptInterface(Gm(it), "via_gm")
            it.observeValues { script, name, value, oldValue, origin ->
                val code = it.valueChangeScript(script, name, value, oldValue, origin !== it) ?: return@observeValues
                webView.post { webView.evaluateJavascript(code, null) }
            }
        }
        val page = Via(via)
        webView.addJavascriptInterface(page, "via")
        webView.addJavascriptInterface(page, "via_page")
    }
}
