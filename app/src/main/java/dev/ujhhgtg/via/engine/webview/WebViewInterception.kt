package dev.ujhhgtg.via.engine.webview

import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import dev.ujhhgtg.via.browser.filter.FilterRequest
import dev.ujhhgtg.via.browser.filter.FilterRule
import dev.ujhhgtg.via.browser.filter.ResourceType
import dev.ujhhgtg.via.engine.InterceptDecision
import java.io.ByteArrayInputStream

/** Maps [InterceptDecision]s to WebView responses, and WebView requests to filter requests. */
internal object WebViewInterception {
    // u4.a.c/d are process-wide response objects, including their streams.
    // Recreating the GIF for every hit changes later blocked documents into image pages.
    private val emptyBlockedResponse = WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(byteArrayOf()))
    private val imageBlockedResponse = WebResourceResponse("image/gif", "UTF-8", ByteArrayInputStream(
        android.util.Base64.decode("R0lGODlhAQABAID/AMDAwAAAACH5BAEAAAAALAAAAAABAAEAAAICRAEAOw==", android.util.Base64.DEFAULT)))

    fun response(decision: InterceptDecision): WebResourceResponse? = when (decision) {
        InterceptDecision.Allow -> null
        InterceptDecision.BlockEmpty -> emptyBlockedResponse
        InterceptDecision.BlockImage -> imageBlockedResponse
        is InterceptDecision.Serve -> runCatching {
            WebResourceResponse(decision.mime, "UTF-8", decision.open()).apply {
                if (decision.headers.isNotEmpty()) responseHeaders = decision.headers
            }
        }.getOrNull()
    }

    /** b5.c.j/l: one request can carry document/WebSocket/XHR and content-type bits. */
    fun filterRequest(request: WebResourceRequest, topUrl: String?): FilterRequest {
        val target = request.url.toString()
        val headers = request.requestHeaders ?: emptyMap()
        val main = request.isForMainFrame && target == topUrl
        var mask = if (main) 512 else 0
        if (target.startsWith("ws")) mask = mask or 8192
        if (headers["X-Requested-With"] == "XMLHttpRequest") mask = mask or 16384
        val filename = target.substringBefore('?').substringAfterLast('/')
        val extension = filename.indexOf('.').takeIf { it > 0 }?.let { filename.substring(it + 1).lowercase() }?.takeIf { it.length <= 8 }
        fun mimeMask(mime: String?): Int = when {
            mime in listOf("application/javascript", "application/x-javascript", "text/javascript", "application/json") -> 32
            mime == "text/css" -> 128
            mime?.startsWith("image/") == true -> 64
            mime?.startsWith("video/") == true || mime?.startsWith("audio/") == true -> 1024
            mime?.startsWith("font/") == true -> 2048
            else -> 16
        }
        val known = when (extension) {
            "js", "json" -> 32
            "css" -> 128
            "otf", "ttf", "ttc", "woff", "woff2" -> 2048
            "php", null -> null
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.let(::mimeMask)
        }
        val content = known ?: if (main) 16 else headers["Accept"]?.takeUnless { it == "*/*" }?.substringBefore(',')?.trim()?.let(::mimeMask) ?: 3312
        mask = mask or content
        val type = ResourceType.entries.firstOrNull { it.mask == content } ?: if (main) ResourceType.DOCUMENT else ResourceType.OTHER
        val sourceHost = FilterRule.host(topUrl)
        val resourceHost = FilterRule.host(target)
        return FilterRequest(target, topUrl, type, request.isForMainFrame,
            sourceHost != null && resourceHost != null && resourceHost != sourceHost && !resourceHost.contains(FilterRule.baseDomain(sourceHost)), headers, mask)
    }
}
