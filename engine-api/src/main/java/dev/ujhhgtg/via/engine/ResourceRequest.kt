package dev.ujhhgtg.via.engine

/** A network request as the engine reports it, before the app's filtering decides it. */
class ResourceRequest(
    val url: String,
    val isMainFrame: Boolean,
    val headers: Map<String, String> = emptyMap(),
    /** The engine's own classification when it has one; null lets the app infer it from the URL and headers. */
    val type: Type? = null,
) {
    enum class Type { DOCUMENT, SUBDOCUMENT, SCRIPT, STYLESHEET, IMAGE, MEDIA, FONT, XHR, WEBSOCKET, OTHER }
}
