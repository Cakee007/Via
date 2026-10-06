package dev.ujhhgtg.via.engine

/** What a backend supports. UI hides or degrades features whose flag is false. */
data class Capabilities(
    /** Page JavaScript can call native code and get a return value synchronously. */
    val syncScriptBridge: Boolean = true,
    /** GM_xmlhttpRequest({synchronous: true}) blocks until the response arrives. */
    val syncGmXhr: Boolean = true,
    /** Pages can be saved as a single-file MHT archive. */
    val mhtArchive: Boolean = true,
    /** The SSL error dialog can proceed to the page. */
    val sslProceed: Boolean = true,
    /** Text zoom can differ per page; otherwise it is runtime-wide. */
    val perPageTextZoom: Boolean = true,
    /** The engine can darken pages itself; otherwise night mode only sets the pages' preferred color scheme. */
    val algorithmicDarkening: Boolean = true,
    /** User-Agent Client Hints metadata can be overridden. */
    val userAgentMetadata: Boolean = true,
    /** A tab may hold several live pages for QuickBack. */
    val quickBackSegments: Boolean = true,
    /** Pages can render with a transparent background so the native window background shows through. */
    val translucentPages: Boolean = true,
    /** WebExtensions can be installed. */
    val webExtensions: Boolean = false,
)
