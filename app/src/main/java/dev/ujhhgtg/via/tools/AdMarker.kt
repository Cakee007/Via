package dev.ujhhgtg.via.tools

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.webkit.WebView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.browser.filter.FilterStore
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** i6.a/b, f8.q, c8.s6.q9/u: script selection, original overlay actions, cosmetic rule persistence. */
class AdMarker(private val activity: Activity, private val onRulesChanged: () -> Unit) {
    private class Session(view: WebView, val secret: String) { val webView = WeakReference(view) }
    private val sessions = WeakHashMap<WebView, Session>()
    private var active: Session? = null
    private var panel = WeakReference<AdMarkerFragment>(null)
    private var selection = ""
    private var lastStart = 0L
    val isActive get() = active != null
    var presentPanel: (() -> Unit)? = null
    var dismissPanel: (() -> Unit)? = null
    private val script by lazy { activity.assets.open("tools/ad-marker.js").bufferedReader().use { it.readText() } }

    fun attach(view: WebView, secret: String) { if (sessions[view]?.secret != secret) sessions[view] = Session(view, secret) }
    fun handleMessage(view: WebView, message: JSONObject): Boolean {
        val session = sessions[view] ?: return false
        val action = message.optInt("action")
        if (action != 102 && action != 103) return false
        view.post {
            if (active !== session) return@post
            if (action == 102) { selection = message.optString("filter"); panel.get()?.updateSelection(selection) }
            else {
                val host = message.optString("host")
                val filter = message.optString("filter")
                if (host.isNotEmpty() && filter.isNotEmpty()) confirm(session, host, filter)
            }
        }
        return true
    }

    fun start(view: WebView): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastStart < 300) return false
        lastStart = now
        val url = view.url.orEmpty()
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return reject(R.string.cannot_work)
        if (!view.settings.javaScriptEnabled) return reject(R.string.cannot_work_javascript_is_blocked)
        val session = sessions[view] ?: return false
        if (active !== session) disableScript()
        active = session; selection = ""
        view.evaluateJavascript(script.replace("\"__VIA_MARKER_SECRET__\"", JSONObject.quote(session.secret)), null)
        view.evaluateJavascript("try{window.__setMarkerEnabled(!0)}catch(a){}", null)
        presentPanel?.invoke()
        return true
    }

    internal fun bindPanel(fragment: AdMarkerFragment) { panel = WeakReference(fragment); fragment.updateSelection(selection) }
    internal fun panelDestroyed(fragment: AdMarkerFragment) {
        if (panel.get() === fragment) { panel.clear(); disableScript() }
    }
    fun expand() = evaluate("try{window.__markParent()}catch(a){}")
    fun shrink() = evaluate("try{window.__markChild()}catch(a){}")
    fun requestRule() = evaluate("try{window.__getMarkerFilter()}catch(r){}")
    fun copySelection() {
        if (selection.isEmpty()) return
        (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(null, selection))
        ViaToast.show(activity, R.string.toast_copy_text_successful)
    }
    private fun evaluate(script: String) { active?.webView?.get()?.evaluateJavascript(script, null) }
    private fun disableScript() { evaluate("try{window.__setMarkerEnabled(!1)}catch(a){}"); active = null; selection = "" }
    fun onPageStarted(view: WebView) { if (active?.webView?.get() === view) close() }
    fun onBackPressed(): Boolean = if (active == null) false else { close(); true }
    fun close() { if (active == null) return; disableScript(); dismissPanel?.invoke() }

    private fun confirm(session: Session, host: String, selector: String) {
        val rule = rule(host, selector) ?: return
        ViaDialog(activity).title(R.string.action_mark).message(activity.getString(R.string.dialog_confirm_mark) + "($selector)")
            .negative(android.R.string.cancel).positive(android.R.string.ok) { _, _ ->
                FilterStore(activity).appendCustom(rule)
                onRulesChanged()
                val view = session.webView.get()
                close()
                val escaped = selector.replace("\"", "\\\"")
                view?.evaluateJavascript("(function(){var e=document.getElementById(\"__via__marker_temp__\");if(e)e.innerText+=\"" + escaped + "{display:none !important}\";else{(e=document.createElement(\"style\")).type=\"text/css\";e.charset=\"UTF-8\";e.id=\"__via__marker_temp__\";e.appendChild(document.createTextNode(\"" + escaped + "{display:none !important}\"));document.head.appendChild(e)}})();", null)
            }.show()
    }
    private fun reject(resource: Int): Boolean { ViaToast.show(activity, resource); return false }
    companion object {
        fun rule(hostOrUrl: String, selector: String): String? {
            if (hostOrUrl.isEmpty() || selector.isEmpty()) return null
            var host = hostOrUrl.substringAfter("://")
            val slash = host.indexOf('/')
            if (slash > 0) host = host.substring(0, slash)
            val port = host.lastIndexOf(':')
            if (port > 0) host = host.substring(0, port)
            return host.takeIf(String::isNotEmpty)?.let { "$it##${selector.trim()}" }
        }
    }
}
