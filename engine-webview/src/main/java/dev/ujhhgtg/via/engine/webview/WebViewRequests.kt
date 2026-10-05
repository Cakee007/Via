package dev.ujhhgtg.via.engine.webview

import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.os.Message
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
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.ujhhgtg.via.engine.EnginePage
import dev.ujhhgtg.via.engine.FileChooserRequest
import dev.ujhhgtg.via.engine.FormResubmissionRequest
import dev.ujhhgtg.via.engine.HttpAuthRequest
import dev.ujhhgtg.via.engine.JsDialogRequest
import dev.ujhhgtg.via.engine.LoadError
import dev.ujhhgtg.via.engine.LocationRequest
import dev.ujhhgtg.via.engine.MediaPermissionRequest
import dev.ujhhgtg.via.engine.PopupRequest
import dev.ujhhgtg.via.engine.SslErrorRequest

/** Adapters from android.webkit callback objects to the engine-neutral requests. */
internal object WebViewRequests {
    fun loadError(request: WebResourceRequest, error: WebResourceError) = LoadError(request.url.toString(), request.method.orEmpty(),
        when (error.errorCode) {
            WebViewClient.ERROR_HOST_LOOKUP -> LoadError.Kind.HOST_LOOKUP
            WebViewClient.ERROR_CONNECT -> LoadError.Kind.CONNECT
            WebViewClient.ERROR_IO -> LoadError.Kind.IO
            WebViewClient.ERROR_TIMEOUT -> LoadError.Kind.TIMEOUT
            WebViewClient.ERROR_UNKNOWN -> LoadError.Kind.UNKNOWN
            else -> LoadError.Kind.OTHER
        })

    fun httpAuth(handler: HttpAuthHandler, host: String, realm: String?) = object : HttpAuthRequest(host, realm) {
        override fun proceed(username: String, password: String) = handler.proceed(username, password)
        override fun cancel() = handler.cancel()
    }

    private val sslKinds = listOf(
        SslError.SSL_UNTRUSTED to SslErrorRequest.Kind.UNTRUSTED,
        SslError.SSL_DATE_INVALID to SslErrorRequest.Kind.DATE_INVALID,
        SslError.SSL_EXPIRED to SslErrorRequest.Kind.EXPIRED,
        SslError.SSL_IDMISMATCH to SslErrorRequest.Kind.ID_MISMATCH,
        SslError.SSL_NOTYETVALID to SslErrorRequest.Kind.NOT_YET_VALID,
        SslError.SSL_INVALID to SslErrorRequest.Kind.INVALID,
    )

    fun sslError(handler: SslErrorHandler, error: SslError) = object : SslErrorRequest(error.url.orEmpty(),
        sslKinds.firstOrNull { it.first == error.primaryError }?.second ?: Kind.INVALID,
        sslKinds.filter { error.hasError(it.first) }.map { it.second }.toSet(), error.certificate) {
        override fun proceed() = handler.proceed()
        override fun cancel() = handler.cancel()
    }

    /** Holds the WebViewTransport message until a popup WebView is attached or the request is denied. */
    class Popup(isDialog: Boolean, userGesture: Boolean, private val message: Message) : PopupRequest(isDialog, userGesture) {
        private var done = false
        override fun deny() { if (!done) { done = true; message.sendToTarget() } }
        override fun attach(page: EnginePage) {
            if (done) return
            done = true
            (message.obj as? WebView.WebViewTransport)?.webView = (page as WebViewPage).webView
            message.sendToTarget()
        }
        override val canAttach: Boolean get() = message.obj is WebView.WebViewTransport
    }

    fun jsDialog(kind: JsDialogRequest.Kind, url: String, message: String?, defaultValue: String?, result: JsResult) =
        object : JsDialogRequest(kind, url, message, defaultValue) {
            override fun confirm(text: String) { if (result is JsPromptResult) result.confirm(text) else result.confirm() }
            override fun cancel() = result.cancel()
        }

    fun formResubmission(dontResend: Message, resend: Message) = object : FormResubmissionRequest() {
        override fun resend() = resend.sendToTarget()
        override fun cancel() = dontResend.sendToTarget()
    }

    fun location(origin: String, callback: GeolocationPermissions.Callback) = object : LocationRequest(origin) {
        override fun respond(allow: Boolean, retain: Boolean) = callback.invoke(origin, allow, retain)
    }

    private val mediaKinds = mapOf(
        PermissionRequest.RESOURCE_AUDIO_CAPTURE to MediaPermissionRequest.Resource.AUDIO_CAPTURE,
        PermissionRequest.RESOURCE_VIDEO_CAPTURE to MediaPermissionRequest.Resource.VIDEO_CAPTURE,
        PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID to MediaPermissionRequest.Resource.PROTECTED_MEDIA,
    )

    /** Equal to any other adapter of the same platform request, so a cancellation matches the pending prompt. */
    private class Media(private val platform: PermissionRequest) :
        MediaPermissionRequest(platform.origin.toString(), platform.resources.mapNotNull(mediaKinds::get).toSet()) {
        override fun grant(resources: Set<Resource>) {
            val names = mediaKinds.filterValues { it in resources }.keys
            if (names.isEmpty()) platform.deny() else platform.grant(names.toTypedArray())
        }
        override fun deny() = platform.deny()
        override fun equals(other: Any?) = other is Media && other.platform === platform
        override fun hashCode() = System.identityHashCode(platform)
    }

    fun media(request: PermissionRequest): MediaPermissionRequest = Media(request)

    fun fileChooser(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams) =
        object : FileChooserRequest(params.acceptTypes?.toList().orEmpty(), params.title?.toString()) {
            private var done = false
            override fun createIntent(): Intent = params.createIntent()
            override fun complete(resultCode: Int, data: Intent?) = complete(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            override fun cancel() = complete(null)
            override fun complete(uris: Array<Uri>?) { if (!done) { done = true; callback.onReceiveValue(uris) } }
        }
}
