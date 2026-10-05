package dev.ujhhgtg.via.engine

import android.graphics.Bitmap

/**
 * What an [EnginePage] reports to the app. Each backend translates its own client/delegate
 * callbacks into these; Via's per-page policy lives behind this interface.
 * Calls arrive on the main thread unless noted.
 */
interface PageEvents {
    fun onPageStarted(url: String)
    fun onPageFinished(url: String)
    fun onProgressChanged(progress: Int)
    fun onReceivedTitle(title: String)
    fun onReceivedIcon(icon: Bitmap?)
    fun onReceivedTouchIconUrl(url: String)

    /** A navigation is about to start; true cancels it. */
    fun onNavigation(url: String, mainFrame: Boolean, isRedirect: Boolean): Boolean

    /** Decides a network request. Called on the engine's network thread. */
    fun onRequest(request: ResourceRequest): InterceptDecision
    /** Requests that only expose a URL (WebView's legacy interception path); only virtual resources are served. */
    fun onUrlRequest(url: String): InterceptDecision?

    fun onDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?, size: Long)
    fun onError(error: LoadError)
    fun onHttpAuth(request: HttpAuthRequest)
    fun onSslError(request: SslErrorRequest)
    fun onFormResubmission(request: FormResubmissionRequest)
    /** True when the dialog was handled; false lets the engine show its own. */
    fun onJsDialog(request: JsDialogRequest): Boolean
    fun onCreateWindow(request: PopupRequest)
    fun onCloseWindow()
    fun onGeolocationPrompt(request: LocationRequest)
    fun onGeolocationHidePrompt()
    fun onPermissionRequest(request: MediaPermissionRequest)
    fun onPermissionRequestCanceled(request: MediaPermissionRequest)
    fun onShowFullscreen(request: FullscreenRequest)
    fun onHideFullscreen()
    /** True when the app shows a chooser and will complete [request]. */
    fun onFileChooser(request: FileChooserRequest): Boolean
}
