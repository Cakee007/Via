package dev.ujhhgtg.via.engine.webview

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import dev.ujhhgtg.via.engine.InterceptDecision
import dev.ujhhgtg.via.engine.ResourceRequest
import java.io.ByteArrayInputStream

/** Maps WebView requests to [ResourceRequest]s and [InterceptDecision]s to WebView responses. */
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

    fun resourceRequest(request: WebResourceRequest) =
        ResourceRequest(request.url.toString(), request.isForMainFrame, request.requestHeaders ?: emptyMap())
}
