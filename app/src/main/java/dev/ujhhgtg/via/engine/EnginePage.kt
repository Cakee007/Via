package dev.ujhhgtg.via.engine

import android.graphics.Bitmap
import android.net.http.SslCertificate
import android.os.Bundle
import android.print.PrintDocumentAdapter
import android.view.View
import dev.ujhhgtg.via.browser.ViaBridge
import dev.ujhhgtg.via.browser.script.ScriptBridge

/**
 * One rendered page (a QuickBack segment) as the UI sees it. Backends wrap their native view;
 * UI code reaches the engine only through this interface.
 */
interface EnginePage {
    /** The view to attach to the browser host. Treat as opaque: size, position and visibility only. */
    val view: View

    /** Unique among live pages; identifies the page in messages from its document. */
    val id: Int get() = view.id

    val url: String?
    val title: String?
    val progress: Int
    val favicon: Bitmap?
    val certificate: SslCertificate?
    val userAgent: String?
    val javaScriptEnabled: Boolean

    /** Page-independent settings; applied at creation and again when preferences change. */
    fun configure(config: EngineConfig)
    /** Settings chosen per document from site configuration; applied before each navigation. */
    fun apply(settings: PageSettings)
    fun setDarkening(enabled: Boolean)

    /** Exposes the page bridges to the document's JavaScript. Called once, before the first load. */
    fun installBridges(via: ViaBridge, scripts: ScriptBridge?)

    /** Also accepts `javascript:` URLs. */
    fun load(url: String, headers: Map<String, String> = emptyMap())
    fun reload()
    fun stopLoading()

    val canGoBack: Boolean
    val canGoForward: Boolean
    fun goBack()
    fun goForward()

    val scrollX: Int
    val scrollY: Int
    fun scrollTo(x: Int, y: Int)

    /** Writes the back-forward state into [out]; false when the engine has nothing to save. */
    fun saveState(out: Bundle): Boolean
    /** Restores a state from [saveState]; returns the restored current URL (empty when there is none), or null on failure. */
    fun restoreState(state: Bundle): String?

    /** Detaches and releases the page. It must not be used afterwards. */
    fun destroy()

    /** Runs [script] in the main frame; [callback] receives the JSON-encoded result ("null" when there is none). */
    fun evaluate(script: String, callback: ((String) -> Unit)? = null)

    /** Starts a find; [listener] receives (activeIndex, total, finished). An empty query clears it. */
    fun find(query: String, listener: ((Int, Int, Boolean) -> Unit)?)
    fun findNext(forward: Boolean)

    /** [toEdge] jumps to the top/bottom instead of scrolling one page. */
    fun pageUp(toEdge: Boolean)
    fun pageDown(toEdge: Boolean)

    /** Live text-zoom preview; navigation re-applies the configured value. */
    fun setTextZoom(percent: Int)

    /** Reads the rendered top-left pixel, or 0 when it can't be read. */
    fun sampleTopLeftPixel(callback: (Int) -> Unit)

    /** True once the current touch sequence reached a horizontal scroll edge, so a page swipe may start. */
    val edgeGestureReady: Boolean

    /** [direction] < 0 checks left, > 0 checks right. */
    fun canScrollHorizontally(direction: Int): Boolean

    /**
     * Customizes the text-selection toolbar. [onClick] receives the action id and the selected text.
     * The backend also hides its own web-search entry, since the app provides Search.
     */
    fun setSelectionActions(actions: List<SelectionAction>, onClick: ((Int, String) -> Unit)?)
    fun finishSelection()

    /** Long-press element menu; null removes it. */
    fun setContextMenuHandler(handler: ContextMenuHandler?)

    fun pause()
    fun resume()

    fun createPrintAdapter(title: String): PrintDocumentAdapter?

    /** Saves a single-file archive; [callback] receives the written path, or null. */
    fun saveArchive(path: String, callback: (String?) -> Unit)
}

/** Settings that don't depend on the loaded document. */
data class EngineConfig(
    val multipleWindows: Boolean,
    val safeBrowsing: Boolean,
    val acceptCookies: Boolean,
    val thirdPartyCookies: Boolean,
    val remoteDebugging: Boolean,
    val darkening: Boolean,
)

/** Settings resolved for one document from global preferences and its site configuration. */
data class PageSettings(
    val javaScript: Boolean,
    val images: Boolean,
    /** Null restores the engine default. */
    val userAgent: String?,
    val textZoom: Int,
)

/** A selection-toolbar entry. [hide] removes a platform entry with the same title instead of adding one. */
data class SelectionAction(val id: Int, val title: String, val hide: Boolean = false)

/** The element under a long press, with the screen position of the press. */
class ContextTarget(val linkUrl: String?, val srcUrl: String?, val title: String?, val rawX: Int, val rawY: Int)

interface ContextMenuHandler {
    /** Read on every touch event; while false, long presses are not probed. */
    val enabled: Boolean
    fun onContextMenu(target: ContextTarget)
}
