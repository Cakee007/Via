package dev.ujhhgtg.via.engine.webview

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.graphics.createBitmap
import dev.ujhhgtg.via.engine.FullscreenRequest
import dev.ujhhgtg.via.engine.JsDialogRequest
import dev.ujhhgtg.via.engine.PageEvents

/** Translates WebView client callbacks into [PageEvents]. */
internal class PageWebViewClient(private val events: PageEvents) : WebViewClient() {
    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        events.onPageStarted(url)
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView, url: String) {
        events.onPageFinished(url)
        super.onPageFinished(view, url)
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val decision = events.onRequest(request.url.toString(), request.requestHeaders?.get("Range")?.startsWith("bytes=0-") == true) { topUrl ->
            WebViewInterception.filterRequest(request, topUrl)
        }
        return WebViewInterception.response(decision) ?: super.shouldInterceptRequest(view, request)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("OverridingDeprecatedMember")
    override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? =
        events.onUrlRequest(url)?.let(WebViewInterception::response)

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url?.toString().orEmpty()
        val redirect = request.isRedirect || !request.hasGesture()
        return events.onNavigation(url, request.isForMainFrame, redirect)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("OverridingDeprecatedMember")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = events.onNavigation(url, mainFrame = true,
        isRedirect = view.hitTestResult.type == WebView.HitTestResult.UNKNOWN_TYPE)

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) events.onError(WebViewRequests.loadError(request, error))
        super.onReceivedError(view, request, error)
    }

    override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String?) =
        events.onHttpAuth(WebViewRequests.httpAuth(handler, host, realm))

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) =
        events.onSslError(WebViewRequests.sslError(handler, error))

    override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) =
        events.onFormResubmission(WebViewRequests.formResubmission(dontResend, resend))
}

/** Translates WebChromeClient callbacks into [PageEvents]. */
internal class PageChromeClient(private val events: PageEvents) : WebChromeClient() {
    override fun getDefaultVideoPoster(): Bitmap = createBitmap(1, 1)

    private fun dialog(kind: JsDialogRequest.Kind, url: String, message: String?, defaultValue: String?, result: JsResult) =
        events.onJsDialog(WebViewRequests.jsDialog(kind, url, message, defaultValue, result))

    override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean =
        dialog(JsDialogRequest.Kind.ALERT, url, message, null, result)

    override fun onJsBeforeUnload(view: WebView, url: String, message: String, result: JsResult): Boolean =
        dialog(JsDialogRequest.Kind.BEFORE_UNLOAD, url, message, null, result)

    override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean =
        dialog(JsDialogRequest.Kind.CONFIRM, url, message, null, result)

    override fun onJsPrompt(view: WebView, url: String, message: String?, defaultValue: String?, result: JsPromptResult): Boolean =
        dialog(JsDialogRequest.Kind.PROMPT, url, message, defaultValue, result)

    override fun onProgressChanged(view: WebView, progress: Int) {
        events.onProgressChanged(progress)
        super.onProgressChanged(view, progress)
    }

    override fun onReceivedTitle(view: WebView, title: String) {
        events.onReceivedTitle(title)
        super.onReceivedTitle(view, title)
    }

    override fun onReceivedIcon(view: WebView, icon: Bitmap?) = events.onReceivedIcon(icon)

    override fun onReceivedTouchIconUrl(view: WebView, url: String, precomposed: Boolean) = events.onReceivedTouchIconUrl(url)

    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
        events.onCreateWindow(WebViewRequests.Popup(isDialog, isUserGesture, resultMsg))
        return true
    }

    override fun onCloseWindow(window: WebView) = events.onCloseWindow()

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) =
        events.onGeolocationPrompt(WebViewRequests.location(origin, callback))

    override fun onGeolocationPermissionsHidePrompt() = events.onGeolocationHidePrompt()

    override fun onPermissionRequest(request: PermissionRequest) = events.onPermissionRequest(WebViewRequests.media(request))

    override fun onPermissionRequestCanceled(request: PermissionRequest) = events.onPermissionRequestCanceled(WebViewRequests.media(request))

    override fun onShowCustomView(view: View, callback: CustomViewCallback) =
        events.onShowFullscreen(FullscreenRequest(view, callback::onCustomViewHidden))

    @Deprecated("Deprecated in Java")
    @Suppress("OverridingDeprecatedMember")
    override fun onShowCustomView(view: View, requestedOrientation: Int, callback: CustomViewCallback) =
        onShowCustomView(view, callback)

    override fun onHideCustomView() = events.onHideFullscreen()

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean = events.onFileChooser(WebViewRequests.fileChooser(filePathCallback, fileChooserParams))
}
