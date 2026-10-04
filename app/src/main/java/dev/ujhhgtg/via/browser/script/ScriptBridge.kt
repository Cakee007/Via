package dev.ujhhgtg.via.browser.script

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.lang.ref.WeakReference
import org.json.JSONObject

/** o5.a: both call secrets and each native operation's original grant mask are checked. */
class ScriptBridge(
    private val manager: ScriptManager,
    view: WebView,
    private val callbacks: Callbacks,
) {
    private val webView = WeakReference(view)

    interface Callbacks {
        fun onDownload(url: String, name: String)
        fun onOpenTab(url: String, active: Boolean, insert: Int)
        fun onCopy(text: String, mimeType: String) = Unit
    }

    @JavascriptInterface
    fun call(message: String?, secret: String?): String? {
        if (secret.isNullOrEmpty() || secret != manager.secret || message.isNullOrEmpty()) return null
        return runCatching {
            val request = JSONObject(message)
            if (request.optString("secret") != manager.secret) return null
            val id = request.optString("identifier")
            val name = request.optString("name")
            if (id.isEmpty() || name.isEmpty()) return null
            val args = request.optJSONObject("arguments") ?: JSONObject()
            val script = manager.findByScriptId(id)
            fun allowed(mask: Int): Boolean = script != null && script.grantMask() and mask != 0
            when (name) {
                "info" -> info(script)
                "getValue" -> if (allowed(2097154)) manager.getValue(id, args.optString("name")) ?: args.optString("value", "undefined") else "undefined"
                "setValue" -> { if (allowed(8388609)) manager.setValue(id, args.optString("name"), args.optString("value", "undefined")); null }
                "deleteValue" -> { if (allowed(1048584)) manager.deleteValue(id, args.optString("name")); null }
                "listValues" -> if (allowed(4194308)) manager.listValues(id).joinToString(",") else null
                "getResourceText" -> if (allowed(32)) manager.resourceText(script!!, args.optString("resource")) ?: "undefined" else null
                "getResourceURL" -> if (allowed(67108880)) manager.resourceUrl(script!!, args.optString("resource")) ?: "undefined" else null
                "log" -> { if (allowed(512)) Log.d("ViaUserscript", "$id: ${args.optString("message")}"); null }
                "setClipboard" -> {
                    if (allowed(1073742848)) webView.get()?.post {
                        val text = args.optString("data")
                        val type = args.optString("type").takeUnless { it.isEmpty() || it == "undefined" || it == "null" } ?: "text/plain"
                        if (!type.startsWith("text/") && !type.equals("text", true)) return@post
                        val context = webView.get()?.context ?: return@post
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Via", text))
                        callbacks.onCopy(text, type)
                    }
                    null
                }
                "download" -> { if (allowed(2048)) webView.get()?.post { callbacks.onDownload(args.optString("url"), args.optString("name")) }; null }
                "openInTab" -> {
                    if (allowed(268468224)) {
                        val options = runCatching { JSONObject(args.optString("options")) }.getOrDefault(JSONObject())
                        webView.get()?.post { callbacks.onOpenTab(args.optString("url"), options.optBoolean("active", false), options.optInt("insert", -1)) }
                    }
                    null
                }
                "xmlhttpRequest" -> {
                    if (!allowed(-2147483392)) "" else {
                        webView.get()?.let { ScriptHttpRequest(it, args.optString("details")).start() }
                        null
                    }
                }
                "isInstalled" -> {
                    val nameArg = args.optString("name")
                    val namespace = args.optString("namespace")
                    val installed = manager.findByScriptId(UserScript.stableId(namespace, nameArg))
                    JSONObject().put("name", nameArg).put("installed", installed != null).apply {
                        installed?.let { put("enabled", it.enabled); put("version", it.version) }
                    }.toString()
                }
                "openOptions" -> null // o5.a.n only logs this request in the original.
                else -> null
            }
        }.getOrNull()
    }

    private fun info(script: UserScript?): String = JSONObject()
        .put("scriptHandler", "Via").put("version", "2.0.0").put("scriptWillUpdate", false)
        .put("script", JSONObject().apply {
            script?.let {
                put("version", it.version); put("downloadURL", it.downloadUrl); put("homepage", it.homepageUrl)
                put("lastModified", it.lastUpdatedAt); put("name", it.name); put("supportURL", it.supportUrl)
            }
        }).toString()
}
