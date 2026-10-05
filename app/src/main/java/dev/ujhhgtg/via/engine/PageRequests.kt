package dev.ujhhgtg.via.engine

import android.content.Intent
import android.net.Uri
import android.net.http.SslCertificate

/*
 * Engine-neutral payloads for page callbacks. Each backend wraps its own handler objects in these,
 * so UI code never sees android.webkit (or GeckoView) types.
 */

/** A failed main-frame load. */
class LoadError(val url: String, val method: String, val kind: Kind) {
    enum class Kind { HOST_LOOKUP, CONNECT, IO, TIMEOUT, UNKNOWN, OTHER }

    /** Errors that may be caused by a missing local-network permission. */
    val mayBeNetworkUnreachable: Boolean get() = kind != Kind.OTHER
}

abstract class HttpAuthRequest(val host: String, val realm: String?) {
    abstract fun proceed(username: String, password: String)
    abstract fun cancel()
}

abstract class SslErrorRequest(
    val url: String,
    val primaryError: Kind,
    val errors: Set<Kind>,
    val certificate: SslCertificate?,
) {
    enum class Kind { UNTRUSTED, DATE_INVALID, EXPIRED, ID_MISMATCH, NOT_YET_VALID, INVALID }
    abstract fun proceed()
    abstract fun cancel()
}

/** A page asked to open a new window. The engine holds it until [deny] or the tab controller attaches a page. */
abstract class PopupRequest(val isDialog: Boolean, val userGesture: Boolean) {
    /** Whether a page can be attached; when false the request can only be denied. */
    abstract val canAttach: Boolean
    /** Loads the window's content into [page], a fresh page from the same backend. */
    abstract fun attach(page: EnginePage)
    abstract fun deny()
}

/** alert/confirm/prompt/beforeunload. Exactly one of [confirm] or [cancel] must be called. */
abstract class JsDialogRequest(val kind: Kind, val url: String, val message: String?, val defaultValue: String?) {
    enum class Kind { ALERT, CONFIRM, PROMPT, BEFORE_UNLOAD }
    /** [text] is the prompt answer; other kinds ignore it. */
    abstract fun confirm(text: String = "")
    abstract fun cancel()
}

abstract class FormResubmissionRequest {
    abstract fun resend()
    abstract fun cancel()
}

abstract class LocationRequest(val origin: String) {
    abstract fun respond(allow: Boolean, retain: Boolean)
}

abstract class MediaPermissionRequest(val origin: String, val resources: Set<Resource>) {
    enum class Resource { AUDIO_CAPTURE, VIDEO_CAPTURE, PROTECTED_MEDIA }
    abstract fun grant(resources: Set<Resource>)
    abstract fun deny()
}

abstract class FileChooserRequest(val acceptTypes: List<String>, val title: String?) {
    /** The backend's default chooser intent; callers may adjust its type and extras. */
    abstract fun createIntent(): Intent
    /** Completes with the activity result; null [data] or a cancelled result clears the selection. */
    abstract fun complete(resultCode: Int, data: Intent?)
    abstract fun cancel()
    /** Explicit selection, for callers that collect URIs themselves. */
    abstract fun complete(uris: Array<Uri>?)
}

/** Fullscreen content supplied by the engine, and the callback that tells it the host left fullscreen. */
class FullscreenRequest(val view: android.view.View, private val onExit: () -> Unit) {
    fun exited() = onExit()
}
